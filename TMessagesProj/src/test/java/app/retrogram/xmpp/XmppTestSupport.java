package app.retrogram.xmpp;

import static org.junit.Assert.fail;

import org.jivesoftware.smackx.chatstates.ChatState;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import javax.net.ssl.X509TrustManager;

import app.retrogram.xmpp.core.Callback;
import app.retrogram.xmpp.core.MemoryXmppStore;
import app.retrogram.xmpp.core.XmppAccountConfig;
import app.retrogram.xmpp.core.XmppConnectionState;
import app.retrogram.xmpp.core.XmppController;
import app.retrogram.xmpp.core.XmppError;
import app.retrogram.xmpp.core.XmppMessage;
import app.retrogram.xmpp.core.XmppSession;

/** Helpers for the tests that talk to a real XMPP server (a local Prosody with a self-signed certificate). */
final class XmppTestSupport {
    private XmppTestSupport() {
    }

    static final String DOMAIN = "localhost";

    static boolean serverAvailable() {
        String s = System.getenv("XMPP_TEST_SERVER");
        if (s == null || s.isEmpty()) {
            return false;
        }
        try (java.net.Socket sock = new java.net.Socket()) {
            sock.connect(new java.net.InetSocketAddress(s, 5222), 1000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Trusts every certificate. For the local test server only. */
    static X509TrustManager trustAll() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }

    static String unique(String prefix) {
        return prefix + Long.toString(System.nanoTime(), 36);
    }

    static XmppAccountConfig config(String user, long id) {
        XmppAccountConfig c = new XmppAccountConfig();
        c.id = id;
        c.jid = user + "@" + DOMAIN;
        c.password = "pw-" + user;
        c.host = "127.0.0.1";
        c.port = 5222;
        c.resource = "test";
        return c;
    }

    static void register(XmppAccountConfig cfg) throws Exception {
        AtomicReference<XmppError> err = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        XmppSession.register(cfg, trustAll(), new Callback<Void>() {
            @Override
            public void onResult(Void v) {
                done.countDown();
            }

            @Override
            public void onError(XmppError e) {
                err.set(e);
                done.countDown();
            }
        });
        if (!done.await(30, TimeUnit.SECONDS)) {
            fail("registration timed out");
        }
        if (err.get() != null) {
            throw err.get();
        }
    }

    /** A controller with an in-memory store that records every event. */
    static final class Client implements XmppController.Events {
        final XmppAccountConfig config;
        final MemoryXmppStore store = new MemoryXmppStore();
        final XmppController controller;
        final List<XmppMessage> notified = Collections.synchronizedList(new ArrayList<>());
        final List<String> subscribeRequests = Collections.synchronizedList(new ArrayList<>());
        final List<String> invitations = Collections.synchronizedList(new ArrayList<>());
        final List<String> typing = Collections.synchronizedList(new ArrayList<>());
        volatile XmppConnectionState state = XmppConnectionState.DISCONNECTED;
        volatile XmppError error;

        Client(XmppAccountConfig cfg) {
            this(cfg, trustAll());
        }

        /** {@code trust == null}: the real trust logic (platform store plus the pinned certificate). */
        Client(XmppAccountConfig cfg, javax.net.ssl.X509TrustManager trust) {
            this.config = cfg;
            this.controller = new XmppController(cfg, store, this, trust);
        }

        Client start() {
            controller.start();
            return this;
        }

        void awaitOnline() {
            await("online " + config.jid, () -> state == XmppConnectionState.ONLINE);
        }

        @Override
        public void onConnectionState(long accountId, XmppConnectionState s, XmppError e) {
            state = s;
            error = e;
        }

        @Override
        public void onNotify(XmppMessage m, app.retrogram.xmpp.core.XmppConversation c) {
            notified.add(m);
        }

        @Override
        public void onSubscribeRequest(long accountId, String jid) {
            subscribeRequests.add(jid);
        }

        @Override
        public void onInvitation(long accountId, String roomJid, String from, String reason, String password) {
            invitations.add(roomJid);
        }

        @Override
        public void onChatState(long accountId, String conversation, String from, ChatState s) {
            typing.add(conversation + ":" + s);
        }

        List<XmppMessage> messages(String conv) {
            return store.messages(config.id, conv, 0, 100);
        }

        void close() {
            controller.release();
        }
    }

    static void await(String what, BooleanSupplier cond) {
        await(what, 20, cond);
    }

    static void await(String what, int seconds, BooleanSupplier cond) {
        long end = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < end) {
            try {
                if (cond.getAsBoolean()) {
                    return;
                }
            } catch (RuntimeException ignored) {
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        fail("timed out waiting for: " + what);
    }
}
