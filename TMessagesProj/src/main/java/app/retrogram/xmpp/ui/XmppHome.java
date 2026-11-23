package app.retrogram.xmpp.ui;

import android.graphics.Bitmap;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Adapters.DrawerLayoutAdapter;
import org.telegram.ui.Cells.DrawerProfileCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.List;

import app.retrogram.xmpp.XmppAccounts;
import app.retrogram.xmpp.XmppAvatars;
import app.retrogram.xmpp.XmppManager;
import app.retrogram.xmpp.core.XmppAccountConfig;
import app.retrogram.xmpp.core.XmppController;

/**
 * Lets an XMPP account stand in the app as an account of its own, next to the Telegram accounts: it is listed in
 * the drawer's account switcher, it is added from the login screen (⋮ → XMPP), and while it is selected the main
 * screen is {@link XmppDialogsActivity} and the drawer shows this account's header and actions.
 * <p>
 * The Telegram account selection ({@code UserConfig.selectedAccount}) is left as it is underneath; switching to any
 * Telegram account in the drawer leaves XMPP mode.
 */
public final class XmppHome {

    // Drawer rows shown while an XMPP account is the current account. Far from the Telegram / Nagram ids.
    public static final int DRAWER_NEW_GROUP = 1101;
    public static final int DRAWER_JOIN_GROUP = 1102;
    public static final int DRAWER_BROWSE_GROUPS = 1103;
    public static final int DRAWER_CONTACTS = 1104;
    public static final int DRAWER_ADD_CONTACT = 1105;
    public static final int DRAWER_STATUS = 1106;
    public static final int DRAWER_ACCOUNT = 1107;
    public static final int DRAWER_ACCOUNTS = 1108;

    private XmppHome() {
    }

    /** The XMPP account the main screen shows, or 0 when a Telegram account is shown (or the selected XMPP account is gone). */
    public static long activeAccount() {
        long id = XmppAccounts.selected();
        if (id == 0) {
            return 0;
        }
        if (XmppManager.get().controller(id) == null) {
            XmppAccounts.setSelected(0);
            return 0;
        }
        return id;
    }

    public static boolean isActive() {
        return activeAccount() != 0;
    }

    /** Remembers which account the main screen shows; 0 = Telegram. Call {@link LaunchActivity#switchToXmppAccount} to also rebuild the screens. */
    public static void select(long accountId) {
        XmppAccounts.setSelected(accountId);
    }

    /** The main screen for the selected XMPP account, or null when a Telegram account is the current one. */
    public static BaseFragment createHomeFragment() {
        long id = activeAccount();
        return id == 0 ? null : new XmppDialogsActivity(id);
    }

    /** XMPP accounts for the drawer's account list, in the order they were added. */
    public static List<Long> drawerAccounts() {
        List<Long> out = new ArrayList<>();
        for (XmppController c : XmppManager.get().controllers()) {
            out.add(c.accountId());
        }
        return out;
    }

    /** The name the drawer shows for an account: its label, else the nickname, else the address. */
    public static String accountName(XmppAccountConfig c) {
        if (c == null) {
            return "";
        }
        if (c.label != null && !c.label.trim().isEmpty()) {
            return c.label.trim();
        }
        if (c.nickname != null && !c.nickname.trim().isEmpty()) {
            return c.nickname.trim();
        }
        return c.jid;
    }

    // ---------------------------------------------------------------------------------------------- drawer

    /** Fills the drawer's action rows for the selected XMPP account (instead of the Telegram rows). */
    public static void addDrawerItems(List<DrawerLayoutAdapter.Item> items) {
        items.add(item(DRAWER_NEW_GROUP, R.string.XmppCreateGroup, R.drawable.msg_groups));
        items.add(item(DRAWER_JOIN_GROUP, R.string.XmppJoinGroup, R.drawable.msg_discussion));
        items.add(item(DRAWER_BROWSE_GROUPS, R.string.XmppBrowseGroups, R.drawable.msg_search));
        items.add(item(DRAWER_CONTACTS, R.string.XmppContacts, R.drawable.msg_contacts));
        items.add(item(DRAWER_ADD_CONTACT, R.string.XmppAddContact, R.drawable.msg_addcontact));
        items.add(item(DRAWER_STATUS, R.string.XmppSetStatus, R.drawable.msg_online));
        items.add(null);
        items.add(item(DRAWER_ACCOUNT, R.string.XmppAccountSettings, R.drawable.msg_settings_old));
        items.add(item(DRAWER_ACCOUNTS, R.string.XmppAccounts, R.drawable.msg2_devices));
        if (UserConfig.getInstance(UserConfig.selectedAccount).isClientActivated()) {
            // App-wide settings live under a Telegram account; offer them when there is one to open them from.
            DrawerLayoutAdapter.Item settings = DrawerLayoutAdapter.buildItemForId(DrawerLayoutAdapter.nkbtnSettings);
            if (settings != null) {
                items.add(settings);
            }
        }
    }

    private static DrawerLayoutAdapter.Item item(int id, int text, int icon) {
        return new DrawerLayoutAdapter.Item(id, LocaleController.getString(text), icon).onClick(v -> onDrawerItem(id));
    }

    private static void onDrawerItem(int id) {
        long accountId = activeAccount();
        XmppController c = XmppManager.get().controller(accountId);
        BaseFragment f = LaunchActivity.getSafeLastFragment();
        if (c == null || f == null) {
            return;
        }
        switch (id) {
            case DRAWER_NEW_GROUP:
                XmppDialogs.joinRoom(f, c, "", true, room -> f.presentFragment(new XmppChatActivity(accountId, room, true)));
                break;
            case DRAWER_JOIN_GROUP:
                XmppDialogs.joinRoom(f, c, "", false, room -> f.presentFragment(new XmppChatActivity(accountId, room, true)));
                break;
            case DRAWER_BROWSE_GROUPS:
                f.presentFragment(new XmppRoomsActivity(accountId));
                break;
            case DRAWER_CONTACTS:
                f.presentFragment(new XmppContactsActivity(accountId));
                break;
            case DRAWER_ADD_CONTACT:
                XmppDialogs.addContact(f, c, "");
                break;
            case DRAWER_STATUS:
                XmppDialogs.setStatus(f, c);
                break;
            case DRAWER_ACCOUNT:
                f.presentFragment(new XmppAccountEditActivity(accountId));
                break;
            case DRAWER_ACCOUNTS:
                f.presentFragment(new XmppAccountsActivity());
                break;
            default:
                break;
        }
    }

    /** Shows the selected XMPP account in the drawer header. Returns false (header untouched) when Telegram is current. */
    public static boolean bindDrawerHeader(DrawerProfileCell cell, boolean accountsShown) {
        long id = activeAccount();
        if (cell == null || id == 0) {
            return false;
        }
        XmppController c = XmppManager.get().controller(id);
        XmppAccountConfig config = c.config();
        AvatarDrawable fallback = new AvatarDrawable();
        fallback.setInfo(Math.abs((long) config.jid.toLowerCase().hashCode()), accountName(config), "");
        Bitmap photo = XmppAvatars.get(id, config.jid);
        cell.setXmppAccount(accountName(config), config.jid, photo, fallback, accountsShown);
        return true;
    }

    /** The drawer header's avatar was tapped while an XMPP account is current. */
    public static void openAccountSettings() {
        long id = activeAccount();
        BaseFragment f = LaunchActivity.getSafeLastFragment();
        if (id != 0 && f != null) {
            f.presentFragment(new XmppAccountEditActivity(id));
        }
    }

    /** An account was removed in the XMPP screens. Leaves XMPP mode when it was the current account. */
    public static void onAccountRemoved(long accountId) {
        if (XmppAccounts.selected() == accountId) {
            XmppAccounts.setSelected(0);
        }
        LaunchActivity a = LaunchActivity.instance;
        if (a == null) {
            return;
        }
        if (isShowingHomeOf(a, accountId)) {
            a.exitXmppMode();
        } else {
            a.refreshDrawerAccounts();
        }
    }

    /** An account was added or renamed: the drawer's account list changes. */
    public static void onAccountsChanged() {
        if (LaunchActivity.instance != null) {
            LaunchActivity.instance.refreshDrawerAccounts();
        }
    }

    private static boolean isShowingHomeOf(LaunchActivity a, long accountId) {
        if (a.getActionBarLayout() == null || a.getActionBarLayout().getFragmentStack().isEmpty()) {
            return false;
        }
        BaseFragment root = a.getActionBarLayout().getFragmentStack().get(0);
        return root instanceof XmppDialogsActivity && ((XmppDialogsActivity) root).getAccountId() == accountId;
    }
}
