// SPDX-License-Identifier: GPL-3.0-or-later
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
     * Experimental: the chat's round buttons (page down, mentions, reactions, reply/forward bar, channel buttons) as solid circles with a
     * soft shadow (12.1.1) instead of glass circles. Off by default; read when a button is created, so it applies to chats opened afterwards.
     */
    @JvmField
    val classicRoundButtons = ConfigItem("RetroClassicRoundButtons", ConfigItem.configTypeBool, false)

    /**
     * Experimental: the shared-media tab strip of profiles (Media, Files, Links, ...) as the flat full-width strip with an underline selector (12.1.1)
     * instead of the 12.4 rounded floating pill. Off by default; read when a profile is created.
     */
    @JvmField
    val classicProfileTabs = ConfigItem("RetroClassicProfileTabs", ConfigItem.configTypeBool, false)

    /**
     * Experimental: bottom sheets with flat full-width bars instead of floating rounded pills (WP 4.2). First covered: the share sheet
     * (caption bar, "Send" bar, timestamp bar, keyboard panel corners). Off by default; read when a sheet is created.
     */
    @JvmField
    val classicSheets = ConfigItem("RetroClassicSheets", ConfigItem.configTypeBool, false)

    /**
     * Experimental: profile headers (own settings page, other users, groups, channels) in the plain theme colors of 12.1.1 instead of the
     * person's or chat's profile color / collectible-gift background (WP 3.2). Off by default; read when a profile loads its data.
     */
    @JvmField
    val plainProfileHeader = ConfigItem("RetroPlainProfileHeader", ConfigItem.configTypeBool, false)

    /**
     * Compliance (PLAN.md section 4): bypass "restrict saving content". NagramXF ignores a group's or channel's no-forwards flag unconditionally and re-sends
     * protected, self-destructing and paid media through its "AyuForward" engine; Retrogram ships that **off**: protected content cannot be forwarded, copied or saved
     * (stock Telegram behaviour) unless this is switched on, and turning it on shows a warning that the account can be limited. Applies to chats opened afterwards.
     */
    @JvmField
    val bypassSavingRestrictions = ConfigItem("RetroBypassSavingRestrictions", ConfigItem.configTypeBool, false)

    /**
     * Privacy: send usage statistics to Telegram. Telegram 13.x reports, in groups and channels, how long each message was on screen, for how long you
     * were active and how much of the screen it took (`messages.reportReadMetrics`), and how long you listened to music (`messages.reportMusicListen`).
     * Off by default in Retrogram: nothing of that is sent unless this is on. Applies to chats opened and tracks played afterwards.
     */
    @JvmField
    val sendUsageStats = ConfigItem("RetroSendUsageStats", ConfigItem.configTypeBool, false)

    /**
     * Experimental: the 12.1.1 default-theme palette (blue action bar, white icons, ...) for the keys whose default
     * changed in 13.1.1 (see [RetroThemeColors]). Off by default; applied at process start, so it needs a restart.
     * `RetroThemeColors.enabled()` reads the stored value itself because `Theme` can load before [init].
     */
    @JvmField
    val classicColors = ConfigItem("RetroClassicColors", ConfigItem.configTypeBool, false)

    /**
     * Graph features (Phase 6), all off by default. Local auto-reply (WP 6.3): answers a first private message from each
     * sender once per 6 hours with [graphAutoReplyText], only while the app is not in the foreground, never in groups, to bots or to old messages.
     */
    @JvmField
    val graphAutoReply = ConfigItem("RetroGraphAutoReply", ConfigItem.configTypeBool, false)

    /** Only messages containing one of these words (comma separated, case-insensitive) get the auto-reply; empty = every message. A string item, read in [init]. */
    @JvmField
    val graphAutoReplyKeywords = ConfigItem("RetroGraphAutoReplyKeywords", ConfigItem.configTypeString, "")

    /** Auto-reply schedule in whole hours (start inclusive, end exclusive, may wrap midnight); equal hours = always. Int items, read in [init]. */
    @JvmField
    val graphAutoReplyStart = ConfigItem("RetroGraphAutoReplyStart", ConfigItem.configTypeInt, 0)

    @JvmField
    val graphAutoReplyEnd = ConfigItem("RetroGraphAutoReplyEnd", ConfigItem.configTypeInt, 0)

    /** Index into [1, 3, 6, 12, 24] hours: at most one auto-reply per person in that time. Default 2 = 6 hours. */
    @JvmField
    val graphAutoReplyCooldown = ConfigItem("RetroGraphAutoReplyCooldown", ConfigItem.configTypeInt, 2)

    /** Text sent by the auto-reply. Empty text disables it even when [graphAutoReply] is on. A string item, so it is read in [init], not in [load]. */
    @JvmField
    val graphAutoReplyText = ConfigItem("RetroGraphAutoReplyText", ConfigItem.configTypeString, "")

    /**
     * Special contacts change log (WP 6.6): records online/offline, name, username, photo and phone changes of the users in
     * [graphSpecialContactIds] that Telegram already sends updates for. Off by default; the log stays on the device.
     */
    @JvmField
    val graphSpecialContacts = ConfigItem("RetroGraphSpecialContacts", ConfigItem.configTypeBool, false)

    /** Also show an Android notification for each recorded change of a watched contact. Off by default. */
    @JvmField
    val graphSpecialContactsNotify = ConfigItem("RetroGraphSpecialContactsNotify", ConfigItem.configTypeBool, false)

    /** Comma-separated numeric user ids to watch. A string item, read in [init]. */
    @JvmField
    val graphSpecialContactIds = ConfigItem("RetroGraphSpecialContactIds", ConfigItem.configTypeString, "")

    /**
     * Per-chat lock (WP 6.1): a PIN gate in front of chats you lock from the chat menu, notification text hidden for them. Off by default.
     * It does not encrypt messages and does not hide the chat from the chat list.
     */
    @JvmField
    val graphChatLock = ConfigItem("RetroGraphChatLock", ConfigItem.configTypeBool, false)

    /**
     * Hidden chats (WP 6.2): chats hidden from the chat list until revealed with the chat-lock PIN. Off by default.
     */
    @JvmField
    val graphHiddenChats = ConfigItem("RetroGraphHiddenChats", ConfigItem.configTypeBool, false)

    /**
     * Voice changer (WP 6.4): 0 off, 1 robot, 2 echo, 3 higher, 4 deeper. Applied to voice notes while recording (read when a recording starts). An int item, read in [init].
     */
    @JvmField
    val graphVoiceEffect = ConfigItem("RetroGraphVoiceEffect", ConfigItem.configTypeInt, 0)

    /**
     * Large files on Wi-Fi only (WP 6.5 remainder): explicit downloads of files of 25 MB or more wait for Wi-Fi. Off by default.
     */
    @JvmField
    val graphWifiOnlyDownloads = ConfigItem("RetroGraphWifiOnlyDownloads", ConfigItem.configTypeBool, false)

    /**
     * Download window for large files (WP 6.5): explicit downloads of 25 MB or more wait until the clock is inside the daily window
     * [graphDownloadWindowStart]:00 to [graphDownloadWindowEnd]:00 (may wrap midnight; equal hours mean all day). Off by default. Hours are int items, read in [init].
     */
    @JvmField
    val graphDownloadWindow = ConfigItem("RetroGraphDownloadWindow", ConfigItem.configTypeBool, false)

    @JvmField
    val graphDownloadWindowStart = ConfigItem("RetroGraphDownloadWindowStart", ConfigItem.configTypeInt, 1)

    @JvmField
    val graphDownloadWindowEnd = ConfigItem("RetroGraphDownloadWindowEnd", ConfigItem.configTypeInt, 6)

    private val items = listOf(retroUi, upstreamUiDebug, remoteMetadata, classicSettings, classicInput, classicChatHeader, classicEmojiPanel, classicRoundButtons, classicProfileTabs, classicSheets, plainProfileHeader, sendUsageStats, bypassSavingRestrictions, classicColors, graphAutoReply, graphSpecialContacts, graphSpecialContactsNotify, graphChatLock, graphHiddenChats, graphWifiOnlyDownloads, graphDownloadWindow)

    // Snapshot taken once per process. Until init() runs, behave like the defaults.
    @Volatile
    private var retroUiAtStart = true

    /** Whether the old UI is active in this process. Hook point for Phases 1-4. */
    @JvmStatic
    fun isRetroUi(): Boolean = retroUiAtStart

    /** Whether the emoji panel uses the classic full-width bottom tab bar. */
    @JvmStatic
    fun isClassicEmojiPanel(): Boolean = retroUiAtStart && classicEmojiPanel.Bool()

    /** Whether the chat's round buttons are solid circles instead of glass. */
    @JvmStatic
    fun isClassicRoundButtons(): Boolean = retroUiAtStart && classicRoundButtons.Bool()

    /** Whether profile headers ignore profile colors and use the theme's colors. */
    @JvmStatic
    fun isPlainProfileHeader(): Boolean = retroUiAtStart && plainProfileHeader.Bool()

    /** Whether bottom sheets use flat full-width bars instead of floating pills. */
    @JvmStatic
    fun isClassicSheets(): Boolean = retroUiAtStart && classicSheets.Bool()

    /** Whether profiles use the flat full-width shared-media tab strip. */
    @JvmStatic
    fun isClassicProfileTabs(): Boolean = retroUiAtStart && classicProfileTabs.Bool()

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
        graphAutoReplyText.value = prefs.getString(graphAutoReplyText.key, "") ?: ""
        graphSpecialContactIds.value = prefs.getString(graphSpecialContactIds.key, "") ?: ""
        graphAutoReplyKeywords.value = prefs.getString(graphAutoReplyKeywords.key, "") ?: ""
        graphAutoReplyStart.value = prefs.getInt(graphAutoReplyStart.key, 0)
        graphAutoReplyEnd.value = prefs.getInt(graphAutoReplyEnd.key, 0)
        graphAutoReplyCooldown.value = prefs.getInt(graphAutoReplyCooldown.key, 2)
        graphVoiceEffect.value = prefs.getInt(graphVoiceEffect.key, 0)
        graphDownloadWindowStart.value = prefs.getInt(graphDownloadWindowStart.key, 1)
        graphDownloadWindowEnd.value = prefs.getInt(graphDownloadWindowEnd.key, 6)
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
