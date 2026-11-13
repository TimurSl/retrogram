package app.retrogram.unigif;

/**
 * Text handling for the UniGif search field (docs/plans/08-unigif.md, WP U.5). The server reads the query as
 * words: every word is a hashtag, the last word is a prefix, a trailing space makes the last word exact, and
 * {@code -word} excludes. No Android classes, so this runs in JVM unit tests.
 */
public final class UnigifQuery {

    private UnigifQuery() {
    }

    /**
     * The text to send as {@code q}: as typed (case kept), without leading whitespace, and with trailing
     * whitespace reduced to one space, because that space carries meaning. "" when there is nothing to search.
     */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        int start = 0;
        while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        int end = text.length();
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        if (end == start) {
            return "";
        }
        return end < text.length() ? text.substring(start, end) + " " : text.substring(start, end);
    }

    /** Where the last word starts: after the last whitespace character, or 0. */
    private static int lastWordStart(String text) {
        int i = text.length();
        while (i > 0 && !Character.isWhitespace(text.charAt(i - 1))) {
            i--;
        }
        return i;
    }

    /** The word being typed, without a leading {@code -} or {@code #}. "" when the text ends in whitespace. */
    public static String lastWord(String text) {
        if (text == null) {
            return "";
        }
        String word = text.substring(lastWordStart(text));
        if (word.startsWith("-")) {
            word = word.substring(1);
        }
        if (word.startsWith("#")) {
            word = word.substring(1);
        }
        return word;
    }

    /**
     * Replaces the word being typed with {@code tag} and a trailing space (so the server matches it exactly).
     * A leading {@code -} on the replaced word is kept.
     */
    public static String replaceLastWord(String text, String tag) {
        if (text == null) {
            text = "";
        }
        final int start = lastWordStart(text);
        final boolean exclude = text.startsWith("-", start);
        return text.substring(0, start) + (exclude ? "-" : "") + tagName(tag) + " ";
    }

    /** A tag name as it goes into a query: no leading {@code #}. */
    public static String tagName(String tag) {
        if (tag == null) {
            return "";
        }
        tag = tag.trim();
        return tag.startsWith("#") ? tag.substring(1) : tag;
    }
}
