package app.retrogram.xmpp.core;

import org.jivesoftware.smackx.bookmarks.BookmarkedConference;
import org.jivesoftware.smackx.chatstates.ChatState;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.X509TrustManager;

/**
 * One account: the connection ({@link XmppSession}) plus the message store. All the rules that decide what
 * is stored, shown, marked read or notified live here so they can be tested without a device.
 *
 * <p>Events are delivered on internal threads while a lock is held: listeners must return quickly and
 * must not call back into the controller synchronously (post to another thread first).</p>
 */
public final class XmppController {

    public interface Events {
        default void onConnectionState(long accountId, XmppConnectionState state, XmppError error) {
        }

        default void onConversationsChanged(long accountId) {
        }

        default void onMessagesChanged(long accountId, String conversation) {
        }

        /** A live incoming message that should alert the user. */
        default void onNotify(XmppMessage message, XmppConversation conversation) {
        }

        default void onContactsChanged(long accountId) {
        }

        default void onSubscribeRequest(long accountId, String jid) {
        }

        default void onInvitation(long accountId, String roomJid, String from, String reason, String password) {
        }

        default void onChatState(long accountId, String conversation, String from, ChatState state) {
        }

        default void onUploadProgress(long accountId, long messageDbId, long sent, long total) {
        }

        default void onOccupantsChanged(long accountId, String roomJid) {
        }

        default void onRoomClosed(long accountId, String roomJid, XmppError reason) {
        }

        default void onBlocklistChanged(long accountId) {
        }
    }

    public static final String META_MAM_LAST = "mam.last";

    private final XmppAccountConfig config;
    private final XmppStore store;
    private final Events events;
    private final XmppSession session;
    private final Object lock = new Object();

    private volatile List<XmppContact> contacts = new ArrayList<>();
    private volatile String activeConversation = "";
    private final Set<String> blocked = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingSubscribers = ConcurrentHashMap.newKeySet();
    private boolean catchingUp;

    public XmppController(XmppAccountConfig config, XmppStore store, Events events) {
        this(config, store, events, null);
    }

    public XmppController(XmppAccountConfig config, XmppStore store, Events events, X509TrustManager trustManager) {
        this.config = config.copy();
        this.store = store;
        this.events = events;
        this.contacts = store.contacts(config.id);
        this.session = new XmppSession(config, new SessionEvents(), trustManager);
    }

    public XmppAccountConfig config() {
        return config;
    }

    public long accountId() {
        return config.id;
    }

    public XmppSession session() {
        return session;
    }

    public XmppStore store() {
        return store;
    }

    public XmppConnectionState state() {
        return session.state();
    }

    public void start() {
        session.connect();
    }

    public void stop() {
        session.disconnect();
    }

    public void release() {
        session.release();
    }

    public void reconnectNow() {
        session.reconnectNow();
    }

    public List<XmppContact> contacts() {
        return new ArrayList<>(contacts);
    }

    public XmppContact contact(String jid) {
        for (XmppContact c : contacts) {
            if (c.jid.equalsIgnoreCase(jid)) {
                return c;
            }
        }
        return null;
    }

    public Set<String> pendingSubscribers() {
        return new HashSet<>(pendingSubscribers);
    }

    public Set<String> blocked() {
        return new HashSet<>(blocked);
    }

    public List<XmppConversation> conversations() {
        return store.conversations(config.id);
    }

    public XmppConversation conversation(String jid) {
        return store.conversation(config.id, jid);
    }

    /** The conversation the user is looking at: incoming messages there are read at once and do not notify. */
    public void setActiveConversation(String jid) {
        activeConversation = jid == null ? "" : jid;
        if (!activeConversation.isEmpty()) {
            markRead(activeConversation);
        }
    }

    // ------------------------------------------------------------------------------------------ conversations

    /** Opens (creates if needed) a 1:1 conversation. */
    public XmppConversation openChat(String jid) {
        synchronized (lock) {
            XmppConversation c = store.conversation(config.id, jid);
            if (c == null) {
                c = newConversation(jid, false);
                store.saveConversation(c);
                events.onConversationsChanged(config.id);
            }
            return c;
        }
    }

    private XmppConversation newConversation(String jid, boolean group) {
        XmppConversation c = new XmppConversation();
        c.accountId = config.id;
        c.jid = jid;
        c.groupChat = group;
        if (!group && jid.indexOf('/') >= 0) {
            // A private message to a room occupant: "Nick (room)".
            String room = Jids.bare(jid);
            int at = room.indexOf('@');
            c.title = Jids.resource(jid) + " (" + (at > 0 ? room.substring(0, at) : room) + ")";
        } else if (!group) {
            XmppContact ct = contact(jid);
            c.title = ct != null ? ct.name : "";
        }
        return c;
    }

    public void deleteConversation(String jid) {
        synchronized (lock) {
            store.deleteConversation(config.id, jid);
            events.onConversationsChanged(config.id);
            events.onMessagesChanged(config.id, jid);
        }
    }

    public void setMuted(String jid, boolean muted) {
        synchronized (lock) {
            XmppConversation c = store.conversation(config.id, jid);
            if (c != null) {
                c.muted = muted;
                store.saveConversation(c);
                events.onConversationsChanged(config.id);
            }
        }
    }

    public void setNotifyAll(String jid, boolean all) {
        synchronized (lock) {
            XmppConversation c = store.conversation(config.id, jid);
            if (c != null) {
                c.notifyAll = all;
                store.saveConversation(c);
            }
        }
    }

    public void setPinned(String jid, boolean pinned) {
        synchronized (lock) {
            XmppConversation c = store.conversation(config.id, jid);
            if (c != null) {
                c.pinned = pinned;
                store.saveConversation(c);
                events.onConversationsChanged(config.id);
            }
        }
    }

    public void markRead(String jid) {
        String lastId = null;
        boolean group;
        synchronized (lock) {
            XmppConversation c = store.conversation(config.id, jid);
            if (c == null) {
                return;
            }
            group = c.groupChat;
            List<XmppMessage> unread = store.unreadIncoming(config.id, jid);
            if (!unread.isEmpty()) {
                lastId = unread.get(unread.size() - 1).stanzaId;
            }
            if (unread.isEmpty() && c.unread == 0) {
                return;
            }
            store.markRead(config.id, jid);
            events.onConversationsChanged(config.id);
            events.onMessagesChanged(config.id, jid);
        }
        if (lastId != null && !lastId.isEmpty() && !group) {
            session.sendDisplayed(jid, lastId, false);
        }
    }

    public int totalUnread() {
        int n = 0;
        for (XmppConversation c : store.conversations(config.id)) {
            if (!c.muted) {
                n += c.unread;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------------------------------ sending

    /**
     * Sends a text message. Returns the stored message (status pending); updates arrive through
     * {@link Events#onMessagesChanged}. Works while offline: the message is sent after the next login.
     */
    public XmppMessage sendText(String conversation, boolean group, String body, XmppMessage replyTo) {
        XmppMessage m = new XmppMessage();
        m.accountId = config.id;
        m.conversation = conversation;
        m.groupChat = group;
        m.body = body;
        if (replyTo != null) {
            // In a group chat the id that counts is the one the room assigned.
            m.replyToId = group && !replyTo.serverId.isEmpty() ? replyTo.serverId : replyTo.stanzaId;
            m.replyToBody = replyTo.body;
            m.replyToAuthor = replyTo.sender;
        }
        return enqueue(m);
    }

    private XmppMessage enqueue(XmppMessage m) {
        session.prepareOutgoing(m);
        m.read = true;
        synchronized (lock) {
            store.insertMessage(m);
            touchConversation(m);
            events.onMessagesChanged(config.id, m.conversation);
            events.onConversationsChanged(config.id);
        }
        transmit(m);
        return m;
    }

    private void transmit(XmppMessage m) {
        session.send(m, new Callback<Void>() {
            @Override
            public void onResult(Void v) {
                // The ack (or the immediate "sent" for servers without stream management) updates the status.
            }

            @Override
            public void onError(XmppError error) {
                if (error.kind == XmppError.Kind.NOT_CONNECTED || error.kind == XmppError.Kind.NETWORK) {
                    return; // stays pending, sent after the next login
                }
                setStatus(m.conversation, m.stanzaId, XmppMessage.STATUS_FAILED, error.detail);
            }
        });
    }

    /** Replaces the text of one of my messages (XEP-0308). */
    public void editMessage(long dbId, String newBody, Callback<Void> cb) {
        XmppMessage original = findByDbId(dbId);
        if (original == null || !original.outgoing) {
            if (cb != null) {
                cb.onError(new XmppError(XmppError.Kind.INVALID, "not my message"));
            }
            return;
        }
        XmppMessage correction = new XmppMessage();
        correction.accountId = config.id;
        correction.conversation = original.conversation;
        correction.groupChat = original.groupChat;
        correction.body = newBody;
        correction.replaceId = original.stanzaId;
        correction.attachmentUrl = original.attachmentUrl;
        session.prepareOutgoing(correction);
        synchronized (lock) {
            original.body = newBody;
            original.edited = true;
            store.updateMessage(original);
            XmppConversation c = store.conversation(config.id, original.conversation);
            if (c != null && c.lastOutgoing && c.lastTime <= original.time) {
                c.lastBody = newBody;
                store.saveConversation(c);
                events.onConversationsChanged(config.id);
            }
            events.onMessagesChanged(config.id, original.conversation);
        }
        session.send(correction, cb);
    }

    /** Removes a message from this device only. */
    public void deleteMessageLocally(long dbId) {
        synchronized (lock) {
            XmppMessage m = findByDbId(dbId);
            if (m == null) {
                return;
            }
            store.deleteMessage(dbId);
            refreshLast(m.conversation);
            events.onMessagesChanged(config.id, m.conversation);
            events.onConversationsChanged(config.id);
        }
    }

    public void resend(long dbId) {
        XmppMessage m = findByDbId(dbId);
        if (m == null || !m.outgoing) {
            return;
        }
        m.status = XmppMessage.STATUS_PENDING;
        m.error = "";
        synchronized (lock) {
            store.updateMessage(m);
            events.onMessagesChanged(config.id, m.conversation);
        }
        if (m.attachmentUrl.isEmpty() && !m.attachmentPath.isEmpty()) {
            upload(m, new File(m.attachmentPath), guessMime(m.attachmentPath));
        } else {
            transmit(m);
        }
    }

    private XmppMessage findByDbId(long dbId) {
        // The store has no get-by-id; conversations are small enough to scan the loaded page.
        for (XmppConversation c : store.conversations(config.id)) {
            for (XmppMessage m : store.messages(config.id, c.jid, 0, 500)) {
                if (m.dbId == dbId) {
                    return m;
                }
            }
        }
        return null;
    }

    /** Uploads a file to the server and sends the link. The message appears at once and shows progress. */
    public XmppMessage sendFile(String conversation, boolean group, File file, String mime) {
        XmppMessage m = new XmppMessage();
        m.accountId = config.id;
        m.conversation = conversation;
        m.groupChat = group;
        m.body = file.getName();
        m.attachmentPath = file.getAbsolutePath();
        session.prepareOutgoing(m);
        m.read = true;
        synchronized (lock) {
            store.insertMessage(m);
            touchConversation(m);
            events.onMessagesChanged(config.id, conversation);
            events.onConversationsChanged(config.id);
        }
        upload(m, file, mime);
        return m;
    }

    private void upload(XmppMessage m, File file, String mime) {
        session.uploadFile(file, mime, (sent, total) -> events.onUploadProgress(config.id, m.dbId, sent, total),
                new Callback<String>() {
                    @Override
                    public void onResult(String url) {
                        synchronized (lock) {
                            m.attachmentUrl = url;
                            m.body = url;
                            store.updateMessage(m);
                            XmppConversation c = store.conversation(config.id, m.conversation);
                            if (c != null && c.lastTime <= m.time) {
                                c.lastBody = url;
                                store.saveConversation(c);
                                events.onConversationsChanged(config.id);
                            }
                            events.onMessagesChanged(config.id, m.conversation);
                        }
                        transmit(m);
                    }

                    @Override
                    public void onError(XmppError error) {
                        if (error.kind == XmppError.Kind.NOT_CONNECTED || error.kind == XmppError.Kind.NETWORK) {
                            setStatus(m.conversation, m.stanzaId, XmppMessage.STATUS_FAILED, error.detail);
                        } else {
                            setStatus(m.conversation, m.stanzaId, XmppMessage.STATUS_FAILED, error.detail);
                        }
                    }
                });
    }

    private static String guessMime(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".gif")) return "image/gif";
        if (p.endsWith(".webp")) return "image/webp";
        if (p.endsWith(".mp4")) return "video/mp4";
        if (p.endsWith(".ogg") || p.endsWith(".oga") || p.endsWith(".opus")) return "audio/ogg";
        if (p.endsWith(".mp3")) return "audio/mpeg";
        if (p.endsWith(".m4a")) return "audio/mp4";
        if (p.endsWith(".pdf")) return "application/pdf";
        if (p.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }

    public void sendTyping(String conversation, boolean group, ChatState state) {
        session.sendChatState(conversation, group, state);
    }

    // ------------------------------------------------------------------------------------------ history

    public List<XmppMessage> messages(String conversation, long beforeTime, int limit) {
        return store.messages(config.id, conversation, beforeTime, limit);
    }

    /**
     * Pulls older messages of a 1:1 conversation from the server archive into the store.
     * {@code cb} gets the number of messages added; the UI then reloads from the store.
     */
    public void loadOlder(String conversation, Callback<Integer> cb) {
        long oldest = store.oldestTime(config.id, conversation);
        session.loadHistory(conversation, oldest, 30, cb);
    }

    // ------------------------------------------------------------------------------------------ contacts

    public void addContact(String jid, String name, List<String> groups, Callback<Void> cb) {
        session.addContact(jid, name, groups, cb);
    }

    public void removeContact(String jid, Callback<Void> cb) {
        session.removeContact(jid, cb);
    }

    public void renameContact(String jid, String name, Callback<Void> cb) {
        session.renameContact(jid, name, cb);
    }

    public void acceptSubscription(String jid, boolean addBack, Callback<Void> cb) {
        pendingSubscribers.remove(jid);
        session.acceptSubscription(jid, addBack, cb);
    }

    public void denySubscription(String jid, Callback<Void> cb) {
        pendingSubscribers.remove(jid);
        session.denySubscription(jid, cb);
    }

    public void setPresence(XmppShow show, String status) {
        session.setPresence(show, status);
    }

    // ------------------------------------------------------------------------------------------ rooms

    /**
     * Joins a group chat and keeps it in the chat list. {@code bookmark} also stores it on the server
     * so that other devices join it too.
     */
    public void joinRoom(String roomJid, String nick, String password, boolean create, boolean bookmark, Callback<Void> cb) {
        long since = store.newestTime(config.id, roomJid);
        session.joinRoom(roomJid, nick, password, create, since, new Callback<Void>() {
            @Override
            public void onResult(Void v) {
                synchronized (lock) {
                    XmppConversation c = store.conversation(config.id, roomJid);
                    if (c == null) {
                        c = newConversation(roomJid, true);
                    }
                    c.groupChat = true;
                    c.joined = true;
                    c.nickname = nick == null ? "" : nick;
                    c.password = password == null ? "" : password;
                    store.saveConversation(c);
                    events.onConversationsChanged(config.id);
                }
                if (bookmark) {
                    session.saveBookmark(roomJid, null, nick, password, true, null);
                }
                if (cb != null) {
                    cb.onResult(null);
                }
            }

            @Override
            public void onError(XmppError error) {
                if (cb != null) {
                    cb.onError(error);
                }
            }
        });
    }

    /** Leaves the room. With {@code forget} it is also removed from the chat list and the server bookmarks. */
    public void leaveRoom(String roomJid, boolean forget, Callback<Void> cb) {
        session.leaveRoom(roomJid, new Callback<Void>() {
            @Override
            public void onResult(Void v) {
                synchronized (lock) {
                    XmppConversation c = store.conversation(config.id, roomJid);
                    if (c != null) {
                        c.joined = false;
                        store.saveConversation(c);
                    }
                    events.onConversationsChanged(config.id);
                }
                if (forget) {
                    session.removeBookmark(roomJid, null);
                    deleteConversation(roomJid);
                }
                if (cb != null) {
                    cb.onResult(null);
                }
            }

            @Override
            public void onError(XmppError error) {
                if (cb != null) {
                    cb.onError(error);
                }
            }
        });
    }

    // ------------------------------------------------------------------------------------------ internals

    private void touchConversation(XmppMessage m) {
        XmppConversation c = store.conversation(config.id, m.conversation);
        if (c == null) {
            c = newConversation(m.conversation, m.groupChat);
        }
        if (m.time >= c.lastTime) {
            c.lastBody = preview(m);
            c.lastTime = m.time;
            c.lastOutgoing = m.outgoing;
        }
        if (!m.outgoing && !m.read) {
            c.unread++;
        }
        store.saveConversation(c);
    }

    private static String preview(XmppMessage m) {
        String b = m.body == null ? "" : m.body;
        int nl = b.indexOf('\n');
        return nl >= 0 ? b.substring(0, nl) : b;
    }

    private void refreshLast(String conversation) {
        XmppConversation c = store.conversation(config.id, conversation);
        if (c == null) {
            return;
        }
        List<XmppMessage> last = store.messages(config.id, conversation, 0, 1);
        if (last.isEmpty()) {
            c.lastBody = "";
            c.lastTime = 0;
            c.unread = 0;
        } else {
            XmppMessage m = last.get(0);
            c.lastBody = preview(m);
            c.lastTime = m.time;
            c.lastOutgoing = m.outgoing;
        }
        store.saveConversation(c);
    }

    private void setStatus(String conversation, String stanzaId, int status, String error) {
        synchronized (lock) {
            XmppMessage m = store.findByStanzaId(config.id, conversation, stanzaId);
            if (m == null || !m.outgoing) {
                return;
            }
            // Never go backwards (a receipt can arrive before the stream management ack).
            if (status != XmppMessage.STATUS_FAILED && m.status >= status) {
                return;
            }
            if (status == XmppMessage.STATUS_FAILED && m.status >= XmppMessage.STATUS_DELIVERED) {
                return;
            }
            m.status = status;
            if (error != null) {
                m.error = error;
            }
            store.updateMessage(m);
            events.onMessagesChanged(config.id, conversation);
        }
    }

    private boolean mentionsMe(String body, String room) {
        if (body == null) {
            return false;
        }
        String lower = body.toLowerCase(Locale.ROOT);
        String nick = session.myRoomNick(room);
        if (nick == null || nick.isEmpty()) {
            nick = config.effectiveNickname();
        }
        if (lower.contains(nick.toLowerCase(Locale.ROOT))) {
            return true;
        }
        return lower.contains(config.localPart().toLowerCase(Locale.ROOT)) || lower.contains(config.jid.toLowerCase(Locale.ROOT));
    }

    /** Message arrives from the session: live, carbon, archive or room history. */
    void handleIncoming(XmppMessage m) {
        XmppMessage toAck = null;
        synchronized (lock) {
            m.accountId = config.id;

            if (m.replaceId != null && !m.replaceId.isEmpty()) {
                applyCorrection(m);
                return;
            }

            // De-duplication: archive id first, then the sender's id.
            if (!m.serverId.isEmpty()) {
                XmppMessage dup = store.findByServerId(config.id, m.conversation, m.serverId);
                if (dup != null) {
                    return;
                }
            }
            if (m.outgoing && !m.originId.isEmpty()) {
                XmppMessage mine = store.findByOriginId(config.id, m.conversation, m.originId);
                if (mine == null) {
                    mine = store.findByStanzaId(config.id, m.conversation, m.stanzaId);
                }
                if (mine != null && mine.outgoing) {
                    // The room reflected my message, or the archive returned it: it is on the server.
                    boolean changed = false;
                    if (!m.serverId.isEmpty() && mine.serverId.isEmpty()) {
                        mine.serverId = m.serverId;
                        changed = true;
                    }
                    if (mine.status < XmppMessage.STATUS_SENT) {
                        mine.status = XmppMessage.STATUS_SENT;
                        changed = true;
                    }
                    if (changed) {
                        store.updateMessage(mine);
                        events.onMessagesChanged(config.id, m.conversation);
                    }
                    return;
                }
            }
            if (!m.stanzaId.isEmpty()) {
                XmppMessage dup = store.findByStanzaId(config.id, m.conversation, m.stanzaId);
                if (dup != null && dup.outgoing == m.outgoing && dup.sender.equals(m.sender)) {
                    if (!m.serverId.isEmpty() && dup.serverId.isEmpty()) {
                        dup.serverId = m.serverId;
                        store.updateMessage(dup);
                    }
                    return;
                }
            }

            XmppConversation conv = store.conversation(config.id, m.conversation);
            boolean active = m.conversation.equalsIgnoreCase(activeConversation);
            if (!m.outgoing && !m.fromArchive && !m.delayed && active) {
                m.read = true;
                if (!m.groupChat && !m.stanzaId.isEmpty()) {
                    toAck = m;
                }
            }
            if (m.outgoing) {
                m.read = true;
            }
            m.replyToId = m.replyToId == null ? "" : m.replyToId;
            store.insertMessage(m);
            touchConversation(m);
            conv = store.conversation(config.id, m.conversation);
            events.onMessagesChanged(config.id, m.conversation);
            events.onConversationsChanged(config.id);

            boolean live = !m.outgoing && !m.fromArchive && !m.delayed && !active && !m.read;
            if (live && conv != null && !conv.muted) {
                boolean notify = !m.groupChat || conv.notifyAll || mentionsMe(m.body, m.conversation);
                if (notify) {
                    events.onNotify(m, conv);
                }
            }
        }
        if (toAck != null) {
            session.sendDisplayed(toAck.conversation, toAck.stanzaId, false);
        }
    }

    private void applyCorrection(XmppMessage c) {
        XmppMessage original = store.findByStanzaId(config.id, c.conversation, c.replaceId);
        if (original == null && !c.originId.isEmpty()) {
            original = store.findByOriginId(config.id, c.conversation, c.replaceId);
        }
        if (original == null) {
            return;
        }
        // XEP-0308 §5: only the author may correct.
        boolean sameAuthor = original.groupChat
                ? original.sender.equals(c.sender) && original.outgoing == c.outgoing
                : original.outgoing == c.outgoing && (original.outgoing || Jids.sameBare(original.sender, c.sender));
        if (!sameAuthor) {
            return;
        }
        original.body = c.body;
        original.edited = true;
        if (!c.attachmentUrl.isEmpty()) {
            original.attachmentUrl = c.attachmentUrl;
        }
        store.updateMessage(original);
        XmppConversation conv = store.conversation(config.id, original.conversation);
        if (conv != null && conv.lastTime <= original.time) {
            conv.lastBody = preview(original);
            store.saveConversation(conv);
            events.onConversationsChanged(config.id);
        }
        events.onMessagesChanged(config.id, original.conversation);
    }

    private final class SessionEvents implements XmppSession.Listener {

        @Override
        public void onState(XmppConnectionState state, XmppError error) {
            events.onConnectionState(config.id, state, error);
        }

        @Override
        public void onSessionStarted(boolean resumed) {
            // Offline queue first, so the order of messages is kept.
            for (XmppMessage m : store.pendingOutgoing(config.id)) {
                if (m.attachmentUrl.isEmpty() && !m.attachmentPath.isEmpty()) {
                    upload(m, new File(m.attachmentPath), guessMime(m.attachmentPath));
                } else {
                    session.send(m, null);
                }
            }
            if (resumed) {
                return;
            }
            synchronized (lock) {
                if (catchingUp) {
                    return;
                }
                catchingUp = true;
            }
            runCatchUp(store.getMeta(config.id, META_MAM_LAST), true);
        }

        private void runCatchUp(String last, boolean retryFromScratch) {
            session.catchUp(last, 300, new Callback<String>() {
                @Override
                public void onResult(String newest) {
                    if (newest != null && !newest.isEmpty() && !newest.equals(last)) {
                        store.setMeta(config.id, META_MAM_LAST, newest);
                    }
                    synchronized (lock) {
                        catchingUp = false;
                    }
                    rejoinRooms();
                }

                @Override
                public void onError(XmppError error) {
                    if (retryFromScratch && last != null && !last.isEmpty() && "item-not-found".equals(error.condition)) {
                        // The server no longer knows our marker (archive purged or replaced): start again from the newest page,
                        // otherwise every later login would fail the same way.
                        store.setMeta(config.id, META_MAM_LAST, "");
                        runCatchUp("", false);
                        return;
                    }
                    synchronized (lock) {
                        catchingUp = false;
                    }
                    rejoinRooms();
                }
            });
        }

        private void rejoinRooms() {
            // Rooms the user is in, plus server-side bookmarks with auto-join (other devices).
            final Set<String> done = new HashSet<>();
            for (XmppConversation c : store.conversations(config.id)) {
                if (c.groupChat && c.joined) {
                    done.add(c.jid);
                    if (session.joinedRooms().contains(c.jid)) {
                        continue;
                    }
                    session.joinRoom(c.jid, c.nickname, c.password, false, store.newestTime(config.id, c.jid), null);
                }
            }
            session.bookmarks(new Callback<List<BookmarkedConference>>() {
                @Override
                public void onResult(List<BookmarkedConference> list) {
                    for (BookmarkedConference b : list) {
                        String jid = b.getJid().toString();
                        if (!b.isAutoJoin() || done.contains(jid)) {
                            continue;
                        }
                        String nick = b.getNickname() == null ? "" : b.getNickname().toString();
                        String pw = b.getPassword() == null ? "" : b.getPassword();
                        XmppConversation existing = store.conversation(config.id, jid);
                        if (existing != null && !existing.joined) {
                            continue; // the user left it on this device
                        }
                        joinRoom(jid, nick, pw, false, false, null);
                    }
                }

                @Override
                public void onError(XmppError error) {
                }
            });
        }

        @Override
        public void onMessage(XmppMessage m) {
            handleIncoming(m);
        }

        @Override
        public void onReceipt(String conversation, String stanzaId) {
            setStatus(conversation, stanzaId, XmppMessage.STATUS_DELIVERED, null);
        }

        @Override
        public void onDisplayed(String conversation, String stanzaId) {
            setStatus(conversation, stanzaId, XmppMessage.STATUS_DISPLAYED, null);
        }

        @Override
        public void onServerAck(String conversation, String stanzaId) {
            setStatus(conversation, stanzaId, XmppMessage.STATUS_SENT, null);
        }

        @Override
        public void onMessageError(String conversation, String stanzaId, XmppError error) {
            setStatus(conversation, stanzaId, XmppMessage.STATUS_FAILED, error.detail.isEmpty() ? error.condition : error.detail);
        }

        @Override
        public void onChatState(String conversation, String from, ChatState state) {
            events.onChatState(config.id, conversation, from, state);
        }

        @Override
        public void onRoster(List<XmppContact> list) {
            for (XmppContact c : list) {
                c.accountId = config.id;
            }
            contacts = list;
            store.saveContacts(config.id, list);
            synchronized (lock) {
                boolean changed = false;
                for (XmppContact ct : list) {
                    XmppConversation c = store.conversation(config.id, ct.jid);
                    if (c != null && !c.groupChat && !ct.name.isEmpty() && !ct.name.equals(c.title)) {
                        c.title = ct.name;
                        store.saveConversation(c);
                        changed = true;
                    }
                }
                if (changed) {
                    events.onConversationsChanged(config.id);
                }
            }
            events.onContactsChanged(config.id);
        }

        @Override
        public void onSubscribeRequest(String jid) {
            pendingSubscribers.add(jid);
            events.onSubscribeRequest(config.id, jid);
        }

        @Override
        public void onInvitation(String roomJid, String from, String reason, String password) {
            events.onInvitation(config.id, roomJid, from, reason, password);
        }

        @Override
        public void onRoom(String roomJid, boolean joined, String subject, XmppError error) {
            synchronized (lock) {
                XmppConversation c = store.conversation(config.id, roomJid);
                if (c == null) {
                    return;
                }
                boolean changed = false;
                if (!joined) {
                    c.joined = false;
                    changed = true;
                } else if (subject != null && !subject.equals(c.subject)) {
                    c.subject = subject;
                    changed = true;
                }
                if (changed) {
                    store.saveConversation(c);
                    events.onConversationsChanged(config.id);
                }
            }
            if (!joined) {
                events.onRoomClosed(config.id, roomJid, error);
            }
        }

        @Override
        public void onOccupants(String roomJid) {
            events.onOccupantsChanged(config.id, roomJid);
        }

        @Override
        public void onBlocklist(Set<String> set) {
            blocked.clear();
            blocked.addAll(set);
            events.onBlocklistChanged(config.id);
        }
    }

    /** Wipes this account's local data (the account itself stays on the server). */
    public void deleteLocalData() {
        synchronized (lock) {
            store.deleteAccount(config.id);
        }
    }

    public static List<String> sortedGroups(List<XmppContact> contacts) {
        Set<String> groups = new HashSet<>();
        for (XmppContact c : contacts) {
            groups.addAll(c.groups);
        }
        List<String> out = new ArrayList<>(groups);
        Collections.sort(out, String::compareToIgnoreCase);
        return out;
    }
}
