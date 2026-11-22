package app.retrogram.xmpp.core;

import java.net.IDN;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Trust decisions for one account: the platform's CA store, plus one certificate the user chose to trust
 * (by SHA-256 fingerprint) after a failed login. Self-hosted servers often use a self-signed certificate.
 */
public final class PinnedTrustManager implements X509TrustManager {

    /** The certificate that was refused last, so the UI can show its fingerprint and ask. */
    public static final class Rejected {
        public final String sha256;
        public final String subject;

        Rejected(String sha256, String subject) {
            this.sha256 = sha256;
            this.subject = subject;
        }
    }

    private final X509TrustManager delegate;
    private final String pinned;
    private volatile Rejected lastRejected;

    public PinnedTrustManager(X509TrustManager delegate, String pinnedSha256) {
        this.delegate = delegate;
        this.pinned = normalize(pinnedSha256);
    }

    public static X509TrustManager systemDefault() {
        try {
            TrustManagerFactory f = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            f.init((KeyStore) null);
            for (TrustManager tm : f.getTrustManagers()) {
                if (tm instanceof X509TrustManager) {
                    return (X509TrustManager) tm;
                }
            }
        } catch (Exception ignored) {
        }
        throw new IllegalStateException("no platform trust manager");
    }

    public Rejected lastRejected() {
        return lastRejected;
    }

    /** Upper-case hex without separators; empty for null or empty input. */
    public static String normalize(String fingerprint) {
        if (fingerprint == null) {
            return "";
        }
        return fingerprint.replace(":", "").replace(" ", "").trim().toUpperCase(Locale.ROOT);
    }

    public static String sha256(X509Certificate cert) throws CertificateException {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02X", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new CertificateException(e);
        }
    }

    /** {@code AB:CD:...} for display. */
    public static String format(String hex) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < hex.length(); i += 2) {
            if (sb.length() > 0) {
                sb.append(':');
            }
            sb.append(hex, i, i + 2);
        }
        return sb.toString();
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        delegate.checkClientTrusted(chain, authType);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        if (chain == null || chain.length == 0) {
            throw new CertificateException("empty certificate chain");
        }
        String fp = sha256(chain[0]);
        if (!pinned.isEmpty() && pinned.equals(fp)) {
            return;
        }
        try {
            delegate.checkServerTrusted(chain, authType);
        } catch (CertificateException e) {
            lastRejected = new Rejected(fp, chain[0].getSubjectX500Principal().getName());
            throw e;
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return delegate.getAcceptedIssuers();
    }

    /**
     * Host name check for a connection that uses this trust manager: a pinned certificate is accepted for
     * any name (it is the very certificate the user approved), anything else must match the XMPP domain.
     */
    public HostnameVerifier hostnameVerifier(String domain) {
        return (host, session) -> {
            try {
                java.security.cert.Certificate[] peer = session.getPeerCertificates();
                if (peer.length == 0 || !(peer[0] instanceof X509Certificate)) {
                    return false;
                }
                X509Certificate leaf = (X509Certificate) peer[0];
                if (!pinned.isEmpty() && pinned.equals(sha256(leaf))) {
                    return true;
                }
                return matchesDomain(leaf, domain);
            } catch (SSLPeerUnverifiedException | CertificateException e) {
                return false;
            }
        };
    }

    /** RFC 6125 style match against dNSName subject alternative names, falling back to the CN when there are none. */
    static boolean matchesDomain(X509Certificate cert, String domain) {
        String d = IDN.toASCII(domain).toLowerCase(Locale.ROOT);
        try {
            Collection<List<?>> sans = cert.getSubjectAlternativeNames();
            boolean sawDns = false;
            if (sans != null) {
                for (List<?> san : sans) {
                    if (san.size() >= 2 && Integer.valueOf(2).equals(san.get(0))) {
                        sawDns = true;
                        if (matchesName(String.valueOf(san.get(1)), d)) {
                            return true;
                        }
                    }
                }
            }
            if (sawDns) {
                return false;
            }
        } catch (CertificateParsingException ignored) {
        }
        String dn = cert.getSubjectX500Principal().getName();
        for (String part : dn.split(",")) {
            String p = part.trim();
            if (p.regionMatches(true, 0, "CN=", 0, 3) && matchesName(p.substring(3), d)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesName(String pattern, String domain) {
        String p = pattern.toLowerCase(Locale.ROOT);
        if (p.equals(domain)) {
            return true;
        }
        if (p.startsWith("*.") && p.indexOf('*', 1) < 0) {
            int dot = domain.indexOf('.');
            return dot > 0 && domain.substring(dot).equals(p.substring(1));
        }
        return false;
    }
}
