package app.retrogram;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class RetroConfigTest {

    private final Map<String, Boolean> store = new HashMap<>();

    private void reload() {
        RetroConfig.load((key, def) -> store.getOrDefault(key, def));
    }

    @Before
    public void setUp() {
        store.clear();
        reload();
        RetroConfig.captureColdStart();
    }

    @Test
    public void defaults() {
        assertTrue(RetroConfig.retroUi.Bool());
        assertFalse(RetroConfig.upstreamUiDebug.Bool());
        assertFalse(RetroConfig.remoteMetadata.Bool());
        assertTrue(RetroConfig.classicSettings.Bool());
        assertTrue(RetroConfig.isClassicSettings());
        assertFalse(RetroConfig.classicInput.Bool());
        assertFalse(RetroConfig.isClassicInput());
        assertFalse(RetroConfig.classicColors.Bool());
        assertFalse(RetroConfig.classicChatHeader.Bool());
        assertFalse(RetroConfig.isClassicChatHeader());
        assertFalse(RetroConfig.classicEmojiPanel.Bool());
        assertTrue(RetroConfig.isRetroUi());
    }

    @Test
    public void storedValuesSurviveReload() {
        store.put(RetroConfig.retroUi.getKey(), false);
        store.put(RetroConfig.remoteMetadata.getKey(), true);
        reload();
        assertFalse(RetroConfig.retroUi.Bool());
        assertTrue(RetroConfig.remoteMetadata.Bool());
        RetroConfig.captureColdStart();
        assertFalse(RetroConfig.isRetroUi());
    }

    @Test
    public void uiModeChangesOnlyAfterRestart() {
        store.put(RetroConfig.retroUi.getKey(), false);
        reload();
        assertTrue("snapshot must not follow live changes", RetroConfig.isRetroUi());
        RetroConfig.captureColdStart();
        assertFalse(RetroConfig.isRetroUi());
    }

    @Test
    public void upstreamDebugOverridesRetroUi() {
        store.put(RetroConfig.upstreamUiDebug.getKey(), true);
        reload();
        RetroConfig.captureColdStart();
        assertFalse(RetroConfig.isRetroUi());
    }
}
