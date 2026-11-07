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
| Rich-text / Instant View editor | 12.8.0 | `iv/RichMediaCell` (47 classes in `iv/`) | ? | ? | ? | |
| AI editor | 12.6.0 | `Components/AIEditorAlert` | `ChatActivityEnterView` (2 sites), `CaptionPhotoViewer` | reachable (unverified) | ? | Phase 2 must keep it reachable from the classic input bar |
| Communities | 12.9.0 | `community/CommunityCreateActivity`, `CommunitySheet`, `CommunityPendingRequestsActivity` | `ChatEditActivity` (add group/channel to community), `DialogsActivity` pending-requests panel, `CommunityEditActivity` | reachable (unverified) | ? | no top-level browse entry found yet; check `DialogsActivity` `communityId` flow |
| Live stories + live comments | 12.2.0 | `Stories/LivePlayer`, `LiveCommentsView` | stories | ? | n/a | |
| Paid reactions on stories / highlight message | 12.2.0 | `Stories/PaidReactionButton`, `HighlightMessageSheet` | stories | ? | n/a | |
| Story mode tabs (recorder) | 12.2.0 | `Stories/recorder/StoryModeTabs` | story camera FAB | ? | ? | Phase 1 FAB role swap (WP 1.3) touches this |
| Gift auctions | 12.2.0 | `Gifts/AuctionBidSheet`, `AuctionJoinSheet`, `ActiveAuctionsSheet`, `Cells/ActiveGiftAuctionsHintCell` | chat-list hint cell, gift sheets | ? | ? | `DialogsActivity` has `activeGiftAuctionsHintCell` |
| Gift offers | 12.2.8 | `Stars/GiftOfferSheet` | `ChatActionCell` (accept offer), `StarGiftSheet` | reachable (unverified) | ? | opened from chat/gift UI, not the shell |
| Gift crafting | 12.4.0 | `Stars/CraftGiftSheet` | no reference outside the class found | ? | ? | probably reached through `StarGiftSheet`; confirm by reading its crafting flow |
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
| Web proxy transport | 12.10.4 | `utils/proxy/WebProxyTransport` | Settings → Proxy | ? | n/a | |
| Android Auto | 12.8.0 | `messenger/car/*` | n/a | n/a | n/a | no UI in the app |
| Wear OS auth | 12.8.0 | `WearAuthSheet` | system-triggered (`WearAuthListenerService`) | n/a | ? |  |
| Emoji attach layout | 12.6.0 | `Components/ChatAttachAlertEmojiLayout` | `ChatAttachAlert` (layouts 8 and 9: stickers / emoji) | reachable (unverified) | ? |  |
| Select chat/user sheet | 12.4.0 | `SelectChatUserSheet` | `AlertsCreator` | reachable (unverified) | ? |  |
| Summaries | ? | `Components/SummarizeDrawable` | `ChatMessageCell`, `ChatActivity`, `ItemOptions` (message/chat menus) | reachable (unverified) | ? | version not derived: class predates the added-file list or was modified, not added |
| Stories albums | ? | (no new class) | `SharedMediaLayout`, `StoriesController`, `ProfileActivity` | reachable (unverified) | ? | |

## Glass-only chrome (not features, but the retro shell replaces them)

`MainTabsActivity`/`MainTabsLayout`/`MainTabsActivityController` (12.4.0), `ProfileActivity2` (12.4.0),
`Components/DialogsActivityStatusLayout`, `DialogsActivityTopPanelLayout`, `ChatActivityTopPanelLayout`,
`chat/layouts/ChatActivity{Actions,Channel,SideControls}ButtonsLayout`, `SearchTabsAndFiltersLayout`, `utils/glass/*`.

## NagramXF-only features that must stay reachable

Ghost mode, recent chats, bookmarks manager, in-app browser, devices, feed, N-settings: all are drawer entries
(`DrawerMenuHelper.entries`). AyuGram options (saved deleted messages and so on) live under N-settings.

## Still to do for WP 5.1

1. Walk the remaining `?` rows (gift crafting, rich/IV editor, Live stories entry points, Telegram Wallet fragments). Then diff `TLRPC.java` layer 216 → 232 (new constructors) for user-visible features missing from the file list above (summaries, albums, ...).
2. Most entry points live *inside* screens that Phases 2 and 3 re-skin (chat, profile, Settings, Privacy): the Phase 3 checklist must keep every row listed above.
3. Add per-release notes from Telegram's public changelogs.
