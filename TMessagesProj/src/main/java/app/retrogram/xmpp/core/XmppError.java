package app.retrogram.xmpp.core;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.sasl.SASLErrorException;
import org.jivesoftware.smack.packet.StanzaError;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLException;

/** A failure, sorted so the UI can show a translated message. {@link #detail} is for logs and "more info". */
public final class XmppError extends Exception {

    public enum Kind {
        /** Wrong user name or password, or the account is disabled. */
        AUTH,
        /** The server certificate was rejected (not trusted, expired, wrong host). */
        TLS_CERTIFICATE,
        /** TLS is required but the server does not offer it. */
        TLS_REQUIRED,
        /** The server name does not resolve. */
        DNS,
        /** The server did not answer in time. */
        TIMEOUT,
        /** Connection refused, reset, no route. */
        NETWORK,
        /** The server answered with an XMPP error (see {@link #condition}). */
        SERVER,
        /** The server does not support what was asked (upload service, MAM, registration, ...). */
        UNSUPPORTED,
        /** The operation needs a connection and there is none. */
        NOT_CONNECTED,
        /** The input is not valid (bad JID, empty password, ...). */
        INVALID,
        OTHER
    }

    public final Kind kind;
    /** XMPP error condition, e.g. {@code conflict}, {@code not-authorized}, {@code forbidden}. Empty if not from the server. */
    public final String condition;
    public final String detail;
    /** For {@link Kind#TLS_CERTIFICATE}: SHA-256 fingerprint (hex) and subject of the refused certificate. */
    public String certFingerprint = "";
    public String certSubject = "";

    public XmppError(Kind kind, String condition, String detail, Throwable cause) {
        super(detail, cause);
        this.kind = kind;
        this.condition = condition == null ? "" : condition;
        this.detail = detail == null ? "" : detail;
    }

    public XmppError(Kind kind, String detail) {
        this(kind, "", detail, null);
    }

    /** Never retry a login that failed like this without the user changing something. */
    public boolean isPermanent() {
        return kind == Kind.AUTH || kind == Kind.TLS_CERTIFICATE || kind == Kind.TLS_REQUIRED || kind == Kind.INVALID;
    }

    public static XmppError from(Throwable t) {
        if (t instanceof XmppError) {
            return (XmppError) t;
        }
        Throwable root = t;
        String msg = String.valueOf(t.getMessage());
        if (t instanceof SASLErrorException || root instanceof SASLErrorException) {
            SASLErrorException e = (SASLErrorException) (t instanceof SASLErrorException ? t : root);
            return new XmppError(Kind.AUTH, e.getSASLFailure().getSASLErrorString(), e.getMessage(), t);
        }
        if (t instanceof SmackException.SecurityRequiredByServerException || t instanceof SmackException.SecurityRequiredByClientException
                || t instanceof SmackException.SecurityNotPossibleException) {
            return new XmppError(Kind.TLS_REQUIRED, "", msg, t);
        }
        if (t instanceof XMPPException.XMPPErrorException) {
            StanzaError se = ((XMPPException.XMPPErrorException) t).getStanzaError();
            String cond = se.getCondition().toString();
            String text = se.getDescriptiveText();
            Kind k = StanzaError.Condition.service_unavailable.equals(se.getCondition())
                    || StanzaError.Condition.feature_not_implemented.equals(se.getCondition()) ? Kind.UNSUPPORTED : Kind.SERVER;
            return new XmppError(k, cond, text == null || text.isEmpty() ? cond : text, t);
        }
        if (t instanceof SmackException.NotConnectedException || t instanceof SmackException.NotLoggedInException) {
            return new XmppError(Kind.NOT_CONNECTED, "", msg, t);
        }
        if (t instanceof SmackException.NoResponseException) {
            return new XmppError(Kind.TIMEOUT, "", msg, t);
        }
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            if (c instanceof CertificateException
                    || (c instanceof SSLException && String.valueOf(c.getMessage()).toLowerCase().contains("cert"))
                    || (c instanceof SSLException && String.valueOf(c.getMessage()).toLowerCase().contains("trust"))) {
                return new XmppError(Kind.TLS_CERTIFICATE, "", String.valueOf(c.getMessage()), t);
            }
            if (c instanceof UnknownHostException) {
                return new XmppError(Kind.DNS, "", String.valueOf(c.getMessage()), t);
            }
            if (c instanceof SocketTimeoutException) {
                return new XmppError(Kind.TIMEOUT, "", String.valueOf(c.getMessage()), t);
            }
            if (c instanceof ConnectException || c instanceof NoRouteToHostException) {
                return new XmppError(Kind.NETWORK, "", String.valueOf(c.getMessage()), t);
            }
        }
        if (t instanceof IOException) {
            return new XmppError(Kind.NETWORK, "", msg, t);
        }
        if (t instanceof SmackException.ConnectionException) {
            return new XmppError(Kind.NETWORK, "", msg, t);
        }
        return new XmppError(Kind.OTHER, "", msg, t);
    }
}
