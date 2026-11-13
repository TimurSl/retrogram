package app.retrogram.unigif;

import android.text.TextUtils;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessageSuggestionParams;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.SendMessageChatArguments;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Sends a GIF picked from the UniGif library (docs/plans/08-unigif.md, WP U.6).
 *
 * A UniGif result has no {@code query_id}, so it cannot go out as an inline-bot result. Instead:
 * a GIF that was sent before is sent again as the Telegram document it became (no upload); otherwise the MP4 is
 * fetched from the UniGif server into the cache file {@link SendMessagesHelper#prepareSendingBotContextResult}
 * looks for, and that method uploads it as a normal GIF.
 */
public final class UnigifSender {

    /** Content URL → sends waiting for that file. UI thread only. More than one entry = taps during the download. */
    private static final HashMap<String, ArrayList<Utilities.Callback<UnigifModels.UnigifError>>> downloads = new HashMap<>();

    private UnigifSender() {
    }

    /** Call on the UI thread, where the generic inline-result branch would have run. */
    public static void send(BaseFragment fragment, AccountInstance accountInstance, TLRPC.BotInlineResult result, String query,
                            long dialogId, MessageObject replyToMsg, MessageObject replyToTopMsg, TL_stories.StoryItem storyItem,
                            ChatActivity.ReplyQuote quote, boolean notify, int scheduleDate, int scheduleRepeatPeriod,
                            SendMessageChatArguments sendMessageChatArguments, long stars, long monoForumPeerId,
                            MessageSuggestionParams suggestionParams) {
        final String gifId = UnigifResults.gifId(result);
        if (gifId == null || accountInstance == null || result.content == null || TextUtils.isEmpty(result.content.url)) {
            return;
        }
        final String url = result.content.url;

        final Runnable upload = () -> fetch(url, cacheFile(result.content), error -> {
            if (error != null) {
                BulletinFactory.of(fragment).createErrorBulletin(UnigifErrors.message(error)).show();
                return;
            }
            // Non-null on purpose: prepareSendingBotContextResult stores the content URL in it as "originalPath",
            // the key under which the uploaded document is remembered. No "id"/"query_id": those would turn the
            // send into messages.sendInlineBotResult.
            HashMap<String, String> params = new HashMap<>();
            params.put("force_gif", "1");
            SendMessagesHelper.prepareSendingBotContextResult(fragment, accountInstance, result, params, dialogId, replyToMsg, replyToTopMsg, storyItem, quote, notify, scheduleDate, scheduleRepeatPeriod, sendMessageChatArguments, stars, monoForumPeerId);
            UnigifClient.use(gifId);
        });

        if (DialogObject.isEncryptedDialog(dialogId)) {
            // Sent documents are not remembered for secret chats; the file is encrypted and uploaded each time.
            upload.run();
            return;
        }
        // getSentFile waits for the storage queue, so it is kept off the UI thread.
        Utilities.globalQueue.postRunnable(() -> {
            Object[] sent = null;
            try {
                sent = accountInstance.getMessagesStorage().getSentFile(url, MessagesStorage.SENT_FILE_TYPE_VIDEO);
            } catch (Exception e) {
                FileLog.e(e);
            }
            final TLRPC.TL_document document = sent != null && sent[0] instanceof TLRPC.TL_document ? (TLRPC.TL_document) sent[0] : null;
            // "sent_<channel>_<message>_…": lets FileRefController renew an expired file reference from that message.
            final Object parent = sent != null ? sent[1] : null;
            AndroidUtilities.runOnUIThread(() -> {
                if (document == null || !MessageObject.isGifDocument(document)) {
                    upload.run();
                    return;
                }
                accountInstance.getSendMessagesHelper().sendSticker(document, query, dialogId, replyToMsg, replyToTopMsg, storyItem, quote, null, notify, scheduleDate, scheduleRepeatPeriod, false, parent, sendMessageChatArguments, stars, monoForumPeerId, suggestionParams);
                UnigifClient.use(gifId);
            });
        });
    }

    /**
     * The file prepareSendingBotContextResult checks before it falls back to the URL. Same expression as there:
     * the URL's own short extension when it has one, else the one for the MIME type (".mp4" for UniGif media).
     */
    static File cacheFile(TLRPC.WebDocument content) {
        String ext = ImageLoader.getHttpUrlExtension(content.url, null);
        if (TextUtils.isEmpty(ext)) {
            ext = FileLoader.getExtensionByMimeType(content.mime_type);
        } else {
            ext = "." + ext;
        }
        return new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(content.url) + ext);
    }

    /** Runs {@code done} with null once {@code file} is in place, or with the error. One download per URL at a time. */
    private static void fetch(String url, File file, Utilities.Callback<UnigifModels.UnigifError> done) {
        if (file.length() > 0) {
            done.run(null);
            return;
        }
        ArrayList<Utilities.Callback<UnigifModels.UnigifError>> waiting = downloads.get(url);
        if (waiting != null) {
            waiting.add(done);
            return;
        }
        waiting = new ArrayList<>();
        waiting.add(done);
        downloads.put(url, waiting);
        UnigifClient.download(url, file, (downloaded, error) -> {
            if (error == null && file.length() == 0) {
                file.delete();
                error = new UnigifModels.UnigifError(UnigifModels.UnigifError.KIND_BAD_RESPONSE, 200, null, null);
            }
            ArrayList<Utilities.Callback<UnigifModels.UnigifError>> finished = downloads.remove(url);
            if (finished == null) {
                return;
            }
            for (int a = 0; a < finished.size(); a++) {
                // Only the first waiting send reports a failure; the rest would repeat the same bulletin.
                if (error == null || a == 0) {
                    finished.get(a).run(error);
                }
            }
        });
    }
}
