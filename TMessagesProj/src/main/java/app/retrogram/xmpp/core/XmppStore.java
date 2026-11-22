package app.retrogram.xmpp.core;

import java.util.List;

/**
 * Persistence used by {@link XmppController}. The Android build backs it with SQLite, the tests with
 * {@link MemoryXmppStore}. Implementations must be thread safe.
 */
public interface XmppStore {

    /** Inserts and returns the new row id (also set on {@code m.dbId}). */
    long insertMessage(XmppMessage m);

    void updateMessage(XmppMessage m);

    void deleteMessage(long dbId);

    /** By {@code id} attribute within one conversation, newest first if several. */
    XmppMessage findByStanzaId(long accountId, String conversation, String stanzaId);

    XmppMessage findByServerId(long accountId, String conversation, String serverId);

    /** By XEP-0359 origin-id within one conversation. */
    XmppMessage findByOriginId(long accountId, String conversation, String originId);

    /**
     * Messages of one conversation, oldest first. Ordered by the whole second of {@code time}, then by
     * insertion order: servers stamp archive entries with one-second resolution, so a live message and
     * its neighbours from the archive can share a second. {@code beforeTime == 0} means the newest {@code limit}
     * messages, otherwise the newest {@code limit} messages strictly older than {@code beforeTime}.
     */
    List<XmppMessage> messages(long accountId, String conversation, long beforeTime, int limit);

    /** Oldest stored message time of a conversation, or 0. */
    long oldestTime(long accountId, String conversation);

    /** Newest stored message time of a conversation, or 0. */
    long newestTime(long accountId, String conversation);

    /** Outgoing messages in {@code STATUS_PENDING} that were never confirmed, oldest first. */
    List<XmppMessage> pendingOutgoing(long accountId);

    void deleteConversation(long accountId, String jid);

    // --- conversations

    XmppConversation conversation(long accountId, String jid);

    void saveConversation(XmppConversation c);

    /** All conversations of one account, newest activity first, pinned first. */
    List<XmppConversation> conversations(long accountId);

    /** Sets read=true on every message of the conversation and the unread counter to 0. */
    void markRead(long accountId, String jid);

    /** Messages of the conversation that are incoming and unread, newest last. */
    List<XmppMessage> unreadIncoming(long accountId, String jid);

    // --- roster cache

    void saveContacts(long accountId, List<XmppContact> contacts);

    List<XmppContact> contacts(long accountId);

    // --- misc

    String getMeta(long accountId, String key);

    void setMeta(long accountId, String key, String value);

    /** Removes everything that belongs to the account. */
    void deleteAccount(long accountId);
}
