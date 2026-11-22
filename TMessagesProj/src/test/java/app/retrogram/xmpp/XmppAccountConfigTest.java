package app.retrogram.xmpp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import app.retrogram.xmpp.core.XmppAccountConfig;
import app.retrogram.xmpp.core.XmppAccountConfig.Security;

public class XmppAccountConfigTest {

    @Test
    public void normalizeJidLowercasesTheDomainAndKeepsTheLocalPart() {
        assertEquals("Alice@example.org", XmppAccountConfig.normalizeJid("  Alice@Example.ORG "));
        assertEquals("alice@example.org", XmppAccountConfig.normalizeJid("xmpp:alice@example.org"));
        assertEquals("alice@example.org", XmppAccountConfig.normalizeJid("alice@example.org/phone"));
    }

    @Test
    public void normalizeJidRejectsWhatCannotBeAnAccount() {
        assertNull(XmppAccountConfig.normalizeJid(null));
        assertNull(XmppAccountConfig.normalizeJid(""));
        assertNull(XmppAccountConfig.normalizeJid("example.org"));
        assertNull(XmppAccountConfig.normalizeJid("@example.org"));
        assertNull(XmppAccountConfig.normalizeJid("alice@"));
        assertNull(XmppAccountConfig.normalizeJid("a@b@example.org"));
        assertNull(XmppAccountConfig.normalizeJid("al ice@example.org"));
        assertNull(XmppAccountConfig.normalizeJid("alice@example..org"));
        assertNull(XmppAccountConfig.normalizeJid("alice@.example.org"));
        assertNull(XmppAccountConfig.normalizeJid("a/b@example.org"));
    }

    @Test
    public void defaultsFollowTheSecurityMode() {
        XmppAccountConfig c = new XmppAccountConfig();
        c.jid = "alice@example.org";
        assertEquals(5222, c.effectivePort());
        c.security = Security.DIRECT_TLS;
        assertEquals(5223, c.effectivePort());
        c.port = 443;
        assertEquals(443, c.effectivePort());
        assertEquals("alice", c.localPart());
        assertEquals("example.org", c.domain());
        assertEquals("alice", c.effectiveNickname());
        c.nickname = " Ally ";
        assertEquals("Ally", c.effectiveNickname());
        assertEquals("Retrogram", c.effectiveResource());
    }

    @Test
    public void securityNamesSurviveARoundTripAndUnknownFallsBackToTheSafeMode() {
        for (Security s : Security.values()) {
            assertEquals(s, Security.fromName(s.name()));
        }
        assertEquals(Security.STARTTLS_REQUIRED, Security.fromName("bogus"));
        assertEquals(Security.STARTTLS_REQUIRED, Security.fromName(null));
    }

    @Test
    public void copyIsIndependent() {
        XmppAccountConfig a = new XmppAccountConfig();
        a.jid = "a@b.c";
        a.pinnedCertificate = "AB";
        XmppAccountConfig b = a.copy();
        b.jid = "x@y.z";
        assertEquals("a@b.c", a.jid);
        assertEquals("AB", b.pinnedCertificate);
    }
}
