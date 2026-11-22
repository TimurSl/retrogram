package app.retrogram.xmpp.core;

import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

/** Small helpers around jxmpp. All methods are null-safe and return {@code null} / "" instead of throwing. */
public final class Jids {
    private Jids() {
    }

    /** {@code user@host/res} -> {@code user@host}; a host-only JID is returned unchanged. */
    public static String bare(String jid) {
        if (jid == null) {
            return "";
        }
        int slash = jid.indexOf('/');
        return slash < 0 ? jid : jid.substring(0, slash);
    }

    /** The resource, or the nick for a room JID. Empty if none. */
    public static String resource(String jid) {
        if (jid == null) {
            return "";
        }
        int slash = jid.indexOf('/');
        return slash < 0 ? "" : jid.substring(slash + 1);
    }

    public static EntityBareJid entityBare(String jid) {
        try {
            return JidCreate.entityBareFrom(jid);
        } catch (XmppStringprepException | RuntimeException e) {
            return null;
        }
    }

    public static BareJid bareJid(String jid) {
        try {
            return JidCreate.bareFrom(jid);
        } catch (XmppStringprepException | RuntimeException e) {
            return null;
        }
    }

    public static Jid from(String jid) {
        try {
            return JidCreate.from(jid);
        } catch (XmppStringprepException | RuntimeException e) {
            return null;
        }
    }

    /** Case-insensitive on the whole bare JID (the local part is case-folded by stringprep anyway). */
    public static boolean sameBare(String a, String b) {
        return bare(a).equalsIgnoreCase(bare(b));
    }

    /** Parses what a user typed or what an {@code xmpp:} link carries into a normalized bare JID, or null. */
    public static String parseUserInput(String input) {
        String n = XmppAccountConfig.normalizeJid(input);
        if (n == null) {
            return null;
        }
        return entityBare(n) == null ? null : entityBare(n).toString();
    }
}
