package app.retrogram.xmpp.core;

/** A chat message, as stored and as shown. */
public final class XmppMessage {

    public static final int STATUS_NONE = 0;      // incoming
    public static final int STATUS_PENDING = 1;   // outgoing, handed to the connection
    public static final int STATUS_SENT = 2;      // outgoing, the server has it (stream management ack or MUC reflection)
    public static final int STATUS_DELIVERED = 3; // outgoing, XEP-0184 receipt
    public static final int STATUS_DISPLAYED = 4; // outgoing, XEP-0333 displayed marker
    public static final int STATUS_FAILED = 5;    // outgoing, error from the server or never sent

    /** Row id from the store, 0 until stored. */
    public long dbId;
    public long accountId;
    /** Bare JID of the contact, or of the room for group chats. */
    public String conversation = "";
    public boolean groupChat;
    public boolean outgoing;
    /** Incoming 1:1: sender's full JID. Group chat: the occupant's nick. Outgoing: my bare JID or my nick. */
    public String sender = "";
    public String body = "";
    /** Epoch milliseconds. */
    public long time;
    /** The {@code id} attribute. */
    public String stanzaId = "";
    /** XEP-0359 origin-id, set by the sender. */
    public String originId = "";
    /** XEP-0359 stanza-id, set by the server or room archive. Unique per archive. */
    public String serverId = "";
    /** Set when this message is a correction (XEP-0308) of the message with this id. Never stored. */
    public String replaceId = "";
    public boolean edited;
    public int status = STATUS_NONE;
    public boolean read = true;
    /** XEP-0066 out-of-band URL, or the URL a file upload produced. */
    public String attachmentUrl = "";
    /** XEP-0461 reply: id of the message this answers, and its body for display. */
    public String replyToId = "";
    public String replyToBody = "";
    /** Group chat reply: nick of the author of the replied-to message. */
    public String replyToAuthor = "";
    /** Local file path of a file I sent or downloaded. Not synchronised. */
    public String attachmentPath = "";
    /** Set on a message that came from an archive query. Never stored. */
    public boolean fromArchive;
    /** Set when the stanza carried a delay stamp (offline delivery, room history, archive). Never stored. */
    public boolean delayed;
    /** Error text from a message error, shown under a failed message. */
    public String error = "";

    public boolean hasAttachment() {
        return attachmentUrl != null && !attachmentUrl.isEmpty();
    }

    public XmppMessage copy() {
        XmppMessage m = new XmppMessage();
        m.dbId = dbId;
        m.accountId = accountId;
        m.conversation = conversation;
        m.groupChat = groupChat;
        m.outgoing = outgoing;
        m.sender = sender;
        m.body = body;
        m.time = time;
        m.stanzaId = stanzaId;
        m.originId = originId;
        m.serverId = serverId;
        m.replaceId = replaceId;
        m.edited = edited;
        m.status = status;
        m.read = read;
        m.attachmentUrl = attachmentUrl;
        m.attachmentPath = attachmentPath;
        m.replyToId = replyToId;
        m.replyToBody = replyToBody;
        m.replyToAuthor = replyToAuthor;
        m.delayed = delayed;
        m.fromArchive = fromArchive;
        m.error = error;
        return m;
    }
}
