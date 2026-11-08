package app.retrogram.graph;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Fired by the alarm that {@link WifiOnlyDownloads} sets for the start of the download window. The process is already up when this runs;
 * it loads the persisted download queues (a cold start has no screen that would) and starts the held files shortly after.
 */
public final class DownloadWindowReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            WifiOnlyDownloads.releaseSoon();
        } catch (Throwable ignore) {
        }
    }
}
