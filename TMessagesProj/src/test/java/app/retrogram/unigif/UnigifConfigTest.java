package app.retrogram.unigif;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import java.util.Arrays;

import app.retrogram.RetroConfig;
import app.retrogram.unigif.UnigifModels.Me;
import app.retrogram.unigif.UnigifModels.UnigifError;

public class UnigifConfigTest {

    private static void set(int provider, String url, String key) {
        RetroConfig.loadGif((k, def) -> provider, (k, def) -> k.equals("RetroUnigifUrl") ? url : key);
    }

    @After
    public void tearDown() {
        set(0, "", "");
        UnigifConfig.forgetScopes();
    }

    @Test
    public void scopesBelongToOneServerAndKey() {
        set(1, "gifs.example.com", "key");
        assertFalse(UnigifConfig.scopesKnown());
        assertFalse(UnigifConfig.hasScope("delete"));

        UnigifConfig.rememberScopes("https://gifs.example.com", "key", Arrays.asList("read", "delete"));
        assertTrue(UnigifConfig.scopesKnown());
        assertTrue(UnigifConfig.hasScope("delete"));
        assertFalse(UnigifConfig.hasScope("upload"));

        set(1, "gifs.example.com", "other key");
        assertFalse(UnigifConfig.scopesKnown());
        assertFalse(UnigifConfig.hasScope("delete"));
        set(1, "other.example.com", "key");
        assertFalse(UnigifConfig.hasScope("delete"));

        set(1, "gifs.example.com", "key");
        assertTrue(UnigifConfig.hasScope("delete"));
        UnigifConfig.forgetScopes();
        assertFalse(UnigifConfig.scopesKnown());
        assertFalse(UnigifConfig.hasScope("delete"));
    }

    @Test
    public void normalizeUrl() {
        assertEquals("", UnigifConfig.normalizeUrl(null));
        assertEquals("", UnigifConfig.normalizeUrl("   "));
        assertEquals("https://gifs.example.com", UnigifConfig.normalizeUrl(" gifs.example.com/ "));
        assertEquals("https://gifs.example.com/api", UnigifConfig.normalizeUrl("https://gifs.example.com/api//"));
        assertEquals("http://192.168.1.5:8080", UnigifConfig.normalizeUrl("http://192.168.1.5:8080/"));
    }

    @Test
    public void validUrl() {
        assertTrue(UnigifConfig.isValidUrl("https://gifs.example.com"));
        assertTrue(UnigifConfig.isValidUrl("http://192.168.1.5:8080/unigif"));
        assertFalse(UnigifConfig.isValidUrl(""));
        assertFalse(UnigifConfig.isValidUrl("ftp://gifs.example.com"));
        assertFalse(UnigifConfig.isValidUrl("https://"));
        assertFalse(UnigifConfig.isValidUrl("https://gifs example.com"));
        assertFalse(UnigifConfig.isValidUrl("https://gifs.example.com?x=1"));
        assertFalse(UnigifConfig.isValidUrl("https://user:pw@gifs.example.com"));
    }

    @Test
    public void cleartext() {
        assertTrue(UnigifConfig.isCleartext("http://10.0.0.2"));
        assertTrue(UnigifConfig.isCleartext("HTTP://10.0.0.2"));
        assertFalse(UnigifConfig.isCleartext("https://gifs.example.com"));
        assertFalse(UnigifConfig.isCleartext(""));
    }

    @Test
    public void activeNeedsProviderUrlAndKey() {
        set(1, "gifs.example.com/", " key ");
        assertTrue(UnigifConfig.isActive());
        assertEquals("https://gifs.example.com", UnigifConfig.baseUrl());
        assertEquals("key", UnigifConfig.key());

        set(0, "gifs.example.com", "key");
        assertFalse(UnigifConfig.isActive());
        set(1, "", "key");
        assertFalse(UnigifConfig.isActive());
        set(1, "gifs.example.com", "  ");
        assertFalse(UnigifConfig.isActive());
        set(1, "ftp://gifs.example.com", "key");
        assertTrue(UnigifConfig.isSelected());
        assertFalse(UnigifConfig.isActive());
    }

    @Test
    public void parseMe() {
        Me me = UnigifClient.parseMe("{\"name\":\"phone\",\"scopes\":[\"read\",\"upload\"]}");
        assertEquals("phone", me.name);
        assertTrue(me.hasScope("read"));
        assertTrue(me.hasScope("upload"));
        assertFalse(me.hasScope("delete"));

        assertTrue(UnigifClient.parseMe("{\"scopes\":\"read delete\"}").hasScope("delete"));
        assertTrue(UnigifClient.parseMe("{}").scopes.isEmpty());
        assertNull(UnigifClient.parseMe("<html></html>"));
        assertNull(UnigifClient.parseMe("[]"));
        assertNull(UnigifClient.parseMe(""));
    }

    @Test
    public void parseError() {
        UnigifError error = UnigifClient.parseError(403, "{\"code\":\"invalid_credentials\",\"detail\":\"unknown key\"}");
        assertEquals(UnigifError.KIND_HTTP, error.kind);
        assertEquals("unknown key", error.detail);
        assertTrue(error.isKeyRejected());

        assertTrue(UnigifClient.parseError(401, "nope").isKeyRejected());
        UnigifError other = UnigifClient.parseError(500, "<html>");
        assertFalse(other.isKeyRejected());
        assertEquals(500, other.httpStatus);
        assertEquals("", other.code);
    }
}
