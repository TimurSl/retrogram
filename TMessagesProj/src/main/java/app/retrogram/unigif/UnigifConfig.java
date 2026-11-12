package app.retrogram.unigif;

import java.net.URI;
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
