package app.retrogram.xmpp.core;

/** One row of the chat list. */
public final class XmppConversation {
    public long accountId;
    /** Bare JID of the contact or the room. */
    public String jid = "";
    public boolean groupChat;
    /** Room name or contact name, whatever is known. Empty = show the JID. */
    public String title = "";
    public String lastBody = "";
    public long lastTime;
    public boolean lastOutgoing;
    public int unread;
    /** Group chat only: the user wants to be in the room (bookmark / auto-join). */
    public boolean joined;
    public String subject = "";
    public String nickname = "";
    public String password = "";
    public boolean muted;
    /** Group chat only: notify for every message, not just mentions. */
    public boolean notifyAll;
    public boolean pinned;

    public String displayName() {
        return title != null && !title.isEmpty() ? title : jid;
    }

    public XmppConversation copy() {
        XmppConversation c = new XmppConversation();
        c.accountId = accountId;
        c.jid = jid;
        c.groupChat = groupChat;
        c.title = title;
        c.lastBody = lastBody;
        c.lastTime = lastTime;
        c.lastOutgoing = lastOutgoing;
        c.unread = unread;
        c.joined = joined;
        c.subject = subject;
        c.nickname = nickname;
        c.password = password;
        c.muted = muted;
        c.notifyAll = notifyAll;
        c.pinned = pinned;
        return c;
    }
}
