# Feature matrix: Telegram 12.1.1 → 13.1.1 (WP 5.1, first cut)

Seeded on 2026-11-07 from `git -C upstream/telegram diff --diff-filter=A --name-only 9cbf0333 3de37c8c`
(417 added Java files). "Since" is the first `update to X` commit that adds the representative class
(`git log --diff-filter=A`). Entry points are **not verified yet**: the columns say what is known; `?` means
"find the launch code and fill this in" (WP 5.2). Status values: `reachable` / `?` / `n/a`.

Retro-mode entry points land in the drawer (WP 1.4), the chat-list overflow menu, the attach menu or settings.
Nothing here has been run on a device.

| Feature | Since | Representative class | Retro entry point | Reachable | Old-styled | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| Gram Wallet (TON) | 13.0.0 | `Wallet/WalletActivity` | Drawer "Wallet" (added WP 1.4, shown when `config.walletAvailable`) | reachable (unverified) | ? | also `WalletTonConnectSessionsFragment`, import/backup/secret-phrase fragments |
| Wallet in attach menu | 13.0.0 | `Components/ChatAttachAlertGramLayout` | attach menu | ? | ? | Phase 4 sweep covers the layout |
| QR share / scan | 13.1.0 | `QrShare/QrShareActivity`, `QrScanOverlay` | ? (profile QR) | ? | ? | NagramXF also has `nkbtnQrLogin` (import login) in the drawer |
| Rich-text / Instant View editor | 12.8.0 | `iv/RichMediaCell` (47 classes in `iv/`) | ? | ? | ? | |
| AI editor | 12.6.0 | `Components/AIEditorAlert` | chat input menu | ? | ? | Phase 2 must keep it reachable from the classic input bar |
| Communities | 12.9.0 | `community/CommunityCreateActivity`, `CommunitySheet`, `CommunityPendingRequestsActivity` | ? | ? | ? | `DialogsActivity` has `communityId` handling; find the create/browse entry point |
| Live stories + live comments | 12.2.0 | `Stories/LivePlayer`, `LiveCommentsView` | stories | ? | n/a | |
| Paid reactions on stories / highlight message | 12.2.0 | `Stories/PaidReactionButton`, `HighlightMessageSheet` | stories | ? | n/a | |
| Story mode tabs (recorder) | 12.2.0 | `Stories/recorder/StoryModeTabs` | story camera FAB | ? | ? | Phase 1 FAB role swap (WP 1.3) touches this |
| Gift auctions | 12.2.0 | `Gifts/AuctionBidSheet`, `AuctionJoinSheet`, `ActiveAuctionsSheet`, `Cells/ActiveGiftAuctionsHintCell` | chat-list hint cell, gift sheets | ? | ? | `DialogsActivity` has `activeGiftAuctionsHintCell` |
| Gift offers | 12.2.8 | `Stars/GiftOfferSheet` | gift sheets | ? | ? | |
| Gift crafting | 12.4.0 | `Stars/CraftGiftSheet` | gift sheets | ? | ? | |
| Acquired / selected gifts, gift messages | 12.2.0 / 12.10.x | `Gifts/AcquiredGiftsSheet`, `SelectGiftsBottomSheet`, `GiftMessageBottomSheet` | profile, gift sheets | ? | ? | |
| Polls v2 (options, stats, menus) | 12.6.0 / 12.7.0 | `PollItemMenu`, `Components/poll/*` (e.g. `PollStatisticsBottomSheet`) | attach menu → Poll | ? | ? | |
| Markdown parser / code highlighting | 13.0.2 | `utils/code/highlight/CodeHighlighting` | automatic | n/a | n/a | rendering only |
| OAuth sheet | 12.4.0 | `OAuthSheet` | deep link / login URL | n/a | ? | |
| Passkeys | 12.2.8 | `PasskeysActivity` | Settings → Privacy? | ? | ? | |
| Chatbot (business) sheet | 12.8.0 | `Business/ChatbotSheet` | ? | ? | ? | |
| Create bot / guard-bot replace | 12.6.0 / 12.8.0 | `Components/CreateBotAlert`, `GuardBotReplaceSheet` | ? | ? | ? | |
| Staked dice | 12.3.0 | `StakedDiceSheet` | chat | ? | n/a | |
| Round video settings | 12.10.5 | `RoundVideoSettingsActivity` | Settings → Chat? | ? | ? | recorder in `utils/recorder/round` |
| Audio recorder settings | 13.0.3 | `AudioRecorderSettingsActivity` | Settings → Chat? | ? | ? | recorder in `utils/recorder/audio` |
| Web proxy transport | 12.10.4 | `utils/proxy/WebProxyTransport` | Settings → Proxy | ? | n/a | |
| Android Auto | 12.8.0 | `messenger/car/*` | n/a | n/a | n/a | no UI in the app |
| Wear OS auth | 12.8.0 | `WearAuthSheet` | ? | ? | ? | |
| Emoji attach layout | 12.6.0 | `Components/ChatAttachAlertEmojiLayout` | attach menu | ? | ? | |
| Select chat/user sheet | 12.4.0 | `SelectChatUserSheet` | share flows | ? | ? | |
| Summaries | ? | (not in the added-file list; layer addition) | ? | ? | ? | find via the TL layer diff (step 2 of the method) |
| Stories albums | ? | (not in the added-file list; layer addition) | ? | ? | ? | find via the TL layer diff |

## Glass-only chrome (not features, but the retro shell replaces them)

`MainTabsActivity`/`MainTabsLayout`/`MainTabsActivityController` (12.4.0), `ProfileActivity2` (12.4.0),
`Components/DialogsActivityStatusLayout`, `DialogsActivityTopPanelLayout`, `ChatActivityTopPanelLayout`,
`chat/layouts/ChatActivity{Actions,Channel,SideControls}ButtonsLayout`, `SearchTabsAndFiltersLayout`, `utils/glass/*`.

## NagramXF-only features that must stay reachable

Ghost mode, recent chats, bookmarks manager, in-app browser, devices, feed, N-settings: all are drawer entries
(`DrawerMenuHelper.entries`). AyuGram options (saved deleted messages and so on) live under N-settings.

## Still to do for WP 5.1

1. Diff `TLRPC.java` layer 216 → 232 (new constructors) for user-visible features missing from the file list above (summaries, albums, ...).
2. Walk each `?` row: find the launch code, record the new-UI and retro entry points, set Reachable.
3. Add per-release notes from Telegram's public changelogs.
