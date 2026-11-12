package app.retrogram.unigif;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.telegram.tgnet.TLRPC;

import app.retrogram.unigif.UnigifModels.Gif;
import app.retrogram.unigif.UnigifModels.GifPage;
import app.retrogram.unigif.UnigifModels.Media;

public class UnigifResultsTest {

    private static final String MP4 = "https://gifs.example.com/m/abc/mp4";
    private static final String PREVIEW = "https://gifs.example.com/m/abc/preview_mp4";

    private static Gif gif(String id) {
        Gif gif = new Gif();
        gif.id = id;
        gif.status = "ready";
        gif.tags.add("cat");
        gif.tags.add("funny");
        gif.width = 480;
        gif.height = 270;
        gif.durationMs = 2500;
        gif.mp4 = new Media();
        gif.mp4.url = MP4;
        gif.mp4.width = 480;
        gif.mp4.height = 270;
        gif.mp4.bytes = 123456;
        gif.previewUrl = PREVIEW;
        gif.posterUrl = "https://gifs.example.com/m/abc/poster";
        return gif;
    }

    private static TLRPC.TL_documentAttributeVideo video(TLRPC.WebDocument document) {
        assertEquals(1, document.attributes.size());
        return (TLRPC.TL_documentAttributeVideo) document.attributes.get(0);
    }

    @Before
    public void setUp() {
        UnigifResults.forgetAll();
    }

    @Test
    public void mapsEveryField() {
        TLRPC.TL_botInlineResult result = UnigifResults.toInlineResult(gif("abc"));

        assertEquals("ugf:abc", result.id);
        assertEquals("gif", result.type);
        assertEquals("cat funny", result.title);
        assertEquals(0, result.query_id);
        assertNull(result.document);
        assertNull(result.photo);

        assertTrue(result.content instanceof TLRPC.TL_webDocumentNoProxy);
        assertEquals(MP4, result.content.url);
        assertEquals("video/mp4", result.content.mime_type);
        assertEquals(123456, result.content.size);
        assertEquals(480, video(result.content).w);
        assertEquals(270, video(result.content).h);
        assertEquals(2.5, video(result.content).duration, 0.0001);

        assertTrue(result.thumb instanceof TLRPC.TL_webDocumentNoProxy);
        assertEquals(PREVIEW, result.thumb.url);
        assertEquals("video/mp4", result.thumb.mime_type);
        assertEquals(480, video(result.thumb).w);
        assertEquals(270, video(result.thumb).h);

        assertTrue(result.send_message instanceof TLRPC.TL_botInlineMessageMediaAuto);
        assertEquals("", result.send_message.message);
        // title, thumb, content
        assertEquals(2 | 16 | 32, result.flags);
    }

    @Test
    public void previewFallsBackToMp4() {
        Gif gif = gif("abc");
        gif.previewUrl = "";
        assertEquals(MP4, UnigifResults.toInlineResult(gif).thumb.url);
    }

    @Test
    public void noTagsMeansNoTitle() {
        Gif gif = gif("abc");
        gif.tags.clear();
        TLRPC.TL_botInlineResult result = UnigifResults.toInlineResult(gif);
        assertNull(result.title);
        assertEquals(16 | 32, result.flags);
    }

    @Test
    public void sizeFallbacks() {
        Gif fromRendition = gif("a");
        fromRendition.width = 0;
        fromRendition.height = 0;
        fromRendition.mp4.width = 320;
        fromRendition.mp4.height = 200;
        TLRPC.TL_botInlineResult result = UnigifResults.toInlineResult(fromRendition);
        assertEquals(320, video(result.content).w);
        assertEquals(200, video(result.content).h);

        Gif unknown = gif("b");
        unknown.width = 0;
        unknown.height = 270;
        unknown.mp4.width = 0;
        unknown.mp4.height = 0;
        result = UnigifResults.toInlineResult(unknown);
        assertEquals(100, video(result.content).w);
        assertEquals(100, video(result.content).h);
        assertEquals(100, video(result.thumb).w);
    }

    @Test
    public void hugeFileSizeIsClamped() {
        Gif gif = gif("abc");
        gif.mp4.bytes = 5_000_000_000L;
        assertEquals(Integer.MAX_VALUE, UnigifResults.toInlineResult(gif).content.size);
    }

    @Test
    public void itemsWithoutMp4AreSkipped() {
        Gif noMedia = gif("a");
        noMedia.mp4 = null;
        Gif noUrl = gif("b");
        noUrl.mp4.url = "";
        assertNull(UnigifResults.toInlineResult(noMedia));
        assertNull(UnigifResults.toInlineResult(noUrl));
        assertNull(UnigifResults.toInlineResult(null));

        GifPage page = new GifPage();
        page.gifs.add(noMedia);
        page.gifs.add(gif("c"));
        page.gifs.add(noUrl);
        TLRPC.TL_messages_botResults results = UnigifResults.toBotResults(page);
        assertEquals(1, results.results.size());
        assertEquals("ugf:c", results.results.get(0).id);
    }

    @Test
    public void pageWithCursor() {
        GifPage page = new GifPage();
        page.gifs.add(gif("a"));
        page.gifs.add(gif("b"));
        page.nextCursor = "cur1";
        TLRPC.TL_messages_botResults results = UnigifResults.toBotResults(page);
        assertEquals(2, results.results.size());
        assertEquals("cur1", results.next_offset);
        assertEquals(0, results.cache_time);
        assertEquals(0, results.query_id);
        assertTrue(results.users.isEmpty());
    }

    @Test
    public void lastPageHasEmptyOffset() {
        GifPage page = new GifPage();
        page.gifs.add(gif("a"));
        assertEquals("", UnigifResults.toBotResults(page).next_offset);
        page.nextCursor = null;
        assertEquals("", UnigifResults.toBotResults(page).next_offset);
        assertEquals("", UnigifResults.toBotResults(null).next_offset);
        assertTrue(UnigifResults.toBotResults(null).results.isEmpty());
    }

    @Test
    public void recognisesOwnResults() {
        TLRPC.TL_botInlineResult own = UnigifResults.toInlineResult(gif("abc"));
        assertTrue(UnigifResults.isUnigif(own));
        assertEquals("abc", UnigifResults.gifId(own));

        TLRPC.TL_botInlineMediaResult bot = new TLRPC.TL_botInlineMediaResult();
        bot.id = "4242";
        assertFalse(UnigifResults.isUnigif(bot));
        assertNull(UnigifResults.gifId(bot));
        assertNull(UnigifResults.gif(bot));

        bot.id = null;
        assertFalse(UnigifResults.isUnigif(bot));
        assertFalse(UnigifResults.isUnigif(null));
    }

    @Test
    public void remembersTheSourceItem() {
        Gif gif = gif("abc");
        TLRPC.TL_botInlineResult result = UnigifResults.toInlineResult(gif);
        assertSame(gif, UnigifResults.gif(result));
        assertEquals("https://gifs.example.com/m/abc/poster", UnigifResults.gif(result).posterUrl);

        Gif newer = gif("abc");
        UnigifResults.toInlineResult(newer);
        assertSame(newer, UnigifResults.gif(result));
    }

    @Test
    public void sideMapIsBounded() {
        TLRPC.TL_botInlineResult first = UnigifResults.toInlineResult(gif("first"));
        TLRPC.TL_botInlineResult second = UnigifResults.toInlineResult(gif("second"));
        for (int i = 0; i < UnigifResults.MAX_REMEMBERED - 2; i++) {
            UnigifResults.toInlineResult(gif("g" + i));
        }
        assertNotNull(UnigifResults.gif(first));
        // "first" was just read, so the next new item pushes out "second".
        UnigifResults.toInlineResult(gif("one-more"));
        assertNotNull(UnigifResults.gif(first));
        assertNull(UnigifResults.gif(second));
        assertEquals("second", UnigifResults.gifId(second));
    }
}
