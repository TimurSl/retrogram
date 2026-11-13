package app.retrogram.unigif;

import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.BulletinFactory;

import java.util.HashSet;

import app.retrogram.unigif.UnigifModels.UnigifError;

/**
 * "Delete" on a library item in the GIF preview (docs/plans/08-unigif.md, WP U.8): moves the item to the
 * server's trash. Offered only when the key is known to have the {@code delete} permission.
 * Everything here runs on the UI thread.
 */
public final class UnigifDeleter {

    /** Called after the server has deleted the item, so the caller can take it off the screen. */
    public interface OnDeleted {
        void onDeleted(TLRPC.BotInlineResult result);
    }

    /** Ids of the items being deleted. A second tap on the same one while it is here does nothing. */
    private static final HashSet<String> inFlight = new HashSet<>();

    private UnigifDeleter() {
    }

    /** Whether to show the entry. Asks the server for the key's permissions when they are not known yet. */
    public static boolean canDelete(TLRPC.BotInlineResult result) {
        if (!UnigifResults.isUnigif(result) || !UnigifConfig.isActive()) {
            return false;
        }
        if (!UnigifConfig.scopesKnown()) {
            UnigifClient.ensureScopes();
            return false;
        }
        return UnigifConfig.hasScope("delete");
    }

    public static void delete(TLRPC.BotInlineResult result, OnDeleted onDeleted) {
        final String id = UnigifResults.gifId(result);
        if (id == null || !inFlight.add(id)) {
            return;
        }
        UnigifClient.delete(id, (nothing, error) -> {
            inFlight.remove(id);
            // 404: already gone on the server (deleted from another device), so the tile goes too.
            if (error == null || error.kind == UnigifError.KIND_HTTP && error.httpStatus == 404) {
                if (onDeleted != null) {
                    onDeleted.onDeleted(result);
                }
                show(false, getString(R.string.UnigifDeleted));
                return;
            }
            if (error.message() == UnigifError.MSG_SCOPE || error.message() == UnigifError.MSG_KEY_REJECTED) {
                UnigifConfig.forgetScopes(); // the key changed on the server; read its permissions again
            }
            show(true, UnigifErrors.message(error));
        });
    }

    /** The preview has no screen of its own and closes with its menu, so the bulletin goes to the top fragment. */
    private static void show(boolean error, CharSequence text) {
        try {
            BulletinFactory factory = BulletinFactory.global();
            (error ? factory.createErrorBulletin(text) : factory.createSimpleBulletin(R.raw.ic_delete, text)).show();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
