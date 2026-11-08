package app.retrogram.graph;

import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import app.retrogram.RetroConfig;

/**
 * "Large files on Wi-Fi only" (WP 6.5 remainder). When on, an explicit download (tap) of a document of at least 25 MB
 * that is requested while the phone is not on Wi-Fi is put on hold and starts by itself when Wi-Fi comes back. Tapping the same
 * file again while it is on hold downloads it over the current connection anyway, so nobody gets stuck.
 * Hooked into `FileLoader`'s private document `loadFile`; it does not touch streaming (video playback), photos, stickers, voice notes,
 * downloads started at low priority (Telegram's own auto-download settings already cover those) or uploads.
 * The hold list lives in memory: if the app is killed the file simply has to be tapped again. A time-of-day schedule is not implemented.
 */
public final class WifiOnlyDownloads {

    public static final long MIN_SIZE_BYTES = 25L * 1024 * 1024;

    private static final Map<String, Runnable> held = new LinkedHashMap<>();

    private WifiOnlyDownloads() {}

    /**
     * Returns true when the download was put on hold ({@code start} is kept to run later). Call it with the runnable that performs the
     * real download; when it returns false the caller proceeds as usual.
     */
    public static boolean maybeHold(TLRPC.Document document, int priority, String fileName, Runnable start) {
        try {
            if (!RetroConfig.graphWifiOnlyDownloads.Bool() || document == null || fileName == null
                || document.size < MIN_SIZE_BYTES || priority < FileLoader.PRIORITY_NORMAL
                || ApplicationLoader.isConnectedToWiFi()) {
                return false;
            }
            synchronized (held) {
                if (held.containsKey(fileName)) {
                    held.remove(fileName); // second tap: go ahead over the current connection
                    return false;
                }
                held.put(fileName, start);
            }
            AndroidUtilities.runOnUIThread(() -> Toast.makeText(ApplicationLoader.applicationContext,
                LocaleController.getString(R.string.RetroGraphWifiOnlyHeld), Toast.LENGTH_LONG).show());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Called when the network changes (`FileLoader.onNetworkChanged`). Starts everything on hold once Wi-Fi is there. */
    public static void onNetworkChanged() {
        if (!ApplicationLoader.isConnectedToWiFi()) {
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
