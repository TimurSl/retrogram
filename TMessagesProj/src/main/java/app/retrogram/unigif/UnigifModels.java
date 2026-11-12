package app.retrogram.unigif;

import java.util.ArrayList;
import java.util.List;

/** Plain data read from the UniGif API. WP U.2 adds Gif, GifPage and Tag. */
public final class UnigifModels {

    private UnigifModels() {
    }

    /** {@code GET /me}: the key in use. */
    public static final class Me {
        public String name = "";
        public final List<String> scopes = new ArrayList<>();

        public boolean hasScope(String scope) {
            return scopes.contains(scope);
        }
    }

    /** A failed call. Exactly one of the kinds below; {@link #code} and {@link #detail} come from problem+json when present. */
    public static final class UnigifError {
        public static final int KIND_NOT_SET_UP = 0;
        public static final int KIND_NETWORK = 1;
        public static final int KIND_HTTP = 2;
        public static final int KIND_BAD_RESPONSE = 3;

        public final int kind;
        public final int httpStatus;
        public final String code;
        public final String detail;

        public UnigifError(int kind, int httpStatus, String code, String detail) {
            this.kind = kind;
            this.httpStatus = httpStatus;
            this.code = code == null ? "" : code;
            this.detail = detail == null ? "" : detail;
        }

        public boolean isKeyRejected() {
            return kind == KIND_HTTP && (httpStatus == 401 || code.equals("unauthorized") || code.equals("invalid_credentials"));
        }
    }
}
