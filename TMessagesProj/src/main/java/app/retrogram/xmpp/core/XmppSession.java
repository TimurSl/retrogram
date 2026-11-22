package app.retrogram.xmpp.core;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.MessageBuilder;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.PresenceBuilder;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.StanzaBuilder;
import org.jivesoftware.smack.packet.StandardExtensionElement;
import org.jivesoftware.smack.packet.XmlElement;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.RosterGroup;
import org.jivesoftware.smack.roster.RosterListener;
import org.jivesoftware.smack.roster.SubscribeListener;
import org.jivesoftware.smack.roster.packet.RosterPacket;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.bookmarks.BookmarkManager;
import org.jivesoftware.smackx.bookmarks.BookmarkedConference;
import org.jivesoftware.smackx.blocking.BlockingCommandManager;
import org.jivesoftware.smackx.carbons.CarbonManager;
import org.jivesoftware.smackx.chat_markers.element.ChatMarkersElements;
import org.jivesoftware.smackx.carbons.packet.CarbonExtension;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jivesoftware.smackx.chatstates.packet.ChatStateExtension;
import org.jivesoftware.smackx.delay.DelayInformationManager;
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.jivesoftware.smackx.forward.packet.Forwarded;
import org.jivesoftware.smackx.hints.element.NoStoreHint;
import org.jivesoftware.smackx.httpfileupload.HttpFileUploadManager;
import org.jivesoftware.smackx.httpfileupload.element.Slot;
import org.jivesoftware.smackx.iqregister.AccountManager;
import org.jivesoftware.smackx.mam.MamManager;
import org.jivesoftware.smackx.mam.element.MamElements;
import org.jivesoftware.smackx.message_correct.element.MessageCorrectExtension;
import org.jivesoftware.smackx.muc.InvitationListener;
import org.jivesoftware.smackx.muc.MucEnterConfiguration;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jivesoftware.smackx.muc.Occupant;
import org.jivesoftware.smackx.muc.RoomInfo;
import org.jivesoftware.smackx.muc.packet.MUCUser;
import org.jivesoftware.smackx.ping.PingFailedListener;
import org.jivesoftware.smackx.ping.PingManager;
import org.jivesoftware.smackx.receipts.DeliveryReceiptManager;
import org.jivesoftware.smackx.receipts.DeliveryReceiptRequest;
import org.jivesoftware.smackx.sid.element.OriginIdElement;
import org.jivesoftware.smackx.sid.element.StanzaIdElement;
import org.jivesoftware.smackx.vcardtemp.VCardManager;
import org.jivesoftware.smackx.vcardtemp.packet.VCard;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.parts.Resourcepart;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * One connection to one XMPP account. Owns the Smack connection, reconnects with a back-off, and turns
 * stanzas into the plain objects of this package. It does not store anything: see {@link XmppController}.
 *
 * <p>Supported: SASL (SCRAM, PLAIN), STARTTLS / direct TLS, stream management, roster and presence,
 * subscription requests, 1:1 chat with receipts (XEP-0184), displayed markers (XEP-0333), typing
 * (XEP-0085), carbons (XEP-0280), archive catch-up (XEP-0313), corrections (XEP-0308), replies (XEP-0461),
 * out-of-band links (XEP-0066), HTTP file upload (XEP-0363), group chat (XEP-0045) with bookmarks
 * (XEP-0048), vCard avatars (XEP-0054), blocking (XEP-0191), ping (XEP-0199), in-band registration
 * (XEP-0077).</p>
 *
 * <p>Not supported: end-to-end encryption (OMEMO / OpenPGP), calls (Jingle RTP), MIX, message retraction.</p>
 *
 * <p>Threading: public methods return immediately and run on internal executors. Listener callbacks
 * come from internal threads, one at a time per category but not on the same thread.</p>
 */
public final class XmppSession {

    public interface Listener {
        default void onState(XmppConnectionState state, XmppError error) {
        }

        /** The account is logged in and the roster is loaded; archive catch-up and room re-join happen after this. */
        default void onSessionStarted(boolean resumed) {
        }

        /** A message to show: incoming, sent from another device (carbon), group chat, or from the archive. */
        default void onMessage(XmppMessage m) {
        }

        /** A delivery receipt (XEP-0184) for an outgoing message. */
        default void onReceipt(String conversation, String stanzaId) {
        }

        /** The other side displayed (XEP-0333) the message with this id. */
        default void onDisplayed(String conversation, String stanzaId) {
        }

        /** The server accepted the stanza (stream management ack), or a group chat reflected it. */
        default void onServerAck(String conversation, String stanzaId) {
        }

        /** An outgoing message bounced. */
        default void onMessageError(String conversation, String stanzaId, XmppError error) {
        }

        default void onChatState(String conversation, String from, ChatState state) {
        }

        default void onRoster(List<XmppContact> contacts) {
        }

        default void onSubscribeRequest(String jid) {
        }

        default void onInvitation(String roomJid, String from, String reason, String password) {
        }

        /** Room joined or left, or the subject changed. */
        default void onRoom(String roomJid, boolean joined, String subject, XmppError error) {
        }

        default void onOccupants(String roomJid) {
        }

        default void onBlocklist(Set<String> blocked) {
        }
    }

    private static final String NS_MARKERS = "urn:xmpp:chat-markers:0";
    private static final String NS_OOB = "jabber:x:oob";
    private static final String NS_REPLY = "urn:xmpp:reply:0";
    private static final String NS_FALLBACK = "urn:xmpp:fallback:0";
    private static final String NS_HINTS_STORE = "urn:xmpp:hints";

    private final XmppAccountConfig config;
    private final Listener listener;
    private final X509TrustManager trustManager;
    private final PinnedTrustManager pinnedTrust;

    private final ScheduledExecutorService serial;
    private final ExecutorService io;
    /** One thread for everything that goes out in order: messages, typing, markers. */
    private final ExecutorService outbox;

    private volatile XMPPTCPConnection connection;
    private volatile XmppConnectionState state = XmppConnectionState.DISCONNECTED;
    private volatile XmppError lastError;
    private volatile boolean wanted;
    private volatile boolean released;
    private int attempt;
    private ScheduledFuture<?> retryTask;

    private volatile XmppShow myShow = XmppShow.ONLINE;
    private volatile String myStatus = "";

    private final Set<String> pendingSubscriptions = ConcurrentHashMap.newKeySet();
    private final Map<String, MultiUserChat> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> roomNicks = new ConcurrentHashMap<>();
    private final Set<String> blocked = ConcurrentHashMap.newKeySet();
    private final Set<String> roomsToRejoin = ConcurrentHashMap.newKeySet();
    private final Map<String, String> roomPasswords = new ConcurrentHashMap<>();
    private final Map<String, Long> roomSince = new ConcurrentHashMap<>();
    private volatile boolean uploadChecked;
    private volatile boolean uploadAvailable;
    private volatile boolean mamAvailable;
    private volatile boolean markersAvailable = true;

    public XmppSession(XmppAccountConfig config, Listener listener) {
        this(config, listener, null);
    }

    /** {@code trustManager} decides which server certificates are accepted. Null = the platform's trust store. */
    public XmppSession(XmppAccountConfig config, Listener listener, X509TrustManager trustManager) {
        this.config = config.copy();
        this.listener = listener;
        if (trustManager == null) {
            this.pinnedTrust = new PinnedTrustManager(PinnedTrustManager.systemDefault(), config.pinnedCertificate);
            this.trustManager = pinnedTrust;
        } else {
            this.pinnedTrust = null;
            this.trustManager = trustManager;
        }
        final String tag = "xmpp-" + config.jid;
        this.serial = Executors.newSingleThreadScheduledExecutor(daemon(tag + "-ctl"));
        this.io = Executors.newCachedThreadPool(daemon(tag + "-io"));
        this.outbox = Executors.newSingleThreadExecutor(daemon(tag + "-out"));
    }

    private static ThreadFactory daemon(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    public XmppAccountConfig config() {
        return config;
    }

    public XmppConnectionState state() {
        return state;
    }

    public XmppError lastError() {
        return lastError;
    }

    public boolean isOnline() {
        return state == XmppConnectionState.ONLINE;
    }

    public XmppShow myShow() {
        return myShow;
    }

    public String myStatus() {
        return myStatus;
    }

    // ------------------------------------------------------------------------------------------ lifecycle

    /** Starts connecting and keeps the account online (with back-off retries) until {@link #disconnect()}. */
    public void connect() {
        serial.execute(() -> {
            if (released) {
                return;
            }
            wanted = true;
            attempt = 0;
            cancelRetry();
            if (state != XmppConnectionState.ONLINE) {
                connectNow();
            }
        });
    }

    /** The network came back or the user tapped "retry". No-op when already online or not wanted. */
    public void reconnectNow() {
        serial.execute(() -> {
            if (released || !wanted || state == XmppConnectionState.ONLINE) {
                return;
            }
            cancelRetry();
            attempt = 0;
            connectNow();
        });
    }

    /** Logs out and closes. The account stays offline until {@link #connect()}. */
    public void disconnect() {
        serial.execute(() -> {
            wanted = false;
            cancelRetry();
            closeConnection(true);
            setState(XmppConnectionState.DISCONNECTED, null);
        });
    }

    /** Disconnects and frees the threads. The session cannot be used afterwards. */
    public void release() {
        released = true;
        wanted = false;
        serial.execute(() -> {
            cancelRetry();
            closeConnection(true);
            setState(XmppConnectionState.DISCONNECTED, null);
        });
        serial.schedule(() -> {
            serial.shutdown();
            io.shutdown();
            outbox.shutdown();
        }, 3, TimeUnit.SECONDS);
    }

    private void cancelRetry() {
        if (retryTask != null) {
            retryTask.cancel(false);
            retryTask = null;
        }
    }

    private void setState(XmppConnectionState s, XmppError error) {
        state = s;
        lastError = error;
        try {
            listener.onState(s, error);
        } catch (RuntimeException e) {
            // A misbehaving listener must not kill the connection loop.
        }
    }

    private void closeConnection(boolean clean) {
        XMPPTCPConnection c = connection;
        connection = null;
        if (c != null) {
            try {
                c.removeConnectionListener(connectionListener);
                if (clean) {
                    c.disconnect();
                } else {
                    c.instantShutdown();
                }
            } catch (Exception ignored) {
            }
        }
        rooms.clear();
        uploadChecked = false;
    }

    private XMPPTCPConnectionConfiguration buildConfiguration() throws Exception {
        XMPPTCPConnectionConfiguration.Builder b = XMPPTCPConnectionConfiguration.builder()
                .setUsernameAndPassword(config.localPart(), config.password)
                .setXmppDomain(config.domain())
                .setResource(config.effectiveResource())
                .setSendPresence(false)
                .setConnectTimeout(20000);
        if (!config.host.trim().isEmpty()) {
            b.setHost(config.host.trim());
            b.setPort(config.effectivePort());
        } else if (config.port > 0) {
            b.setPort(config.port);
        }
        switch (config.security) {
            case STARTTLS_OPTIONAL:
                b.setSecurityMode(ConnectionConfiguration.SecurityMode.ifpossible);
                break;
            case DIRECT_TLS:
                b.setSecurityMode(ConnectionConfiguration.SecurityMode.disabled);
                b.setSocketFactory(new DirectTlsSocketFactory(config.domain(), trustManager));
                if (config.host.trim().isEmpty()) {
                    // Direct TLS has no SRV lookup in Smack: fall back to the domain on 5223.
                    b.setHost(config.domain());
                    b.setPort(config.effectivePort());
                }
                break;
            default:
                b.setSecurityMode(ConnectionConfiguration.SecurityMode.required);
                break;
        }
        b.setCustomX509TrustManager(trustManager);
        if (pinnedTrust != null) {
            b.setHostnameVerifier(pinnedTrust.hostnameVerifier(config.domain()));
        }
        return b.build();
    }

    private void connectNow() {
        XMPPTCPConnection old = connection;
        if (old != null && old.isConnected() && state == XmppConnectionState.ONLINE) {
            return;
        }
        setState(XmppConnectionState.CONNECTING, null);
        try {
            if (old == null || !old.isSmResumptionPossible()) {
                closeConnection(false);
                connection = new XMPPTCPConnection(buildConfiguration());
                setupConnection(connection);
            }
            XMPPTCPConnection c = connection;
            if (!c.isConnected()) {
                c.connect();
            }
            c.login();
            try {
                afterLogin(c);
            } catch (RuntimeException e) {
                // A failure in the start-up work (storage, a listener) is not a reason to drop a working connection.
            }
        } catch (Throwable t) {
            if (t instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            XmppError e = XmppError.from(t);
            if (e.kind == XmppError.Kind.TLS_CERTIFICATE && pinnedTrust != null && pinnedTrust.lastRejected() != null) {
                e.certFingerprint = pinnedTrust.lastRejected().sha256;
                e.certSubject = pinnedTrust.lastRejected().subject;
            }
            closeConnection(false);
            if (!wanted || released) {
                return;
            }
            if (e.isPermanent()) {
                wanted = false;
                setState(XmppConnectionState.FAILED, e);
            } else {
                setState(XmppConnectionState.CONNECTING, e);
                scheduleRetry();
            }
        }
    }

    private void scheduleRetry() {
        if (!wanted || released) {
            return;
        }
        attempt++;
        long delay = Math.min(300, (long) Math.pow(2, Math.min(attempt, 8))) * 1000L;
        delay += (long) (Math.random() * 1000);
        cancelRetry();
        retryTask = serial.schedule(() -> {
            if (wanted && !released && state != XmppConnectionState.ONLINE) {
                connectNow();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private final ConnectionListener connectionListener = new ConnectionListener() {
        @Override
        public void connectionClosed() {
            // Closed on purpose by us, or the server ended the stream cleanly.
            serial.execute(() -> {
                if (wanted && !released && state == XmppConnectionState.ONLINE) {
                    setState(XmppConnectionState.CONNECTING, null);
                    scheduleRetry();
                }
            });
        }

        @Override
        public void connectionClosedOnError(Exception e) {
            serial.execute(() -> {
                if (!wanted || released) {
                    return;
                }
                XmppError err = XmppError.from(e);
                if (err.isPermanent()) {
                    wanted = false;
                    closeConnection(false);
                    setState(XmppConnectionState.FAILED, err);
                } else {
                    setState(XmppConnectionState.CONNECTING, err);
                    scheduleRetry();
                }
            });
        }
    };

    private void setupConnection(XMPPTCPConnection c) {
        c.addConnectionListener(connectionListener);
        c.setUseStreamManagement(true);
        c.setUseStreamManagementResumption(true);

        // We drive reconnection ourselves so that failures are classified and back-off is in one place.
        org.jivesoftware.smack.ReconnectionManager.getInstanceFor(c).disableAutomaticReconnection();

        Roster roster = Roster.getInstanceFor(c);
        roster.setSubscriptionMode(Roster.SubscriptionMode.manual);
        roster.setRosterLoadedAtLogin(true);
        roster.addRosterListener(new RosterListener() {
            @Override
            public void entriesAdded(Collection<Jid> addresses) {
                publishRoster();
            }

            @Override
            public void entriesUpdated(Collection<Jid> addresses) {
                publishRoster();
            }

            @Override
            public void entriesDeleted(Collection<Jid> addresses) {
                publishRoster();
            }

            @Override
            public void presenceChanged(Presence presence) {
                publishRoster();
            }
        });
        roster.addSubscribeListener((from, request) -> {
            String jid = from.asBareJid().toString();
            // Someone I added on purpose asks to see me in return: that is the second half of "add contact".
            RosterEntry known = roster.getEntry(from.asBareJid());
            if (known != null) {
                return SubscribeListener.SubscribeAnswer.Approve;
            }
            if (pendingSubscriptions.add(jid)) {
                listener.onSubscribeRequest(jid);
            }
            return null; // answer later, from acceptSubscription / denySubscription
        });

        DeliveryReceiptManager receipts = DeliveryReceiptManager.getInstanceFor(c);
        receipts.setAutoReceiptMode(DeliveryReceiptManager.AutoReceiptMode.ifIsSubscribed);
        receipts.addReceiptReceivedListener((from, to, id, stanza) ->
                listener.onReceipt(from.asBareJid().toString(), id));

        PingManager ping = PingManager.getInstanceFor(c);
        ping.setPingInterval(240);
        ping.registerPingFailedListener(new PingFailedListener() {
            @Override
            public void pingFailed() {
                serial.execute(() -> {
                    if (wanted && !released) {
                        closeConnection(false);
                        setState(XmppConnectionState.CONNECTING, new XmppError(XmppError.Kind.TIMEOUT, "ping failed"));
                        scheduleRetry();
                    }
                });
            }
        });

        MultiUserChatManager muc = MultiUserChatManager.getInstanceFor(c);
        muc.addInvitationListener(new InvitationListener() {
            @Override
            public void invitationReceived(XMPPConnection conn, MultiUserChat room, org.jxmpp.jid.EntityJid inviter,
                                           String reason, String password, Message message, MUCUser.Invite invitation) {
                listener.onInvitation(room.getRoom().toString(), inviter == null ? "" : inviter.toString(),
                        reason == null ? "" : reason, password == null ? "" : password);
            }
        });
        muc.setAutoJoinOnReconnect(false);

        CarbonManager.getInstanceFor(c); // load the extension provider

        // Synchronous, not async: async listeners run on a thread pool and would store a burst of messages out of order.
        c.addSyncStanzaListener(stanzaListener, StanzaTypeFilter.MESSAGE);
    }

    private void afterLogin(XMPPTCPConnection c) {
        boolean resumed = c.streamWasResumed();
        attempt = 0;
        setState(XmppConnectionState.ONLINE, null);

        if (!resumed) {
            // Everything below is best effort: a server without carbons or blocking is still a usable server.
            try {
                CarbonManager carbons = CarbonManager.getInstanceFor(c);
                if (carbons.isSupportedByServer()) {
                    carbons.enableCarbons();
                }
            } catch (Exception ignored) {
            }
            try {
                MamManager mam = MamManager.getInstanceFor(c);
                mamAvailable = mam.isSupported();
            } catch (Exception e) {
                mamAvailable = false;
            }
            try {
                BlockingCommandManager bcm = BlockingCommandManager.getInstanceFor(c);
                if (bcm.isSupportedByServer()) {
                    blocked.clear();
                    for (Jid j : bcm.getBlockList()) {
                        blocked.add(j.toString());
                    }
                    bcm.addJidsBlockedListener(jids -> {
                        for (Jid j : jids) {
                            blocked.add(j.toString());
                        }
                        listener.onBlocklist(new HashSet<>(blocked));
                    });
                    bcm.addJidsUnblockedListener(jids -> {
                        for (Jid j : jids) {
                            blocked.remove(j.toString());
                        }
                        listener.onBlocklist(new HashSet<>(blocked));
                    });
                    bcm.addAllJidsUnblockedListener(() -> {
                        blocked.clear();
                        listener.onBlocklist(new HashSet<>(blocked));
                    });
                    listener.onBlocklist(new HashSet<>(blocked));
                }
            } catch (Exception ignored) {
            }
        }
        sendPresence();
        publishRoster();
        listener.onSessionStarted(resumed);
    }

    // ------------------------------------------------------------------------------------------ presence & roster

    public void setPresence(XmppShow show, String status) {
        myShow = show == null || show == XmppShow.OFFLINE ? XmppShow.ONLINE : show;
        myStatus = status == null ? "" : status.trim();
        io.execute(this::sendPresence);
    }

    private void sendPresence() {
        XMPPTCPConnection c = connection;
        if (c == null || !c.isAuthenticated()) {
            return;
        }
        PresenceBuilder pb = StanzaBuilder.buildPresence().ofType(Presence.Type.available);
        switch (myShow) {
            case CHAT:
                pb.setMode(Presence.Mode.chat);
                break;
            case AWAY:
                pb.setMode(Presence.Mode.away);
                break;
            case XA:
                pb.setMode(Presence.Mode.xa);
                break;
            case DND:
                pb.setMode(Presence.Mode.dnd);
                break;
            default:
                pb.setMode(Presence.Mode.available);
                break;
        }
        if (!myStatus.isEmpty()) {
            pb.setStatus(myStatus);
        }
        try {
            c.sendStanza(pb.build());
        } catch (Exception ignored) {
        }
    }

    private static XmppShow showOf(Presence p) {
        if (p == null || !p.isAvailable()) {
            return XmppShow.OFFLINE;
        }
        Presence.Mode m = p.getMode();
        if (m == null) {
            return XmppShow.ONLINE;
        }
        switch (m) {
            case chat:
                return XmppShow.CHAT;
            case away:
                return XmppShow.AWAY;
            case xa:
                return XmppShow.XA;
            case dnd:
                return XmppShow.DND;
            default:
                return XmppShow.ONLINE;
        }
    }

    private void publishRoster() {
        XMPPTCPConnection c = connection;
        if (c == null) {
            return;
        }
        Roster roster = Roster.getInstanceFor(c);
        List<XmppContact> out = new ArrayList<>();
        for (RosterEntry e : roster.getEntries()) {
            XmppContact x = new XmppContact();
            x.jid = e.getJid().toString();
            x.name = e.getName() == null ? "" : e.getName();
            for (RosterGroup g : e.getGroups()) {
                x.groups.add(g.getName());
            }
            switch (e.getType()) {
                case to:
                    x.subscription = XmppContact.Subscription.TO;
                    break;
                case from:
                    x.subscription = XmppContact.Subscription.FROM;
                    break;
                case both:
                    x.subscription = XmppContact.Subscription.BOTH;
                    break;
                default:
                    x.subscription = XmppContact.Subscription.NONE;
                    break;
            }
            x.pendingOut = e.isSubscriptionPending();
            Presence p = roster.getPresence(e.getJid());
            x.show = showOf(p);
            x.status = p != null && p.getStatus() != null ? p.getStatus() : "";
            out.add(x);
        }
        listener.onRoster(out);
    }

    public Set<String> pendingSubscriptions() {
        return new HashSet<>(pendingSubscriptions);
    }

    public Set<String> blocked() {
        return new HashSet<>(blocked);
    }

    /** Adds a contact and asks to see their presence. */
    public void addContact(String jid, String name, List<String> groups, Callback<Void> cb) {
        run(cb, () -> {
            BareJid j = requireBare(jid);
            Roster r = Roster.getInstanceFor(requireConnection());
            String[] g = groups == null ? new String[0] : groups.toArray(new String[0]);
            r.createItemAndRequestSubscription(j, name == null || name.isEmpty() ? null : name, g);
            return null;
        });
    }

    public void removeContact(String jid, Callback<Void> cb) {
        run(cb, () -> {
            Roster r = Roster.getInstanceFor(requireConnection());
            RosterEntry e = r.getEntry(requireBare(jid));
            if (e != null) {
                r.removeEntry(e);
            }
            return null;
        });
    }

    public void renameContact(String jid, String name, Callback<Void> cb) {
        run(cb, () -> {
            Roster r = Roster.getInstanceFor(requireConnection());
            RosterEntry e = r.getEntry(requireBare(jid));
            if (e == null) {
                throw new XmppError(XmppError.Kind.INVALID, "not in roster");
            }
            e.setName(name == null || name.isEmpty() ? null : name);
            // The roster push that follows does not always reach the cached entry before we return.
            r.reloadAndWait();
            publishRoster();
            return null;
        });
    }

    public void setContactGroups(String jid, List<String> groups, Callback<Void> cb) {
        run(cb, () -> {
            XMPPTCPConnection c = requireConnection();
            Roster r = Roster.getInstanceFor(c);
            BareJid j = requireBare(jid);
            RosterEntry e = r.getEntry(j);
            if (e == null) {
                throw new XmppError(XmppError.Kind.INVALID, "not in roster");
            }
            // One roster set with the complete list: adding groups one by one would replace the previous ones.
            RosterPacket.Item item = new RosterPacket.Item(j, e.getName());
            for (String g : groups) {
                item.addGroupName(g);
            }
            RosterPacket set = new RosterPacket();
            set.setType(org.jivesoftware.smack.packet.IQ.Type.set);
            set.addRosterItem(item);
            c.sendIqRequestAndWaitForResponse(set);
            r.reloadAndWait();
            publishRoster();
            return null;
        });
    }

    /** Answer to a subscription request: allow them to see my presence, and optionally ask for theirs too. */
    public void acceptSubscription(String jid, boolean subscribeBack, Callback<Void> cb) {
        run(cb, () -> {
            XMPPTCPConnection c = requireConnection();
            BareJid j = requireBare(jid);
            c.sendStanza(StanzaBuilder.buildPresence().ofType(Presence.Type.subscribed).to(j).build());
            pendingSubscriptions.remove(jid);
            Roster r = Roster.getInstanceFor(c);
            if (subscribeBack && !r.iAmSubscribedTo(j)) {
                if (r.getEntry(j) == null) {
                    r.createItemAndRequestSubscription(j, null, new String[0]);
                } else {
                    r.sendSubscriptionRequest(j);
                }
            }
            return null;
        });
    }

    public void denySubscription(String jid, Callback<Void> cb) {
        run(cb, () -> {
            XMPPTCPConnection c = requireConnection();
            c.sendStanza(StanzaBuilder.buildPresence().ofType(Presence.Type.unsubscribed).to(requireBare(jid)).build());
            pendingSubscriptions.remove(jid);
            return null;
        });
    }

    /** Stop sharing my presence with them (the contact stays in the roster). */
    public void revokeSubscription(String jid, Callback<Void> cb) {
        run(cb, () -> {
            requireConnection().sendStanza(StanzaBuilder.buildPresence().ofType(Presence.Type.unsubscribed).to(requireBare(jid)).build());
            return null;
        });
    }

    public void block(String jid, boolean block, Callback<Void> cb) {
        run(cb, () -> {
            BlockingCommandManager m = BlockingCommandManager.getInstanceFor(requireConnection());
            if (!m.isSupportedByServer()) {
                throw new XmppError(XmppError.Kind.UNSUPPORTED, "blocking command");
            }
            Jid j = Jids.from(jid);
            if (j == null) {
                throw new XmppError(XmppError.Kind.INVALID, jid);
            }
            if (block) {
                m.blockContacts(Collections.singletonList(j));
                blocked.add(jid);
            } else {
                m.unblockContacts(Collections.singletonList(j));
                blocked.remove(jid);
            }
            listener.onBlocklist(new HashSet<>(blocked));
            return null;
        });
    }

    // ------------------------------------------------------------------------------------------ messages

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Sends a chat or group chat message.
     *
     * @param m a message with {@code conversation}, {@code groupChat}, {@code body}, optionally
     *          {@code attachmentUrl}, {@code replyToId}/{@code replyToBody} and {@code replaceId}; the ids and time
     *          are filled in here and the same object is returned so the caller can store it first.
     */
    public XmppMessage prepareOutgoing(XmppMessage m) {
        if (m.stanzaId == null || m.stanzaId.isEmpty()) {
            m.stanzaId = newId();
        }
        m.originId = m.stanzaId;
        m.outgoing = true;
        m.status = XmppMessage.STATUS_PENDING;
        if (m.time == 0) {
            m.time = System.currentTimeMillis();
        }
        if (m.sender == null || m.sender.isEmpty()) {
            m.sender = m.groupChat ? roomNickOrDefault(m.conversation) : config.jid;
        }
        return m;
    }

    private String roomNickOrDefault(String room) {
        String n = roomNicks.get(room);
        return n == null ? config.effectiveNickname() : n;
    }

    public void send(XmppMessage m, Callback<Void> cb) {
        outbox.execute(() -> {
            try {
                doSend(m);
                if (cb != null) {
                    cb.onResult(null);
                }
            } catch (Throwable t) {
                if (cb != null) {
                    cb.onError(XmppError.from(t));
                }
            }
        });
    }

    private void doSend(XmppMessage m) throws Exception {
        XMPPTCPConnection c = requireConnection();
        Jid to = m.conversation.indexOf('/') >= 0 ? Jids.from(m.conversation) : Jids.entityBare(m.conversation);
        if (to == null) {
            throw new XmppError(XmppError.Kind.INVALID, m.conversation);
        }
        String body = m.body;
        MessageBuilder b = StanzaBuilder.buildMessage(m.stanzaId)
                .ofType(m.groupChat ? Message.Type.groupchat : Message.Type.chat)
                .to(to);
        b.addExtension(new OriginIdElement(m.originId));

        if (m.replyToId != null && !m.replyToId.isEmpty()) {
            String quoted = firstLine(m.replyToBody, 200);
            String prefix = "> " + quoted + "\n";
            body = prefix + m.body;
            b.addExtension(StandardExtensionElement.builder("reply", NS_REPLY)
                    .addAttribute("id", m.replyToId)
                    .addAttribute("to", m.groupChat ? m.conversation + "/" + replyAuthor(m) : m.conversation)
                    .build());
            b.addExtension(StandardExtensionElement.builder("fallback", NS_FALLBACK)
                    .addAttribute("for", NS_REPLY)
                    .addElement(StandardExtensionElement.builder("body", NS_FALLBACK)
                            .addAttribute("start", "0")
                            .addAttribute("end", String.valueOf(prefix.length()))
                            .build())
                    .build());
        }
        b.setBody(body);
        if (m.attachmentUrl != null && !m.attachmentUrl.isEmpty()) {
            b.addExtension(StandardExtensionElement.builder("x", NS_OOB).addElement("url", m.attachmentUrl).build());
        }
        if (m.replaceId != null && !m.replaceId.isEmpty()) {
            b.addExtension(new MessageCorrectExtension(m.replaceId));
        }
        if (!m.groupChat) {
            b.addExtension(new DeliveryReceiptRequest());
        }
        b.addExtension(StandardExtensionElement.builder("markable", NS_MARKERS).build());
        Message msg = b.build();
        final String id = m.stanzaId;
        final String conv = m.conversation;
        final boolean group = m.groupChat;
        if (!group && c.isSmEnabled()) {
            c.addStanzaIdAcknowledgedListener(id, stanza -> listener.onServerAck(conv, id));
        }
        c.sendStanza(msg);
        if (!group && !c.isSmEnabled()) {
            listener.onServerAck(conv, id);
        }
    }

    private String replyAuthor(XmppMessage m) {
        return m.replyToAuthor == null ? "" : m.replyToAuthor;
    }

    private static String firstLine(String s, int max) {
        if (s == null) {
            return "";
        }
        int nl = s.indexOf('\n');
        String l = nl < 0 ? s : s.substring(0, nl);
        return l.length() > max ? l.substring(0, max - 1) + "…" : l;
    }

    /** Typing indicator (XEP-0085). Not stored by the server. */
    public void sendChatState(String conversation, boolean group, ChatState state) {
        outbox.execute(() -> {
            try {
                XMPPTCPConnection c = connection;
                Jid to = conversation.indexOf('/') >= 0 ? Jids.from(conversation) : Jids.entityBare(conversation);
                if (c == null || !c.isAuthenticated() || to == null) {
                    return;
                }
                MessageBuilder b = StanzaBuilder.buildMessage()
                        .ofType(group ? Message.Type.groupchat : Message.Type.chat)
                        .to(to)
                        .addExtension(new ChatStateExtension(state))
                        .addExtension(NoStoreHint.INSTANCE);
                c.sendStanza(b.build());
            } catch (Exception ignored) {
            }
        });
    }

    /** Tells the sender that I displayed their message (XEP-0333). */
    public void sendDisplayed(String conversation, String stanzaId, boolean group) {
        if (group) {
            return; // markers are 1:1 only here; rooms would leak read state to everyone
        }
        outbox.execute(() -> {
            try {
                XMPPTCPConnection c = connection;
                EntityBareJid to = Jids.entityBare(conversation);
                if (c == null || !c.isAuthenticated() || to == null || stanzaId == null || stanzaId.isEmpty()) {
                    return;
                }
                MessageBuilder b = StanzaBuilder.buildMessage()
                        .ofType(Message.Type.chat)
                        .to(to)
                        .addExtension(StandardExtensionElement.builder("displayed", NS_MARKERS).addAttribute("id", stanzaId).build())
                        .addExtension(NoStoreHint.INSTANCE);
                c.sendStanza(b.build());
            } catch (Exception ignored) {
            }
        });
    }

    // Incoming ---------------------------------------------------------------------------------------

    private final StanzaListener stanzaListener = packet -> {
        try {
            if (packet instanceof Message) {
                handleMessage((Message) packet);
            }
        } catch (RuntimeException e) {
            // One malformed stanza must not take the listener thread down.
        }
    };

    private void handleMessage(Message message) {
        // Archive results are collected by the query, not shown from the live stream.
        if (MamElements.MamResultExtension.from(message) != null) {
            return;
        }
        CarbonExtension carbon = CarbonExtension.from(message);
        if (carbon != null) {
            // XEP-0280 §9: a carbon is only valid when it comes from my own bare JID.
            String from = message.getFrom() == null ? "" : message.getFrom().asBareJid().toString();
            if (!from.equalsIgnoreCase(config.jid)) {
                return;
            }
            Message inner = carbon.getForwarded().getForwardedStanza();
            boolean sent = carbon.getDirection() == CarbonExtension.Direction.sent;
            Date stamp = carbon.getForwarded().getDelayInformation() != null ? carbon.getForwarded().getDelayInformation().getStamp() : null;
            XmppMessage m = convert(inner, sent, stamp, false);
            if (m != null) {
                listener.onMessage(m);
            }
            return;
        }
        Jid fromJid = message.getFrom();
        if (fromJid == null) {
            return;
        }
        String fromBare = fromJid.asBareJid().toString();
        Message.Type type = message.getType();

        if (type == Message.Type.error) {
            String id = message.getStanzaId();
            if (id != null && message.getError() != null) {
                String text = message.getError().getDescriptiveText();
                listener.onMessageError(fromBare, id, new XmppError(XmppError.Kind.SERVER,
                        message.getError().getCondition().toString(), text == null ? "" : text, null));
            }
            return;
        }
        // Mediated invitations carry no body and are delivered to the InvitationListener.
        MUCUser mucUser = MUCUser.from(message);
        if (mucUser != null && type != Message.Type.groupchat && mucUser.getInvite() != null) {
            return;
        }

        ChatStateExtension cs = null;
        for (XmlElement e : message.getExtensions()) {
            if (e instanceof ChatStateExtension) {
                cs = (ChatStateExtension) e;
            }
        }
        if (cs != null && !fromBare.equalsIgnoreCase(config.jid)) {
            listener.onChatState(type == Message.Type.groupchat ? fromBare : conversationKey(fromJid), fromJid.toString(), cs.getChatState());
        }

        XmlElement displayed = findExtension(message, "displayed", NS_MARKERS);
        if (displayed != null && type == Message.Type.chat) {
            // Smack parses markers into its own classes; an unknown parser would give a generic element.
            String id = displayed instanceof ChatMarkersElements.DisplayedExtension
                    ? ((ChatMarkersElements.DisplayedExtension) displayed).getId()
                    : displayed instanceof StandardExtensionElement ? ((StandardExtensionElement) displayed).getAttributeValue("id") : null;
            if (id != null) {
                listener.onDisplayed(fromBare, id);
            }
        }

        // A receipt-only message is handled by DeliveryReceiptManager; chat states and markers have no body.
        XmppMessage m = convert(message, false, null, false);
        if (m != null) {
            listener.onMessage(m);
        } else if (type == Message.Type.groupchat && message.getSubject() != null && message.getBody() == null) {
            listener.onRoom(fromBare, true, message.getSubject(), null);
        }
    }

    /**
     * The conversation a 1:1 message belongs to. A private message from a room occupant keeps the full
     * address {@code room@host/nick}, so it does not mix with the room's own messages.
     */
    private String conversationKey(Jid peer) {
        String bare = peer.asBareJid().toString();
        if (rooms.containsKey(bare) && peer.hasResource()) {
            return peer.toString();
        }
        return bare;
    }

    private static XmlElement findExtension(Stanza s, String name, String ns) {
        for (XmlElement e : s.getExtensions()) {
            if (name.equals(e.getElementName()) && ns.equals(e.getNamespace())) {
                return e;
            }
        }
        return null;
    }

    /**
     * Turns a message stanza into an {@link XmppMessage}, or null if there is nothing to show (no body,
     * subject-only, marker, receipt). {@code sentByMe} is for carbons of messages my other devices sent.
     */
    XmppMessage convert(Message message, boolean sentByMe, Date archiveStamp, boolean fromArchive) {
        if (message == null) {
            return null;
        }
        String body = message.getBody();
        XmlElement oob = findExtension(message, "x", NS_OOB);
        String oobUrl = "";
        if (oob instanceof StandardExtensionElement) {
            StandardExtensionElement x = (StandardExtensionElement) oob;
            StandardExtensionElement u = x.getFirstElement("url");
            if (u != null && u.getText() != null) {
                oobUrl = u.getText().trim();
            }
        }
        if (body == null || body.isEmpty()) {
            if (oobUrl.isEmpty()) {
                return null;
            }
            body = oobUrl;
        }
        Message.Type type = message.getType();
        boolean group = type == Message.Type.groupchat;
        Jid from = message.getFrom();
        Jid to = message.getTo();
        XmppMessage m = new XmppMessage();
        m.groupChat = group;
        m.stanzaId = message.getStanzaId() == null ? "" : message.getStanzaId();
        m.fromArchive = fromArchive;
        m.attachmentUrl = oobUrl;

        if (group) {
            if (from == null) {
                return null;
            }
            m.conversation = from.asBareJid().toString();
            String nick = from.getResourceOrEmpty().toString();
            m.sender = nick;
            String mine = roomNicks.get(m.conversation);
            m.outgoing = mine != null && mine.equals(nick);
        } else if (sentByMe) {
            if (to == null) {
                return null;
            }
            m.conversation = conversationKey(to);
            m.sender = config.jid;
            m.outgoing = true;
        } else {
            if (from == null) {
                return null;
            }
            m.conversation = conversationKey(from);
            m.sender = from.toString();
            m.outgoing = from.asBareJid().toString().equalsIgnoreCase(config.jid) && to != null
                    && !to.asBareJid().toString().equalsIgnoreCase(config.jid);
            if (m.outgoing) {
                // Archive result of something I sent.
                m.conversation = conversationKey(to);
                m.sender = config.jid;
            }
        }
        if (m.conversation.equalsIgnoreCase(config.jid) && !group && !sentByMe) {
            // Message to myself from the server (e.g. a service notice): show it under my own JID.
            m.outgoing = false;
        }

        // Timestamps: archive stamp wins, then <delay/>, then now.
        Date stamp = archiveStamp;
        if (stamp == null) {
            stamp = DelayInformationManager.getDelayTimestamp(message);
        }
        m.time = stamp != null ? stamp.getTime() : System.currentTimeMillis();
        boolean delayed = stamp != null;

        OriginIdElement origin = OriginIdElement.getOriginId(message);
        if (origin != null) {
            m.originId = origin.getId();
        }
        // The stanza-id that counts is the one added by my own server (or by the room for group chats).
        String archiveBy = group ? m.conversation : config.jid;
        for (XmlElement e : message.getExtensions()) {
            if (e instanceof StanzaIdElement) {
                StanzaIdElement sid = (StanzaIdElement) e;
                if (sid.getBy() != null && sid.getBy().equalsIgnoreCase(archiveBy)) {
                    m.serverId = sid.getId();
                }
            }
        }

        MessageCorrectExtension replace = MessageCorrectExtension.from(message);
        if (replace != null) {
            m.replaceId = replace.getIdInitialMessage();
        }

        XmlElement reply = findExtension(message, "reply", NS_REPLY);
        if (reply instanceof StandardExtensionElement) {
            StandardExtensionElement r = (StandardExtensionElement) reply;
            String id = r.getAttributeValue("id");
            if (id != null) {
                m.replyToId = id;
            }
            // The quoted text travels in the body, as leading lines starting with ">" (XEP-0461 fallback).
            // Smack parses <fallback/> into a class that drops its range, so read the quote from the text.
            int pos = 0;
            StringBuilder quote = new StringBuilder();
            while (pos < body.length() && body.charAt(pos) == '>') {
                int nl = body.indexOf('\n', pos);
                String line = nl < 0 ? body.substring(pos + 1) : body.substring(pos + 1, nl);
                if (quote.length() > 0) {
                    quote.append('\n');
                }
                quote.append(line.startsWith(" ") ? line.substring(1) : line);
                pos = nl < 0 ? body.length() : nl + 1;
            }
            if (pos > 0 && pos < body.length()) {
                m.replyToBody = quote.toString();
                body = body.substring(pos);
            }
        }
        m.body = body;
        // A message that only repeats the attachment URL is just the attachment.
        if (!oobUrl.isEmpty() && body.trim().equals(oobUrl)) {
            m.body = oobUrl;
        }
        m.read = m.outgoing || fromArchive || delayed;
        m.status = m.outgoing ? XmppMessage.STATUS_SENT : XmppMessage.STATUS_NONE;
        m.delayed = delayed;
        return m;
    }

    // ------------------------------------------------------------------------------------------ archive (MAM)

    /**
     * Fetches messages from the account archive that are newer than the archive id {@code afterId}
     * (or the most recent {@code max} when it is empty) and hands them to the listener, oldest first.
     *
     * @return the archive id of the newest message seen, or {@code afterId} if there was nothing new.
     */
    public void catchUp(String afterId, int max, Callback<String> cb) {
        io.execute(() -> {
            try {
                XMPPTCPConnection c = requireConnection();
                if (!mamAvailable) {
                    cb.onResult(afterId);
                    return;
                }
                MamManager mam = MamManager.getInstanceFor(c);
                MamManager.MamQueryArgs.Builder qb = MamManager.MamQueryArgs.builder().setResultPageSizeTo(50);
                if (afterId != null && !afterId.isEmpty()) {
                    qb.afterUid(afterId);
                } else {
                    qb.queryLastPage();
                }
                MamManager.MamQuery query = mam.queryArchive(qb.build());
                String last = afterId;
                int total = 0;
                while (true) {
                    List<MamElements.MamResultExtension> exts = query.getMamResultExtensions();
                    for (MamElements.MamResultExtension ext : exts) {
                        String uid = deliverArchived(ext);
                        if (uid != null) {
                            last = uid;
                        }
                        total++;
                    }
                    if (query.isComplete() || total >= max || exts.isEmpty()) {
                        break;
                    }
                    query.pageNext(50);
                }
                cb.onResult(last);
            } catch (Throwable t) {
                cb.onError(XmppError.from(t));
            }
        });
    }

    /** Older history of one conversation for scrolling back. Messages go to the listener, oldest first. */
    public void loadHistory(String conversation, long beforeMillis, int count, Callback<Integer> cb) {
        io.execute(() -> {
            try {
                XMPPTCPConnection c = requireConnection();
                if (!mamAvailable) {
                    cb.onResult(0);
                    return;
                }
                Jid with = Jids.from(conversation);
                MamManager mam = MamManager.getInstanceFor(c);
                MamManager.MamQueryArgs.Builder qb = MamManager.MamQueryArgs.builder()
                        .limitResultsToJid(with)
                        .setResultPageSizeTo(count)
                        .queryLastPage();
                if (beforeMillis > 0) {
                    qb.limitResultsBefore(new Date(beforeMillis));
                }
                MamManager.MamQuery q = mam.queryArchive(qb.build());
                int n = 0;
                for (MamElements.MamResultExtension ext : q.getMamResultExtensions()) {
                    if (deliverArchived(ext) != null) {
                        n++;
                    }
                }
                cb.onResult(n);
            } catch (Throwable t) {
                cb.onError(XmppError.from(t));
            }
        });
    }

    private String deliverArchived(MamElements.MamResultExtension ext) {
        Forwarded<Message> f = ext.getForwarded();
        Message inner = f.getForwardedStanza();
        Date stamp = f.getDelayInformation() == null ? null : f.getDelayInformation().getStamp();
        XmppMessage m = convert(inner, false, stamp, true);
        if (m != null) {
            if (m.serverId == null || m.serverId.isEmpty()) {
                m.serverId = ext.getId();
            }
            listener.onMessage(m);
        }
        return ext.getId();
    }

    public boolean isArchiveAvailable() {
        return mamAvailable;
    }

    // ------------------------------------------------------------------------------------------ groups (MUC)

    public Set<String> joinedRooms() {
        return new HashSet<>(rooms.keySet());
    }

    /**
     * Enters a room. With {@code create} a room that does not exist is created (instant room); without it
     * the call fails if the room is missing. History: everything newer than {@code sinceMillis}, or the
     * last 50 messages when that is 0.
     */
    public void joinRoom(String roomJid, String nick, String password, boolean create, long sinceMillis, Callback<Void> cb) {
        io.execute(() -> {
            try {
                XMPPTCPConnection c = requireConnection();
                EntityBareJid room = Jids.entityBare(roomJid);
                if (room == null) {
                    throw new XmppError(XmppError.Kind.INVALID, roomJid);
                }
                String nickname = nick == null || nick.trim().isEmpty() ? config.effectiveNickname() : nick.trim();
                Resourcepart rp = Resourcepart.from(nickname);
                MultiUserChatManager mgr = MultiUserChatManager.getInstanceFor(c);
                MultiUserChat muc = mgr.getMultiUserChat(room);
                if (muc.isJoined()) {
                    if (nickname.equals(roomNicks.get(roomJid)) && rooms.containsKey(roomJid)) {
                        // Already in: a second join (for instance the automatic re-join after login racing with the
                        // user's own) must not leave first, that would close an empty room on the server.
                        if (cb != null) {
                            cb.onResult(null);
                        }
                        return;
                    }
                    try {
                        muc.leave();
                    } catch (Exception ignored) {
                    }
                }
                MucEnterConfiguration.Builder eb = muc.getEnterConfigurationBuilder(rp);
                if (password != null && !password.isEmpty()) {
                    eb.withPassword(password);
                }
                if (sinceMillis > 0) {
                    eb.requestHistorySince(new Date(sinceMillis));
                } else {
                    eb.requestMaxStanzasHistory(50);
                }
                roomNicks.put(roomJid, nickname);
                try {
                    if (create) {
                        MultiUserChat.MucCreateConfigFormHandle h = muc.createOrJoin(eb.build());
                        if (h != null) {
                            h.makeInstant();
                        }
                    } else {
                        muc.join(eb.build());
                    }
                } catch (Throwable t) {
                    roomNicks.remove(roomJid);
                    throw t;
                }
                rooms.put(roomJid, muc);
                if (password != null && !password.isEmpty()) {
                    roomPasswords.put(roomJid, password);
                }
                muc.addParticipantListener(p -> listener.onOccupants(roomJid));
                muc.addUserStatusListener(new org.jivesoftware.smackx.muc.UserStatusListener() {
                    @Override
                    public void kicked(Jid actor, String reason) {
                        rooms.remove(roomJid);
                        listener.onRoom(roomJid, false, "", new XmppError(XmppError.Kind.SERVER, "kicked", reason == null ? "" : reason, null));
                    }

                    @Override
                    public void banned(Jid actor, String reason) {
                        rooms.remove(roomJid);
                        listener.onRoom(roomJid, false, "", new XmppError(XmppError.Kind.SERVER, "banned", reason == null ? "" : reason, null));
                    }

                    @Override
                    public void roomDestroyed(MultiUserChat alternate, String reason, String password) {
                        rooms.remove(roomJid);
                        listener.onRoom(roomJid, false, "", new XmppError(XmppError.Kind.SERVER, "gone", reason == null ? "" : reason, null));
                    }
                });
                String subject = muc.getSubject();
                listener.onRoom(roomJid, true, subject == null ? "" : subject, null);
                listener.onOccupants(roomJid);
                if (cb != null) {
                    cb.onResult(null);
                }
            } catch (Throwable t) {
                XmppError e = XmppError.from(t);
                if (cb != null) {
                    cb.onError(e);
                }
            }
        });
    }

    public void leaveRoom(String roomJid, Callback<Void> cb) {
        io.execute(() -> {
            try {
                MultiUserChat muc = rooms.remove(roomJid);
                roomNicks.remove(roomJid);
                if (muc != null && muc.isJoined()) {
                    muc.leave();
                }
                if (cb != null) {
                    cb.onResult(null);
                }
            } catch (Throwable t) {
                if (cb != null) {
                    cb.onError(XmppError.from(t));
                }
            }
        });
    }

    public void setRoomSubject(String roomJid, String subject, Callback<Void> cb) {
        run(cb, () -> {
            requireRoom(roomJid).changeSubject(subject);
            return null;
        });
    }

    public void changeNickname(String roomJid, String nick, Callback<Void> cb) {
        run(cb, () -> {
            requireRoom(roomJid).changeNickname(Resourcepart.from(nick));
            roomNicks.put(roomJid, nick);
            return null;
        });
    }

    public void invite(String roomJid, String userJid, String reason, Callback<Void> cb) {
        run(cb, () -> {
            EntityBareJid user = Jids.entityBare(userJid);
            if (user == null) {
                throw new XmppError(XmppError.Kind.INVALID, userJid);
            }
            requireRoom(roomJid).invite(user, reason == null ? "" : reason);
            return null;
        });
    }

    public static final class Occ {
        public String nick = "";
        public String realJid = "";
        public String role = "";
        public String affiliation = "";
        public XmppShow show = XmppShow.ONLINE;
        public String status = "";
    }

    public List<Occ> occupants(String roomJid) {
        MultiUserChat muc = rooms.get(roomJid);
        List<Occ> out = new ArrayList<>();
        if (muc == null) {
            return out;
        }
        for (org.jxmpp.jid.EntityFullJid f : muc.getOccupants()) {
            Occupant o = muc.getOccupant(f);
            Presence p = muc.getOccupantPresence(f);
            Occ x = new Occ();
            x.nick = f.getResourcepart().toString();
            if (o != null) {
                x.realJid = o.getJid() == null ? "" : o.getJid().asBareJid().toString();
                x.role = o.getRole() == null ? "" : o.getRole().toString();
                x.affiliation = o.getAffiliation() == null ? "" : o.getAffiliation().toString();
            }
            x.show = showOf(p);
            x.status = p != null && p.getStatus() != null ? p.getStatus() : "";
            out.add(x);
        }
        Collections.sort(out, (a, b) -> a.nick.compareToIgnoreCase(b.nick));
        return out;
    }

    public String myRoomNick(String roomJid) {
        return roomNicks.get(roomJid);
    }

    public void roomInfo(String roomJid, Callback<RoomInfo> cb) {
        run(cb, () -> {
            EntityBareJid room = Jids.entityBare(roomJid);
            if (room == null) {
                throw new XmppError(XmppError.Kind.INVALID, roomJid);
            }
            return MultiUserChatManager.getInstanceFor(requireConnection()).getRoomInfo(room);
        });
    }

    /** Public rooms of a MUC service (the domain of the account, with "conference." tried if it is not a service). */
    public void listRooms(String serviceDomain, Callback<Map<String, String>> cb) {
        run(cb, () -> {
            XMPPTCPConnection c = requireConnection();
            MultiUserChatManager mgr = MultiUserChatManager.getInstanceFor(c);
            org.jxmpp.jid.DomainBareJid domain;
            if (serviceDomain == null || serviceDomain.isEmpty()) {
                List<org.jxmpp.jid.DomainBareJid> services = mgr.getMucServiceDomains();
                if (services.isEmpty()) {
                    throw new XmppError(XmppError.Kind.UNSUPPORTED, "no group chat service");
                }
                domain = services.get(0);
            } else {
                domain = org.jxmpp.jid.impl.JidCreate.domainBareFrom(serviceDomain);
            }
            Map<String, String> out = new LinkedHashMap<>();
            for (Map.Entry<EntityBareJid, org.jivesoftware.smackx.muc.HostedRoom> e : mgr.getRoomsHostedBy(domain).entrySet()) {
                out.put(e.getKey().toString(), e.getValue().getName() == null ? "" : e.getValue().getName());
            }
            return out;
        });
    }

    public void bookmarks(Callback<List<BookmarkedConference>> cb) {
        run(cb, () -> BookmarkManager.getBookmarkManager(requireConnection()).getBookmarkedConferences());
    }

    public void saveBookmark(String roomJid, String name, String nick, String password, boolean autoJoin, Callback<Void> cb) {
        run(cb, () -> {
            EntityBareJid room = Jids.entityBare(roomJid);
            if (room == null) {
                throw new XmppError(XmppError.Kind.INVALID, roomJid);
            }
            BookmarkManager bm = BookmarkManager.getBookmarkManager(requireConnection());
            bm.addBookmarkedConference(name == null || name.isEmpty() ? roomJid : name, room, autoJoin,
                    nick == null || nick.isEmpty() ? null : Resourcepart.from(nick), password == null || password.isEmpty() ? null : password);
            return null;
        });
    }

    public void removeBookmark(String roomJid, Callback<Void> cb) {
        run(cb, () -> {
            EntityBareJid room = Jids.entityBare(roomJid);
            if (room != null) {
                BookmarkManager.getBookmarkManager(requireConnection()).removeBookmarkedConference(room);
            }
            return null;
        });
    }

    // ------------------------------------------------------------------------------------------ files

    public boolean isUploadAvailable() {
        XMPPTCPConnection c = connection;
        if (c == null || !c.isAuthenticated()) {
            return false;
        }
        if (!uploadChecked) {
            try {
                uploadAvailable = HttpFileUploadManager.getInstanceFor(c).discoverUploadService();
            } catch (Exception e) {
                uploadAvailable = false;
            }
            uploadChecked = true;
        }
        return uploadAvailable;
    }

    public interface Progress {
        void onProgress(long sent, long total);
    }

    /** Uploads a file to the server's HTTP upload service (XEP-0363) and returns the public URL. */
    public void uploadFile(File file, String mime, Progress progress, Callback<String> cb) {
        io.execute(() -> {
            try {
                XMPPTCPConnection c = requireConnection();
                HttpFileUploadManager mgr = HttpFileUploadManager.getInstanceFor(c);
                if (!mgr.discoverUploadService()) {
                    throw new XmppError(XmppError.Kind.UNSUPPORTED, "http upload");
                }
                String contentType = mime == null || mime.isEmpty() ? "application/octet-stream" : mime;
                Slot slot = mgr.requestSlot(file.getName(), file.length(), contentType);
                HttpURLConnection h = (HttpURLConnection) slot.getPutUrl().openConnection();
                try {
                    h.setRequestMethod("PUT");
                    h.setDoOutput(true);
                    h.setConnectTimeout(20000);
                    h.setReadTimeout(60000);
                    h.setFixedLengthStreamingMode(file.length());
                    h.setRequestProperty("Content-Type", contentType);
                    if (slot.getHeaders() != null) {
                        for (Map.Entry<String, String> e : slot.getHeaders().entrySet()) {
                            h.setRequestProperty(e.getKey(), e.getValue());
                        }
                    }
                    try (InputStream in = new FileInputStream(file); OutputStream out = h.getOutputStream()) {
                        byte[] buf = new byte[16 * 1024];
                        long sent = 0;
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            out.write(buf, 0, n);
                            sent += n;
                            if (progress != null) {
                                progress.onProgress(sent, file.length());
                            }
                        }
                    }
                    int code = h.getResponseCode();
                    if (code < 200 || code >= 300) {
                        throw new XmppError(XmppError.Kind.SERVER, "http-" + code, "Upload failed: HTTP " + code, null);
                    }
                } finally {
                    h.disconnect();
                }
                cb.onResult(slot.getGetUrl().toString());
            } catch (Throwable t) {
                cb.onError(XmppError.from(t));
            }
        });
    }

    // ------------------------------------------------------------------------------------------ profile

    public static final class Profile {
        public String nickname = "";
        public String fullName = "";
        public String email = "";
        public String description = "";
        public byte[] avatar;
    }

    public void loadProfile(String jid, Callback<Profile> cb) {
        run(cb, () -> {
            VCardManager vm = VCardManager.getInstanceFor(requireConnection());
            EntityBareJid j = jid == null || jid.isEmpty() || Jids.sameBare(jid, config.jid) ? null : Jids.entityBare(jid);
            VCard v = j == null ? vm.loadVCard() : vm.loadVCard(j);
            Profile p = new Profile();
            p.nickname = nz(v.getNickName());
            p.fullName = (nz(v.getFirstName()) + " " + nz(v.getLastName())).trim();
            if (p.fullName.isEmpty()) {
                p.fullName = nz(v.getField("FN")).trim();
            }
            p.email = nz(v.getEmailHome());
            if (p.email.isEmpty()) {
                p.email = nz(v.getEmailWork());
            }
            p.description = nz(v.getField("DESC"));
            p.avatar = v.getAvatar();
            return p;
        });
    }

    public void saveProfile(String fullName, String nickname, byte[] avatar, String avatarMime, Callback<Void> cb) {
        run(cb, () -> {
            XMPPTCPConnection c = requireConnection();
            VCardManager vm = VCardManager.getInstanceFor(c);
            VCard v;
            try {
                v = vm.loadVCard();
            } catch (Exception e) {
                v = new VCard();
            }
            if (fullName != null) {
                String name = fullName.trim();
                int sp = name.indexOf(' ');
                v.setFirstName(sp < 0 ? name : name.substring(0, sp));
                v.setLastName(sp < 0 ? "" : name.substring(sp + 1).trim());
            }
            if (nickname != null) {
                v.setNickName(nickname);
            }
            if (avatar != null) {
                v.setAvatar(avatar, avatarMime == null ? "image/jpeg" : avatarMime);
            }
            vm.saveVCard(v);
            return null;
        });
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    // ------------------------------------------------------------------------------------------ account

    public void changePassword(String newPassword, Callback<Void> cb) {
        run(cb, () -> {
            AccountManager am = AccountManager.getInstance(requireConnection());
            am.changePassword(newPassword);
            return null;
        });
    }

    /** Deletes the account on the server (XEP-0077). The connection is closed by the server afterwards. */
    public void deleteAccountOnServer(Callback<Void> cb) {
        run(cb, () -> {
            AccountManager.getInstance(requireConnection()).deleteAccount();
            return null;
        });
    }

    /** Server features as advertised by disco#info, for the "server info" screen. */
    public List<String> serverFeatures() {
        XMPPTCPConnection c = connection;
        List<String> out = new ArrayList<>();
        if (c == null || !c.isAuthenticated()) {
            return out;
        }
        try {
            for (org.jivesoftware.smackx.disco.packet.DiscoverInfo.Feature f
                    : ServiceDiscoveryManager.getInstanceFor(c).discoverInfo(c.getXMPPServiceDomain()).getFeatures()) {
                out.add(f.getVar());
            }
        } catch (Exception ignored) {
        }
        Collections.sort(out);
        return out;
    }

    public boolean isEncryptedTransport() {
        XMPPTCPConnection c = connection;
        return c != null && c.isConnected() && c.isSecureConnection();
    }

    /**
     * Creates an account on the server of {@code config.jid} (XEP-0077 in-band registration) and returns
     * when it exists. Does not log in.
     */
    public static void register(XmppAccountConfig config, X509TrustManager trustManager, Callback<Void> cb) {
        Thread t = new Thread(() -> {
            XMPPTCPConnection c = null;
            XmppSession tmp = null;
            try {
                tmp = new XmppSession(config, new Listener() {
                }, trustManager);
                c = new XMPPTCPConnection(tmp.buildConfiguration());
                c.connect();
                AccountManager am = AccountManager.getInstance(c);
                am.sensitiveOperationOverInsecureConnection(config.security == XmppAccountConfig.Security.STARTTLS_OPTIONAL);
                if (!am.supportsAccountCreation()) {
                    throw new XmppError(XmppError.Kind.UNSUPPORTED, "registration");
                }
                am.createAccount(org.jxmpp.jid.parts.Localpart.from(config.localPart()), config.password);
                tmp.release();
                cb.onResult(null);
            } catch (Throwable e) {
                XmppError err = XmppError.from(e);
                if (err.kind == XmppError.Kind.TLS_CERTIFICATE && tmp != null && tmp.pinnedTrust != null && tmp.pinnedTrust.lastRejected() != null) {
                    err.certFingerprint = tmp.pinnedTrust.lastRejected().sha256;
                    err.certSubject = tmp.pinnedTrust.lastRejected().subject;
                }
                if (tmp != null) {
                    tmp.release();
                }
                cb.onError(err);
            } finally {
                if (c != null) {
                    try {
                        c.disconnect();
                    } catch (Exception ignored) {
                    }
                }
            }
        }, "xmpp-register");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------------------------------ plumbing

    private interface Op<T> {
        T run() throws Exception;
    }

    private <T> void run(Callback<T> cb, Op<T> op) {
        io.execute(() -> {
            try {
                T v = op.run();
                if (cb != null) {
                    cb.onResult(v);
                }
            } catch (Throwable t) {
                if (cb != null) {
                    cb.onError(XmppError.from(t));
                }
            }
        });
    }

    private XMPPTCPConnection requireConnection() throws XmppError {
        XMPPTCPConnection c = connection;
        if (c == null || !c.isAuthenticated()) {
            throw new XmppError(XmppError.Kind.NOT_CONNECTED, "not connected");
        }
        return c;
    }

    private BareJid requireBare(String jid) throws XmppError {
        BareJid j = Jids.bareJid(jid);
        if (j == null) {
            throw new XmppError(XmppError.Kind.INVALID, String.valueOf(jid));
        }
        return j;
    }

    private MultiUserChat requireRoom(String roomJid) throws XmppError {
        MultiUserChat m = rooms.get(roomJid);
        if (m == null || !m.isJoined()) {
            throw new XmppError(XmppError.Kind.NOT_CONNECTED, "not in room");
        }
        return m;
    }

    /** TLS from the first byte. The host name used for SNI and certificate checks is the XMPP domain. */
    private static final class DirectTlsSocketFactory extends SocketFactory {
        private final String domain;
        private final SSLSocketFactory ssl;

        DirectTlsSocketFactory(String domain, X509TrustManager tm) throws Exception {
            this.domain = domain;
            SSLContext c = SSLContext.getInstance("TLS");
            c.init(null, tm == null ? null : new TrustManager[]{tm}, null);
            this.ssl = c.getSocketFactory();
        }

        private Socket wrap(Socket plain) throws java.io.IOException {
            SSLSocket s = (SSLSocket) ssl.createSocket(plain, domain, plain.getPort(), true);
            SSLParameters p = s.getSSLParameters();
            p.setEndpointIdentificationAlgorithm("HTTPS");
            s.setSSLParameters(p);
            s.startHandshake();
            return s;
        }

        @Override
        public Socket createSocket() {
            // Smack calls createSocket() and then connect(): hand back a socket that does the TLS step on connect.
            return new LazyTlsSocket(this);
        }

        @Override
        public Socket createSocket(String host, int port) throws java.io.IOException {
            return wrap(new Socket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws java.io.IOException {
            return wrap(new Socket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws java.io.IOException {
            return wrap(new Socket(host, port));
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws java.io.IOException {
            return wrap(new Socket(address, port, localAddress, localPort));
        }
    }

    /** A socket that connects plain, then upgrades itself to TLS before any byte is read or written. */
    private static final class LazyTlsSocket extends Socket {
        private final DirectTlsSocketFactory factory;
        private Socket tls;

        LazyTlsSocket(DirectTlsSocketFactory factory) {
            this.factory = factory;
        }

        @Override
        public void connect(java.net.SocketAddress endpoint, int timeout) throws java.io.IOException {
            Socket plain = new Socket();
            plain.connect(endpoint, timeout);
            tls = factory.wrap(plain);
        }

        @Override
        public void connect(java.net.SocketAddress endpoint) throws java.io.IOException {
            connect(endpoint, 0);
        }

        @Override
        public InputStream getInputStream() throws java.io.IOException {
            return tls.getInputStream();
        }

        @Override
        public OutputStream getOutputStream() throws java.io.IOException {
            return tls.getOutputStream();
        }

        @Override
        public synchronized void close() throws java.io.IOException {
            if (tls != null) {
                tls.close();
            }
            super.close();
        }

        @Override
        public boolean isConnected() {
            return tls != null && tls.isConnected();
        }

        @Override
        public boolean isClosed() {
            return tls != null ? tls.isClosed() : super.isClosed();
        }

        @Override
        public InetAddress getInetAddress() {
            return tls == null ? null : tls.getInetAddress();
        }

        @Override
        public void setSoTimeout(int timeout) throws java.net.SocketException {
            if (tls != null) {
                tls.setSoTimeout(timeout);
            }
        }

        @Override
        public int getSoTimeout() throws java.net.SocketException {
            return tls == null ? 0 : tls.getSoTimeout();
        }

        @Override
        public void setKeepAlive(boolean on) throws java.net.SocketException {
            if (tls != null) {
                tls.setKeepAlive(on);
            }
        }

        @Override
        public void shutdownOutput() throws java.io.IOException {
            tls.shutdownOutput();
        }

        @Override
        public void shutdownInput() throws java.io.IOException {
            tls.shutdownInput();
        }
    }
}
