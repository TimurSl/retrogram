# SettingsActivity row checklist (WP 3.2)

Source: `SettingsActivity.fillItems()` at NagramXF `49ae647f` (13.1.1), read 2026-11-07. In 13.1.1 the page is a
`UniversalRecyclerView` of `UItem`s with numeric ids, not `*Row = rowCount++` constants. Under the recommended
plan option (a) (re-skin `SettingsActivity`, `docs/plans/03-profile-settings.md`) every row **stays where it is**; this
file is the list a re-skin must not lose. Status values: `kept` (stays in the re-skinned page), `n/a`.
Nothing here has been checked on a device.

| Id | Row | Shown when | Opens (`onClick`) | Status |
| ---: | :--- | :--- | :--- | :--- |
| – | Suggestions (pending server suggestions: 3 cards) | server `pendingSuggestions` | suggestion actions | kept |
| – | Accounts header + `AccountCell`s | more than one account | switch account | kept |
| 100 | NagramX settings (`NekoSettings`) | always | `NekoSettingsActivity` | kept |
| 25 | Wallet | `config.walletAvailable` (above or below the Premium group depending on date) | `WalletActivity` | kept; also in the drawer (WP 1.4) |
| 1 | Account | always | account settings | kept |
| 2 | Chat settings | always | `ThemeActivity` | kept |
| 101 | Stickers (top level) | `NaConfig.showStickersRowToplevel` | stickers | kept |
| 3 | Privacy & Security | always | `PrivacySettingsActivity` (passkeys row lives there) | kept |
| 5 | Notifications | always | `NotificationsSettingsActivity` | kept |
| 6 | Data & Storage | always | `DataSettingsActivity` | kept |
| 7 | Folders | always | `FiltersSetupActivity` | kept |
| 8 | Devices | always | `SessionsActivity` (chatbot sheet lives there) | kept |
| 9 | Power saving | always | `LiteModeSettingsActivity` | kept; hide the Liquid Glass toggle in retro mode (WP 4.1) |
| 10 | Language | always | `LanguageSelectActivity` | kept |
| 11 | Telegram Premium | not hidden (`hidePremiumSection`), premium not blocked | `PremiumPreviewFragment` | kept |
| 12 | Telegram Stars (+ balance) | not hidden, `starsPurchaseAvailable` | `StarsIntroActivity` | kept |
| 13 | Gram earnings (+ balance) | beta/standalone/Huawei build or earnings available | ton earnings | kept |
| 15 | Telegram Business | not hidden, premium not blocked | `PremiumPreviewFragment("business")` | kept |
| 16 | Send a gift | not hidden, premium purchase not blocked | gift flow | kept |
| – | Help header | not `hideHelpSection` | – | kept |
| 17 | Ask a question | not hidden | support chat | kept |
| 18 | Telegram FAQ | not hidden | FAQ URL | kept |
| 23 | Telegram Features | not hidden | `TelegramFeaturesUrl` | kept; also a drawer entry (WP 1.4), as in 12.1.1 |
| 19 | Privacy policy | not hidden | policy URL | kept |
| 24 | Round video settings | `SharedSettings.experimentalSettingsAllowed` | `RoundVideoSettingsActivity` | kept |
| 26 | Audio recorder settings | `experimentalSettingsAllowed` | `AudioRecorderSettingsActivity` | kept |
| – | Version footer | always | long-press: debug menu (`Copy`, logs, `CheckUpdate`, update channel) | kept; `CheckUpdate` / update channel rows depend on the remote update switch (`RetroConfig.remoteMetadata`, WP 0.2) |

Header area (not in `fillItems`): avatar, name, phone/username, edit and QR buttons, status/emoji, stories; these are
the part a 12.1.1 look replaces (WP 3.2 "Header").

## Gaps to settle during WP 3.2

- Ids 4, 14, 20-22 are unused in 13.1.1 (4 = former Contacts row, 14 = commented-out Wallet row): keep them reserved.
- 12.1.1 had the classic order Account/Chat/Privacy/…/Help in the profile screen with `TextCell` rows; here every row is a
  `SettingCell` with a gradient icon tile. The re-skin swaps the cell factory (`SettingCell.Factory`) for classic icon rows,
  not the row list.
- Search (`SettingsSearch`/`search.fillItems`) must keep indexing the same rows.
