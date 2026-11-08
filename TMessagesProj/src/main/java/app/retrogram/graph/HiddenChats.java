package app.retrogram.graph;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import app.retrogram.RetroConfig;

/**
 * Hidden chats (WP 6.2): chats you hide from the main chat list until you reveal them with the chat-lock PIN.
 * Hidden chats still receive messages. Scope, honestly: the chat list and the forward/share pickers built from
 * `DialogsActivity.getDialogsArray` drop them while they are hidden; notifications for them show no text. Not covered:
 * global search results, contacts, the archive, widgets, and the chat itself (hide it behind the lock too if that matters).
 * Revealing lasts until the app goes to the background. The PIN is the one of {@link ChatLockGate}.
 */
public final class HiddenChats {

    private static final String PREFS = "retro_hidden_chats";
    private static volatile boolean revealed;

    private HiddenChats() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean enabled() {
        return RetroConfig.graphHiddenChats.Bool();
    }

    public static boolean isHidden(int account, long dialogId) {
        return enabled() && prefs().getStringSet("hidden_" + account, java.util.Collections.emptySet()).contains(String.valueOf(dialogId));
    }

    /** Hidden and not currently revealed: what lists, search and pickers must leave out. */
    public static boolean isConcealed(int account, long dialogId) {
        return !revealed && isHidden(account, dialogId);
    }

    /** {@link #isConcealed} for the objects search results are made of (user, chat, secret chat, recent-search entry, message). */
    public static boolean isConcealedObject(int account, Object object) {
        if (!enabled() || revealed || object == null) {
            return false;
        }
        long id = 0;
        if (object instanceof TLRPC.User) {
            id = ((TLRPC.User) object).id;
        } else if (object instanceof TLRPC.Chat) {
            id = -((TLRPC.Chat) object).id;
        } else if (object instanceof TLRPC.EncryptedChat) {
            id = org.telegram.messenger.DialogObject.makeEncryptedDialogId(((TLRPC.EncryptedChat) object).id);
        } else if (object instanceof org.telegram.messenger.MessageObject) {
            id = ((org.telegram.messenger.MessageObject) object).getDialogId();
        }
        return id != 0 && isHidden(account, id);
    }

    public static boolean isRevealed() {
        return revealed;
    }

    /** Called when the app goes to the background. */
    public static void onAppBackgrounded() {
        if (revealed) {
            revealed = false;
        }
    }

    /**
     * Copy of {@code source} without hidden chats, or {@code source} itself when nothing needs filtering
     * (feature off, revealed, or no hidden chats), so the common case costs nothing.
     */
    public static ArrayList<TLRPC.Dialog> filter(int account, ArrayList<TLRPC.Dialog> source) {
        if (source == null || !enabled() || revealed) {
            return source;
        }
        final Set<String> hidden = prefs().getStringSet("hidden_" + account, java.util.Collections.emptySet());
        if (hidden.isEmpty()) {
            return source;
        }
        final ArrayList<TLRPC.Dialog> out = new ArrayList<>(source.size());
        for (int i = 0, n = source.size(); i < n; i++) {
            final TLRPC.Dialog dialog = source.get(i);
            if (dialog == null || !hidden.contains(String.valueOf(dialog.id))) {
                out.add(dialog);
            }
        }
        return out;
    }

    private static void refreshLists(int account) {
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
    }

    /** Chat menu: hide this chat, or unhide it (asks for the PIN). */
    public static void toggleFromChatMenu(Context context, int account, long dialogId) {
        if (isHidden(account, dialogId)) {
            ChatLockGate.requirePin(context, () -> {
                set(account, dialogId, false);
                Toast.makeText(context, LocaleController.getString(R.string.RetroGraphHiddenChatsUnhidden), Toast.LENGTH_SHORT).show();
            });
            return;
        }
        if (!ChatLockGate.hasPin()) {
            ChatLockGate.showSetPin(context);
            return;
        }
        set(account, dialogId, true);
        Toast.makeText(context, LocaleController.getString(R.string.RetroGraphHiddenChatsHidden), Toast.LENGTH_SHORT).show();
    }

    private static void set(int account, long dialogId, boolean hidden) {
        final Set<String> ids = new HashSet<>(prefs().getStringSet("hidden_" + account, new HashSet<>()));
        if (hidden) {
            ids.add(String.valueOf(dialogId));
        } else {
            ids.remove(String.valueOf(dialogId));
        }
        prefs().edit().putStringSet("hidden_" + account, ids).apply();
        refreshLists(account);
    }

    /** Settings row: reveal the hidden chats (PIN) or hide them again. */
    public static void toggleReveal(Context context, int account) {
        if (revealed) {
            revealed = false;
            refreshLists(account);
            Toast.makeText(context, LocaleController.getString(R.string.RetroGraphHiddenChatsHiddenAgain), Toast.LENGTH_SHORT).show();
            return;
        }
        ChatLockGate.requirePin(context, () -> {
            revealed = true;
            refreshLists(account);
            Toast.makeText(context, LocaleController.getString(R.string.RetroGraphHiddenChatsShown), Toast.LENGTH_SHORT).show();
        });
    }

    public static String menuTitle(int account, long dialogId) {
        return LocaleController.getString(isHidden(account, dialogId) ? R.string.RetroGraphHiddenChatsUnhideItem : R.string.RetroGraphHiddenChatsHideItem);
    }
}
