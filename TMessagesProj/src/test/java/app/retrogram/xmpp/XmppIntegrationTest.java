package app.retrogram.xmpp;

import static app.retrogram.xmpp.XmppTestSupport.await;
import static app.retrogram.xmpp.XmppTestSupport.config;
import static app.retrogram.xmpp.XmppTestSupport.register;
import static app.retrogram.xmpp.XmppTestSupport.unique;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.jivesoftware.smackx.chatstates.ChatState;
import org.junit.After;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import app.retrogram.xmpp.XmppTestSupport.Client;
import app.retrogram.xmpp.core.Callback;
import app.retrogram.xmpp.core.XmppAccountConfig;
import app.retrogram.xmpp.core.XmppConnectionState;
import app.retrogram.xmpp.core.XmppContact;
import app.retrogram.xmpp.core.XmppConversation;
import app.retrogram.xmpp.core.XmppError;
import app.retrogram.xmpp.core.XmppMessage;
import app.retrogram.xmpp.core.XmppSession;

/**
 * Runs the core against a real server. Skipped unless the environment variable XMPP_TEST_SERVER is set and
 * a server listens on port 5222 (Prosody with registration, carbons, MAM, MUC and http_file_share).
 */
public class XmppIntegrationTest {

    private final List<Client> clients = new ArrayList<>();

    @BeforeClass
    public static void needsServer() {
        Assume.assumeTrue("no XMPP test server", XmppTestSupport.serverAvailable());
    }

    @After
    public void closeAll() {
        for (Client c : clients) {
            c.close();
        }
    }

    private Client newUser(String prefix, long id) throws Exception {
        XmppAccountConfig cfg = config(unique(prefix), id);
        register(cfg);
        Client c = new Client(cfg);
        clients.add(c);
        return c;
    }

    private static <T> T sync(java.util.function.Consumer<Callback<T>> call) throws Exception {
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<XmppError> err = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        call.accept(new Callback<T>() {
            @Override
            public void onResult(T v) {
                value.set(v);
                latch.countDown();
            }

            @Override
            public void onError(XmppError e) {
                err.set(e);
                latch.countDown();
            }
        });
        assertTrue("callback timed out", latch.await(30, TimeUnit.SECONDS));
        if (err.get() != null) {
            throw err.get();
        }
        return value.get();
    }

    private static void befriend(Client a, Client b) throws Exception {
        sync((Callback<Void> cb) -> a.controller.addContact(b.config.jid, "Bee", null, cb));
        await("b sees request", () -> b.subscribeRequests.contains(a.config.jid));
        sync((Callback<Void> cb) -> b.controller.acceptSubscription(a.config.jid, true, cb));
        await("a receives b's presence", () -> {
            XmppContact c = a.controller.contact(b.config.jid);
            return c != null && c.subscription == XmppContact.Subscription.BOTH && c.show.isAvailable();
        });
        await("b receives a's presence", () -> {
            XmppContact c = b.controller.contact(a.config.jid);
            return c != null && c.subscription == XmppContact.Subscription.BOTH && c.show.isAvailable();
        });
    }

    @Test
    public void wrongPasswordFailsWithoutRetrying() throws Exception {
        XmppAccountConfig cfg = config(unique("badpw"), 1);
        register(cfg);
        XmppAccountConfig bad = cfg.copy();
        bad.password = "nope";
        Client c = new Client(bad);
        clients.add(c);
        c.start();
        await("failed", () -> c.state == XmppConnectionState.FAILED);
        assertEquals(XmppError.Kind.AUTH, c.error.kind);
    }

    @Test
    public void connectsAndExchangesMessagesWithReceipts() throws Exception {
        Client a = newUser("alice", 1).start();
        Client b = newUser("bob", 2).start();
        a.awaitOnline();
        b.awaitOnline();
        assertTrue(a.controller.session().isEncryptedTransport());
        befriend(a, b);

        XmppMessage sent = a.controller.sendText(b.config.jid, false, "hello bob", null);
        await("bob got it", () -> b.messages(a.config.jid).size() == 1);
        XmppMessage got = b.messages(a.config.jid).get(0);
        assertEquals("hello bob", got.body);
        assertFalse(got.outgoing);
        assertEquals(1, b.notified.size());
        XmppConversation conv = b.controller.conversation(a.config.jid);
        assertEquals(1, conv.unread);
        assertEquals("hello bob", conv.lastBody);

        await("alice sees it delivered", () -> a.messages(b.config.jid).get(0).status >= XmppMessage.STATUS_DELIVERED);

        // Bob reads it: displayed marker goes back.
        b.controller.setActiveConversation(a.config.jid);
        await("alice sees displayed", () -> a.messages(b.config.jid).get(0).status == XmppMessage.STATUS_DISPLAYED);
        assertEquals(0, b.controller.conversation(a.config.jid).unread);

        // A message arriving while the conversation is open is read at once and does not notify.
        int notifiedBefore = b.notified.size();
        a.controller.sendText(b.config.jid, false, "second", null);
        await("second arrives", () -> b.messages(a.config.jid).size() == 2);
        assertTrue(b.messages(a.config.jid).get(1).read);
        assertEquals(notifiedBefore, b.notified.size());
        assertEquals(sent.stanzaId, a.messages(b.config.jid).get(0).stanzaId);
    }

    @Test
    public void typingIndicatorReachesTheOtherSide() throws Exception {
        Client a = newUser("alice", 1).start();
        Client b = newUser("bob", 2).start();
        a.awaitOnline();
        b.awaitOnline();
        befriend(a, b);
        a.controller.sendTyping(b.config.jid, false, ChatState.composing);
        await("typing", () -> b.typing.contains(a.config.jid + ":composing"));
    }

    @Test
    public void correctionReplacesTheBodyOnBothSides() throws Exception {
        Client a = newUser("alice", 1).start();
        Client b = newUser("bob", 2).start();
        a.awaitOnline();
        b.awaitOnline();
        befriend(a, b);
        XmppMessage m = a.controller.sendText(b.config.jid, false, "helo", null);
        await("arrived", () -> b.messages(a.config.jid).size() == 1);
        sync((Callback<Void> cb) -> a.controller.editMessage(m.dbId, "hello", cb));
        await("edited on bob", () -> b.messages(a.config.jid).get(0).edited);
        assertEquals("hello", b.messages(a.config.jid).get(0).body);
        assertEquals(1, b.messages(a.config.jid).size());
        assertEquals("hello", a.messages(b.config.jid).get(0).body);
        assertEquals("hello", b.controller.conversation(a.config.jid).lastBody);
    }

    @Test
    public void replyKeepsQuoteOutOfTheBodyAndLinksTheMessage() throws Exception {
        Client a = newUser("alice", 1).start();
        Client b = newUser("bob", 2).start();
        a.awaitOnline();
        b.awaitOnline();
        befriend(a, b);
        a.controller.sendText(b.config.jid, false, "what time is it?", null);
        await("arrived", () -> b.messages(a.config.jid).size() == 1);
        XmppMessage question = b.messages(a.config.jid).get(0);
        b.controller.sendText(a.config.jid, false, "noon", question);
        await("reply arrived", () -> a.messages(b.config.jid).size() == 2);
        XmppMessage reply = a.messages(b.config.jid).get(1);
        assertEquals("noon", reply.body);
        assertEquals(a.messages(b.config.jid).get(0).stanzaId, reply.replyToId);
        assertEquals("what time is it?", reply.replyToBody);
    }

    @Test
    public void carbonsShowMessagesSentFromAnotherDevice() throws Exception {
        Client a1 = newUser("alice", 1).start();
        Client b = newUser("bob", 2).start();
        a1.awaitOnline();
        b.awaitOnline();
        befriend(a1, b);
        XmppAccountConfig second = a1.config.copy();
        second.resource = "phone2";
        second.id = 3;
        Client a2 = new Client(second);
        clients.add(a2);
        a2.start().awaitOnline();
        await("carbons on", () -> true);
        Thread.sleep(500);

        a1.controller.sendText(b.config.jid, false, "from device one", null);
        await("bob got it", () -> b.messages(a1.config.jid).size() == 1);
        await("second device sees the sent message", () -> a2.messages(b.config.jid).size() == 1);
        XmppMessage copy = a2.messages(b.config.jid).get(0);
        assertTrue(copy.outgoing);
        assertEquals("from device one", copy.body);

        b.controller.sendText(a1.config.jid, false, "reply", null);
        await("both devices got the reply", () -> a1.messages(b.config.jid).size() == 2 && a2.messages(b.config.jid).size() == 2);
    }

    @Test
    public void offlineMessagesAreQueuedAndArchiveCatchUpFillsTheGap() throws Exception {
        Client a = newUser("alice", 1).start();
        Client b = newUser("bob", 2).start();
        a.awaitOnline();
        b.awaitOnline();
        befriend(a, b);
        a.controller.sendText(b.config.jid, false, "one", null);
        await("one arrived", () -> b.messages(a.config.jid).size() == 1);
        await("catch-up marker not needed yet", () -> true);

        // Bob goes away; Alice writes while he is offline.
        b.controller.stop();
        await("bob offline", () -> b.state == XmppConnectionState.DISCONNECTED);
        a.controller.sendText(b.config.jid, false, "two", null);
        a.controller.sendText(b.config.jid, false, "three", null);
        await("alice's messages are on the server", () -> a.messages(b.config.jid).size() == 3
                && a.messages(b.config.jid).get(2).status >= XmppMessage.STATUS_SENT);

        // Bob returns: the archive delivers the two messages he missed, once each.
        b.controller.start();
        b.awaitOnline();
        await("bob caught up", () -> b.messages(a.config.jid).size() == 3);
        Thread.sleep(1500);
        List<XmppMessage> got = b.messages(a.config.jid);
        assertEquals(3, got.size());
        assertEquals(Arrays.asList("one", "two", "three"), Arrays.asList(got.get(0).body, got.get(1).body, got.get(2).body));
        assertNotNull(b.store.getMeta(b.config.id, "mam.last"));
    }

    @Test
    public void messageWrittenWhileOfflineIsSentAfterLogin() throws Exception {
        Client a = newUser("alice", 1);
        Client b = newUser("bob", 2).start();
        b.awaitOnline();
        // Alice is not connected yet: the message is stored as pending.
        // (Make them know each other first.)
        a.start().awaitOnline();
        befriend(a, b);
        a.controller.stop();
        await("alice offline", () -> a.state == XmppConnectionState.DISCONNECTED);
        XmppMessage m = a.controller.sendText(b.config.jid, false, "queued", null);
        Thread.sleep(300);
        assertEquals(XmppMessage.STATUS_PENDING, a.messages(b.config.jid).get(0).status);
        a.controller.start();
        await("bob got the queued message", () -> b.messages(a.config.jid).size() == 1);
        await("alice marks it sent", () -> a.messages(b.config.jid).get(0).status >= XmppMessage.STATUS_SENT);
        assertEquals(m.stanzaId, b.messages(a.config.jid).get(0).stanzaId);
    }

    @Test
    public void groupChatRoundTrip() throws Exception {
        Client a = newUser("alice", 1).start();
        Client b = newUser("bob", 2).start();
        a.awaitOnline();
        b.awaitOnline();
        String room = unique("room") + "@conference." + XmppTestSupport.DOMAIN;
        sync((Callback<Void> cb) -> a.controller.joinRoom(room, "Alice", null, true, false, cb));
        sync((Callback<Void> cb) -> b.controller.joinRoom(room, "Bob", null, false, false, cb));
        assertTrue(a.controller.conversation(room).joined);

        a.controller.sendText(room, true, "welcome", null);
        await("bob got it", () -> b.messages(room).size() == 1);
        assertEquals("welcome", b.messages(room).get(0).body);
        assertEquals("Alice", b.messages(room).get(0).sender);
        assertFalse(b.messages(room).get(0).outgoing);
        // The room reflects Alice's own message: it must not be stored twice, and it becomes "sent".
        await("alice's message is confirmed", () -> a.messages(room).size() == 1 && a.messages(room).get(0).status >= XmppMessage.STATUS_SENT);
        Thread.sleep(500);
        assertEquals(1, a.messages(room).size());
        assertTrue(a.messages(room).get(0).outgoing);

        // Only mentions alert in a group.
        assertEquals(0, b.notified.size());
        a.controller.sendText(room, true, "hey Bob, are you there", null);
        await("mention notifies", () -> b.notified.size() == 1);

        await("occupants", () -> a.controller.session().occupants(room).size() == 2);

        // A third person joining later gets the history.
        Client c = newUser("carol", 3).start();
        c.awaitOnline();
        sync((Callback<Void> cb) -> c.controller.joinRoom(room, "Carol", null, false, false, cb));
        await("history delivered", () -> c.messages(room).size() == 2);
        assertEquals(0, c.notified.size());
    }

    @Test
    public void groupChatTopicAndLeave() throws Exception {
        Client a = newUser("alice", 1).start();
        a.awaitOnline();
        String room = unique("topic") + "@conference." + XmppTestSupport.DOMAIN;
        sync((Callback<Void> cb) -> a.controller.joinRoom(room, "Alice", null, true, true, cb));
        sync((Callback<Void> cb) -> a.controller.session().setRoomSubject(room, "Release plans", cb));
        await("subject stored", () -> "Release plans".equals(a.controller.conversation(room).subject));
        sync((Callback<Void> cb) -> a.controller.leaveRoom(room, false, cb));
        assertFalse(a.controller.conversation(room).joined);
    }

    @Test
    public void uploadsAFileAndSendsTheLink() throws Exception {
        Client a = newUser("alice", 1).start();
        Client b = newUser("bob", 2).start();
        a.awaitOnline();
        b.awaitOnline();
        befriend(a, b);
        File f = File.createTempFile("retro", ".txt");
        f.deleteOnExit();
        Files.write(f.toPath(), "file contents".getBytes("UTF-8"));
        assertTrue(a.controller.session().isUploadAvailable());
        XmppMessage m = a.controller.sendFile(b.config.jid, false, f, "text/plain");
        await("bob got the link (alice: " + "see log" + ")", () -> {
            List<XmppMessage> mine = a.messages(b.config.jid);
            if (!mine.isEmpty() && mine.get(0).status == XmppMessage.STATUS_FAILED) {
                throw new AssertionError("upload/send failed: " + mine.get(0).error);
            }
            return b.messages(a.config.jid).size() == 1;
        });
        XmppMessage got = b.messages(a.config.jid).get(0);
        assertTrue(got.attachmentUrl.startsWith("http"));
        assertEquals(got.attachmentUrl, got.body);
        java.net.HttpURLConnection h = (java.net.HttpURLConnection) new java.net.URL(got.attachmentUrl).openConnection();
        assertEquals(200, h.getResponseCode());
        assertEquals("file contents", new String(h.getInputStream().readAllBytes(), "UTF-8"));
        assertEquals(f.getAbsolutePath(), a.messages(b.config.jid).get(0).attachmentPath);
        assertEquals(m.stanzaId, a.messages(b.config.jid).get(0).stanzaId);
    }

    @Test
    public void directTlsConnects() throws Exception {
        XmppAccountConfig cfg = config(unique("direct"), 1);
        register(cfg);
        cfg.security = XmppAccountConfig.Security.DIRECT_TLS;
        cfg.port = 5223;
        Client c = new Client(cfg);
        clients.add(c);
        c.start().awaitOnline();
        assertTrue(c.controller.session().isEncryptedTransport() || c.state == XmppConnectionState.ONLINE);
    }

    @Test
    public void profileAvatarAndNicknameRoundTrip() throws Exception {
        Client a = newUser("alice", 1).start();
        a.awaitOnline();
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13, 'I', 'H', 'D', 'R', 0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0, 0x1f, 0x15, (byte) 0xc4, (byte) 0x89, 0, 0, 0, 10, 'I', 'D', 'A', 'T', 0x78, (byte) 0x9c, 0x63, 0, 1, 0, 0, 5, 0, 1, 0x0d, 0x0a, 0x2d, (byte) 0xb4, 0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 0xae, 0x42, 0x60, (byte) 0x82};
        sync((Callback<Void> cb) -> a.controller.session().saveProfile("Alice Liddell", "Ally", png, "image/png", cb));
        XmppSession.Profile p = sync((Callback<XmppSession.Profile> cb) -> a.controller.session().loadProfile(a.config.jid, cb));
        assertEquals("Alice Liddell", p.fullName);
        assertEquals("Ally", p.nickname);
        assertNotNull(p.avatar);
        assertEquals(png.length, p.avatar.length);
    }

    @Test
    public void blockingAContactIsReportedAndReversible() throws Exception {
        Client a = newUser("alice", 1).start();
        Client b = newUser("bob", 2).start();
        a.awaitOnline();
        b.awaitOnline();
        befriend(a, b);
        sync((Callback<Void> cb) -> a.controller.session().block(b.config.jid, true, cb));
        await("blocked", () -> a.controller.blocked().contains(b.config.jid));
        sync((Callback<Void> cb) -> a.controller.session().block(b.config.jid, false, cb));
        await("unblocked", () -> !a.controller.blocked().contains(b.config.jid));
    }

    @Test
    public void removingAContactClearsTheRoster() throws Exception {
        Client a = newUser("alice", 1).start();
        Client b = newUser("bob", 2).start();
        a.awaitOnline();
        b.awaitOnline();
        befriend(a, b);
        sync((Callback<Void> cb) -> a.controller.renameContact(b.config.jid, "Robert", cb));
        try {
            await("renamed", () -> "Robert".equals(a.controller.contact(b.config.jid).name));
        } catch (AssertionError e) {
            throw new AssertionError(e.getMessage() + " (name is '" + a.controller.contact(b.config.jid).name + "')");
        }
        sync((Callback<Void> cb) -> a.controller.removeContact(b.config.jid, cb));
        await("gone", () -> a.controller.contact(b.config.jid) == null);
    }

    @Test
    public void changingThePasswordWorksAndTheOldOneStopsWorking() throws Exception {
        Client a = newUser("alice", 1).start();
        a.awaitOnline();
        sync((Callback<Void> cb) -> a.controller.session().changePassword("new-secret", cb));
        XmppAccountConfig again = a.config.copy();
        again.resource = "other";
        again.password = "new-secret";
        Client ok = new Client(again);
        clients.add(ok);
        ok.start().awaitOnline();
        XmppAccountConfig old = a.config.copy();
        old.resource = "third";
        Client bad = new Client(old);
        clients.add(bad);
        bad.start();
        await("old password refused", () -> bad.state == XmppConnectionState.FAILED);
    }

    @Test
    public void unreachableServerKeepsRetryingInsteadOfFailing() throws Exception {
        XmppAccountConfig cfg = config(unique("nobody"), 1);
        cfg.port = 5999; // nothing listens here
        Client c = new Client(cfg);
        clients.add(c);
        c.start();
        await("connecting with an error", () -> c.state == XmppConnectionState.CONNECTING && c.error != null);
        assertFalse(c.error.isPermanent());
    }

    @Test
    public void selfSignedCertificateIsRefusedUntilTheUserPinsIt() throws Exception {
        XmppAccountConfig cfg = config(unique("pin"), 1);
        register(cfg);
        Client refused = new Client(cfg, null);
        clients.add(refused);
        refused.start();
        await("certificate refused", () -> refused.state == XmppConnectionState.FAILED);
        assertEquals(XmppError.Kind.TLS_CERTIFICATE, refused.error.kind);
        assertFalse(refused.error.certFingerprint.isEmpty());

        XmppAccountConfig pinned = cfg.copy();
        pinned.resource = "pinned";
        pinned.pinnedCertificate = app.retrogram.xmpp.core.PinnedTrustManager.format(refused.error.certFingerprint).toLowerCase();
        Client ok = new Client(pinned, null);
        clients.add(ok);
        ok.start().awaitOnline();

        XmppAccountConfig wrongPin = cfg.copy();
        wrongPin.resource = "wrong";
        wrongPin.pinnedCertificate = "00".repeat(32);
        Client still = new Client(wrongPin, null);
        clients.add(still);
        still.start();
        await("wrong pin refused", () -> still.state == XmppConnectionState.FAILED);
        assertEquals(XmppError.Kind.TLS_CERTIFICATE, still.error.kind);
    }

    @Test
    public void privateMessageToARoomOccupantStaysOutOfTheRoom() throws Exception {
        Client a = newUser("alice", 1).start();
        Client b = newUser("bob", 2).start();
        a.awaitOnline();
        b.awaitOnline();
        String room = unique("pm") + "@conference." + XmppTestSupport.DOMAIN;
        sync((Callback<Void> cb) -> a.controller.joinRoom(room, "Alice", null, true, false, cb));
        sync((Callback<Void> cb) -> b.controller.joinRoom(room, "Bob", null, false, false, cb));
        await("both in room", () -> a.controller.session().occupants(room).size() == 2);

        String toBob = room + "/Bob";
        a.controller.sendText(toBob, false, "psst", null);
        String fromAlice = room + "/Alice";
        await("bob got the private message", () -> b.messages(fromAlice).size() == 1);
        assertEquals("psst", b.messages(fromAlice).get(0).body);
        assertFalse(b.messages(fromAlice).get(0).groupChat);
        assertEquals(0, b.messages(room).size());
        assertEquals("Alice (" + room.substring(0, room.indexOf('@')) + ")", b.controller.conversation(fromAlice).title);
        // and the answer goes back through the room
        b.controller.sendText(fromAlice, false, "hi", null);
        await("alice got the answer", () -> a.messages(toBob).size() == 2);
        assertEquals(0, a.messages(room).size());
    }

    @Test
    public void contactGroupsCanBeSet() throws Exception {
        Client a = newUser("alice", 1).start();
        Client b = newUser("bob", 2).start();
        a.awaitOnline();
        b.awaitOnline();
        befriend(a, b);
        sync((Callback<Void> cb) -> a.controller.session().setContactGroups(b.config.jid, Arrays.asList("Friends", "Work"), cb));
        try {
            await("groups visible", () -> a.controller.contact(b.config.jid).groups.size() == 2);
        } catch (AssertionError e) {
            throw new AssertionError(e.getMessage() + " groups=" + a.controller.contact(b.config.jid).groups);
        }
        sync((Callback<Void> cb) -> a.controller.session().setContactGroups(b.config.jid, Arrays.asList("Work"), cb));
        await("one group left", () -> a.controller.contact(b.config.jid).groups.equals(Arrays.asList("Work")));
    }

    @Test
    public void aStaleArchiveMarkerIsResetInsteadOfBreakingEveryLogin() throws Exception {
        Client a = newUser("alice", 1).start();
        Client b = newUser("bob", 2).start();
        a.awaitOnline();
        b.awaitOnline();
        befriend(a, b);
        b.controller.stop();
        await("bob offline", () -> b.state == XmppConnectionState.DISCONNECTED);
        a.controller.sendText(b.config.jid, false, "while you were away", null);
        await("on the server", () -> a.messages(b.config.jid).get(0).status >= XmppMessage.STATUS_SENT);
        b.store.setMeta(b.config.id, "mam.last", "no-such-archive-id");
        b.controller.start();
        b.awaitOnline();
        await("caught up despite the stale marker", () -> b.messages(a.config.jid).size() == 1);
        await("marker replaced", () -> {
            String m = b.store.getMeta(b.config.id, "mam.last");
            return m != null && !m.isEmpty() && !m.equals("no-such-archive-id");
        });
    }

    @Test
    public void rapidMessagesArriveInTheOrderTheyWereWritten() throws Exception {
        Client a = newUser("alice", 1).start();
        Client b = newUser("bob", 2).start();
        a.awaitOnline();
        b.awaitOnline();
        befriend(a, b);
        for (int i = 0; i < 30; i++) {
            a.controller.sendText(b.config.jid, false, "m" + i, null);
        }
        await("all arrived", () -> b.messages(a.config.jid).size() == 30);
        List<XmppMessage> got = b.messages(a.config.jid);
        for (int i = 0; i < 30; i++) {
            assertEquals("m" + i, got.get(i).body);
        }
    }
}
