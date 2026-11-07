package app.retrogram

import tw.nekomimi.nekogram.config.ConfigItem

/**
 * Retrogram master switches.
 *
 * Items are plain NagramXF [ConfigItem]s, so they persist in the same
 * `nkmrcfg` preferences file and work with the existing settings cells.
 * Their `Bool()` value is live; [isRetroUi] is a cold-start snapshot.
 * Nothing registers them with `NaConfig`, which keeps the diff against NagramXF small.
 */
object RetroConfig {

    /** Old (pre-Liquid-Glass) UI. Default ON. Applied at next start only. */
    @JvmField
    val retroUi = ConfigItem("RetroUi", ConfigItem.configTypeBool, true)

    /** Debug-only escape hatch to the upstream UI. Hidden from settings. */
    @JvmField
    val upstreamUiDebug = ConfigItem("RetroUpstreamUiDebug", ConfigItem.configTypeBool, false)

    /** Remote update check / metadata-channel fetches (WP 0.2). Default OFF. */
    @JvmField
    val remoteMetadata = ConfigItem("RetroRemoteMetadata", ConfigItem.configTypeBool, false)

    /**
     * Open "Settings" as the classic profile-in-self-mode page of 12.1.1 instead of
     * `SettingsActivity`. On by default since the first device test; read live (no restart). Only has an effect in retro mode.
     */
    @JvmField
    val classicSettings = ConfigItem("RetroClassicSettings", ConfigItem.configTypeBool, true)

    /**
     * Experimental: the chat input bar as a flat, full-width bar flush with the bottom (12.1.1 geometry) instead of the
     * floating rounded "island". Off by default; read when a chat is created.
     */
    @JvmField
    val classicInput = ConfigItem("RetroClassicInput", ConfigItem.configTypeBool, false)

    /**
     * Experimental: the chat screen's top bar as the classic solid action bar (12.1.1) instead of the 12.4 floating glass pills,
     * and without the top/bottom fade overlay. Off by default; read when a chat is created.
     */
    @JvmField
    val classicChatHeader = ConfigItem("RetroClassicChatHeader", ConfigItem.configTypeBool, false)

    /**
     * Experimental: the emoji / GIF / sticker panel with its bottom tab strip as a full-width solid bar (12.1.1) instead of the
     * floating glass pills for the tabs, search and backspace buttons. Off by default; read when the panel is created.
     */
    @JvmField
    val classicEmojiPanel = ConfigItem("RetroClassicEmojiPanel", ConfigItem.configTypeBool, false)

    /**
     * Experimental: the 12.1.1 default-theme palette (blue action bar, white icons, ...) for the keys whose default
     * changed in 13.1.1 (see [RetroThemeColors]). Off by default; applied at process start, so it needs a restart.
     * `RetroThemeColors.enabled()` reads the stored value itself because `Theme` can load before [init].
     */
    @JvmField
    val classicColors = ConfigItem("RetroClassicColors", ConfigItem.configTypeBool, false)

    private val items = listOf(retroUi, upstreamUiDebug, remoteMetadata, classicSettings, classicInput, classicChatHeader, classicEmojiPanel, classicColors)

    // Snapshot taken once per process. Until init() runs, behave like the defaults.
    @Volatile
    private var retroUiAtStart = true

    /** Whether the old UI is active in this process. Hook point for Phases 1-4. */
    @JvmStatic
    fun isRetroUi(): Boolean = retroUiAtStart

    /** Whether the emoji panel uses the classic full-width bottom tab bar. */
    @JvmStatic
    fun isClassicEmojiPanel(): Boolean = retroUiAtStart && classicEmojiPanel.Bool()

    /** Whether the chat screen uses the classic solid action bar. */
    @JvmStatic
    fun isClassicChatHeader(): Boolean = retroUiAtStart && classicChatHeader.Bool()

    /** Whether the chat input bar uses the flat full-width geometry. */
    @JvmStatic
    fun isClassicInput(): Boolean = retroUiAtStart && classicInput.Bool()

    /** Whether Settings should open the classic self-mode profile page. */
    @JvmStatic
    fun isClassicSettings(): Boolean = retroUiAtStart && classicSettings.Bool()

    /** Called once from `ApplicationLoader.onCreate`, after `NaConfig.init()`. */
    @JvmStatic
    fun init() {
        val prefs = tw.nekomimi.nekogram.NekoConfig.getPreferences()
        load { key, default -> prefs.getBoolean(key, default) }
        captureColdStart()
        RetroShell.applyForcedConfig()
    }

    /** Reads every item through [read]. Separate from [init] so it runs without Android. */
    @JvmStatic
    fun load(read: (key: String, default: Boolean) -> Boolean) {
        for (item in items) {
            item.value = read(item.key, item.defaultValue as Boolean)
        }
    }

    /** Freezes [isRetroUi] to the current value of [retroUi]; [upstreamUiDebug] wins when set. */
    @JvmStatic
    fun captureColdStart() {
        retroUiAtStart = retroUi.Bool() && !upstreamUiDebug.Bool()
    }
}
