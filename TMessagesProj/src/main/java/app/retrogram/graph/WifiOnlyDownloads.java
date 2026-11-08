package app.retrogram.graph;

import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import app.retrogram.RetroConfig;

/**
 * Holds explicit large downloads (WP 6.5 remainder) until the phone is on Wi-Fi ("Large files on Wi-Fi only") and/or the clock is inside
 * a daily window ("Download window for large files"). A document of at least 25 MB requested at normal or higher priority (a tap) while
 * either rule is not met is put on hold and starts by itself when both are met. Tapping the same file again while it is on hold downloads
 * it right away over the current connection, so nobody gets stuck.
 * Hooked into `FileLoader`'s private document `loadFile`; it does not touch streaming (video playback), photos, stickers, voice notes,
 * downloads started at low priority (Telegram's own auto-download settings cover those) or uploads.
 * Limits: the hold list and the window timer live in the app process, so if the app is killed a held file has to be tapped again; a download that
 * has already started is not paused when the window closes.
 */
public final class WifiOnlyDownloads {

    public static final long MIN_SIZE_BYTES = 25L * 1024 * 1024;

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
    public static boolean maybeHold(TLRPC.Document document, int priority, String fileName, Runnable start) {
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
                if (!held.isEmpty()) {
                    scheduleWindowCheck();
                }
            }
            return;
        }
        final ArrayList<Runnable> toStart;
        synchronized (held) {
            if (held.isEmpty()) {
                return;
            }
            toStart = new ArrayList<>(held.values());
            held.clear();
        }
        for (Runnable r : toStart) {
            r.run();
        }
    }
}
