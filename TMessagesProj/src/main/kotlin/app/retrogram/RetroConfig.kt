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
     * Experimental: open "Settings" as the classic profile-in-self-mode page of 12.1.1 instead of
     * `SettingsActivity`. Off by default; read live (no restart). Only has an effect in retro mode.
     */
    @JvmField
    val classicSettings = ConfigItem("RetroClassicSettings", ConfigItem.configTypeBool, false)

    private val items = listOf(retroUi, upstreamUiDebug, remoteMetadata, classicSettings)

    // Snapshot taken once per process. Until init() runs, behave like the defaults.
    @Volatile
    private var retroUiAtStart = true

    /** Whether the old UI is active in this process. Hook point for Phases 1-4. */
    @JvmStatic
    fun isRetroUi(): Boolean = retroUiAtStart

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
