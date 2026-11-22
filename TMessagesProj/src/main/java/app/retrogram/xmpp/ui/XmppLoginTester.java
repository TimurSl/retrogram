package app.retrogram.xmpp.ui;

import org.telegram.messenger.AndroidUtilities;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import app.retrogram.xmpp.core.XmppAccountConfig;
import app.retrogram.xmpp.core.XmppConnectionState;
import app.retrogram.xmpp.core.XmppError;
import app.retrogram.xmpp.core.XmppSession;

/** Logs in once with a throw-away connection to tell the user at once whether the address and password work. */
final class XmppLoginTester {
    interface Result {
        void onSuccess();

        void onFailure(XmppError error);
    }

    private XmppLoginTester() {
    }

    static void test(XmppAccountConfig config, Result result) {
        XmppAccountConfig probe = config.copy();
        // Another resource, so a running connection of the same account is not kicked off.
        probe.resource = "probe-" + UUID.randomUUID().toString().substring(0, 6);
        AtomicBoolean done = new AtomicBoolean();
        XmppSession[] holder = new XmppSession[1];
        Runnable timeout = () -> finish(done, holder, () -> result.onFailure(new XmppError(XmppError.Kind.TIMEOUT, "timeout")));
        XmppSession session = new XmppSession(probe, new XmppSession.Listener() {
            @Override
            public void onState(XmppConnectionState state, XmppError error) {
                if (state == XmppConnectionState.ONLINE) {
                    AndroidUtilities.cancelRunOnUIThread(timeout);
                    finish(done, holder, () -> result.onSuccess());
                } else if (error != null && (state == XmppConnectionState.FAILED || state == XmppConnectionState.CONNECTING)) {
                    // The session would keep retrying a transient error; for a login check the first failure is the answer.
                    AndroidUtilities.cancelRunOnUIThread(timeout);
                    finish(done, holder, () -> result.onFailure(error));
                }
            }
        });
        holder[0] = session;
        AndroidUtilities.runOnUIThread(timeout, 45000);
        session.connect();
    }

    private static void finish(AtomicBoolean done, XmppSession[] holder, Runnable then) {
        if (!done.compareAndSet(false, true)) {
            return;
        }
        XmppSession s = holder[0];
        if (s != null) {
            s.release();
        }
        AndroidUtilities.runOnUIThread(then);
    }
}
