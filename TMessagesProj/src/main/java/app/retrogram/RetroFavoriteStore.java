package app.retrogram;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

/**
 * Local copies of favorited messages (the star / "Add to Favorites" action, backed by NagramXF's bookmark ids).
 *
 * NagramXF's bookmarks keep only message ids and read the message back from the chat's local history, so a bookmark
 * of a deleted or cleared chat shows an empty placeholder. This store keeps a serialized copy of every favorited message
 * (and the chat title) in a private preferences file, so the Favorites screens can still show the message afterwards.
 * Media files are not copied: the text, captions and file info stay, the file itself is only there while Telegram's
 * cache or the server still has it.
 */
public final class RetroFavoriteStore {

    private static final String PREFS = "retro_favorites";
    private static final String MESSAGE_PREFIX = "m_";
    private static final String TITLE_PREFIX = "t_";

    private RetroFavoriteStore() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String owner(int account) {
        final long userId = UserConfig.getInstance(account).getClientUserId();
        return String.valueOf(userId != 0 ? userId : account);
    }

    private static String messageKey(int account, long dialogId, int messageId) {
        return MESSAGE_PREFIX + owner(account) + "_" + dialogId + "_" + messageId;
    }

    private static String titleKey(int account, long dialogId) {
        return TITLE_PREFIX + owner(account) + "_" + dialogId;
    }

    /** Stores a copy of [message] (and the chat title, when known). Returns false if it could not be serialized. */
    public static boolean put(int account, long dialogId, TLRPC.Message message, String chatTitle) {
        if (message == null || message.id == 0) {
            return false;
        }
        try {
            final SerializedData data = new SerializedData(message.getObjectSize());
            message.serializeToStream(data);
            final String encoded = Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP);
            final SharedPreferences.Editor editor = prefs().edit().putString(messageKey(account, dialogId, message.id), encoded);
            if (chatTitle != null && !chatTitle.isEmpty()) {
                editor.putString(titleKey(account, dialogId), chatTitle);
            }
            editor.apply();
            return true;
        } catch (Throwable e) {
            FileLog.e("Retrogram: could not store a favorite message", e);
            return false;
        }
    }

    /** The stored copy, or null when there is none or it no longer parses. */
    public static TLRPC.Message get(int account, long dialogId, int messageId) {
        final String encoded = prefs().getString(messageKey(account, dialogId, messageId), null);
        if (encoded == null) {
            return null;
        }
        try {
            final SerializedData data = new SerializedData(Base64.decode(encoded, Base64.NO_WRAP));
            final int constructor = data.readInt32(false);
            final TLRPC.Message message = TLRPC.Message.TLdeserialize(data, constructor, false);
            if (message != null) {
                message.dialog_id = dialogId;
            }
            return message;
        } catch (Throwable e) {
            FileLog.e("Retrogram: could not read a favorite message", e);
            return null;
        }
    }

    public static boolean has(int account, long dialogId, int messageId) {
        return prefs().contains(messageKey(account, dialogId, messageId));
    }

    public static void remove(int account, long dialogId, int messageId) {
        prefs().edit().remove(messageKey(account, dialogId, messageId)).apply();
    }

    /** Chat title saved with the favorites, for chats the app no longer knows. */
    public static String getTitle(int account, long dialogId) {
        return prefs().getString(titleKey(account, dialogId), null);
    }

    /** Forgets every copy of this account (used by "clear all bookmarks"). */
    public static void clear(int account) {
        final String messagePrefix = MESSAGE_PREFIX + owner(account) + "_";
        final String titlePrefix = TITLE_PREFIX + owner(account) + "_";
        final SharedPreferences prefs = prefs();
        final SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(messagePrefix) || key.startsWith(titlePrefix)) {
                editor.remove(key);
            }
        }
        editor.apply();
    }
}
