package app.retrogram.unigif;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import app.retrogram.RetroConfig;
import app.retrogram.unigif.UnigifClient.Server;
import app.retrogram.unigif.UnigifModels.Gif;
import app.retrogram.unigif.UnigifModels.GifPage;
import app.retrogram.unigif.UnigifModels.Me;
import app.retrogram.unigif.UnigifModels.Tag;
import app.retrogram.unigif.UnigifModels.UnigifError;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.Call;
import okhttp3.HttpUrl;

public class UnigifClientTest {

    private static final String PROBLEM = "application/problem+json";

    private MockWebServer web;
    private Server server;
    private long now;

    /** Collects the one callback of a call. */
    private static final class Result<T> implements UnigifClient.Callback<T> {
        private final CountDownLatch done = new CountDownLatch(1);
        T value;
        UnigifError error;

        @Override
        public void onResult(T result, UnigifError error) {
            this.value = result;
            this.error = error;
            done.countDown();
        }

        Result<T> await() throws InterruptedException {
            assertTrue("no callback", done.await(10, TimeUnit.SECONDS));
            return this;
        }
    }

    @Before
    public void setUp() throws Exception {
        web = new MockWebServer();
        web.start();
        server = server("");
        now = 1_000_000;
        UnigifClient.clock = () -> now;
        UnigifClient.clearBackoff();
    }

    @After
    public void tearDown() {
        web.close();
        RetroConfig.loadGif((k, def) -> 0, (k, def) -> "");
        UnigifConfig.forgetScopes();
    }

    @Test
    public void meRemembersTheScopes() throws Exception {
        RetroConfig.loadGif((k, def) -> 1, (k, def) -> k.equals("RetroUnigifUrl") ? server.baseUrl : "k3y");
        assertFalse(UnigifConfig.hasScope("delete"));
        enqueue(200, "{\"name\":\"phone\",\"scopes\":[\"read\",\"delete\"]}");
        Result<Me> result = new Result<>();
        UnigifClient.me(server, result);
        assertNotNull(result.await().value);
        assertTrue(UnigifConfig.scopesKnown());
        assertTrue(UnigifConfig.hasScope("delete"));
        assertFalse(UnigifConfig.hasScope("upload"));

        // known: no further request
        UnigifClient.ensureScopes(server);
        assertEquals(1, web.getRequestCount());
    }

    @Test
    public void scopesAreAskedForOnceAMinute() throws Exception {
        RetroConfig.loadGif((k, def) -> 1, (k, def) -> k.equals("RetroUnigifUrl") ? server.baseUrl : "k3y");
        enqueue(500, "{\"code\":\"internal\"}");
        UnigifClient.ensureScopes(server);
        assertEquals("/me", request().getTarget());
        UnigifClient.ensureScopes(server);
        now += 59_000;
        UnigifClient.ensureScopes(server);
        assertEquals(1, web.getRequestCount());
        assertFalse(UnigifConfig.scopesKnown());

        now += 2_000;
        enqueue(200, "{\"scopes\":[\"read\"]}");
        UnigifClient.ensureScopes(server);
        assertEquals("/me", request().getTarget());
        for (int i = 0; i < 100 && !UnigifConfig.scopesKnown(); i++) {
            Thread.sleep(50);
        }
        assertTrue(UnigifConfig.scopesKnown());
        assertFalse(UnigifConfig.hasScope("delete"));

        // a server that is not set up is never asked
        UnigifConfig.forgetScopes();
        UnigifClient.ensureScopes(new Server("", "k3y", Runnable::run));
        assertEquals(2, web.getRequestCount());
    }

    private Server server(String path) {
        String root = web.url("/").toString();
        return new Server(root.substring(0, root.length() - 1) + path, "k3y", Runnable::run);
    }

    private void enqueue(int code, String body) {
        web.enqueue(new MockResponse.Builder().code(code).addHeader("Content-Type", code < 300 ? "application/json" : PROBLEM).body(body).build());
    }

    private RecordedRequest request() throws InterruptedException {
        RecordedRequest request = web.takeRequest(10, TimeUnit.SECONDS);
        assertNotNull("no request", request);
        return request;
    }

    @Test
    public void meSendsTheKey() throws Exception {
        enqueue(200, "{\"name\":\"phone\",\"scopes\":[\"read\"]}");
        Result<Me> result = new Result<>();
        assertNotNull(UnigifClient.me(server, result));
        assertEquals("phone", result.await().value.name);
        assertNull(result.error);
        RecordedRequest request = request();
        assertEquals("GET", request.getMethod());
        assertEquals("/me", request.getTarget());
        assertEquals("Bearer k3y", request.getHeaders().get("Authorization"));
        assertEquals("application/json", request.getHeaders().get("Accept"));
    }

    @Test
    public void listGifsRequestAndPage() throws Exception {
        enqueue(200, "{\"items\":["
                + "{\"id\":\"a1\",\"status\":\"ready\",\"tags\":[\"cat\",{\"name\":\"funny\"}],\"width\":320,\"height\":240,\"duration_ms\":1500,"
                + "\"media\":{\"mp4\":{\"url\":\"/m/a1/mp4\",\"width\":320,\"height\":240,\"bytes\":12345},"
                + "\"preview_mp4\":{\"url\":\"https://cdn.example.com/a1p\"},\"poster\":{\"url\":\"m/a1/poster\"}}},"
                + "{\"id\":\"a2\",\"status\":\"ready\",\"media\":null},"
                + "{\"id\":\"a3\",\"media\":{\"mp4\":null,\"poster\":{\"url\":\"/m/a3/poster\"}}},"
                + "{\"status\":\"ready\"},"
                + "{\"id\":7,\"width\":null,\"media\":{\"mp4\":{\"url\":\"/m/7/mp4\"}}}"
                + "],\"next_cursor\":\"c2\"}");
        Result<GifPage> result = new Result<>();
        UnigifClient.listGifs(server, "cat -dog fu", null, "c1", result);
        GifPage page = result.await().value;
        assertNull(result.error);

        HttpUrl url = request().getUrl();
        assertEquals("/gifs", url.encodedPath());
        assertEquals("cat -dog fu", url.queryParameter("q"));
        assertNull(url.queryParameter("sort"));
        assertEquals("ready", url.queryParameter("status"));
        assertEquals("50", url.queryParameter("limit"));
        assertEquals("c1", url.queryParameter("cursor"));

        assertEquals("c2", page.nextCursor);
        assertEquals(2, page.gifs.size());
        Gif gif = page.gifs.get(0);
        assertEquals("a1", gif.id);
        assertEquals(Arrays.asList("cat", "funny"), gif.tags);
        assertEquals(320, gif.width);
        assertEquals(1500, gif.durationMs);
        assertEquals(web.url("/m/a1/mp4").toString(), gif.mp4.url);
        assertEquals(12345, gif.mp4.bytes);
        assertEquals("https://cdn.example.com/a1p", gif.previewUrl);
        assertEquals(web.url("/m/a1/poster").toString(), gif.posterUrl);
        assertEquals("7", page.gifs.get(1).id);
        assertEquals(0, page.gifs.get(1).width);
        assertEquals("", page.gifs.get(1).previewUrl);
    }

    @Test
    public void listGifsSortAndLastPage() throws Exception {
        enqueue(200, "{\"items\":[],\"next_cursor\":null}");
        Result<GifPage> result = new Result<>();
        UnigifClient.listGifs(server, "", "used", "", result);
        assertEquals("", result.await().value.nextCursor);
        HttpUrl url = request().getUrl();
        assertEquals("used", url.queryParameter("sort"));
        assertNull(url.queryParameter("q"));
        assertNull(url.queryParameter("cursor"));
    }

    @Test
    public void baseUrlWithPath() throws Exception {
        enqueue(200, "{\"items\":[{\"id\":\"a\",\"media\":{\"mp4\":{\"url\":\"m/a/mp4\"}}}]}");
        Result<GifPage> result = new Result<>();
        UnigifClient.listGifs(server("/unigif"), null, "new", null, result);
        assertEquals(web.url("/unigif/m/a/mp4").toString(), result.await().value.gifs.get(0).mp4.url);
        RecordedRequest request = request();
        assertEquals("/unigif/gifs", request.getUrl().encodedPath());
        assertEquals("Bearer k3y", request.getHeaders().get("Authorization"));
    }

    @Test
    public void tags() throws Exception {
        enqueue(200, "[{\"name\":\"cat\",\"gif_count\":12},{\"name\":\"car\"},\"cake\",{\"gif_count\":3}]");
        Result<List<Tag>> listed = new Result<>();
        UnigifClient.listTags(server, "ca", listed);
        List<Tag> tags = listed.await().value;
        assertEquals(3, tags.size());
        assertEquals("cat", tags.get(0).name);
        assertEquals(12, tags.get(0).gifCount);
        assertEquals("cake", tags.get(2).name);
        HttpUrl url = request().getUrl();
        assertEquals("/tags", url.encodedPath());
        assertEquals("ca", url.queryParameter("q"));
        assertEquals("popular", url.queryParameter("sort"));
        assertEquals("20", url.queryParameter("limit"));

        enqueue(200, "{\"recent\":[{\"name\":\"dog\",\"gif_count\":2}]}");
        Result<List<Tag>> suggested = new Result<>();
        UnigifClient.suggestTags(server, suggested);
        assertEquals("dog", suggested.await().value.get(0).name);
        assertEquals("/tags/suggest?limit=12", request().getTarget());
    }

    @Test
    public void use() throws Exception {
        enqueue(204, "");
        assertNotNull(UnigifClient.use(server, "a/1"));
        RecordedRequest request = request();
        assertEquals("POST", request.getMethod());
        assertEquals("/gifs/a%2F1/use", request.getTarget());
        assertEquals("Bearer k3y", request.getHeaders().get("Authorization"));
    }

    @Test
    public void addUploadsTheFile() throws Exception {
        File file = File.createTempFile("unigif", ".mp4");
        file.deleteOnExit();
        Files.write(file.toPath(), "not really an mp4".getBytes(StandardCharsets.UTF_8));

        enqueue(201, "{\"id\":\"n1\",\"status\":\"processing\",\"media\":null}");
        Result<Gif> created = new Result<>();
        UnigifClient.add(server, file, "video/mp4", "4242", created);
        assertEquals("n1", created.await().value.id);
        assertFalse(created.value.duplicate);
        assertNull(created.value.mp4);
        RecordedRequest request = request();
        assertEquals("POST", request.getMethod());
        assertEquals("/gifs?source=telegram&source_id=4242&wait=0", request.getTarget());
        assertEquals("video/mp4", request.getHeaders().get("Content-Type"));
        assertEquals("Bearer k3y", request.getHeaders().get("Authorization"));
        assertEquals("not really an mp4", request.getBody().utf8());

        enqueue(200, "{\"duplicate\":true,\"gif\":{\"id\":\"n1\",\"status\":\"ready\"}}");
        Result<Gif> again = new Result<>();
        UnigifClient.add(server, file, "image/gif", "4242", again);
        assertTrue(again.await().value.duplicate);
        assertEquals("n1", again.value.id);
        assertEquals("image/gif", request().getHeaders().get("Content-Type"));
    }

    @Test
    public void delete() throws Exception {
        enqueue(204, "");
        Result<Void> result = new Result<>();
        UnigifClient.delete(server, "a1", result);
        assertNull(result.await().error);
        assertNull(result.value);
        RecordedRequest request = request();
        assertEquals("DELETE", request.getMethod());
        assertEquals("/gifs/a1", request.getTarget());
    }

    @Test
    public void problemJson() throws Exception {
        enqueue(403, "{\"code\":\"scope_required\",\"detail\":\"needs delete\"}");
        Result<Void> forbidden = new Result<>();
        UnigifClient.delete(server, "a1", forbidden);
        UnigifError error = forbidden.await().error;
        assertEquals(UnigifError.MSG_SCOPE, error.message());
        assertEquals("delete", error.scope);
        assertEquals("needs delete", error.detail);
        assertEquals(403, error.httpStatus);

        enqueue(401, "{\"code\":\"invalid_credentials\"}");
        Result<Me> rejected = new Result<>();
        UnigifClient.me(server, rejected);
        assertEquals(UnigifError.MSG_KEY_REJECTED, rejected.await().error.message());

        assertEquals(UnigifError.MSG_TOO_LARGE, UnigifClient.parseError(413, "{\"code\":\"too_large\"}").message());
        assertEquals(UnigifError.MSG_UNSUPPORTED, UnigifClient.parseError(422, "{\"code\":\"invalid_media\"}").message());
        assertEquals(UnigifError.MSG_UNSUPPORTED, UnigifClient.parseError(415, "").message());
        assertEquals(UnigifError.MSG_SCOPE, UnigifClient.parseError(403, "<html>").message());
        assertEquals(UnigifError.MSG_OTHER, UnigifClient.parseError(500, "{\"code\":\"internal\"}").message());
        assertEquals(UnigifError.MSG_OTHER, UnigifClient.parseError(422, "{\"code\":\"validation\"}").message());
    }

    @Test
    public void notUnigifAnswers() throws Exception {
        enqueue(200, "<html>hello</html>");
        Result<GifPage> html = new Result<>();
        UnigifClient.listGifs(server, null, null, null, html);
        assertEquals(UnigifError.MSG_NOT_UNIGIF, html.await().error.message());
        assertNull(html.value);

        // A redirect on an API call is an error and is not followed.
        web.enqueue(new MockResponse.Builder().code(302).addHeader("Location", web.url("/elsewhere").toString()).build());
        Result<Me> redirect = new Result<>();
        UnigifClient.me(server, redirect);
        assertEquals(302, redirect.await().error.httpStatus);
        assertEquals(2, web.getRequestCount());
    }

    @Test
    public void rateLimitBackOff() throws Exception {
        web.enqueue(new MockResponse.Builder().code(429).addHeader("Retry-After", "7").addHeader("Content-Type", PROBLEM).body("{\"code\":\"rate_limited\"}").build());
        Result<GifPage> first = new Result<>();
        UnigifClient.listGifs(server, null, "used", null, first);
        assertEquals(UnigifError.MSG_RATE_LIMITED, first.await().error.message());
        assertEquals(7, first.error.retryAfterSeconds);
        assertEquals(1, web.getRequestCount());

        // Refused locally until the wait is over: list, search and tags.
        now += 2_500;
        Result<GifPage> second = new Result<>();
        assertNull(UnigifClient.listGifs(server, "cat", null, null, second));
        assertEquals(UnigifError.MSG_RATE_LIMITED, second.await().error.message());
        assertEquals(5, second.error.retryAfterSeconds);
        Result<List<Tag>> tags = new Result<>();
        assertNull(UnigifClient.listTags(server, "c", tags));
        assertTrue(tags.await().error.isRateLimited());
        assertEquals(1, web.getRequestCount());

        // Other calls are not held back, and another server is not affected.
        enqueue(200, "{\"name\":\"phone\"}");
        Result<Me> me = new Result<>();
        UnigifClient.me(server, me);
        assertNull(me.await().error);
        assertEquals(2, web.getRequestCount());

        now += 4_500;
        enqueue(200, "{\"items\":[]}");
        Result<GifPage> third = new Result<>();
        assertNotNull(UnigifClient.listGifs(server, null, "used", null, third));
        assertNull(third.await().error);
        assertEquals(3, web.getRequestCount());
    }

    @Test
    public void rateLimitWithoutRetryAfter() {
        assertEquals(30, UnigifClient.parseError(429, "", "read", null).retryAfterSeconds);
        assertEquals(30, UnigifClient.parseError(429, "", "read", "Wed, 21 Oct 2026 07:28:00 GMT").retryAfterSeconds);
        assertEquals(3600, UnigifClient.parseError(429, "", "read", "999999").retryAfterSeconds);
        assertEquals(0, UnigifClient.parseError(500, "", "read", "5").retryAfterSeconds);
    }

    @Test
    public void mediaIsFetchedWithoutTheKey() throws Exception {
        File target = new File(Files.createTempDirectory("unigif").toFile(), "a1.mp4");
        // A redirect is followed for media.
        web.enqueue(new MockResponse.Builder().code(302).addHeader("Location", "/m/a1/mp4-real").build());
        web.enqueue(new MockResponse.Builder().code(200).body("bytes").build());
        Result<File> result = new Result<>();
        UnigifClient.download(server, web.url("/m/a1/mp4").toString(), target, result);
        assertEquals(target, result.await().value);
        assertEquals("bytes", new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8));
        assertFalse(new File(target.getPath() + ".part").exists());
        assertNull(request().getHeaders().get("Authorization"));
        assertNull(request().getHeaders().get("Authorization"));

        enqueue(404, "{\"code\":\"not_found\"}");
        File missing = new File(target.getParentFile(), "missing.mp4");
        Result<File> failed = new Result<>();
        UnigifClient.download(server, web.url("/m/zz/mp4").toString(), missing, failed);
        assertEquals(404, failed.await().error.httpStatus);
        assertFalse(missing.exists());
    }

    @Test
    public void whereTheKeyGoes() {
        HttpUrl base = HttpUrl.get("https://gifs.example.com");
        assertTrue(UnigifClient.shouldAuthorize(HttpUrl.get("https://gifs.example.com/gifs?q=m"), base));
        assertTrue(UnigifClient.shouldAuthorize(HttpUrl.get("https://gifs.example.com/me"), base));
        assertFalse(UnigifClient.shouldAuthorize(HttpUrl.get("https://gifs.example.com/m/a1/mp4"), base));
        assertFalse(UnigifClient.shouldAuthorize(HttpUrl.get("https://cdn.example.com/gifs"), base));
        assertFalse(UnigifClient.shouldAuthorize(HttpUrl.get("http://gifs.example.com/gifs"), base));
        assertFalse(UnigifClient.shouldAuthorize(HttpUrl.get("https://gifs.example.com:8443/gifs"), base));
        assertFalse(UnigifClient.shouldAuthorize(HttpUrl.get("https://gifs.example.com/gifs"), null));

        HttpUrl nested = HttpUrl.get("https://example.com/unigif");
        assertTrue(UnigifClient.shouldAuthorize(HttpUrl.get("https://example.com/unigif/gifs"), nested));
        assertFalse(UnigifClient.shouldAuthorize(HttpUrl.get("https://example.com/unigif/m/a1/mp4"), nested));
        assertFalse(UnigifClient.shouldAuthorize(HttpUrl.get("https://example.com/m/a1/mp4"), nested));
        assertFalse(UnigifClient.shouldAuthorize(HttpUrl.get("https://example.com/other"), nested));
        assertFalse(UnigifClient.shouldAuthorize(HttpUrl.get("https://example.com/unigifs/gifs"), nested));
    }

    @Test
    public void notSetUp() throws Exception {
        String url = server.baseUrl;
        for (Server broken : new Server[]{
                new Server("", "k3y", Runnable::run),
                new Server(url, "", Runnable::run),
                new Server(url, "bad\nkey", Runnable::run),
                new Server("not a url", "k3y", Runnable::run)}) {
            Result<GifPage> result = new Result<>();
            Call call = UnigifClient.listGifs(broken, null, null, null, result);
            assertNull(call);
            assertEquals(UnigifError.MSG_NOT_SET_UP, result.await().error.message());
            assertNull(UnigifClient.use(broken, "a1"));
        }
        assertEquals(0, web.getRequestCount());
    }

    @Test
    public void unreachable() throws Exception {
        Server gone = new Server(server.baseUrl, "k3y", Runnable::run);
        web.close();
        Result<Me> result = new Result<>();
        UnigifClient.me(gone, result);
        assertEquals(UnigifError.MSG_NETWORK, result.await().error.message());
    }
}
