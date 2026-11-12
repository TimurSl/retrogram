package app.retrogram.unigif;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.telegram.messenger.AndroidUtilities;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import app.retrogram.unigif.UnigifModels.Me;
import app.retrogram.unigif.UnigifModels.UnigifError;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * HTTP client for a UniGif server. WP U.1 only needs {@link #me}; WP U.2 adds the list, tag, use, add and delete
 * calls, the auth interceptor and the 429 back-off. No Android UI imports: results are delivered through an
 * {@link Executor}, which is the UI thread by default.
 */
public final class UnigifClient {

    public interface Callback<T> {
        void onResult(T result, UnigifError error);
    }

    private static final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            // The key must not follow a redirect to another host; a redirect on an API call is reported as an error.
            .followRedirects(false)
            .followSslRedirects(false)
            .build();

    private UnigifClient() {
    }

    /** {@code GET /me} with the configured URL and key; the callback runs on the UI thread. */
    public static Call me(Callback<Me> callback) {
        return me(UnigifConfig.baseUrl(), UnigifConfig.key(), AndroidUtilities::runOnUIThread, callback);
    }

    /** Returns the running call, or null when the error was delivered without a request. */
    public static Call me(String baseUrl, String key, Executor callbackExecutor, Callback<Me> callback) {
        if (baseUrl == null || baseUrl.isEmpty() || key == null || key.isEmpty()) {
            callbackExecutor.execute(() -> callback.onResult(null, new UnigifError(UnigifError.KIND_NOT_SET_UP, 0, null, null)));
            return null;
        }
        Request request;
        try {
            request = new Request.Builder()
                    .url(baseUrl + "/me")
                    .header("Authorization", "Bearer " + key)
                    .header("Accept", "application/json")
                    .build();
        } catch (IllegalArgumentException e) {
            // A key with characters that cannot go in a header, or a URL OkHttp rejects.
            callbackExecutor.execute(() -> callback.onResult(null, new UnigifError(UnigifError.KIND_NOT_SET_UP, 0, null, e.getMessage())));
            return null;
        }
        Call call = http.newCall(request);
        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (call.isCanceled()) {
                    return;
                }
                callbackExecutor.execute(() -> callback.onResult(null, new UnigifError(UnigifError.KIND_NETWORK, 0, null, e.getMessage())));
            }

            @Override
            public void onResponse(Call call, Response response) {
                Me me = null;
                UnigifError error;
                try (Response r = response) {
                    ResponseBody responseBody = r.body();
                    String body = responseBody == null ? "" : responseBody.string();
                    if (r.isSuccessful()) {
                        me = parseMe(body);
                        error = me == null ? new UnigifError(UnigifError.KIND_BAD_RESPONSE, r.code(), null, null) : null;
                    } else {
                        error = parseError(r.code(), body);
                    }
                } catch (IOException e) {
                    error = new UnigifError(UnigifError.KIND_NETWORK, 0, null, e.getMessage());
                }
                Me result = me;
                UnigifError finalError = error;
                callbackExecutor.execute(() -> callback.onResult(result, finalError));
            }
        });
        return call;
    }

    /** Null when the body is not a JSON object (for example an HTML page from some other server). */
    static Me parseMe(String body) {
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonObject()) {
                return null;
            }
            JsonObject object = root.getAsJsonObject();
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
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Non-2xx responses are application/problem+json with {@code code} and {@code detail}; anything else keeps only the status. */
    static UnigifError parseError(int status, String body) {
        String code = null;
        String detail = null;
        try {
            JsonElement root = JsonParser.parseString(body);
            if (root.isJsonObject()) {
                code = string(root.getAsJsonObject(), "code");
                detail = string(root.getAsJsonObject(), "detail");
            }
        } catch (RuntimeException ignore) {
        }
        return new UnigifError(UnigifError.KIND_HTTP, status, code, detail);
    }

    private static String string(JsonObject object, String name) {
        JsonElement element = object.get(name);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
    }
}
