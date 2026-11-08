package app.retrogram.graph;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.core.app.NotificationCompat;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.LaunchActivity;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import app.retrogram.RetroConfig;

/**
 * Feeds {@link ContactChangeDetector} from `NotificationCenter.updateInterfaces` (WP 6.6) and keeps a capped log on the device.
 * Nothing is polled and nothing is sent anywhere: it only sees what Telegram already delivers for users the account receives updates about.
 * Off unless "Special contacts log" is on and at least one user id is set. The log is a plain text list in SharedPreferences
 * (newest first, 200 lines); a SQLite store, notifications and a drawer screen are not done.
 */
public final class ContactWatchHook implements NotificationCenter.NotificationCenterDelegate {

    private static final int MASK = MessagesController.UPDATE_MASK_NAME | MessagesController.UPDATE_MASK_AVATAR
        | MessagesController.UPDATE_MASK_STATUS | MessagesController.UPDATE_MASK_PHONE;
    private static final int MAX_LINES = 200;
    private static final String PREFS = "retro_contact_log";
    private static final String KEY_LOG = "log";

    private static final ContactWatchHook INSTANCE = new ContactWatchHook();
    private static boolean installed;

    private final ContactChangeDetector[] detectors = new ContactChangeDetector[UserConfig.MAX_ACCOUNT_COUNT];
    private final String[] watchedFor = new String[UserConfig.MAX_ACCOUNT_COUNT];

    private ContactWatchHook() {}

    /** Call once at startup, after `RetroConfig.init()`. */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).addObserver(INSTANCE, NotificationCenter.updateInterfaces);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        try {
            if (id != NotificationCenter.updateInterfaces || !RetroConfig.graphSpecialContacts.Bool()
                || args.length == 0 || !(args[0] instanceof Integer) || ((Integer) args[0] & MASK) == 0) {
                return;
            }
            final List<Long> ids = parseIds(RetroConfig.graphSpecialContactIds.String());
            if (ids.isEmpty()) {
                return;
            }
            final ContactChangeDetector detector = detectorFor(account, RetroConfig.graphSpecialContactIds.String(), ids);
            final long now = System.currentTimeMillis();
            for (long userId : ids) {
                final TLRPC.User user = MessagesController.getInstance(account).getUser(userId);
                if (user == null) {
                    continue;
                }
                final ContactChangeDetector.Snapshot snapshot = new ContactChangeDetector.Snapshot(
                    user.status instanceof TLRPC.TL_userStatusOnline,
                    UserObject.getUserName(user),
                    UserObject.getPublicUsername(user),
                    user.photo != null ? user.photo.photo_id : 0,
                    user.phone);
                for (ContactChangeDetector.Change change : detector.update(userId, snapshot, now)) {
                    append(change, UserObject.getUserName(user));
                }
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private ContactChangeDetector detectorFor(int account, String raw, List<Long> ids) {
        ContactChangeDetector detector = detectors[account];
        if (detector == null || !raw.equals(watchedFor[account])) {
            // watch list changed: start from fresh baselines so a newly added user does not log everything as changed
            detector = new ContactChangeDetector();
            for (long id : ids) {
                detector.watch(id);
            }
            detectors[account] = detector;
            watchedFor[account] = raw;
        }
        return detector;
    }

    static List<Long> parseIds(String raw) {
        final List<Long> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String part : raw.split("[,;\\s]+")) {
            try {
                if (!part.isEmpty()) {
                    final long id = Long.parseLong(part);
                    if (id > 0 && !out.contains(id)) {
                        out.add(id);
                    }
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\n', ' ').replace('|', '/');
    }

    private static synchronized void append(ContactChangeDetector.Change change, String displayName) {
        final SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        final String line = change.atMs + "|" + clean(displayName) + "|" + change.kind + "|" + clean(change.oldValue) + "|" + clean(change.newValue);
        final String old = prefs.getString(KEY_LOG, "");
        final StringBuilder sb = new StringBuilder(line);
        int lines = 1;
        for (String l : old.split("\n")) {
            if (l.isEmpty() || lines >= MAX_LINES) {
                continue;
            }
            sb.append('\n').append(l);
            lines++;
        }
        prefs.edit().putString(KEY_LOG, sb.toString()).apply();
        if (RetroConfig.graphSpecialContactsNotify.Bool()) {
            notifyChange(change, displayName);
        }
    }

    private static final String CHANNEL_ID = "retro_contacts";

    private static String describe(ContactChangeDetector.Change change) {
        final StringBuilder sb = new StringBuilder(change.kind.toString().toLowerCase());
        if (change.oldValue != null || change.newValue != null) {
            sb.append(' ').append(change.oldValue == null ? "" : change.oldValue).append(" -> ").append(change.newValue == null ? "" : change.newValue);
        }
        return sb.toString();
    }

    /** One notification per change, in its own channel so the user can silence it in the system settings. Ignored when notifications are not allowed. */
    private static void notifyChange(ContactChangeDetector.Change change, String displayName) {
        try {
            final Context context = ApplicationLoader.applicationContext;
            final NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) {
                return;
            }
            manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID, LocaleController.getString(R.string.RetroGraphContactsLog), NotificationManager.IMPORTANCE_DEFAULT));
            final Intent open = new Intent(context, LaunchActivity.class);
            final PendingIntent pending = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            final Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.notification)
                .setContentTitle(displayName)
                .setContentText(describe(change))
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build();
            manager.notify(("retro_contact_" + change.userId).hashCode(), notification);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    /** The log in a dialog (settings row and drawer entry). */
    public static void showLog(Context context) {
        final String log = readableLog();
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.RetroGraphContactsLog));
        builder.setMessage(log.isEmpty() ? LocaleController.getString(R.string.RetroGraphContactsLogEmpty) : log);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        if (!log.isEmpty()) {
            builder.setNegativeButton(LocaleController.getString(R.string.RetroGraphContactsLogClear), (d, w) -> clearLog());
        }
        builder.show();
    }

    /** Human-readable log, newest first. */
    public static synchronized String readableLog() {
        final SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        final String raw = prefs.getString(KEY_LOG, "");
        if (raw.isEmpty()) {
            return "";
        }
        final DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        final StringBuilder sb = new StringBuilder();
        for (String line : raw.split("\n")) {
            final String[] p = line.split("\\|", -1);
            if (p.length < 5) {
                continue;
            }
            String when = p[0];
            try {
                when = format.format(new Date(Long.parseLong(p[0])));
            } catch (NumberFormatException ignored) {
            }
            sb.append(when).append("  ").append(p[1]).append(": ").append(p[2].toLowerCase());
            if (!p[3].isEmpty() || !p[4].isEmpty()) {
                sb.append(" ").append(p[3]).append(" -> ").append(p[4]);
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    public static synchronized void clearLog() {
        ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_LOG).apply();
    }
}
