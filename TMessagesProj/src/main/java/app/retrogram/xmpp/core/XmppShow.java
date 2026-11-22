package app.retrogram.xmpp.core;

/** Presence "show" values (RFC 6121 §4.7.2.1) plus OFFLINE. Ordered from most to least reachable. */
public enum XmppShow {
    CHAT, ONLINE, AWAY, XA, DND, OFFLINE;

    public boolean isAvailable() {
        return this != OFFLINE;
    }

    public static XmppShow fromName(String name) {
        for (XmppShow s : values()) {
            if (s.name().equals(name)) {
                return s;
            }
        }
        return OFFLINE;
    }
}
