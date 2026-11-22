package app.retrogram.xmpp.core;

/** Result of an asynchronous operation. Called on a background thread unless the caller says otherwise. */
public interface Callback<T> {
    void onResult(T value);

    void onError(XmppError error);
}
