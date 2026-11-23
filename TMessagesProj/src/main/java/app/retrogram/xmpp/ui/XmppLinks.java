package app.retrogram.xmpp.ui;

import android.content.Intent;
import android.net.Uri;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;

import java.net.URLDecoder;

import app.retrogram.xmpp.XmppManager;
import app.retrogram.xmpp.XmppNotifications;
import app.retrogram.xmpp.core.Jids;
import app.retrogram.xmpp.core.XmppController;
import app.retrogram.xmpp.core.XmppConversation;

/** Entry points from outside the XMPP screens: notification taps and {@code xmpp:} links. */
public final class XmppLinks {
    private XmppLinks() {
    }

    /** Called by {@link LaunchActivity} for every intent. Returns true when the intent was an XMPP one and has been handled. */
    public static boolean handleIntent(LaunchActivity activity, Intent intent) {
        if (intent == null) {
            return false;
        }
        if (intent.hasExtra(XmppNotifications.EXTRA_ACCOUNT)) {
            long account = intent.getLongExtra(XmppNotifications.EXTRA_ACCOUNT, -1);
            String jid = intent.getStringExtra(XmppNotifications.EXTRA_JID);
            // Consume the extras: the same intent is delivered again after a configuration change.
            intent.removeExtra(XmppNotifications.EXTRA_ACCOUNT);
            intent.removeExtra(XmppNotifications.EXTRA_JID);
            if (account >= 0 && jid != null) {
                XmppController c = XmppManager.get().controller(account);
                if (c != null) {
                    XmppConversation conv = c.conversation(jid);
                    boolean group = conv != null && conv.groupChat;
                    // Like a Telegram notification of another account: that account becomes the current one first.
                    if (XmppHome.activeAccount() != account) {
                        activity.switchToXmppAccount(account);
                    }
                    activity.presentFragment(new XmppChatActivity(account, jid, group));
                    return true;
                }
            }
            activity.presentFragment(new XmppChatsActivity());
            return true;
        }
        Uri data = intent.getData();
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && data != null && "xmpp".equalsIgnoreCase(data.getScheme())) {
            openLink(activity, data);
            return true;
        }
        return false;
    }

    /** {@code xmpp:user@host}, {@code ?message}, {@code ?join}, {@code ?roster}, {@code ?subscribe}. */
    static void openLink(LaunchActivity activity, Uri uri) {
        String ssp = uri.getSchemeSpecificPart();
        if (ssp == null) {
            return;
        }
        // xmpp://account@host/user@host is the authority form: the target is the path.
        String target = ssp.startsWith("//") ? ssp.substring(ssp.indexOf('/', 2) + 1) : ssp;
        String query = "";
        int q = target.indexOf('?');
        if (q >= 0) {
            query = target.substring(q + 1);
            target = target.substring(0, q);
        }
        try {
            target = URLDecoder.decode(target, "UTF-8");
        } catch (Exception ignored) {
        }
        String jid = Jids.parseUserInput(target);
        if (!XmppManager.get().hasAccounts()) {
            activity.presentFragment(new XmppAccountsActivity());
            return;
        }
        long current = XmppHome.activeAccount();
        BaseFragment base;
        if (current != 0) {
            // An XMPP account is the current account: the link is handled on its chat list, with that account.
            base = LaunchActivity.getSafeLastFragment();
        } else {
            XmppChatsActivity chats = new XmppChatsActivity();
            activity.presentFragment(chats);
            base = chats;
        }
        if (jid == null || base == null) {
            return;
        }
        String action = query.split("[;&]")[0].toLowerCase();
        run(base, jid, action, current);
    }

    private static void run(BaseFragment f, String jid, String action, long preferred) {
        switch (action) {
            case "join":
                XmppDialogs.pickAccount(f, preferred, c -> XmppDialogs.joinRoom(f, c, jid, false, room -> f.presentFragment(new XmppChatActivity(c.accountId(), room, true))));
                break;
            case "roster":
            case "subscribe":
                XmppDialogs.pickAccount(f, preferred, c -> XmppDialogs.addContact(f, c, jid));
                break;
            default:
                XmppDialogs.pickAccount(f, preferred, c -> {
                    c.openChat(jid);
                    f.presentFragment(new XmppChatActivity(c.accountId(), jid, false));
                });
                break;
        }
    }
}
