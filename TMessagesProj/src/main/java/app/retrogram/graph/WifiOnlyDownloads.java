package app.retrogram.graph;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import app.retrogram.RetroConfig;

/**
 * Holds explicit large downloads (WP 6.5 remainder) until the phone is on Wi-Fi ("Large files on Wi-Fi only") and/or the clock is inside
 * a daily window ("Download window for large files"). A document of at least 25 MB requested at normal or higher priority (a tap) while
 * either rule is not met is put on hold and starts by itself when both are met. Tapping the same file again while it is on hold downloads
 * it right away over the current connection, so nobody gets stuck.
 * Hooked into `FileLoader`'s private document `loadFile`; it does not touch streaming (video playback), photos, stickers, voice notes,
 * downloads started at low priority (Telegram's own auto-download settings cover those) or uploads.
 * Survives the app being killed: a held file is also registered in Telegram's own persistent download queue (`DownloadController.startDownloadFile`,
 * the "Downloading" list of the Downloads tab, stored in `downloading_documents`) and noted in a small preference set. An alarm at the window start
 * ({@link DownloadWindowReceiver}), network changes and the app start then start every noted file that is still in that queue once the rules are met.
 * Limits: the alarm is inexact (`setAndAllowWhileIdle`, may be late), Android may stop a background process during a long download, and a download that has
 * already started is not paused when the window closes.
 */
public final class WifiOnlyDownloads {

    public static final long MIN_SIZE_BYTES = 25L * 1024 * 1024;

    private static final String PREFS = "retro_held_downloads";
    private static final long MAX_HOLD_MS = 2L * 24 * 60 * 60 * 1000;

    private static final Map<String, Runnable> held = new LinkedHashMap<>();
    private static final Runnable windowTimer = WifiOnlyDownloads::releaseReady;

    private WifiOnlyDownloads() {}

    private static boolean wifiRuleActive() {
        return RetroConfig.graphWifiOnlyDownloads.Bool();
    }

    private static boolean windowRuleActive() {
        return RetroConfig.graphDownloadWindow.Bool();
    }

    private static boolean waitingForWifi() {
        return wifiRuleActive() && !ApplicationLoader.isConnectedToWiFi();
    }

    private static boolean outsideWindow() {
        if (!windowRuleActive()) {
            return false;
        }
        final Calendar c = Calendar.getInstance();
        return !DownloadWindow.contains(c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE),
            RetroConfig.graphDownloadWindowStart.Int(), RetroConfig.graphDownloadWindowEnd.Int());
    }

    /**
     * Returns true when the download was put on hold ({@code start} is kept to run later). Call it with the runnable that performs the
     * real download; when it returns false the caller proceeds as usual.
     */
    public static boolean maybeHold(int account, TLRPC.Document document, Object parentObject, int priority, String fileName, Runnable start) {
        try {
            if (!(wifiRuleActive() || windowRuleActive()) || document == null || fileName == null
                || document.size < MIN_SIZE_BYTES || priority < FileLoader.PRIORITY_NORMAL) {
                return false;
            }
            final boolean wifi = waitingForWifi();
            final boolean window = outsideWindow();
            if (!wifi && !window) {
                return false;
            }
            synchronized (held) {
                if (held.containsKey(fileName)) {
                    held.remove(fileName); // second tap: go ahead right now
                    return false;
                }
                held.put(fileName, start);
            }
            persistHold(account, fileName, parentObject, document);
            final String message = window
                ? String.format(Locale.US, LocaleController.getString(R.string.RetroGraphDownloadWindowHeld),
                    RetroConfig.graphDownloadWindowStart.Int(), RetroConfig.graphDownloadWindowEnd.Int())
                : LocaleController.getString(R.string.RetroGraphWifiOnlyHeld);
            AndroidUtilities.runOnUIThread(() -> Toast.makeText(ApplicationLoader.applicationContext, message, Toast.LENGTH_LONG).show());
            if (window) {
                scheduleWindowCheck();
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void scheduleWindowCheck() {
        final Calendar c = Calendar.getInstance();
        final long delay = DownloadWindow.millisUntilOpen(c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE), c.get(Calendar.SECOND),
            RetroConfig.graphDownloadWindowStart.Int(), RetroConfig.graphDownloadWindowEnd.Int());
        AndroidUtilities.cancelRunOnUIThread(windowTimer);
        AndroidUtilities.runOnUIThread(windowTimer, delay + 1000);
        scheduleAlarm(delay + 1000);
    }

    private static PendingIntent alarmIntent() {
        final Context context = ApplicationLoader.applicationContext;
        final Intent intent = new Intent(context, DownloadWindowReceiver.class);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Wakes the app at the window start even if the process was killed; inexact on purpose (no exact-alarm permission). */
    private static void scheduleAlarm(long delayMs) {
        try {
            final AlarmManager am = (AlarmManager) ApplicationLoader.applicationContext.getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMs, alarmIntent());
            }
        } catch (Throwable ignore) {
        }
    }

    // ---- persistence of what was held (survives a killed process) ----

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void persistHold(int account, String fileName, Object parentObject, TLRPC.Document document) {
        try {
            if (parentObject instanceof MessageObject) {
                // Telegram's own persistent queue: the file shows up under "Downloading" and is reloaded after a restart
                DownloadController.getInstance(account).startDownloadFile(document, (MessageObject) parentObject);
            }
            synchronized (PREFS) {
                final Set<String> set = new HashSet<>(prefs().getStringSet("held", new HashSet<>()));
                set.add(account + "|" + System.currentTimeMillis() + "|" + fileName);
                prefs().edit().putStringSet("held", set).apply();
            }
        } catch (Throwable ignore) {
        }
    }

    /** Whether any file is noted as held (used at app start to decide if a release check is worth scheduling). */
    public static boolean hasPersistedHolds() {
        try {
            return !prefs().getStringSet("held", new HashSet<>()).isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Loads each account's persisted download queue; Telegram itself only does it when the chat list opens, which a cold start from the alarm never does. */
    private static void loadQueues() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                DownloadController.getInstance(a).loadDownloadingFiles();
            }
        }
    }

    /** Starts the noted files that are still in the persistent queue; drops notes that are gone or older than two days. */
    private static void releasePersisted() {
        final Set<String> remaining = new HashSet<>();
        final long now = System.currentTimeMillis();
        synchronized (PREFS) {
            final Set<String> set = new HashSet<>(prefs().getStringSet("held", new HashSet<>()));
            for (String entry : set) {
                final String[] p = entry.split("\\|", 3);
                if (p.length < 3) {
                    continue;
                }
                try {
                    final int account = Integer.parseInt(p[0]);
                    final long since = Long.parseLong(p[1]);
                    final String fileName = p[2];
                    MessageObject found = null;
                    for (MessageObject m : new ArrayList<>(DownloadController.getInstance(account).downloadingFiles)) {
                        if (m != null && fileName.equals(m.getFileName())) {
                            found = m;
                            break;
                        }
                    }
                    if (found != null && found.getDocument() != null) {
                        FileLoader.getInstance(account).loadFile(found.getDocument(), found, FileLoader.PRIORITY_NORMAL, 0);
                    } else if (now - since < MAX_HOLD_MS) {
                        remaining.add(entry); // queue not loaded yet, try again later
                    }
                } catch (NumberFormatException ignore) {
                }
            }
            prefs().edit().putStringSet("held", remaining).apply();
        }
    }

    /** From the alarm and from the app start: waits a few seconds for the queue to load, then releases what may run. */
    public static void releaseSoon() {
        loadQueues();
        AndroidUtilities.runOnUIThread(WifiOnlyDownloads::releaseReady, 5000);
    }

    /** Called when the network changes (`FileLoader.onNetworkChanged`) and when the window opens. Starts what on hold may run now. */
    public static void onNetworkChanged() {
        releaseReady();
    }

    private static void releaseReady() {
        if (waitingForWifi()) {
            return;
        }
        if (outsideWindow()) {
            synchronized (held) {
                if (!held.isEmpty() || hasPersistedHolds()) {
                    scheduleWindowCheck();
                }
            }
            return;
        }
        final ArrayList<Runnable> toStart;
        synchronized (held) {
            toStart = new ArrayList<>(held.values());
            held.clear();
        }
        for (Runnable r : toStart) {
            r.run();
        }
        if (hasPersistedHolds()) {
            releasePersisted();
        }
    }
}
