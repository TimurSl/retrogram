package app.retrogram.unigif;

import static org.telegram.messenger.LocaleController.getString;

import android.text.TextUtils;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;

import java.io.File;
import java.util.HashSet;

/**
 * "Add to UniGif" (docs/plans/08-unigif.md, WP U.7): uploads the file of a Telegram GIF document to the UniGif
 * server, with no tags. A document that is not on the device yet is downloaded from Telegram first.
 * Everything here runs on the UI thread except the upload itself (OkHttp's thread).
 */
public final class UnigifUploader {

    /** How long to wait for Telegram to deliver a file that was not on the device. */
    private static final long DOWNLOAD_TIMEOUT_MS = 60_000;

    /** Ids of the documents being added. A second tap on the same GIF while it is here does nothing. */
    private static final HashSet<Long> inFlight = new HashSet<>();

    /** Where the bulletins go. Asked again for each bulletin, because the screen may be gone when the upload ends. */
    private interface Bulletins {
        BulletinFactory get();
    }

    private UnigifUploader() {
    }

    /**
     * @param parentObject what the document came from (a {@link MessageObject}, a web page, …); used to renew the
     *                     file reference if the file has to be downloaded, and to find a file the user sent themselves
     */
    public static void add(int account, TLRPC.Document document, Object parentObject, BaseFragment fragment) {
        add(account, document, parentObject, () -> BulletinFactory.canShowBulletin(fragment) ? BulletinFactory.of(fragment) : BulletinFactory.global());
    }

    /** For screens that are not fragments (the media viewer). Falls back to the top fragment once the container is gone. */
    public static void add(int account, TLRPC.Document document, Object parentObject, FrameLayout container, Theme.ResourcesProvider resourcesProvider) {
        add(account, document, parentObject, () -> container != null && container.isAttachedToWindow() && container.isShown() ? BulletinFactory.of(container, resourcesProvider) : BulletinFactory.global());
    }

    private static void add(int account, TLRPC.Document document, Object parentObject, Bulletins bulletins) {
        if (document == null || !inFlight.add(document.id)) {
            return;
        }
        File file = localFile(account, document, parentObject);
        if (file != null) {
            upload(document, file, bulletins);
            return;
        }

        show(bulletins, false, getString(R.string.UnigifAdding));
        final String fileName = FileLoader.getAttachFileName(document);
        final NotificationCenter center = NotificationCenter.getInstance(account);
        final Runnable[] timeout = new Runnable[1];
        final NotificationCenter.NotificationCenterDelegate observer = new NotificationCenter.NotificationCenterDelegate() {
            @Override
            public void didReceivedNotification(int id, int accountNum, Object... args) {
                if (args == null || args.length == 0 || !fileName.equals(args[0])) {
                    return;
                }
                center.removeObserver(this, NotificationCenter.fileLoaded);
                center.removeObserver(this, NotificationCenter.fileLoadFailed);
                AndroidUtilities.cancelRunOnUIThread(timeout[0]);
                File loaded = null;
                if (id == NotificationCenter.fileLoaded) {
                    loaded = args.length > 1 && args[1] instanceof File && ((File) args[1]).length() > 0 ? (File) args[1] : localFile(account, document, parentObject);
                }
                if (loaded != null) {
                    upload(document, loaded, bulletins);
                } else {
                    inFlight.remove(document.id);
                    show(bulletins, true, getString(R.string.UnigifAddDownloadFailed));
                }
            }
        };
        timeout[0] = () -> {
            // The download itself is left running: the chat may be showing its progress.
            center.removeObserver(observer, NotificationCenter.fileLoaded);
            center.removeObserver(observer, NotificationCenter.fileLoadFailed);
            inFlight.remove(document.id);
            show(bulletins, true, getString(R.string.UnigifAddDownloadFailed));
        };
        center.addObserver(observer, NotificationCenter.fileLoaded);
        center.addObserver(observer, NotificationCenter.fileLoadFailed);
        AndroidUtilities.runOnUIThread(timeout[0], DOWNLOAD_TIMEOUT_MS);
        FileLoader.getInstance(account).loadFile(document, parentObject, FileLoader.PRIORITY_NORMAL, 0);
    }

    private static void upload(TLRPC.Document document, File file, Bulletins bulletins) {
        // The document's own type is sent: "video/mp4" for a Telegram GIF, "image/gif" for a real GIF file
        // (the server converts it). The client falls back to "video/mp4" when the document has none.
        UnigifClient.add(file, document.mime_type, String.valueOf(document.id), (gif, error) -> {
            inFlight.remove(document.id);
            if (error != null) {
                show(bulletins, true, UnigifErrors.message(error));
            } else {
                show(bulletins, false, getString(gif != null && gif.duplicate ? R.string.UnigifAddDuplicate : R.string.UnigifAdded));
            }
        });
    }

    /** The complete file of the document on this device, or null. */
    private static File localFile(int account, TLRPC.Document document, Object parentObject) {
        if (parentObject instanceof MessageObject) {
            MessageObject message = (MessageObject) parentObject;
            // A GIF the user sent from this device: the original file, if it is the document's file.
            if (message.messageOwner != null && !TextUtils.isEmpty(message.messageOwner.attachPath) && message.getDocument() == document) {
                File file = new File(message.messageOwner.attachPath);
                if (isComplete(file, document.size)) {
                    return file;
                }
            }
        }
        FileLoader loader = FileLoader.getInstance(account);
        File file = loader.getPathToAttach(document);
        if (isComplete(file, document.size)) {
            return file;
        }
        file = loader.getPathToAttach(document, true);
        return isComplete(file, document.size) ? file : null;
    }

    static boolean isComplete(File file, long size) {
        if (file == null || !file.isFile()) {
            return false;
        }
        return size > 0 ? file.length() == size : file.length() > 0;
    }

    private static void show(Bulletins bulletins, boolean error, CharSequence text) {
        try {
            BulletinFactory factory = bulletins.get();
            (error ? factory.createErrorBulletin(text) : factory.createSimpleBulletin(R.raw.ic_save_to_gifs, text)).show();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
