package app.retrogram.xmpp.core;

public enum XmppConnectionState {
    /** Not connected and not trying to (disabled, or disconnected by the user). */
    DISCONNECTED,
    /** Connecting, or waiting to retry after a drop. */
    CONNECTING,
    /** Logged in. */
    ONLINE,
    /** The last attempt failed in a way a retry will not fix (bad password, TLS refused, ...). */
    FAILED
}
