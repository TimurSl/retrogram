package app.retrogram.xmpp;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

/**
 * Foreground service that keeps the process (and so the XMPP connections) alive while the app is in the
 * background. Without push there is no other way to receive messages instantly. Switched on and off by
 * {@link XmppManager#updateService()}.
 */
public class XmppService extends Service {
    private static volatile boolean running;

    @Override
    public void onDestroy() {
        running = false;
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ApplicationLoader.postInitApplication();
        XmppManager.get().start();
        Notification n = XmppManager.get().serviceNotification(this);
        try {
            startForeground(XmppNotifications.SERVICE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } catch (Throwable e) {
            FileLog.e(e);
            stopSelf();
            return START_NOT_STICKY;
        }
        running = true;
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Refreshes the ongoing notification text. */
    static void refresh(Context context) {
        if (!running) {
            return; // posting the notification without the service would leave a stray ongoing entry
        }
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(XmppNotifications.SERVICE_ID, XmppManager.get().serviceNotification(context));
        }
    }
}
