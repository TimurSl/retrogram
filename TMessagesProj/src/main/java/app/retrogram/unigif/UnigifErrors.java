package app.retrogram.unigif;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.R;

import app.retrogram.unigif.UnigifModels.UnigifError;

/** User-facing text for a failed UniGif call (the error table of WP U.2). The server's own {@code detail} is not included. */
public final class UnigifErrors {

    private UnigifErrors() {
    }

    public static String message(UnigifError error) {
        switch (error == null ? UnigifError.MSG_NOT_SET_UP : error.message()) {
            case UnigifError.MSG_NOT_SET_UP:
                return getString(R.string.UnigifErrorNotSetUp);
            case UnigifError.MSG_KEY_REJECTED:
                return getString(R.string.UnigifErrorKeyRejected);
            case UnigifError.MSG_SCOPE:
                return error.scope.isEmpty() ? getString(R.string.UnigifErrorForbidden) : formatString(R.string.UnigifErrorScope, error.scope);
            case UnigifError.MSG_RATE_LIMITED:
                return formatString(R.string.UnigifErrorRateLimited, error.retryAfterSeconds);
            case UnigifError.MSG_TOO_LARGE:
                return getString(R.string.UnigifErrorTooLarge);
            case UnigifError.MSG_UNSUPPORTED:
                return getString(R.string.UnigifErrorUnsupported);
            case UnigifError.MSG_NETWORK:
                return getString(R.string.UnigifErrorNetwork);
            case UnigifError.MSG_NOT_UNIGIF:
                return getString(R.string.UnigifErrorNotUnigif);
            default:
                return formatString(R.string.UnigifErrorOther, error.httpStatus);
        }
    }
}
