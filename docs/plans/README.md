# Retrogram — implementation plans

`PLAN.md` (repo root) is the strategy: what Retrogram is, why it starts from NagramXF,
and the phase list. The files here turn each phase into **work packages (WPs)** that
can be picked up one at a time. Each WP has a goal, the exact files it touches, steps,
and an acceptance check.

| File | Phase | Status |
| :--- | :--- | :--- |
| [00-bootstrap.md](00-bootstrap.md) | 0 — import NagramXF, rebrand, `retrogram.plugins` switch, CI | in progress: 0.1 done (minus submodule forks); 0.2 partial; 0.3 (flag off), 0.4, 0.5 build-verified in CI (run 14, APK artifact); `plugins=true` also green (run 15, manual dispatch); WP 0.3 acceptance checks run in CI (run 16); nothing run on a device yet |
| [01-main-shell.md](01-main-shell.md) | 1 — drawer + `DialogsActivity` as root | ready after WP 0.1 |
| [02-chat-screen.md](02-chat-screen.md) | 2 — old chat screen | opt-in switches for input bar (2.1a), emoji panel (2.2a), round buttons (2.3, CI run 31), chat header and top panels (2.4a/b); 2.5 needs no code; all untested beyond the owner's screenshots up to run 25 |
| [03-profile-settings.md](03-profile-settings.md) | 3 — old profile and settings | after Phase 1 |
| [04-global-sweep.md](04-global-sweep.md) | 4 — blur3 choke point, remaining glass, `glass-guard.sh` | spike can start after 0.1 |
| [05-feature-audit.md](05-feature-audit.md) | 5 — 12.2 → 13.1.1 feature matrix | can start after 0.1 |
| [06-graph-features.md](06-graph-features.md) | 6 — Graph Messenger features, clean-room | spec step needs a phone; built from the plan's own designs, all off by default, CI-compiled, not run on a device: 6.1 chat lock, 6.2 hidden chats, 6.3 auto-reply, 6.6 special contacts log (+ JVM-tested cores for 6.4 voice effects and 6.5 queue rules); 6.4 voice changer (recorder buffers PCM per segment); 6.5 reuses Telegram's Downloads tab via a drawer entry |
| [07-validation-and-sync.md](07-validation-and-sync.md) | 7 + 8 — POCO gate, upstream sync | continuous |

## What was checked against the pinned code

The submodules are checked out at the pinned commits
(`git submodule update --init`; NagramXF `49ae647f` = 13.1.1, Telegram `3de37c8c`).
These points from `PLAN.md` hold up, and these were wrong or incomplete (PLAN.md has been
corrected to match):

| Claim in PLAN.md | Result |
| :--- | :--- |
| Drawer exists and is gated by `NekoConfig.navigationDrawerEnabled` (default `false`) | **Confirmed** (`NekoConfig.java:210`). It is read in `LaunchActivity.syncHomeDrawer()`, `DialogsActivity` (5 places), `MainTabsActivity:1506`, `MainTabsConfigManager:289`. |
| Root fragment is `MainTabsActivity` | **Confirmed**, and it is hard-wired in many places: `LaunchActivity` lines 687, 1295/1326, 4133/4146, plus about 15 `instanceof MainTabsActivity` checks (4310, 4623, 5375, 8032, 8053, 8837, 9527, 9644, 9757). Phase 1 cannot be "just a config flag". |
| `plugin` flavor and Chaquopy tasks are registered unconditionally | **Confirmed** (`TMessagesProj/build.gradle` lines 78–84, 166–181, 282–288, 294–540, `androidComponents` block at 514–540). |
| CMake gates on `ENABLE_PLUGINS` | **Confirmed**, but `CMakeLists.txt:351` **defaults it to `ON`** when undefined. The normal flavor passes `OFF` explicitly; keep that. |
| `src/normal` has no-op plugin stubs | **Confirmed.** The stubs share class names with `src/plugin` (`PluginsController`, `PluginsActivity`, ...), so the `normal` flavor must stay declared even when `plugin` is not. About 31 `src/main` files import `com.exteragram.messenger.plugins.*`. |
| `TopPanelLayout`, `ChatActivityTopFadeView` | **Wrong names.** Real classes: `DialogsActivityTopPanelLayout`, `ChatActivityTopPanelLayout`; chat list top fade is `topBubblesFadeView` in `DialogsActivity`. |
| "One choke point for blur: the `blur3` factories" | **Partly true.** `BlurredBackgroundDrawableViewFactory.create()` is one funnel, but 135 files mention `blur3`, 37 construct `BlurredBackgroundSourceRenderNode` directly and 104 use `BlurredBackgroundSourceColor`. The real choke point is `BlurredBackgroundSource.createDrawable()`. See Phase 4. |
| Old `ProfileActivity` is gone | **It is still there** (17k lines, 83 `new ProfileActivity(` call sites). Only the *self* profile moved to `SettingsActivity` (8 call sites) and `ProfileActivity2` is referenced only by itself and `SharedMediaLayout`. Phase 3 is smaller than PLAN.md implied. |
| NagramXF has its own submodules | **Not in PLAN.md.** About 24 (td, tgcalls, ffmpeg, boringssl, tlottie, wallet-engine, media, jlatexmath, ...). `lsplant`, `shadowhook` are only needed for plugin builds. Some are Keeperorowner forks (`tgcalls`, `LSPlant`, `android-inline-hook`), so mirror them. See WP 0.1. |
| Folder tabs at bottom exist | NagramXF has `foldersAtBottom` (`FoldersHelper.moveFoldersToBottom()`). Phase 1 must force it off. |

## Conventions for all phases

- **Hook, don't revert.** Upstream-file edits are one-liners `if (RetroConfig.retroUi) …`.
  Bigger revived code goes in `app.retrogram.ui.legacy`. Reason: keep merges from NagramXF cheap (Phase 8).
- **Revive from history.** Old source: `git -C upstream/telegram show 9cbf0333:<path>` (12.1.1).
  Each revived file gets a header comment with the source commit and path.
- **Every WP ends with**: it builds with `retrogram.plugins` off and on (CI), and screenshots
  on the POCO (or an emulator with the same 1080×2400 density 440 profile until the device is available).
- **No Graph Messenger code** is ever copied or decompiled (PLAN.md §4).
- **Size hint** is relative effort, not calendar time: S < 1 day, M 1–3 days, L 3–7 days, XL > 1 week.

## Critical path

```
0.1 import ─► 0.2 rebrand ─► 0.3 plugins switch ─► 0.4 CI ─► 0.5 RetroConfig
                                                        │
              Phase 1 ─► Phase 2 ─┐                     └─► Phase 4 spike (blur choke point)
                    └──► Phase 3 ─┴─► Phase 4 sweep ─► Phase 5 matrix closes ─► release gate (7)
Phase 6 spec (needs phone)  ─────────► Phase 6 build (after 0.5, independent of UI phases)
```

Phases 2 and 3 are independent of each other once Phase 1 lands. Phase 6 builds are
independent of the UI work, so they are the best parallel track.

## Open decisions (need the project owner)

1. Final package name / app name (placeholder `app.retrogram.messenger`). Needed by WP 0.2, FCM and signing.
2. Own Telegram `api_id` / `api_hash`, FCM project, signing keystore, Maps key. Needed by WP 0.4.
3. Who has a phone with Graph Messenger and a throwaway account (blocks Phase 6 step 1).
4. Whether to mirror the `Keeperorowner/*` submodule forks under the project's own org (recommended, WP 0.1).

Manual device test script: [`../device-test-checklist.md`](../device-test-checklist.md) (switches, what to look at, what to send back).
