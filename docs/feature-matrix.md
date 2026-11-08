# Feature matrix: Telegram 12.1.1 → 13.1.1 (WP 5.1, first cut)

Seeded on 2026-11-07, entry points traced by grep the same day (launch sites only: nothing was run) from `git -C upstream/telegram diff --diff-filter=A --name-only 9cbf0333 3de37c8c`
(417 added Java files). "Since" is the first `update to X` commit that adds the representative class
(`git log --diff-filter=A`). Entry points are **not verified yet**: the columns say what is known; `?` means
"find the launch code and fill this in" (WP 5.2). Status values: `reachable` / `?` / `n/a`.

Retro-mode entry points land in the drawer (WP 1.4), the chat-list overflow menu, the attach menu or settings.
Nothing here has been run on a device.

| Feature | Since | Representative class | Retro entry point | Reachable | Old-styled | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| Gram Wallet (TON) | 13.0.0 | `Wallet/WalletActivity` | Drawer "Wallet" (added WP 1.4, shown when `config.walletAvailable`) | reachable (unverified) | ? | also `WalletTonConnectSessionsFragment`, import/backup/secret-phrase fragments |
| Wallet in attach menu | 13.0.0 | `Components/ChatAttachAlertGramLayout` | `ChatAttachAlert` (layout 11) | reachable (unverified) | ? | Phase 4 sweep covers the layout |
| QR share / scan | 13.1.0 | `QrShare/QrShareActivity`, `QrScanOverlay` | `ProfileActivity` (menu/buttons, 3 sites: `new QrShareActivity`) | reachable (unverified) | ? | Phase 3 must keep these profile entries; NagramXF also has `nkbtnQrLogin` (import login) in the drawer |
| Rich-text / Instant View editor | 12.8.0 | `iv/RichMediaCell` (47 classes in `iv/`) | `RichEditor` is created in `ChatActivityEnterView` (rich-text button of the input bar) and `ChatActivity` | reachable (unverified) | ? | traced 2026-11-08; the classic input bar keeps the button (WP 2.1a spike) |
| AI editor | 12.6.0 | `Components/AIEditorAlert` | `ChatActivityEnterView` (2 sites), `CaptionPhotoViewer` | reachable (unverified) | ? | Phase 2 must keep it reachable from the classic input bar |
| Communities | 12.9.0 | `community/CommunityCreateActivity`, `CommunitySheet`, `CommunityPendingRequestsActivity` | per-community, no global list: `CommunitySheet` opens from a linked community in `DialogCell`, `ChatActivity`, `ChatActionCell`; a community's chat list is `DialogsActivity` with arg `community_id`; create from `ChatEditActivity`; pending requests from the chat-list panel | reachable (unverified) | ? | no top-level Communities screen exists in 13.1.1, so there is nothing to put in the drawer (WP 1.4 item closed as n/a) |
| Live stories + live comments | 12.2.0 | `Stories/LivePlayer`, `LiveCommentsView` | `StoryViewer`, `PeerStoriesView` (viewer and comments), `StoryRecorder` (go live), `FragmentContextView` (live bar), `LiveStoryPipOverlay` | reachable (unverified) | n/a | traced 2026-11-08; all inside the stories UI, not the shell |
| Paid reactions on stories / highlight message | 12.2.0 | `Stories/PaidReactionButton`, `HighlightMessageSheet` | `PeerStoriesView` (button), `LiveCommentsView`, `StarsReactionsSheet`, `AuctionBidSheet`, `ChatActivityEnterView`, `MessagesController` | reachable (unverified) | n/a | traced 2026-11-08 |
| Story mode tabs (recorder) | 12.2.0 | `Stories/recorder/StoryModeTabs` | `StoryRecorder` (opened by the story camera FAB) | reachable (unverified) | ? | traced 2026-11-08; Phase 1 FAB role swap (WP 1.3) opens the recorder |
| Gift auctions | 12.2.0 | `Gifts/AuctionBidSheet`, `AuctionJoinSheet`, `ActiveAuctionsSheet`, `Cells/ActiveGiftAuctionsHintCell` | chat-list hint cell (`DialogsActivity.activeGiftAuctionsHintCell` → `ActiveAuctionsSheet`), `GiftSheet`, `SendGiftSheet`, `AuctionWearingSheet`, deep links in `LaunchActivity` | reachable (unverified) | ? | traced 2026-11-08 |
| Gift offers | 12.2.8 | `Stars/GiftOfferSheet` | `ChatActionCell` (accept offer), `StarGiftSheet` | reachable (unverified) | ? | opened from chat/gift UI, not the shell |
| Gift crafting | 12.4.0 | `Stars/CraftGiftSheet` | `StarGiftSheet.openCrafting` (top-view button of a gift's sheet; no direct reference to the class outside the sheet) | reachable (unverified) | ? | traced 2026-11-08 |
| Acquired / selected gifts, gift messages | 12.2.0 / 12.10.x | `Gifts/AcquiredGiftsSheet`, `SelectGiftsBottomSheet`, `GiftMessageBottomSheet` | `AuctionBidSheet`/`AuctionJoinSheet` (acquired gifts); others from gift sheets | reachable (unverified) | ? |  |
| Polls v2 (options, stats, menus) | 12.6.0 / 12.7.0 | `PollItemMenu`, `Components/poll/*` (e.g. `PollStatisticsBottomSheet`) | attach menu → Poll; stats from `ChatActivity` (`PollStatisticsBottomSheet`) | reachable (unverified) | ? |  |
| Markdown parser / code highlighting | 13.0.2 | `utils/code/highlight/CodeHighlighting` | automatic | n/a | n/a | rendering only |
| OAuth sheet | 12.4.0 | `OAuthSheet` | link-driven: `LinkManager`, `BotWebViewContainer` | n/a (no menu entry) | ? |  |
| Passkeys | 12.2.8 | `PasskeysActivity` | `PrivacySettingsActivity`, `LinkManager` | reachable (unverified) | ? | Settings → Privacy; Phase 3 must keep the row |
| Chatbot (business) sheet | 12.8.0 | `Business/ChatbotSheet` | `SessionsActivity` (Devices) | reachable (unverified) | ? | drawer has a Devices entry (`nkbtnSessions`, hidden by default) |
| Create bot / guard-bot replace | 12.6.0 / 12.8.0 | `Components/CreateBotAlert`, `GuardBotReplaceSheet` | `ChatActivityEnterView`, `LinkManager`, `BotWebViewContainer`; guard bot: `ChatRightsEditActivity` | reachable (unverified) | ? |  |
| Staked dice | 12.3.0 | `StakedDiceSheet` | `ChatActivity` (dice message toast) | reachable (unverified) | n/a |  |
| Round video settings | 12.10.5 | `RoundVideoSettingsActivity` | `SettingsActivity` row (case 24) | reachable (unverified) | ? | Phase 3 checklist: keep the row in the classic Settings layout |
| Audio recorder settings | 13.0.3 | `AudioRecorderSettingsActivity` | `SettingsActivity` row (case 26) | reachable (unverified) | ? | Phase 3 checklist: keep the row in the classic Settings layout |
| Web proxy transport | 12.10.4 | `utils/proxy/WebProxyTransport` | Settings → Proxy (`ProxySettingsActivity`, `WebProxyConnectionTester`, `ConnectionsManager`) | reachable (unverified) | n/a | traced 2026-11-08 |
| Android Auto | 12.8.0 | `messenger/car/*` | n/a | n/a | n/a | no UI in the app |
| Wear OS auth | 12.8.0 | `WearAuthSheet` | system-triggered (`WearAuthListenerService`) | n/a | ? |  |
| Emoji attach layout | 12.6.0 | `Components/ChatAttachAlertEmojiLayout` | `ChatAttachAlert` (layouts 8 and 9: stickers / emoji) | reachable (unverified) | ? |  |
| Select chat/user sheet | 12.4.0 | `SelectChatUserSheet` | `AlertsCreator` | reachable (unverified) | ? |  |
| Summaries | ? | `Components/SummarizeDrawable` | `ChatMessageCell`, `ChatActivity`, `ItemOptions` (message/chat menus) | reachable (unverified) | ? | version not derived: class predates the added-file list or was modified, not added |
| Stories albums | ? | (no new class) | `SharedMediaLayout`, `StoriesController`, `ProfileActivity` | reachable (unverified) | ? | |
| Share theme (QR theme share) | 13.1.x | `messages.setShareTheme` / `getShareTheme` / `getShareThemeBackdrops` | `QrShare/QrShareServer` (the QR share screen), no other caller | reachable (unverified) | ? | found by the TL class-set diff 2026-11-08; reached through the QR share entries (profile, drawer QR entry) |
| Member tags / ranks in groups | 13.x | `Components/TagEditCell`, `messages.editChatParticipantRank` | `ChatRightsEditActivity` (admin and member rights editor) | reachable (unverified) | ? | TL diff 2026-11-08 |
| Delete a member's reactions | 13.x | `messages.deleteParticipantReaction(s)` | `MessagesController` (called from message/reaction menus) | reachable (unverified) | n/a | TL diff 2026-11-08 |
| Pay-to-message tokens | 12.x | `contacts.exportNoPaidMessagesToken` / `importNoPaidMessagesToken` | `QrShare/QrShareServer` | reachable (unverified) | n/a | TL diff 2026-11-08 |
| Ownership transfer on leave | 13.x | `channels.getFutureCreatorAfterLeave` | `AlertsCreator` (leave-group confirmation) | reachable (unverified) | n/a | TL diff 2026-11-08 |
| Join web view (bots) | 13.x | `messages.requestChatJoinWebView` | `bots/BotWebViewSheet` | reachable (unverified) | n/a | TL diff 2026-11-08 |
| Bot usernames management | 13.x | `bots.addUsername` / `removeUsername` | no Java caller outside `tgnet/` | not reachable in this tree (upstream UI missing) | n/a | TL diff 2026-11-08: request types exist, nothing in the UI calls them; nothing for Retrogram to port |
| Read metrics and music listening reports | 13.x | `messages.reportReadMetrics`, `messages.reportMusicListen` | `Components/chat/ChatActivityMessageMetricsView` (groups/channels), `MediaController` | **now off by default** | n/a | privacy: Retrogram gates both behind Settings → Retrogram → "Send usage statistics to Telegram" (off) |

## Glass-only chrome (not features, but the retro shell replaces them)

`MainTabsActivity`/`MainTabsLayout`/`MainTabsActivityController` (12.4.0), `ProfileActivity2` (12.4.0),
`Components/DialogsActivityStatusLayout`, `DialogsActivityTopPanelLayout`, `ChatActivityTopPanelLayout`,
`chat/layouts/ChatActivity{Actions,Channel,SideControls}ButtonsLayout`, `SearchTabsAndFiltersLayout`, `utils/glass/*`.

## NagramXF-only features that must stay reachable

Ghost mode, recent chats, bookmarks manager, in-app browser, devices, feed, N-settings: all are drawer entries
(`DrawerMenuHelper.entries`). AyuGram options (saved deleted messages and so on) live under N-settings.

## Still to do for WP 5.1

1. Done 2026-11-08: the `?` rows above were traced (launch sites by grep), and the TL layer diff was done by comparing the sets of `TL_*` classes in `org/telegram/tgnet` at 12.1.1 and 13.1.1 (306 new classes; the request types are listed in the new rows). Remaining: Telegram Wallet fragments, and everything still `unverified`, which needs a device (docs/device-test-checklist.md).
2. Most entry points live *inside* screens that Phases 2 and 3 re-skin (chat, profile, Settings, Privacy): the Phase 3 checklist must keep every row listed above.
3. Add per-release notes from Telegram's public changelogs.
