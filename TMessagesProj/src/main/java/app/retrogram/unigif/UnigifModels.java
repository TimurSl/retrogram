package app.retrogram.unigif;

import java.util.ArrayList;
import java.util.List;

/** Plain data read from the UniGif API (docs/plans/08-unigif.md, "UniGif API used"). */
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

    /** One rendition of a GIF. {@link #url} is absolute. */
    public static final class Media {
        public String url = "";
        public int width;
        public int height;
        public long bytes;
    }

    /** A library item. {@link #mp4} is null while the server is still processing it. */
    public static final class Gif {
        public String id = "";
        public String status = "";
        public final List<String> tags = new ArrayList<>();
        public int width;
        public int height;
        public long durationMs;
        public Media mp4;
        /** Absolute URLs, or "" when the server sent none. */
        public String previewUrl = "";
        public String posterUrl = "";
        /** Set on the answer to an upload: the file was already in the library. */
        public boolean duplicate;
    }

    /** One page of {@code GET /gifs}. {@link #nextCursor} is "" on the last page. */
    public static final class GifPage {
        public final List<Gif> gifs = new ArrayList<>();
        public String nextCursor = "";
    }

    public static final class Tag {
        public String name = "";
        public int gifCount;
    }

    /** A failed call. Exactly one of the kinds below; {@link #code} and {@link #detail} come from problem+json when present. */
    public static final class UnigifError {
        public static final int KIND_NOT_SET_UP = 0;
        public static final int KIND_NETWORK = 1;
        public static final int KIND_HTTP = 2;
        public static final int KIND_BAD_RESPONSE = 3;

        /** What to tell the user; see {@link #message()}. */
        public static final int MSG_NOT_SET_UP = 0;
        public static final int MSG_KEY_REJECTED = 1;
        public static final int MSG_SCOPE = 2;
        public static final int MSG_RATE_LIMITED = 3;
        public static final int MSG_TOO_LARGE = 4;
        public static final int MSG_UNSUPPORTED = 5;
        public static final int MSG_NETWORK = 6;
        public static final int MSG_NOT_UNIGIF = 7;
        public static final int MSG_OTHER = 8;

        public final int kind;
        public final int httpStatus;
        public final String code;
        public final String detail;
        /** The permission the failed call needs ("read", "upload", "delete"), or "". */
        public final String scope;
        /** Seconds to wait after a 429, else 0. */
        public final int retryAfterSeconds;

        public UnigifError(int kind, int httpStatus, String code, String detail) {
            this(kind, httpStatus, code, detail, null, 0);
        }

        public UnigifError(int kind, int httpStatus, String code, String detail, String scope, int retryAfterSeconds) {
            this.kind = kind;
            this.httpStatus = httpStatus;
            this.code = code == null ? "" : code;
            this.detail = detail == null ? "" : detail;
            this.scope = scope == null ? "" : scope;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public boolean isKeyRejected() {
            return kind == KIND_HTTP && (httpStatus == 401 || code.equals("unauthorized") || code.equals("invalid_credentials"));
        }

        public boolean isRateLimited() {
            return kind == KIND_HTTP && (httpStatus == 429 || code.equals("rate_limited"));
        }

        /** The error table of WP U.2. The problem+json code wins; the HTTP status is the fallback. */
        public int message() {
            switch (kind) {
                case KIND_NOT_SET_UP:
                    return MSG_NOT_SET_UP;
                case KIND_NETWORK:
                    return MSG_NETWORK;
                case KIND_BAD_RESPONSE:
                    return MSG_NOT_UNIGIF;
            }
            if (isKeyRejected()) {
                return MSG_KEY_REJECTED;
            }
            if (code.equals("scope_required") || code.equals("forbidden") || (code.isEmpty() && httpStatus == 403)) {
                return MSG_SCOPE;
            }
            if (isRateLimited()) {
                return MSG_RATE_LIMITED;
            }
            if (code.equals("too_large") || httpStatus == 413) {
                return MSG_TOO_LARGE;
            }
            if (code.equals("unsupported_media") || code.equals("invalid_media") || httpStatus == 415) {
                return MSG_UNSUPPORTED;
            }
            return MSG_OTHER;
        }
    }
}
