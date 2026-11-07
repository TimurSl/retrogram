package app.retrogram;

import org.telegram.ui.ActionBar.Theme;

/**
 * Default-theme colors of Telegram 12.1.1 for the keys whose default changed in 13.1.1 (the 12.4 redesign made the
 * default action bar white with dark icons; 12.1.1 had the blue bar with white icons).
 *
 * Generated from `git diff 9cbf0333 3de37c8c -- ActionBar/ThemeColors.java` (default table, keys present in both, literal values):
 * 123 keys. The trailing comment of each line is the 13.1.1 value. Applied to `Theme.defaultColors` while the
 * table is built, when Settings > Retrogram > "Classic theme colors" is on (read before RetroConfig is initialised,
 * so it reads the preference itself and fails safe).
 */
public final class RetroThemeColors {
    private RetroThemeColors() {}

    /** Preference key of RetroConfig.classicColors (same store as the other Retrogram switches). */
    private static final String PREF_KEY = "RetroClassicColors";

    /** Early, process-start read; any failure means "off". */
    public static boolean enabled() {
        try {
            final android.content.SharedPreferences p = tw.nekomimi.nekogram.NekoConfig.getPreferences();
            return p.getBoolean("RetroUi", true)
                    && !p.getBoolean("RetroUpstreamUiDebug", false)
                    && p.getBoolean(PREF_KEY, false);
        } catch (Throwable ignore) {
            return false;
        }
    }

    public static void apply(int[] c) {
        c[Theme.key_dialogTextBlack] = 0xff222222; // 13.1.1: 0xff1a1d21
        c[Theme.key_dialogIcon] = 0xff676b70; // 13.1.1: 0xff1a1d21
        c[Theme.key_dialogTopBackground] = 0xff6fb2e5; // 13.1.1: 0xff298acf
        c[Theme.key_dialogInputFieldActivated] = 0xff37a9f0; // 13.1.1: 0xff229af0
        c[Theme.key_dialogCheckboxSquareBackground] = 0xff43a0df; // 13.1.1: 0xff229af0
        c[Theme.key_dialogRadioBackgroundChecked] = 0xff37a9f0; // 13.1.1: 0xff229af0
        c[Theme.key_dialogButton] = 0xff4991cc; // 13.1.1: 0xff298acf
        c[Theme.key_dialogRoundCheckBox] = 0xff4cb4f5; // 13.1.1: 0xff229af0
        c[Theme.key_dialogFloatingButton] = 0xff4cb4f5; // 13.1.1: 0xff229af0
        c[Theme.key_dialogReactionMentionBackground] = 0xfff05459; // 13.1.1: 0xffeb5e5e
        c[Theme.key_windowBackgroundUnchecked] = 0xff9da7b1; // 13.1.1: 0xff96a2ad
        c[Theme.key_windowBackgroundChecked] = 0xff579ed9; // 13.1.1: 0xff229af0
        c[Theme.key_windowBackgroundWhiteGrayIcon] = 0xff81868b; // 13.1.1: 0xff1a1d21
        c[Theme.key_windowBackgroundWhiteGrayText] = 0xff838c96; // 13.1.1: 0xff808384
        c[Theme.key_windowBackgroundWhiteBlackText] = 0xff222222; // 13.1.1: 0xff1a1d21
        c[Theme.key_windowBackgroundWhiteValueText] = 0xff3a95d5; // 13.1.1: 0xff298acf
        c[Theme.key_windowBackgroundWhiteLinkText] = 0xff2678b6; // 13.1.1: 0xff298acf
        c[Theme.key_windowBackgroundWhiteBlueHeader] = 0xff3a95d5; // 13.1.1: 0xff298acf
        c[Theme.key_windowBackgroundWhiteInputFieldActivated] = 0xff37a9f0; // 13.1.1: 0xff229af0
        c[Theme.key_switchTrack] = 0xffb0b5ba; // 13.1.1: 0xffa6adb3
        c[Theme.key_switchTrackChecked] = 0xff52ade9; // 13.1.1: 0xff229af0
        c[Theme.key_switchTrackBlue] = 0xff828e99; // 13.1.1: 0xff78828a
        c[Theme.key_switchTrackBlueChecked] = 0xff3c88c7; // 13.1.1: 0xff1079c4
        c[Theme.key_switch2TrackChecked] = 0xff52ade9; // 13.1.1: 0xff229af0
        c[Theme.key_checkboxSquareBackground] = 0xff43a0df; // 13.1.1: 0xff229af0
        c[Theme.key_radioBackgroundChecked] = 0xff37a9f0; // 13.1.1: 0xff229af0
        c[Theme.key_windowBackgroundGray] = 0xfff0f0f0; // 13.1.1: 0xfff1f1f3
        c[Theme.key_emptyListPlaceholder] = 0xff959595; // 13.1.1: 0xff73787b
        c[Theme.key_fastScrollActive] = 0xff52a3db; // 13.1.1: 0xff229af0
        c[Theme.key_avatar_backgroundArchivedHidden] = 0xff66bffa; // 13.1.1: 0xff229af0
        c[Theme.key_avatar_backgroundInProfileBlue] = 0xff5085b1; // 13.1.1: 0xffffffff
        c[Theme.key_avatar_backgroundActionBarBlue] = 0xff598fba; // 13.1.1: 0xfff5f5f5
        c[Theme.key_avatar_subtitleInProfileBlue] = 0xffd7eafa; // 13.1.1: 0xff1a1d21
        c[Theme.key_avatar_actionBarSelectorBlue] = 0xff4981ad; // 13.1.1: 0x121a1d21
        c[Theme.key_avatar_actionBarIconBlue] = 0xffffffff; // 13.1.1: 0xff1a1d21
        c[Theme.key_actionBarDefault] = 0xff527da3; // 13.1.1: 0xffffffff
        c[Theme.key_actionBarDefaultIcon] = 0xffffffff; // 13.1.1: 0xff1a1d21
        c[Theme.key_actionBarActionModeDefaultIcon] = 0xff676a6f; // 13.1.1: 0xff1a1d21
        c[Theme.key_actionBarDefaultTitle] = 0xffffffff; // 13.1.1: 0xff1a1d21
        c[Theme.key_actionBarDefaultSubtitle] = 0xffd5e8f7; // 13.1.1: 0xff79817e
        c[Theme.key_actionBarDefaultSelector] = 0xff406d94; // 13.1.1: 0x121a1d21
        c[Theme.key_actionBarWhiteSelector] = 0x1d000000; // 13.1.1: 0x121a1d21
        c[Theme.key_actionBarDefaultSearch] = 0xffffffff; // 13.1.1: 0xff1a1d21
        c[Theme.key_actionBarDefaultSearchPlaceholder] = 0x88ffffff; // 13.1.1: 0xff79817e
        c[Theme.key_actionBarDefaultSubmenuItem] = 0xff222222; // 13.1.1: 0xff1a1d21
        c[Theme.key_actionBarDefaultSubmenuItemIcon] = 0xff676b70; // 13.1.1: 0xff1a1d21
        c[Theme.key_actionBarTabActiveText] = 0xffffffff; // 13.1.1: 0xff298acf
        c[Theme.key_actionBarTabUnactiveText] = 0xffd5e8f7; // 13.1.1: 0xff777c7f
        c[Theme.key_actionBarTabLine] = 0xffffffff; // 13.1.1: 0xff298acf
        c[Theme.key_actionBarTabSelector] = 0xff406d94; // 13.1.1: 0x121a1d21
        c[Theme.key_actionBarDefaultArchived] = 0xff6f7a87; // 13.1.1: 0xffffffff
        c[Theme.key_actionBarDefaultArchivedSelector] = 0xff5e6772; // 13.1.1: 0x121a1d21
        c[Theme.key_actionBarDefaultArchivedIcon] = 0xffffffff; // 13.1.1: 0xff1a1d21
        c[Theme.key_actionBarDefaultArchivedTitle] = 0xffffffff; // 13.1.1: 0xff1a1d21
        c[Theme.key_actionBarDefaultArchivedSearch] = 0xffffffff; // 13.1.1: 0xff1a1d21
        c[Theme.key_actionBarDefaultArchivedSearchPlaceholder] = 0x88ffffff; // 13.1.1: 0xff838c96
        c[Theme.key_chats_unreadCounter] = 0xff4ecc5e; // 13.1.1: 0xff229af0
        c[Theme.key_chats_unreadCounterMuted] = 0xffc6c9cc; // 13.1.1: 0xffbec3c7
        c[Theme.key_chats_archiveBackground] = 0xff66a9e0; // 13.1.1: 0xff229af0
        c[Theme.key_chats_name] = 0xff222222; // 13.1.1: 0xff1a1d21
        c[Theme.key_chats_nameArchived] = 0xff525252; // 13.1.1: 0xff1a1d21
        c[Theme.key_chats_pinnedIcon] = 0xffa8a8a8; // 13.1.1: 0xff919294
        c[Theme.key_chats_message] = 0xff8b8d8f; // 13.1.1: 0xff75787a
        c[Theme.key_chats_nameMessage] = 0xff3c7eb0; // 13.1.1: 0xff298acf
        c[Theme.key_chats_attachMessage] = 0xff3c7eb0; // 13.1.1: 0xff298acf
        c[Theme.key_chats_actionMessage] = 0xff3c7eb0; // 13.1.1: 0xff298acf
        c[Theme.key_chats_date] = 0xff95999c; // 13.1.1: 0xff848688
        c[Theme.key_chats_archivePullDownBackgroundActive] = 0xff66a9e0; // 13.1.1: 0xff229af0
        c[Theme.key_chat_attachCheckBoxBackground] = 0xff4cb4f5; // 13.1.1: 0xff229af0
        c[Theme.key_chat_status] = 0xffd5e8f7; // 13.1.1: 0xff298acf
        c[Theme.key_chat_lockIcon] = 0xffffffff; // 13.1.1: 0xff222222
        c[Theme.key_chat_muteIcon] = 0xffb1cce3; // 13.1.1: 0xff79817e
        c[Theme.key_chat_inInstant] = 0xff3a8ccf; // 13.1.1: 0xff298acf
        c[Theme.key_chat_inPreviewInstantText] = 0xff3a8ccf; // 13.1.1: 0xff298acf
        c[Theme.key_chat_inViaBotNameText] = 0xff3a8ccf; // 13.1.1: 0xff298acf
        c[Theme.key_chat_inReplyNameText] = 0xff3a8ccf; // 13.1.1: 0xff298acf
        c[Theme.key_chat_inSiteNameText] = 0xff3a8ccf; // 13.1.1: 0xff298acf
        c[Theme.key_chat_inAudioSeekbarFill] = 0xff72b5e8; // 13.1.1: 0xff229af0
        c[Theme.key_chat_inVoiceSeekbarFill] = 0xff72b5e8; // 13.1.1: 0xff229af0
        c[Theme.key_chat_inFileNameText] = 0xff4e9ad4; // 13.1.1: 0xff298acf
        c[Theme.key_chat_botKeyboardButtonText] = 0xff36474f; // 13.1.1: 0xf0444444
        c[Theme.key_chat_botKeyboardButtonBackground] = 0xffe4e7e9; // 13.1.1: 0x66bcc3c8
        c[Theme.key_chat_botKeyboardButtonBackgroundPressed] = 0xffccd1d4; // 13.1.1: 0x66808c94
        c[Theme.key_chat_inContactBackground] = 0xff72b5e8; // 13.1.1: 0xff229af0
        c[Theme.key_chat_fieldOverlayText] = 0xff3a8ccf; // 13.1.1: 0xff298acf
        c[Theme.key_chat_replyPanelIcons] = 0xff57a8e6; // 13.1.1: 0xff229af0
        c[Theme.key_chat_replyPanelName] = 0xff3a8ccf; // 13.1.1: 0xff298acf
        c[Theme.key_chat_messagePanelHint] = 0xffa4acb3; // 13.1.1: 0xff858a84
        c[Theme.key_chat_messagePanelSend] = 0xff62b0eb; // 13.1.1: 0xff229af0
        c[Theme.key_chat_gifSaveHintBackground] = 0xcc111111; // 13.1.1: 0xe21f2b38
        c[Theme.key_chat_goDownButtonCounterBackground] = 0xff4da2e8; // 13.1.1: 0xff229af0
        c[Theme.key_chat_messagePanelVoiceBackground] = 0xff5da6de; // 13.1.1: 0xff229af0
        c[Theme.key_chat_topPanelClose] = 0xff8b969b; // 13.1.1: 0xff818786
        c[Theme.key_chat_topPanelLine] = 0xff6c9fd2; // 13.1.1: 0xff3fa8ef
        c[Theme.key_chat_topPanelTitle] = 0xff3a8ccf; // 13.1.1: 0xff298acf
        c[Theme.key_chat_topPanelMessage] = 0xff878e91; // 13.1.1: 0xff767e7c
        c[Theme.key_chat_addContact] = 0xff4a82b5; // 13.1.1: 0xff298acf
        c[Theme.key_chat_inLoader] = 0xff72b5e8; // 13.1.1: 0xff229af0
        c[Theme.key_profile_actionPressedBackground] = 0xfff2f2f2; // 13.1.1: 0x121a1d21
        c[Theme.key_profile_verifiedBackground] = 0xffb2d6f8; // 13.1.1: 0xff229af0
        c[Theme.key_profile_verifiedCheck] = 0xff4983b8; // 13.1.1: 0xffffffff
        c[Theme.key_profile_title] = 0xffffffff; // 13.1.1: 0xff222222
        c[Theme.key_profile_status] = 0xffd7eafa; // 13.1.1: 0xff222222
        c[Theme.key_featuredStickers_addedIcon] = 0xff50a8eb; // 13.1.1: 0xff229af0
        c[Theme.key_featuredStickers_addButton] = 0xff50a8eb; // 13.1.1: 0xff229af0
        c[Theme.key_featuredStickers_addButtonPressed] = 0xff439bde; // 13.1.1: 0xff2288d1
        c[Theme.key_inappPlayerPerformer] = 0xff2f3438; // 13.1.1: 0xff1a1d21
        c[Theme.key_inappPlayerTitle] = 0xff2f3438; // 13.1.1: 0xff1a1d21
        c[Theme.key_inappPlayerPlayPause] = 0xff62b0eb; // 13.1.1: 0xff229af0
        c[Theme.key_inappPlayerClose] = 0xff8b969b; // 13.1.1: 0xff898b86
        c[Theme.key_changephoneinfo_image2] = 0xff50a7ea; // 13.1.1: 0xff229af0
        c[Theme.key_chat_BlurAlpha] = 0xff000000; // 13.1.1: 0xb2000000
        c[Theme.key_voipgroup_windowBackgroundWhiteInputFieldActivated] = 0xff37a9f0; // 13.1.1: 0xff229af0
        c[Theme.key_chat_inReactionButtonBackground] = 0xff72b5e8; // 13.1.1: 0xff229af0
        c[Theme.key_chat_inReactionButtonText] = 0xff3a8ccf; // 13.1.1: 0xff298acf
        c[Theme.key_stories_circle1] = 0xff39df3c; // 13.1.1: 0xff2c9efc
        c[Theme.key_stories_circle2] = 0xff4dbbff; // 13.1.1: 0xff2fc183
        c[Theme.key_stories_circle_dialog1] = 0xff4aed55; // 13.1.1: 0xff2c9efc
        c[Theme.key_stories_circle_dialog2] = 0xff4dc3ff; // 13.1.1: 0xff2fc183
        c[Theme.key_stories_circle_closeFriends1] = 0xffc9eb38; // 13.1.1: 0xff81ce2d
        c[Theme.key_stories_circle_closeFriends2] = 0xff09c167; // 13.1.1: 0xff18bd36
        c[Theme.key_code_string] = 0xff61c752; // 13.1.1: 0xff37c123
        c[Theme.key_iv_ab_progress] = 0xff50a8eb; // 13.1.1: 0xff229af0
    }
}
