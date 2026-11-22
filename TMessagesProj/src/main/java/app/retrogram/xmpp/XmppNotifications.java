package app.retrogram.xmpp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.retrogram.xmpp.core.XmppAccountConfig;
import app.retrogram.xmpp.core.XmppConversation;
import app.retrogram.xmpp.core.XmppMessage;

/** System notifications for XMPP messages and the ongoing "connected" notification of {@link XmppService}. */
public final class XmppNotifications {
    public static final String EXTRA_ACCOUNT = "retro_xmpp_account";
    public static final String EXTRA_JID = "retro_xmpp_jid";

    static final String CHANNEL_MESSAGES = "retrogram_xmpp_messages";
    static final String CHANNEL_SERVICE = "retrogram_xmpp_service";
    static final int SERVICE_ID = 71001;
    private static final int MAX_LINES = 6;

    private static final Map<String, List<String>> lines = new HashMap<>();

    private XmppNotifications() {
    }

    private static NotificationManager manager(Context context) {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    static void ensureChannels(Context context) {
        NotificationManager nm = manager(context);
        if (nm == null) {
            return;
        }
        NotificationChannel messages = new NotificationChannel(CHANNEL_MESSAGES,
                LocaleController.getString(R.string.XmppChannelMessages), NotificationManager.IMPORTANCE_HIGH);
        messages.enableLights(true);
        nm.createNotificationChannel(messages);
        NotificationChannel service = new NotificationChannel(CHANNEL_SERVICE,
                LocaleController.getString(R.string.XmppChannelService), NotificationManager.IMPORTANCE_MIN);
        service.setShowBadge(false);
        nm.createNotificationChannel(service);
    }

    static int idFor(long accountId, String jid) {
        return 72000 + ((int) (accountId * 31) + jid.toLowerCase().hashCode()) % 20000 + 20000;
    }

    private static PendingIntent openChat(Context context, long accountId, String jid, int requestCode) {
        Intent i = new Intent(context, LaunchActivity.class);
        i.setAction(Intent.ACTION_MAIN);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra(EXTRA_ACCOUNT, accountId);
        i.putExtra(EXTRA_JID, jid);
        return PendingIntent.getActivity(context, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void showMessage(Context context, XmppMessage m, XmppConversation c, XmppAccountConfig account) {
        NotificationManager nm = manager(context);
        if (nm == null || !nm.areNotificationsEnabled()) {
            return;
        }
        ensureChannels(context);
        String key = account.id + "/" + c.jid;
        String text = m.hasAttachment() && m.body.equals(m.attachmentUrl)
                ? "📎 " + XmppFiles.fileNameOf(m.attachmentUrl) : m.body;
        String line = m.groupChat ? m.sender + ": " + text : text;
        List<String> all;
        synchronized (lines) {
            all = lines.get(key);
            if (all == null) {
                all = new ArrayList<>();
                lines.put(key, all);
            }
            all.add(line);
            while (all.size() > MAX_LINES) {
                all.remove(0);
            }
            all = new ArrayList<>(all);
        }
        int id = idFor(account.id, c.jid);
        Notification.InboxStyle style = new Notification.InboxStyle();
        for (String l : all) {
            style.addLine(l);
        }
        style.setSummaryText(account.displayName());
        Notification n = new Notification.Builder(context, CHANNEL_MESSAGES)
                .setSmallIcon(R.drawable.neko_notification)
                .setContentTitle(c.displayName())
                .setContentText(line)
                .setStyle(style)
                .setWhen(m.time)
                .setShowWhen(true)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setGroup("retrogram_xmpp")
                .setContentIntent(openChat(context, account.id, c.jid, id))
                .build();
        nm.notify(id, n);
    }

    public static void cancel(Context context, long accountId, String jid) {
        synchronized (lines) {
            lines.remove(accountId + "/" + jid);
        }
        NotificationManager nm = manager(context);
        if (nm != null) {
            nm.cancel(idFor(accountId, jid));
        }
    }

    public static void cancelAll(Context context, long accountId) {
        synchronized (lines) {
            lines.keySet().removeIf(k -> k.startsWith(accountId + "/"));
        }
    }

    static Notification serviceNotification(Context context, int online, int total, boolean connecting) {
        ensureChannels(context);
        String text;
        if (total == 0) {
            text = LocaleController.getString(R.string.XmppServiceIdle);
        } else if (online == total) {
            text = LocaleController.formatString(R.string.XmppServiceConnected, online);
        } else if (connecting) {
            text = LocaleController.formatString(R.string.XmppServiceConnecting, online, total);
        } else {
            text = LocaleController.formatString(R.string.XmppServiceOffline, online, total);
        }
        Intent open = new Intent(context, LaunchActivity.class);
        open.setAction(Intent.ACTION_MAIN);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        open.putExtra(EXTRA_ACCOUNT, -1L);
        return new Notification.Builder(context, CHANNEL_SERVICE)
                .setSmallIcon(R.drawable.neko_notification)
                .setContentTitle(LocaleController.getString(R.string.XmppService))
                .setContentText(text)
                .setOngoing(true)
                .setShowWhen(false)
                .setContentIntent(PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                .build();
    }
}
