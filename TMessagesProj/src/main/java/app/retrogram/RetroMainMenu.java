package app.retrogram;

import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.Base64;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionIntroActivity;
import org.telegram.ui.Adapters.DrawerLayoutAdapter;
import org.telegram.ui.CameraScanActivity;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ItemOptions;

import xyz.nextalone.nagram.helper.DrawerMenuHelper;
import xyz.nextalone.nagram.helper.MainMenuActions;

/**
 * The chat list's "three dots" menu in retro mode, with the set of shortcuts the project owner asked for after Graph
 * Messenger (behaviour described from screenshots only, no Graph code): Saved Messages, Archive, Feed, Hashtags+,
 * Bookmarks, Games, Scan QR code, In-app browser. Graph's "AI chat bot" entry is left out on purpose.
 */
public final class RetroMainMenu {

    private RetroMainMenu() {
    }

    private static final String LOGIN_PREFIX = "tg://login?token=";

    public static void fill(ItemOptions io, BaseFragment fragment, int currentAccount) {
        io.add(R.drawable.msg_saved, getString(R.string.SavedMessages),
                () -> MainMenuActions.openItem(DrawerMenuHelper.ID_SAVED, fragment, currentAccount));
        io.add(R.drawable.msg_archive, getString(R.string.Archive),
                () -> MainMenuActions.openItem(DrawerLayoutAdapter.nkbtnArchivedChats, fragment, currentAccount));
        io.add(R.drawable.ic_feed, getString(R.string.Feed),
                () -> MainMenuActions.openItem(DrawerLayoutAdapter.nkbtnFeed, fragment, currentAccount));
        io.add(R.drawable.menu_hashtag, getString(R.string.RetroHashtags),
                () -> fragment.presentFragment(new RetroHashtagsActivity()));
        io.add(R.drawable.msg_fave, getString(R.string.RetroFavorites),
                () -> MainMenuActions.openItem(DrawerLayoutAdapter.nkbtnBookmarks, fragment, currentAccount));
        io.add(R.drawable.retro_menu_games, getString(R.string.RetroGames),
                () -> fragment.presentFragment(new RetroGamesActivity()));
        io.add(R.drawable.msg_qrcode, getString(R.string.AuthAnotherClientScan),
                () -> scanQr(fragment, currentAccount));
        io.add(R.drawable.web_browser, getString(R.string.InappBrowser),
                () -> MainMenuActions.longClickItem(DrawerLayoutAdapter.nkbtnBrowser, fragment, currentAccount),
                () -> MainMenuActions.openItem(DrawerLayoutAdapter.nkbtnBrowser, fragment, currentAccount));
    }

    /**
     * General QR scanner: a Telegram login code (another device's "Log in by QR code") is accepted like the drawer's
     * QR login, a link is opened (t.me links inside the app), anything else is copied to the clipboard.
     */
    public static void scanQr(BaseFragment fragment, int currentAccount) {
        final Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // LaunchActivity explains a refusal; the user taps the item again once access is granted.
            activity.requestPermissions(new String[]{Manifest.permission.CAMERA}, ActionIntroActivity.CAMERA_PERMISSION_REQUEST_CODE);
            return;
        }
        CameraScanActivity.showAsSheet(fragment, false, CameraScanActivity.TYPE_QR, new CameraScanActivity.CameraScanActivityDelegate() {
            @Override
            public void didFindQr(String text) {
                AndroidUtilities.runOnUIThread(() -> handleScan(fragment, currentAccount, text));
            }
        });
    }

    private static void handleScan(BaseFragment fragment, int currentAccount, String text) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        final String trimmed = text.trim();
        if (trimmed.startsWith(LOGIN_PREFIX)) {
            acceptLoginToken(fragment, currentAccount, trimmed);
        } else if (isLink(trimmed)) {
            Activity activity = fragment.getParentActivity();
            if (activity != null) {
                Browser.openUrl(activity, trimmed);
            }
        } else {
            AndroidUtilities.addToClipboard(trimmed);
            BulletinFactory.of(fragment).createCopyBulletin(getString(R.string.TextCopied)).show();
        }
    }

    static boolean isLink(String text) {
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("tg:") || lower.startsWith("ton:")
                || lower.startsWith("t.me/") || lower.startsWith("telegram.me/");
    }

    private static void acceptLoginToken(BaseFragment fragment, int currentAccount, String link) {
        try {
            String code = link.substring(LOGIN_PREFIX.length()).replace('/', '_').replace('+', '-');
            TLRPC.TL_auth_acceptLoginToken req = new TLRPC.TL_auth_acceptLoginToken();
            req.token = Base64.decode(code, Base64.URL_SAFE);
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (fragment.getParentActivity() == null) {
                    return;
                }
                if (error == null) {
                    BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check, getString(R.string.AuthAnotherClientOk)).show();
                } else {
                    BulletinFactory.of(fragment).createErrorBulletin(getString(R.string.ErrorOccurred) + (error.text != null ? " (" + error.text + ")" : "")).show();
                }
            }));
        } catch (Exception e) {
            FileLog.e("Retrogram: QR login failed", e);
            BulletinFactory.of(fragment).createErrorBulletin(getString(R.string.ErrorOccurred)).show();
        }
    }
}
