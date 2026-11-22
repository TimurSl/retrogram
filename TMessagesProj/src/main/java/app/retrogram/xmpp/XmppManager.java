package app.retrogram.xmpp;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;

import org.jivesoftware.smackx.chatstates.ChatState;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import app.retrogram.xmpp.core.XmppAccountConfig;
import app.retrogram.xmpp.core.XmppConnectionState;
import app.retrogram.xmpp.core.XmppContact;
import app.retrogram.xmpp.core.XmppController;
import app.retrogram.xmpp.core.XmppConversation;
import app.retrogram.xmpp.core.XmppError;
import app.retrogram.xmpp.core.XmppMessage;
import app.retrogram.xmpp.core.XmppShow;

/**
 * Owns one {@link XmppController} per account for the lifetime of the process. Screens register a
 * {@link XmppController.Events} listener; its methods are called on the UI thread.
 */
public final class XmppManager {

    private static final XmppManager INSTANCE = new XmppManager();

    public static XmppManager get() {
        return INSTANCE;
    }

    private final Map<Long, XmppController> controllers = new LinkedHashMap<>();
    private final List<XmppController.Events> listeners = new CopyOnWriteArrayList<>();
    private boolean started;
    private boolean networkCallbackRegistered;

    private XmppManager() {
    }

    private Context context() {
        return ApplicationLoader.applicationContext;
    }

    // ---------------------------------------------------------------------------------------------- lifecycle

    /** Creates the controllers of the stored accounts and connects the enabled ones. Safe to call repeatedly. */
    public synchronized void start() {
        if (started || context() == null) {
            return;
        }
        started = true;
        for (XmppAccountConfig config : XmppAccounts.list()) {
            createController(config);
        }
        registerNetworkCallback();
        updateService();
    }

    private XmppController createController(XmppAccountConfig config) {
        XmppController c = new XmppController(config, XmppDatabase.get(context()), dispatcher);
        controllers.put(config.id, c);
        c.setPresence(XmppAccounts.show(config.id), XmppAccounts.status(config.id));
        if (config.enabled) {
            c.start();
        }
        return c;
    }

    private void registerNetworkCallback() {
        if (networkCallbackRegistered) {
            return;
        }
        try {
            ConnectivityManager cm = (ConnectivityManager) context().getSystemService(Context.CONNECTIVITY_SERVICE);
            cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    List<XmppController> all;
                    synchronized (XmppManager.this) {
                        all = new ArrayList<>(controllers.values());
                    }
                    for (XmppController c : all) {
                        if (c.config().enabled) {
                            c.reconnectNow();
                        }
                    }
                }
            });
            networkCallbackRegistered = true;
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public synchronized List<XmppController> controllers() {
        start();
        return new ArrayList<>(controllers.values());
    }

    public synchronized XmppController controller(long accountId) {
        start();
        return controllers.get(accountId);
    }

    public synchronized boolean hasAccounts() {
        start();
        return !controllers.isEmpty();
    }

    /** Stores a new account and connects it. */
    public synchronized XmppController addAccount(XmppAccountConfig config) {
        start();
        XmppAccountConfig saved = XmppAccounts.save(config);
        XmppController c = createController(saved);
        updateService();
        return c;
    }

    /** Applies edited settings: the connection is rebuilt. */
    public synchronized XmppController updateAccount(XmppAccountConfig config) {
        start();
        XmppAccountConfig saved = XmppAccounts.save(config);
        XmppController old = controllers.remove(saved.id);
        if (old != null) {
            old.release();
        }
        XmppController c = createController(saved);
        updateService();
        return c;
    }

    public void setEnabled(long accountId, boolean enabled) {
        XmppAccountConfig config = XmppAccounts.get(accountId);
        if (config == null) {
            return;
        }
        config.enabled = enabled;
        updateAccount(config);
    }

    /** The user decided to trust the certificate with this fingerprint for the account, then reconnect. */
    public void trustCertificate(long accountId, String sha256) {
        XmppAccountConfig config = XmppAccounts.get(accountId);
        if (config == null) {
            return;
        }
        config.pinnedCertificate = sha256;
        config.enabled = true;
        updateAccount(config);
    }

    public synchronized void removeAccount(long accountId, boolean wipeLocalData) {
        start();
        XmppController old = controllers.remove(accountId);
        if (old != null) {
            old.release();
            if (wipeLocalData) {
                old.deleteLocalData();
            }
        }
        XmppAccounts.delete(accountId);
        if (wipeLocalData) {
            XmppFiles.clearAccount(accountId);
        }
        XmppNotifications.cancelAll(context(), accountId);
        updateService();
    }

    public void setPresence(long accountId, XmppShow show, String status) {
        XmppAccounts.setPresence(accountId, show, status);
        XmppController c = controller(accountId);
        if (c != null) {
            c.setPresence(show, status);
        }
    }

    public int totalUnread() {
        int n = 0;
        for (XmppController c : controllers()) {
            n += c.totalUnread();
        }
        return n;
    }

    public void addListener(XmppController.Events l) {
        listeners.add(l);
    }

    public void removeListener(XmppController.Events l) {
        listeners.remove(l);
    }

    // ---------------------------------------------------------------------------------------------- service

    private int countOnline() {
        int n = 0;
        for (XmppController c : controllers.values()) {
            if (c.config().enabled && c.state() == XmppConnectionState.ONLINE) {
                n++;
            }
        }
        return n;
    }

    private int countEnabled() {
        int n = 0;
        for (XmppController c : controllers.values()) {
            if (c.config().enabled) {
                n++;
            }
        }
        return n;
    }

    synchronized Notification serviceNotification(Context context) {
        boolean connecting = false;
        for (XmppController c : controllers.values()) {
            if (c.config().enabled && c.state() == XmppConnectionState.CONNECTING) {
                connecting = true;
            }
        }
        return XmppNotifications.serviceNotification(context, countOnline(), countEnabled(), connecting);
    }

    /** Starts or stops the foreground service to match the settings and the accounts. */
    public synchronized void updateService() {
        Context ctx = context();
        if (ctx == null) {
            return;
        }
        boolean want = XmppAccounts.backgroundService() && countEnabled() > 0;
        Intent i = new Intent(ctx, XmppService.class);
        try {
            if (want) {
                ctx.startForegroundService(i);
            } else {
                ctx.stopService(i);
            }
        } catch (Throwable e) {
            // Starting a foreground service from the background can be refused; the connections still run while the process lives.
            FileLog.e(e);
        }
    }

    // ---------------------------------------------------------------------------------------------- events

    private interface Call {
        void run(XmppController.Events l);
    }

    private void ui(Call call) {
        AndroidUtilities.runOnUIThread(() -> {
            for (XmppController.Events l : listeners) {
                call.run(l);
            }
        });
    }

    private final XmppController.Events dispatcher = new XmppController.Events() {
        @Override
        public void onConnectionState(long accountId, XmppConnectionState state, XmppError error) {
            ui(l -> l.onConnectionState(accountId, state, error));
            XmppService.refresh(ApplicationLoader.applicationContext);
        }

        @Override
        public void onConversationsChanged(long accountId) {
            ui(l -> l.onConversationsChanged(accountId));
        }

        @Override
        public void onMessagesChanged(long accountId, String conversation) {
            ui(l -> l.onMessagesChanged(accountId, conversation));
        }

        @Override
        public void onNotify(XmppMessage message, XmppConversation conversation) {
            XmppAccountConfig account = XmppAccounts.get(message.accountId);
            if (account != null) {
                XmppNotifications.showMessage(ApplicationLoader.applicationContext, message, conversation, account);
            }
        }

        @Override
        public void onContactsChanged(long accountId) {
            ui(l -> l.onContactsChanged(accountId));
        }

        @Override
        public void onSubscribeRequest(long accountId, String jid) {
            ui(l -> l.onSubscribeRequest(accountId, jid));
        }

        @Override
        public void onInvitation(long accountId, String roomJid, String from, String reason, String password) {
            ui(l -> l.onInvitation(accountId, roomJid, from, reason, password));
        }

        @Override
        public void onChatState(long accountId, String conversation, String from, ChatState state) {
            ui(l -> l.onChatState(accountId, conversation, from, state));
        }

        @Override
        public void onUploadProgress(long accountId, long messageDbId, long sent, long total) {
            ui(l -> l.onUploadProgress(accountId, messageDbId, sent, total));
        }

        @Override
        public void onOccupantsChanged(long accountId, String roomJid) {
            ui(l -> l.onOccupantsChanged(accountId, roomJid));
        }

        @Override
        public void onRoomClosed(long accountId, String roomJid, XmppError reason) {
            ui(l -> l.onRoomClosed(accountId, roomJid, reason));
        }

        @Override
        public void onBlocklistChanged(long accountId) {
            ui(l -> l.onBlocklistChanged(accountId));
        }
    };

    /** Convenience for screens: a contact's display name, falling back to the JID. */
    public String nameOf(long accountId, String jid) {
        XmppController c = controller(accountId);
        if (c != null) {
            XmppContact ct = c.contact(jid);
            if (ct != null && !ct.name.isEmpty()) {
                return ct.name;
            }
            XmppConversation conv = c.conversation(jid);
            if (conv != null && !conv.title.isEmpty()) {
                return conv.title;
            }
        }
        return jid;
    }
}
