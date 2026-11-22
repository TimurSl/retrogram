package app.retrogram.xmpp.core;

import java.util.Locale;

/**
 * Everything needed to log in to one XMPP account. Plain data, no Android types, so the core can run
 * on a JVM (see TMessagesProj/src/test/java/app/retrogram/xmpp).
 */
public final class XmppAccountConfig {

    /** How the TCP connection is secured. */
    public enum Security {
        /** Upgrade with STARTTLS and refuse to log in if the server does not offer it (default). */
        STARTTLS_REQUIRED,
        /** TLS from the first byte (XEP-0368, usually port 5223). */
        DIRECT_TLS,
        /** STARTTLS when offered, otherwise plain text. Passwords may travel unencrypted. */
        STARTTLS_OPTIONAL;

        public static Security fromName(String name) {
            for (Security s : values()) {
                if (s.name().equals(name)) {
                    return s;
                }
            }
            return STARTTLS_REQUIRED;
        }
    }

    public static final int DEFAULT_PORT = 5222;
    public static final int DEFAULT_DIRECT_TLS_PORT = 5223;

    /** Local id, assigned by the account store. 0 = not stored yet. */
    public long id;
    /** Bare JID, {@code user@example.org}. */
    public String jid = "";
    public String password = "";
    /** Optional server host. Empty = resolve the SRV records of the JID's domain. */
    public String host = "";
    /** Optional port. 0 = default for the security mode (only used together with {@link #host}). */
    public int port;
    public Security security = Security.STARTTLS_REQUIRED;
    /** Resource part. Empty = generate once and keep (see {@link #effectiveResource()}). */
    public String resource = "";
    /** Display name used in group chats. Empty = the local part of the JID. */
    public String nickname = "";
    public boolean enabled = true;
    /** Optional label chosen by the user. */
    public String label = "";
    /** SHA-256 fingerprint (hex) of a server certificate the user chose to trust although the platform does not. */
    public String pinnedCertificate = "";

    public XmppAccountConfig() {
    }

    public XmppAccountConfig copy() {
        XmppAccountConfig c = new XmppAccountConfig();
        c.id = id;
        c.jid = jid;
        c.password = password;
        c.host = host;
        c.port = port;
        c.security = security;
        c.resource = resource;
        c.nickname = nickname;
        c.enabled = enabled;
        c.label = label;
        c.pinnedCertificate = pinnedCertificate;
        return c;
    }

    public String localPart() {
        int at = jid.indexOf('@');
        return at < 0 ? jid : jid.substring(0, at);
    }

    public String domain() {
        int at = jid.indexOf('@');
        return at < 0 ? "" : jid.substring(at + 1);
    }

    public String effectiveNickname() {
        return nickname == null || nickname.trim().isEmpty() ? localPart() : nickname.trim();
    }

    public String effectiveResource() {
        return resource == null || resource.trim().isEmpty() ? "Retrogram" : resource.trim();
    }

    public int effectivePort() {
        if (port > 0) {
            return port;
        }
        return security == Security.DIRECT_TLS ? DEFAULT_DIRECT_TLS_PORT : DEFAULT_PORT;
    }

    public String displayName() {
        return label == null || label.trim().isEmpty() ? jid : label.trim();
    }

    /** Lower-cases the domain part and trims. Returns the bare JID, or {@code null} if it is not usable for an account. */
    public static String normalizeJid(String input) {
        if (input == null) {
            return null;
        }
        String s = input.trim();
        if (s.toLowerCase(Locale.ROOT).startsWith("xmpp:")) {
            s = s.substring(5);
        }
        int slash = s.indexOf('/');
        if (slash >= 0) {
            s = s.substring(0, slash);
        }
        int at = s.indexOf('@');
        if (at <= 0 || at != s.lastIndexOf('@') || at == s.length() - 1) {
            return null;
        }
        String local = s.substring(0, at);
        String domain = s.substring(at + 1).toLowerCase(Locale.ROOT);
        for (char c : local.toCharArray()) {
            if (c <= ' ' || c == '"' || c == '&' || c == '\'' || c == '/' || c == ':' || c == '<' || c == '>' || c == '@') {
                return null;
            }
        }
        if (domain.indexOf(' ') >= 0 || domain.startsWith(".") || domain.endsWith(".") || domain.contains("..")) {
            return null;
        }
        return local + "@" + domain;
    }
}
