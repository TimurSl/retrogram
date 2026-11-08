package app.retrogram.graph;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.util.Base64;
import android.util.SparseArray;
import android.widget.EditText;
import android.widget.Toast;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.LaunchActivity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import app.retrogram.RetroConfig;

/**
 * Android side of the per-chat lock (WP 6.1): the set of locked chats, the PIN, the gate dialog and the hooks.
 * Decisions come from {@link ChatLockPolicy}. Honest scope: this only stops the chat screen from opening and hides
 * notification text for locked chats. It does NOT encrypt the message database, does not hide the chat in the chat list
 * (that is WP 6.2), and does not cover every way into a chat (widgets, share targets, in-app search results previews).
 * The PIN is a salted SHA-256 hash in private preferences; there is no biometric option and no PIN recovery:
 * forgetting it means clearing the app data. Off unless "Per-chat lock" is switched on.
 */
public final class ChatLockGate {

    private static final String PREFS = "retro_chat_lock";
    private static final long RELOCK_TIMEOUT_MS = 60_000;

    private static final SparseArray<ChatLockPolicy> policies = new SparseArray<>();

    private ChatLockGate() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static synchronized ChatLockPolicy policy(int account) {
        ChatLockPolicy policy = policies.get(account);
        if (policy == null) {
            policy = new ChatLockPolicy(RELOCK_TIMEOUT_MS, true);
            for (String id : prefs().getStringSet("locked_" + account, new HashSet<>())) {
                try {
                    policy.lock(Long.parseLong(id));
                } catch (NumberFormatException ignored) {
                }
            }
            policies.put(account, policy);
        }
        return policy;
    }

    private static void persist(int account, long dialogId, boolean locked) {
        final Set<String> ids = new HashSet<>(prefs().getStringSet("locked_" + account, new HashSet<>()));
        if (locked) {
            ids.add(String.valueOf(dialogId));
        } else {
            ids.remove(String.valueOf(dialogId));
        }
        prefs().edit().putStringSet("locked_" + account, ids).apply();
    }

    public static boolean enabled() {
        return RetroConfig.graphChatLock.Bool();
    }

    public static synchronized boolean isLocked(int account, long dialogId) {
        return enabled() && policy(account).isLocked(dialogId);
    }

    /** Notification text, previews: true while the chat is locked (also right after unlocking, by design). */
    public static synchronized boolean shouldRedact(int account, long dialogId) {
        return enabled() && policy(account).shouldRedactPreview(dialogId);
    }

    public static synchronized void onAppBackgrounded() {
        for (int i = 0; i < policies.size(); i++) {
            policies.valueAt(i).onAppBackgrounded();
        }
    }

    // ---- PIN ----

    private static String hash(String salt, String pin) throws Exception {
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(Base64.decode(salt, Base64.NO_WRAP));
        return Base64.encodeToString(digest.digest(pin.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
    }

    public static boolean hasPin() {
        return prefs().contains("pin_hash");
    }

    private static void setPin(String pin) {
        try {
            final byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            final String s = Base64.encodeToString(salt, Base64.NO_WRAP);
            prefs().edit().putString("pin_salt", s).putString("pin_hash", hash(s, pin)).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static boolean checkPin(String pin) {
        try {
            final String salt = prefs().getString("pin_salt", null);
            final String expected = prefs().getString("pin_hash", null);
            return salt != null && expected != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), hash(salt, pin).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    private interface PinResult {
        void accept(String pin);
    }

    private static void askPin(Context context, String title, String message, PinResult onPin) {
        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setSingleLine(true);
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setView(input);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (d, w) -> onPin.accept(input.getText().toString()));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }

    /** Settings entry: sets the PIN, or changes it after asking for the old one. */
    public static void showSetPin(Context context) {
        final Runnable ask = () -> askPin(context, LocaleController.getString(R.string.RetroGraphChatLockSetPin),
            LocaleController.getString(R.string.RetroGraphChatLockPinInfo), pin -> {
                if (pin.length() < 4) {
                    Toast.makeText(context, LocaleController.getString(R.string.RetroGraphChatLockPinShort), Toast.LENGTH_SHORT).show();
                    return;
                }
                setPin(pin);
            });
        if (!hasPin()) {
            ask.run();
            return;
        }
        askPin(context, LocaleController.getString(R.string.RetroGraphChatLockSetPin),
            LocaleController.getString(R.string.RetroGraphChatLockEnterOld), old -> {
                if (checkPin(old)) {
                    ask.run();
                } else {
                    Toast.makeText(context, LocaleController.getString(R.string.RetroGraphChatLockWrongPin), Toast.LENGTH_SHORT).show();
                }
            });
    }

    // ---- gate ----

    /** Dialog id of a chat fragment from its arguments, or 0. */
    private static long dialogIdOf(BaseFragment fragment) {
        final Bundle args = fragment.getArguments();
        if (args == null) {
            return 0;
        }
        final long userId = args.getLong("user_id", 0);
        if (userId != 0) {
            return userId;
        }
        final long chatId = args.getLong("chat_id", 0);
        if (chatId != 0) {
            return -chatId;
        }
        final int encId = args.getInt("enc_id", 0);
        return encId != 0 ? DialogObject.makeEncryptedDialogId(encId) : 0;
    }

    /**
     * Called first thing in `ActionBarLayout.presentFragment`. Returns true when it took over (the chat is locked and
     * the gate dialog is shown); after a correct PIN it presents the same fragment again.
     */
    public static boolean intercept(INavigationLayout layout, INavigationLayout.NavigationParams params) {
        try {
            if (!enabled() || !(params.fragment instanceof ChatActivity)) {
                return false;
            }
            final BaseFragment fragment = params.fragment;
            final int account = fragment.getCurrentAccount();
            final long dialogId = dialogIdOf(fragment);
            if (dialogId == 0) {
                return false;
            }
            final long now = System.currentTimeMillis();
            final boolean needs;
            synchronized (ChatLockGate.class) {
                needs = policy(account).needsGate(dialogId, now);
            }
            final Context context = LaunchActivity.instance;
            if (!needs || context == null) {
                return false;
            }
            if (!hasPin()) {
                // locked chats without a PIN cannot be opened by anyone; let it through instead of locking the user out
                return false;
            }
            askPin(context, LocaleController.getString(R.string.RetroGraphChatLockTitle),
                LocaleController.getString(R.string.RetroGraphChatLockEnterPin), pin -> {
                    if (checkPin(pin)) {
                        synchronized (ChatLockGate.class) {
                            policy(account).onUnlocked(dialogId, System.currentTimeMillis());
                        }
                        layout.presentFragment(params);
                    } else {
                        Toast.makeText(context, LocaleController.getString(R.string.RetroGraphChatLockWrongPin), Toast.LENGTH_SHORT).show();
                    }
                });
            return true;
        } catch (Throwable t) {
            FileLog.e(t);
            return false;
        }
    }

    // ---- chat menu ----

    /** "Lock chat" / "Unlock chat" from the chat's menu. Unlocking asks for the PIN. */
    public static void toggleFromChatMenu(Context context, int account, long dialogId) {
        if (isLocked(account, dialogId)) {
            askPin(context, LocaleController.getString(R.string.RetroGraphChatLockTitle),
                LocaleController.getString(R.string.RetroGraphChatLockEnterPin), pin -> {
                    if (checkPin(pin)) {
                        synchronized (ChatLockGate.class) {
                            policy(account).removeLock(dialogId);
                        }
                        persist(account, dialogId, false);
                    } else {
                        Toast.makeText(context, LocaleController.getString(R.string.RetroGraphChatLockWrongPin), Toast.LENGTH_SHORT).show();
                    }
                });
            return;
        }
        if (!hasPin()) {
            showSetPin(context);
            return;
        }
        synchronized (ChatLockGate.class) {
            policy(account).lock(dialogId);
        }
        persist(account, dialogId, true);
        Toast.makeText(context, LocaleController.getString(R.string.RetroGraphChatLockLocked), Toast.LENGTH_SHORT).show();
    }

    public static String menuTitle(int account, long dialogId) {
        return LocaleController.getString(isLocked(account, dialogId) ? R.string.RetroGraphChatLockUnlockItem : R.string.RetroGraphChatLockLockItem);
    }
}
