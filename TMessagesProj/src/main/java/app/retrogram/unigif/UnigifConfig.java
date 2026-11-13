package app.retrogram.unigif;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import app.retrogram.RetroConfig;

/**
 * Read side of the UniGif settings (docs/plans/08-unigif.md, WP U.1). All values are live.
 * The static helpers use no Android classes so they run in JVM unit tests.
 */
public final class UnigifConfig {

    private UnigifConfig() {
    }

    /** UniGif is the selected provider (it may still be unusable: see {@link #isActive()}). */
    public static boolean isSelected() {
        return RetroConfig.gifProvider.Int() == RetroConfig.GIF_PROVIDER_UNIGIF;
    }

    /** UniGif is selected and both the server URL and the key are set. The hook condition for the later WPs. */
    public static boolean isActive() {
        return isSelected() && !baseUrl().isEmpty() && !key().isEmpty();
    }

    /** Server URL without a trailing slash, or "" when it is not set or not usable. */
    public static String baseUrl() {
        String url = normalizeUrl(RetroConfig.unigifUrl.String());
        return isValidUrl(url) ? url : "";
    }

    public static String key() {
        return RetroConfig.unigifKey.String().trim();
    }

    // Permissions of the key, as the last successful GET /me reported them (WP U.8). Kept for the process only.

    private static final Object scopesLock = new Object();
    /** Server URL and key the scopes belong to; a changed URL or key makes them unknown again. */
    private static String scopesOwner;
    private static List<String> scopes;

    private static String owner(String baseUrl, String key) {
        return baseUrl + "\n" + key;
    }

    /** Called by {@code UnigifClient} for every answered {@code GET /me}, with the server and key that call used. */
    public static void rememberScopes(String baseUrl, String key, List<String> granted) {
        synchronized (scopesLock) {
            scopesOwner = owner(baseUrl, key);
            scopes = new ArrayList<>(granted);
        }
    }

    public static void forgetScopes() {
        synchronized (scopesLock) {
            scopesOwner = null;
            scopes = null;
        }
    }

    /** The scopes of the current server and key have been read. */
    public static boolean scopesKnown() {
        synchronized (scopesLock) {
            return scopes != null && owner(baseUrl(), key()).equals(scopesOwner);
        }
    }

    /** The current key is known to have this permission ("read", "upload", "delete"). False while nothing is known. */
    public static boolean hasScope(String scope) {
        synchronized (scopesLock) {
            return scopes != null && owner(baseUrl(), key()).equals(scopesOwner) && scopes.contains(scope);
        }
    }

    /** Trims, removes trailing slashes, and puts {@code https://} in front when no scheme was typed. */
    public static String normalizeUrl(String input) {
        if (input == null) {
            return "";
        }
        String url = input.trim();
        if (url.isEmpty()) {
            return "";
        }
        if (!url.contains("://")) {
            url = "https://" + url;
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    /** True for a normalised http(s) URL with a host and no query or fragment. */
    public static boolean isValidUrl(String normalized) {
        if (normalized == null || normalized.isEmpty()) {
            return false;
        }
        try {
            URI uri = new URI(normalized);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("https") && !scheme.equals("http")) {
                return false;
            }
            return uri.getHost() != null && !uri.getHost().isEmpty() && uri.getRawQuery() == null && uri.getRawFragment() == null && uri.getRawUserInfo() == null;
        } catch (Exception e) {
            return false;
        }
    }

    /** The key would travel unencrypted to this URL. */
    public static boolean isCleartext(String normalized) {
        return normalized != null && normalized.toLowerCase(Locale.ROOT).startsWith("http://");
    }
}
