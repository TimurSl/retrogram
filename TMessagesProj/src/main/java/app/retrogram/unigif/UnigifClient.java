package app.retrogram.unigif;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.telegram.messenger.AndroidUtilities;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import app.retrogram.unigif.UnigifModels.Gif;
import app.retrogram.unigif.UnigifModels.GifPage;
import app.retrogram.unigif.UnigifModels.Me;
import app.retrogram.unigif.UnigifModels.Media;
import app.retrogram.unigif.UnigifModels.Tag;
import app.retrogram.unigif.UnigifModels.UnigifError;
import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import okio.Okio;

/**
 * HTTP client for a UniGif server (docs/plans/08-unigif.md, WP U.2). No Android UI imports: results are delivered
 * through an {@link Executor}, which is the UI thread for the public methods. Every method returns the running
 * call so the caller can cancel it, or null when the error was delivered without a request. A cancelled call
 * delivers nothing.
 */
public final class UnigifClient {

    public interface Callback<T> {
        void onResult(T result, UnigifError error);
    }

    public static final int PAGE_SIZE = 50;
    private static final int TAG_LIMIT = 20;
    private static final int TAG_SUGGEST_LIMIT = 12;
    /** Used when a 429 has no usable Retry-After. */
    private static final int DEFAULT_RETRY_AFTER = 30;
    private static final int MAX_RETRY_AFTER = 3600;

    private static final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            // The key must not follow a redirect to another host; a redirect on an API call is reported as an error.
            .followRedirects(false)
            .followSslRedirects(false)
            // The only place the key is attached. Application interceptors do not see redirect hops.
            .addInterceptor(chain -> {
                Request request = chain.request();
                Server server = request.tag(Server.class);
                if (server != null && shouldAuthorize(request.url(), server.base)) {
                    request = request.newBuilder().header("Authorization", server.authHeader).build();
                }
                return chain.proceed(request);
            })
            .build();

    /** Media may sit behind a redirect (a CDN); those requests never carry the key. */
    private static final OkHttpClient mediaHttp = http.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    /** Base URL → time (on {@link #clock}) before which list and search calls are refused locally. */
    private static final ConcurrentHashMap<String, Long> blockedUntil = new ConcurrentHashMap<>();
    /** Milliseconds, monotonic. Replaced in tests. */
    static LongSupplier clock = () -> System.nanoTime() / 1_000_000L;

    private UnigifClient() {
    }

    /** Forgets every 429 wait. For tests. */
    static void clearBackoff() {
        blockedUntil.clear();
        scopesAskedFor = null;
    }

    /** How long a failed scope read is left alone before {@link #ensureScopes()} asks again. */
    private static final long SCOPES_RETRY_MS = 60_000;
    private static String scopesAskedFor;
    private static long scopesAskedAt;

    /** One server and key, and where to deliver results. */
    static final class Server {
        final String baseUrl;
        /** Null when the URL or the key cannot be used. */
        final HttpUrl base;
        final String key;
        final String authHeader;
        final Executor executor;

        Server(String baseUrl, String key, Executor executor) {
            this.baseUrl = baseUrl == null ? "" : baseUrl;
            this.key = key == null ? "" : key;
            this.executor = executor;
            HttpUrl parsed = this.baseUrl.isEmpty() ? null : HttpUrl.parse(this.baseUrl);
            String header = null;
            if (key != null && !key.isEmpty()) {
                try {
                    // Rejects keys with characters that cannot go in a header.
                    header = new Headers.Builder().add("Authorization", "Bearer " + key).build().get("Authorization");
                } catch (IllegalArgumentException ignore) {
                }
            }
            this.base = header == null ? null : parsed;
            this.authHeader = header;
        }

        static Server current() {
            return new Server(UnigifConfig.baseUrl(), UnigifConfig.key(), AndroidUtilities::runOnUIThread);
        }

        HttpUrl.Builder url(String... segments) {
            HttpUrl.Builder builder = base.newBuilder();
            for (String segment : segments) {
                builder.addPathSegment(segment);
            }
            return builder;
        }
    }

    /**
     * The key goes only to the configured server (same scheme, host and port), only under the API root, and never
     * to media paths ({@code /m/…}), which are public.
     */
    static boolean shouldAuthorize(HttpUrl url, HttpUrl base) {
        if (base == null || !url.scheme().equals(base.scheme()) || !url.host().equals(base.host()) || url.port() != base.port()) {
            return false;
        }
        String root = base.encodedPath();
        while (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        String path = url.encodedPath();
        if (path.startsWith("/m/") || !path.startsWith(root + "/")) {
            return false;
        }
        return !path.startsWith(root + "/m/");
    }

    // Public API: configured server, callbacks on the UI thread.

    /** {@code GET /me}. A good answer also updates the scopes in {@link UnigifConfig}. */
    public static Call me(Callback<Me> callback) {
        return me(Server.current(), callback);
    }

    /**
     * Reads the key's scopes once (WP U.8) so that {@link UnigifConfig#hasScope} can answer. Does nothing when
     * they are known, when the server is not set up, or when the same server and key were asked less than a
     * minute ago.
     */
    public static void ensureScopes() {
        ensureScopes(Server.current());
    }

    static void ensureScopes(Server server) {
        if (server.base == null || UnigifConfig.scopesKnown()) {
            return;
        }
        String asked = server.baseUrl + "\n" + server.key;
        long now = clock.getAsLong();
        synchronized (UnigifClient.class) {
            if (asked.equals(scopesAskedFor) && now - scopesAskedAt < SCOPES_RETRY_MS) {
                return;
            }
            scopesAskedFor = asked;
            scopesAskedAt = now;
        }
        me(server, null);
    }

    /**
     * {@code GET /gifs}, ready items only, {@link #PAGE_SIZE} per page. Any of {@code q} (passed as typed),
     * {@code sort} ("used", "new") and {@code cursor} may be null or empty.
     */
    public static Call listGifs(String q, String sort, String cursor, Callback<GifPage> callback) {
        return listGifs(Server.current(), q, sort, cursor, callback);
    }

    /** {@code GET /tags}: tags starting with {@code prefix}, most popular first. */
    public static Call listTags(String prefix, Callback<List<Tag>> callback) {
        return listTags(Server.current(), prefix, callback);
    }

    /** {@code GET /tags/suggest}: the recently used tags. */
    public static Call suggestTags(Callback<List<Tag>> callback) {
        return suggestTags(Server.current(), callback);
    }

    /** {@code POST /gifs/{id}/use}. Fire-and-forget: errors are ignored. */
    public static void use(String id) {
        use(Server.current(), id);
    }

    /**
     * {@code POST /gifs}: uploads the file as it is, with no tags. {@code mime} is "video/mp4" or "image/gif";
     * {@code sourceId} is the Telegram document id. The result has {@link Gif#duplicate} set when the file was
     * already in the library; its media may still be missing because the call does not wait for processing.
     */
    public static Call add(File file, String mime, String sourceId, Callback<Gif> callback) {
        return add(Server.current(), file, mime, sourceId, callback);
    }

    /** {@code DELETE /gifs/{id}} (moves it to the server's trash). Success is a null result with a null error. */
    public static Call delete(String id, Callback<Void> callback) {
        return delete(Server.current(), id, callback);
    }

    /**
     * Fetches a media URL into {@code target} (written next to it first, then renamed). The key is not sent.
     * Redirects are followed.
     */
    public static Call download(String url, File target, Callback<File> callback) {
        return download(Server.current(), url, target, callback);
    }

    // The same calls against an explicit server (used by tests).

    static Call me(Server server, Callback<Me> callback) {
        return send(server, null, "", false, (status, body) -> {
                    Me me = parseMe(body);
                    if (me != null) {
                        UnigifConfig.rememberScopes(server.baseUrl, server.key, me.scopes);
                    }
                    return me;
                }, callback, false,
                server.base == null ? null : new Request.Builder().url(server.url("me").build()));
    }

    static Call listGifs(Server server, String q, String sort, String cursor, Callback<GifPage> callback) {
        Request.Builder request = null;
        if (server.base != null) {
            HttpUrl.Builder url = server.url("gifs");
            if (q != null && !q.isEmpty()) {
                url.addQueryParameter("q", q);
            }
            if (sort != null && !sort.isEmpty()) {
                url.addQueryParameter("sort", sort);
            }
            url.addQueryParameter("status", "ready").addQueryParameter("limit", String.valueOf(PAGE_SIZE));
            if (cursor != null && !cursor.isEmpty()) {
                url.addQueryParameter("cursor", cursor);
            }
            request = new Request.Builder().url(url.build());
        }
        return send(server, null, "read", true, (status, body) -> parseGifPage(body, server.base), callback, false, request);
    }

    static Call listTags(Server server, String prefix, Callback<List<Tag>> callback) {
        Request.Builder request = server.base == null ? null : new Request.Builder().url(server.url("tags")
                .addQueryParameter("q", prefix == null ? "" : prefix)
                .addQueryParameter("sort", "popular")
                .addQueryParameter("limit", String.valueOf(TAG_LIMIT))
                .build());
        return send(server, null, "read", true, (status, body) -> parseTags(body), callback, false, request);
    }

    static Call suggestTags(Server server, Callback<List<Tag>> callback) {
        Request.Builder request = server.base == null ? null : new Request.Builder().url(server.url("tags", "suggest")
                .addQueryParameter("limit", String.valueOf(TAG_SUGGEST_LIMIT))
                .build());
        return send(server, null, "read", true, (status, body) -> parseTags(body), callback, false, request);
    }

    static Call use(Server server, String id) {
        Request.Builder request = server.base == null ? null : new Request.Builder()
                .url(server.url("gifs", id, "use").build())
                .post(RequestBody.create(new byte[0], null));
        return send(server, null, "read", false, null, null, true, request);
    }

    static Call add(Server server, File file, String mime, String sourceId, Callback<Gif> callback) {
        Request.Builder request = null;
        if (server.base != null) {
            MediaType type = MediaType.parse(mime == null || mime.isEmpty() ? "video/mp4" : mime);
            HttpUrl.Builder url = server.url("gifs").addQueryParameter("source", "telegram");
            if (sourceId != null && !sourceId.isEmpty()) {
                url.addQueryParameter("source_id", sourceId);
            }
            url.addQueryParameter("wait", "0");
            // Streamed from the file, not read into memory.
            request = new Request.Builder().url(url.build()).post(RequestBody.create(file, type));
        }
        return send(server, null, "upload", false, (status, body) -> {
            Gif gif = parseAddedGif(body, server.base);
            if (gif != null && status != 200) {
                gif.duplicate = false;
            }
            return gif;
        }, callback, false, request);
    }

    static Call delete(Server server, String id, Callback<Void> callback) {
        Request.Builder request = server.base == null ? null : new Request.Builder().url(server.url("gifs", id).build()).delete();
        return send(server, null, "delete", false, null, callback, true, request);
    }

    static Call download(Server server, String url, File target, Callback<File> callback) {
        HttpUrl parsed = url == null ? null : HttpUrl.parse(url);
        Request.Builder request = server.base == null || parsed == null ? null : new Request.Builder().url(parsed);
        return send(server, target, "", false, null, callback, true, request);
    }

    private interface Parser<T> {
        /** Null means the body is not what a UniGif server sends. */
        T parse(int status, String body);
    }

    /**
     * Runs one request. {@code request} null = the server is not usable. {@code target} non-null = save the body
     * there and deliver the file. {@code noResult} = a 2xx is a success whatever the body is.
     */
    @SuppressWarnings("unchecked")
    private static <T> Call send(Server server, File target, String scope, boolean throttled, Parser<T> parser,
                                 Callback<T> callback, boolean noResult, Request.Builder request) {
        if (request == null) {
            deliver(server, callback, null, new UnigifError(UnigifError.KIND_NOT_SET_UP, 0, null, null));
            return null;
        }
        if (throttled) {
            Long until = blockedUntil.get(server.baseUrl);
            if (until != null) {
                long left = until - clock.getAsLong();
                if (left > 0) {
                    deliver(server, callback, null, new UnigifError(UnigifError.KIND_HTTP, 429, "rate_limited", null, scope, (int) ((left + 999) / 1000)));
                    return null;
                }
                blockedUntil.remove(server.baseUrl, until);
            }
        }
        Call call = (target != null ? mediaHttp : http).newCall(request
                .header("Accept", target != null ? "*/*" : "application/json")
                .tag(Server.class, server)
                .build());
        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (!call.isCanceled()) {
                    deliver(server, callback, null, new UnigifError(UnigifError.KIND_NETWORK, 0, null, e.getMessage()));
                }
            }

            @Override
            public void onResponse(Call call, Response response) {
                T result = null;
                UnigifError error = null;
                try (Response r = response) {
                    ResponseBody responseBody = r.body();
                    if (!r.isSuccessful()) {
                        error = parseError(r.code(), responseBody == null ? "" : responseBody.string(), scope, r.header("Retry-After"));
                        if (error.isRateLimited()) {
                            blockedUntil.put(server.baseUrl, clock.getAsLong() + error.retryAfterSeconds * 1000L);
                        }
                    } else if (target != null) {
                        File part = new File(target.getPath() + ".part");
                        try (BufferedSink sink = Okio.buffer(Okio.sink(part))) {
                            sink.writeAll(responseBody.source());
                        }
                        if (!part.renameTo(target)) {
                            part.delete();
                            throw new IOException("cannot write " + target.getName());
                        }
                        result = (T) target;
                    } else if (!noResult) {
                        result = parser.parse(r.code(), responseBody == null ? "" : responseBody.string());
                        if (result == null) {
                            error = new UnigifError(UnigifError.KIND_BAD_RESPONSE, r.code(), null, null);
                        }
                    }
                } catch (IOException e) {
                    if (call.isCanceled()) {
                        return;
                    }
                    result = null;
                    error = new UnigifError(UnigifError.KIND_NETWORK, 0, null, e.getMessage());
                }
                deliver(server, callback, result, error);
            }
        });
        return call;
    }

    private static <T> void deliver(Server server, Callback<T> callback, T result, UnigifError error) {
        if (callback != null) {
            server.executor.execute(() -> callback.onResult(result, error));
        }
    }

    // Parsing. Each parser returns null when the body is not what a UniGif server sends.

    /** Null when the body is not a JSON object (for example an HTML page from some other server). */
    static Me parseMe(String body) {
        JsonObject object = object(body);
        if (object == null) {
            return null;
        }
        Me me = new Me();
        me.name = string(object, "name");
        JsonElement scopes = object.get("scopes");
        if (scopes instanceof JsonArray) {
            for (JsonElement scope : (JsonArray) scopes) {
                if (scope.isJsonPrimitive()) {
                    me.scopes.add(scope.getAsString());
                }
            }
        } else if (scopes != null && scopes.isJsonPrimitive()) {
            for (String scope : scopes.getAsString().split("[\\s,]+")) {
                if (!scope.isEmpty()) {
                    me.scopes.add(scope);
                }
            }
        }
        return me;
    }

    /** A page is {@code {"items": [...], "next_cursor": ...}}. Items without an MP4 rendition are skipped. */
    static GifPage parseGifPage(String body, HttpUrl base) {
        JsonObject object = object(body);
        JsonArray items = object == null ? null : array(object, "items", "gifs", "data");
        if (items == null) {
            return null;
        }
        GifPage page = new GifPage();
        for (JsonElement item : items) {
            Gif gif = item.isJsonObject() ? parseGif(item.getAsJsonObject(), base) : null;
            if (gif != null && gif.mp4 != null) {
                page.gifs.add(gif);
            }
        }
        page.nextCursor = string(object, "next_cursor");
        return page;
    }

    /** The answer to an upload: a Gif, either at the top level or under {@code gif}, plus {@code duplicate}. */
    static Gif parseAddedGif(String body, HttpUrl base) {
        JsonObject object = object(body);
        if (object == null) {
            return null;
        }
        JsonElement inner = object.get("gif");
        Gif gif = parseGif(inner != null && inner.isJsonObject() ? inner.getAsJsonObject() : object, base);
        if (gif != null) {
            gif.duplicate = bool(object, "duplicate");
        }
        return gif;
    }

    /** Null when there is no id. */
    static Gif parseGif(JsonObject object, HttpUrl base) {
        Gif gif = new Gif();
        gif.id = string(object, "id");
        if (gif.id.isEmpty()) {
            return null;
        }
        gif.status = string(object, "status");
        JsonArray tags = array(object, "tags");
        if (tags != null) {
            for (JsonElement tag : tags) {
                String name = tag.isJsonObject() ? string(tag.getAsJsonObject(), "name") : tag.isJsonPrimitive() ? tag.getAsString() : "";
                if (!name.isEmpty()) {
                    gif.tags.add(name);
                }
            }
        }
        gif.width = (int) number(object, "width");
        gif.height = (int) number(object, "height");
        gif.durationMs = number(object, "duration_ms");
        JsonElement media = object.get("media");
        if (media != null && media.isJsonObject()) {
            JsonObject renditions = media.getAsJsonObject();
            gif.mp4 = media(renditions.get("mp4"), base);
            Media preview = media(renditions.get("preview_mp4"), base);
            Media poster = media(renditions.get("poster"), base);
            gif.previewUrl = preview == null ? "" : preview.url;
            gif.posterUrl = poster == null ? "" : poster.url;
        }
        return gif;
    }

    private static Media media(JsonElement element, HttpUrl base) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        Media media = new Media();
        media.url = resolve(base, string(object, "url"));
        if (media.url.isEmpty()) {
            return null;
        }
        media.width = (int) number(object, "width");
        media.height = (int) number(object, "height");
        media.bytes = number(object, "bytes");
        return media;
    }

    /** Absolute form of a URL from a response: {@code m/…} is relative to the base URL, {@code /m/…} to its host. "" when unusable. */
    static String resolve(HttpUrl base, String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        HttpUrl resolved = base == null ? HttpUrl.parse(url) : base.newBuilder().addPathSegment("").build().resolve(url);
        return resolved == null ? "" : resolved.toString();
    }

    /**
     * {@code GET /tags} is a list (bare or under {@code items} / {@code tags}); {@code GET /tags/suggest} has it
     * under {@code recent}. A tag is {@code {"name", "gif_count"}} or a bare string.
     */
    static List<Tag> parseTags(String body) {
        JsonElement root = json(body);
        JsonArray items = root == null ? null : root.isJsonArray() ? root.getAsJsonArray()
                : root.isJsonObject() ? array(root.getAsJsonObject(), "recent", "items", "tags") : null;
        if (items == null) {
            return null;
        }
        List<Tag> tags = new ArrayList<>();
        for (JsonElement item : items) {
            Tag tag = new Tag();
            if (item.isJsonObject()) {
                tag.name = string(item.getAsJsonObject(), "name");
                tag.gifCount = (int) number(item.getAsJsonObject(), "gif_count");
            } else if (item.isJsonPrimitive()) {
                tag.name = item.getAsString();
            }
            if (!tag.name.isEmpty()) {
                tags.add(tag);
            }
        }
        return tags;
    }

    static UnigifError parseError(int status, String body) {
        return parseError(status, body, null, null);
    }

    /** Non-2xx responses are application/problem+json with {@code code} and {@code detail}; anything else keeps only the status. */
    static UnigifError parseError(int status, String body, String scope, String retryAfter) {
        JsonObject object = object(body);
        String code = object == null ? null : string(object, "code");
        String detail = object == null ? null : string(object, "detail");
        if (object != null && !string(object, "scope").isEmpty()) {
            scope = string(object, "scope");
        }
        int wait = 0;
        if (status == 429 || "rate_limited".equals(code)) {
            wait = DEFAULT_RETRY_AFTER;
            try {
                // The HTTP-date form is not parsed; it gets the default.
                wait = Math.max(1, Math.min(MAX_RETRY_AFTER, Integer.parseInt(retryAfter.trim())));
            } catch (RuntimeException ignore) {
            }
        }
        return new UnigifError(UnigifError.KIND_HTTP, status, code, detail, scope, wait);
    }

    private static JsonElement json(String body) {
        try {
            return JsonParser.parseString(body);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static JsonObject object(String body) {
        JsonElement root = json(body);
        return root != null && root.isJsonObject() ? root.getAsJsonObject() : null;
    }

    /** The first of the names that holds an array. */
    private static JsonArray array(JsonObject object, String... names) {
        for (String name : names) {
            JsonElement element = object.get(name);
            if (element != null && element.isJsonArray()) {
                return element.getAsJsonArray();
            }
        }
        return null;
    }

    private static String string(JsonObject object, String name) {
        JsonElement element = object.get(name);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
    }

    private static long number(JsonObject object, String name) {
        JsonElement element = object.get(name);
        try {
            return element != null && element.isJsonPrimitive() ? (long) element.getAsDouble() : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static boolean bool(JsonObject object, String name) {
        JsonElement element = object.get(name);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean() && element.getAsBoolean();
    }
}
