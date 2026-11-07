# Retrogram's delta against NagramXF, and what it depends on

Purpose (docs/plans/07-validation-and-sync.md, "Fallback if NagramXF stalls"): if NagramXF stops tracking DrKLO and we
merge DrKLO directly, this is the list of NagramXF-only code that Retrogram code relies on and would have to be
re-applied by hand. Keep it current whenever a change touches an upstream file or imports NagramXF-only code.

Measured against the import commit `2af55562`: `git diff --stat 2af55562 HEAD -- TMessagesProj`
(2026-11-07: 29 files, +734 / -285, resources included).

## Retrogram-only files (no merge conflicts possible)

| File | Depends on (NagramXF-only) |
| :--- | :--- |
| `src/main/kotlin/app/retrogram/RetroConfig.kt` | `tw.nekomimi.nekogram.config.ConfigItem`, `NekoConfig.getPreferences()` |
| `src/main/kotlin/app/retrogram/RetroShell.kt` | `NekoConfig.navigationDrawerEnabled`, `NaConfig.mainTabsDisplayMode` / `foldersAtBottom` / `hideHomeSearchField`, `MainTabsHelper` |
| `src/main/java/tw/nekomimi/nekogram/settings/RetrogramSettingsActivity.java` | NagramXF settings cells and `NekoSettingsActivity` registration |
| `src/test/java/app/retrogram/RetroConfigTest.java` | `ConfigItem` |
| `TMessagesProj/plugins.gradle` | NagramXF's Chaquopy configuration, moved verbatim |

## Hooks in upstream/NagramXF files (where merges conflict)

| File | What the hook does | NagramXF-only code involved |
| :--- | :--- | :--- |
| `build.gradle`, `jni/CMakeLists.txt`, `src/main/AndroidManifest.xml` | package id, `retrogram.plugins` switch, no official API credentials, ENABLE_PLUGINS default | NagramXF's flavors/Chaquopy setup |
| `messenger/ApplicationLoader.java` | `RetroConfig.init()` after `NaConfig.init()` | `NaConfig` |
| `messenger/BuildVars.java` | removed hard-coded credentials | |
| `messenger/LiteMode.java` | `FLAG_LIQUID_GLASS` reports disabled in retro mode | |
| `ui/MainTabsConfigManager.java` | only the Chats tab in retro mode | `NaConfig.mainTabsOrder` machinery |
| `ui/Components/FragmentFloatingButton.java` | 56dp FAB, classic colors, solid 36dp sub-button | `NaConfig.squareFloatingButton` |
| `ui/Components/blur3/source/BlurredBackgroundSourceRenderNode.java` | flat drawables, no display-list work | |
| `ui/DialogsActivity.java` | sub-button offset (2 lines, `glass-guard:ok`) | |
| `ui/LaunchActivity.java` | drawer click handlers for Wallet / Invite / Features | NagramXF's `nkbtn*` drawer ids |
| `ui/Adapters/DrawerLayoutAdapter.java` | `nkbtnWallet/Invite/Features` items, Wallet hidden when unavailable | NagramXF's configurable drawer (`DrawerMenuHelper`) |
| `ui/iv/RichMediaCell.java` | `glass` off in retro mode | |
| `tw/nekomimi/nekogram/helpers/remote/BaseRemoteHelper.java` | no metadata-channel requests unless `remoteMetadata` | NagramXF remote metadata channel |
| `tw/nekomimi/nekogram/helpers/SettingsHelper.java`, `settings/NekoSettingsActivity.java`, `settings/SidebarMenuActivity.java` | Retrogram settings entry, drawer toggle hidden | NagramXF settings UI |
| `xyz/nextalone/nagram/helper/DrawerMenuHelper.kt`, `MainMenuActions.kt` | new drawer entries and their actions | NagramXF drawer configuration |

## NagramXF-only features Retrogram deliberately keeps

Drawer-configurable main menu (`DrawerMenuHelper`), ghost mode, recent chats, bookmarks, in-app browser, feed,
`hideHomeSearchField`, AyuGram options under N-settings. Retro mode forces `navigationDrawerEnabled`,
`mainTabsDisplayMode`, `foldersAtBottom` and `hideHomeSearchField` in memory (not persisted), so those options are
effectively NagramXF's own switches used as Retrogram's shell controls.
