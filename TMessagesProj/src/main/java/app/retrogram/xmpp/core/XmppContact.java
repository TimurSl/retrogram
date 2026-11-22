package app.retrogram.xmpp.core;

import java.util.ArrayList;
import java.util.List;

/** One roster entry plus its best presence. */
public final class XmppContact {

    public enum Subscription {
        NONE, TO, FROM, BOTH;

        public static Subscription fromName(String name) {
            for (Subscription s : values()) {
                if (s.name().equals(name)) {
                    return s;
                }
            }
            return NONE;
        }

        /** I receive their presence. */
        public boolean receives() {
            return this == TO || this == BOTH;
        }

        /** They receive mine. */
        public boolean sends() {
            return this == FROM || this == BOTH;
        }
    }

    public long accountId;
    /** Bare JID. */
    public String jid = "";
    /** Roster name, empty if none. */
    public String name = "";
    public final List<String> groups = new ArrayList<>();
    public Subscription subscription = Subscription.NONE;
    /** I asked to see their presence and they have not answered yet. */
    public boolean pendingOut;
    public XmppShow show = XmppShow.OFFLINE;
    public String status = "";

    public String displayName() {
        if (name != null && !name.isEmpty()) {
            return name;
        }
        return jid;
    }
}
