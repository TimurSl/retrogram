package app.retrogram.unigif;

import org.telegram.tgnet.TLRPC;

import java.util.LinkedHashMap;
import java.util.Map;

import app.retrogram.unigif.UnigifModels.Gif;
import app.retrogram.unigif.UnigifModels.GifPage;

/**
 * Turns UniGif library items into the inline-bot results the GIF panel already displays
 * (docs/plans/08-unigif.md, WP U.3). Only plain TL data classes are used, so this runs in JVM unit tests.
 */
public final class UnigifResults {

    /** Marks a result as coming from UniGif: {@code result.id = ID_PREFIX + gif.id}. */
    public static final String ID_PREFIX = "ugf:";

    private static final String MIME_MP4 = "video/mp4";
    /** The grid's own default cell size, used when the server sent no dimensions. */
    private static final int DEFAULT_SIZE = 100;
    static final int MAX_REMEMBERED = 500;

    /** Gif id → the item it was built from, most recently used last. Guarded by itself. */
    private static final LinkedHashMap<String, Gif> remembered = new LinkedHashMap<String, Gif>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Gif> eldest) {
            return size() > MAX_REMEMBERED;
        }
    };

    private UnigifResults() {
    }

    /**
     * A {@code TL_botInlineResult} (not the media class, so sending takes the {@code content} branch), or null
     * when the item has no playable MP4. The media URLs in {@code gif} are already absolute.
     */
    public static TLRPC.TL_botInlineResult toInlineResult(Gif gif) {
        if (gif == null || gif.id == null || gif.id.isEmpty() || gif.mp4 == null || gif.mp4.url == null || gif.mp4.url.isEmpty()) {
            return null;
        }
        int width = firstPositive(gif.width, gif.mp4.width);
        int height = firstPositive(gif.height, gif.mp4.height);
        if (width == 0 || height == 0) {
            width = DEFAULT_SIZE;
            height = DEFAULT_SIZE;
        }
        double duration = Math.max(0, gif.durationMs) / 1000.0;

        TLRPC.TL_botInlineResult result = new TLRPC.TL_botInlineResult();
        result.id = ID_PREFIX + gif.id;
        result.type = "gif";
        result.query_id = 0;
        if (!gif.tags.isEmpty()) {
            StringBuilder title = new StringBuilder();
            for (String tag : gif.tags) {
                if (title.length() > 0) {
                    title.append(' ');
                }
                title.append(tag);
            }
            result.title = title.toString();
            result.flags |= 2;
        }
        String previewUrl = gif.previewUrl == null || gif.previewUrl.isEmpty() ? gif.mp4.url : gif.previewUrl;
        result.thumb = webDocument(previewUrl, 0, width, height, duration);
        result.flags |= 16;
        result.content = webDocument(gif.mp4.url, gif.mp4.bytes, width, height, duration);
        result.flags |= 32;
        TLRPC.TL_botInlineMessageMediaAuto message = new TLRPC.TL_botInlineMessageMediaAuto();
        message.message = "";
        result.send_message = message;

        synchronized (remembered) {
            remembered.put(gif.id, gif);
        }
        return result;
    }

    /** One page as the panel's adapters expect it. {@code cache_time = 0}: nothing goes to Telegram's bot cache. */
    public static TLRPC.TL_messages_botResults toBotResults(GifPage page) {
        TLRPC.TL_messages_botResults results = new TLRPC.TL_messages_botResults();
        results.gallery = true;
        results.query_id = 0;
        results.cache_time = 0;
        results.next_offset = "";
        if (page == null) {
            return results;
        }
        for (Gif gif : page.gifs) {
            TLRPC.TL_botInlineResult result = toInlineResult(gif);
            if (result != null) {
                results.results.add(result);
            }
        }
        if (page.nextCursor != null && !page.nextCursor.isEmpty()) {
            results.next_offset = page.nextCursor;
            results.flags |= 2;
        }
        return results;
    }

    public static boolean isUnigif(TLRPC.BotInlineResult result) {
        return result != null && result.id != null && result.id.startsWith(ID_PREFIX);
    }

    /** The UniGif id of a result, or null when it is not a UniGif result. */
    public static String gifId(TLRPC.BotInlineResult result) {
        return isUnigif(result) ? result.id.substring(ID_PREFIX.length()) : null;
    }

    /** The library item a result was built from (poster URL, tags, …), or null when it is no longer remembered. */
    public static Gif gif(TLRPC.BotInlineResult result) {
        String id = gifId(result);
        if (id == null) {
            return null;
        }
        synchronized (remembered) {
            return remembered.get(id);
        }
    }

    static void forgetAll() {
        synchronized (remembered) {
            remembered.clear();
        }
    }

    private static TLRPC.TL_webDocumentNoProxy webDocument(String url, long bytes, int width, int height, double duration) {
        TLRPC.TL_webDocumentNoProxy document = new TLRPC.TL_webDocumentNoProxy();
        document.url = url;
        document.mime_type = MIME_MP4;
        document.size = (int) Math.max(0, Math.min(Integer.MAX_VALUE, bytes));
        TLRPC.TL_documentAttributeVideo video = new TLRPC.TL_documentAttributeVideo();
        video.w = width;
        video.h = height;
        video.duration = duration;
        document.attributes.add(video);
        return document;
    }

    private static int firstPositive(int a, int b) {
        return a > 0 ? a : Math.max(b, 0);
    }
}
