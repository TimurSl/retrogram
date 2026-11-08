package app.retrogram.graph;

import android.util.SparseArray;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;

import app.retrogram.RetroConfig;

/**
 * Connects {@link AutoReplyEngine} to incoming messages (WP 6.3). Called from
 * `NotificationsController.processNewMessages`, so it also runs for push-delivered messages while the app is in the background.
 * Off unless "Graph auto-reply" is on and a text is set. Safety limits on top of the engine's rules: only private chats with real
 * users (no bots, service accounts, yourself), only messages that arrived in the last two minutes (no replies to a backlog after reconnecting),
 * never edited or service messages, at most 20 replies per hour per account.
 */
public final class AutoReplyHook {

    private static final long MAX_AGE_SECONDS = 120;
    private static final long SIX_HOURS_COOLDOWN = 6;

    private static final SparseArray<AutoReplyEngine> engines = new SparseArray<>();
    private static final SparseArray<String> engineTexts = new SparseArray<>();

    private AutoReplyHook() {}

    public static void onNewMessages(int account, ArrayList<MessageObject> messages) {
        try {
            if (messages == null || messages.isEmpty() || !RetroConfig.graphAutoReply.Bool()) {
                return;
            }
            final String text = RetroConfig.graphAutoReplyText.String().trim();
            if (text.isEmpty()) {
                return;
            }
            final int now = ConnectionsManager.getInstance(account).getCurrentTime();
            for (int i = 0, n = messages.size(); i < n; i++) {
                final MessageObject message = messages.get(i);
                if (message == null || message.messageOwner == null || message.isOutOwner() || message.scheduled) {
                    continue;
                }
                final TLRPC.Message owner = message.messageOwner;
                if (owner.action != null || owner.edit_date != 0 || now - owner.date > MAX_AGE_SECONDS) {
                    continue;
                }
                final long dialogId = message.getDialogId();
                if (!DialogObject.isUserDialog(dialogId)) {
                    continue;
                }
                final TLRPC.User user = MessagesController.getInstance(account).getUser(dialogId);
                if (user == null || user.bot || user.self || user.deleted || UserObject.isReplyUser(user) || MessagesController.isSupportUser(user)) {
                    continue;
                }
                if (decide(account, text, dialogId, owner.message, now * 1000L)) {
                    send(account, dialogId, text);
                }
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private static synchronized boolean decide(int account, String text, long senderId, String incomingText, long nowMs) {
        AutoReplyEngine engine = engines.get(account);
        if (engine == null || !text.equals(engineTexts.get(account))) {
            final AutoReplyEngine.Rule rule = new AutoReplyEngine.Rule("default", text, true, true, false,
                Collections.<String>emptyList(), 0, 0, (int) SIX_HOURS_COOLDOWN);
            engine = new AutoReplyEngine(Collections.singletonList(rule), 20, 3_600_000L, true);
            engines.put(account, engine);
            engineTexts.put(account, text);
        }
        final Calendar c = Calendar.getInstance();
        final int minute = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        final boolean away = ApplicationLoader.mainInterfacePaused;
        return engine.decide(new AutoReplyEngine.Incoming(AutoReplyEngine.ChatKind.PRIVATE, senderId, incomingText, nowMs, minute, false, false), away) != null;
    }

    private static void send(int account, long dialogId, String text) {
        AndroidUtilities.runOnUIThread(() -> SendMessagesHelper.getInstance(account).sendMessage(SendMessagesHelper.SendMessageParams.of(text, dialogId)));
    }
}
