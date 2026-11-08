# Plan status: every work package, one line each (2026-11-08)

How to read it. **Built** = the code is in the branch and compiled by CI (latest normal build: run 52, `plugins=true`: run 49); nothing in this project has been run on a phone yet,
so "built" never means "seen working". **Device** = what is left can only be checked on a device. **You** = needs something only the project owner can supply.
**Ruled out** = a decision with its reason, recorded in the linked plan. Details and evidence are in each phase file; this page only makes sure nothing is left without a state.

## Phase 0: bootstrap ([00-bootstrap.md](00-bootstrap.md))
| WP | State |
| :--- | :--- |
| 0.1 import NagramXF | Built. Step 5 (fork the `Keeperorowner/*` submodules): **You** (needs the project org, open decision 4) |
| 0.2 rebrand | Built: package id, app name, `13.1.1.r1`, credentials removed, remote metadata and the `@NagramXF` join prompt off by default. **You:** launcher icons, `google-services.json`, own `api_id`/`api_hash`, final package name. **Ruled out for now:** `applicationIdSuffix` (breaks contact sync unless templated and tested on a device) |
| 0.3 `retrogram.plugins` switch | Built; both variants compile (runs 15, 16, 49) |
| 0.4 CI | Built (normal on every push, plugins on dispatch/tags/`sync/*`, glass-guard, unit tests, perf-script tests, APK size). Release signing and Firebase secrets: **You** |
| 0.5 `RetroConfig` | Built |

## Phase 1: shell ([01-main-shell.md](01-main-shell.md))
| WP | State |
| :--- | :--- |
| 1.1 shell switch, 1.2 header, 1.3 FAB, 1.4 drawer, 1.5 leftovers | Built (1.5 satisfied by the existing switches, 1.4 needs no separate retro layout: decisions in the plan). **Device:** screenshot diff of chat list, search open, archive, "Connecting...", stories row, tablet layout |

## Phase 2: chat screen ([02-chat-screen.md](02-chat-screen.md), [02-spike.md](02-spike.md))
| WP | State |
| :--- | :--- |
| Spike | Built (table in `02-spike.md`) |
| 2.1 input panel | Built as opt-in "Classic input bar" (owner's screenshots up to run 25 show it working). **Device:** keyboard animation with Gboard/HyperOS, all extra buttons |
| 2.2 emoji panel | Built as opt-in "Classic emoji panel". The inline search field at the top of the panel is still the 12.4 one (12.1.1 had a search button in the bar): **Device** feedback first |
| 2.3 round buttons | Built (run 31) |
| 2.4 top panels and bars | Built (classic chat header, flat top panels) |
| 2.5 message list chrome | Nothing to change: the cells have no glass code |

## Phase 3: profile and settings ([03-profile-settings.md](03-profile-settings.md))
| WP | State |
| :--- | :--- |
| 3.1 settings spike | Built; classic settings page on by default |
| 3.2 header | Built as opt-in "Plain profile header" (run 44). Anything else in the header that differs from 12.1.1: **You** (a screenshot pair) |
| 3.3 other profiles | Nothing left to change in code (liquid glass is off in retro mode); **Device** check |
| 3.4 shared-media tabs | Built as opt-in "Classic profile tabs" (run 39) |
| 3.5 dead routes | Built (run 39) |

## Phase 4: global sweep ([04-global-sweep.md](04-global-sweep.md))
| WP | State |
| :--- | :--- |
| 4.1 choke point | Built (run 52); step 5 complete (opaque provider), steps 1 and 4 not needed, own-render-node sites checked against 12.1.1 and kept. **Device:** `GlassEngine` (step 7) from a GPU profile |
| 4.2 sweep list | Built or read through for every screen of the list (per-screen table in the plan); **Device:** the before/after screenshots (screen list in `docs/sweep/README.md`, capture helper `tools/sweep/capture.sh`) |
| 4.3 theme | Built: 123 default-theme colors and the five built-in theme files of 12.1.1 (run 45), behind "Classic theme colors". **Device:** looks right in Day, Night, Dark Blue. Not changed: which theme a new install starts with, wallpaper presets |
| 4.4 glass-guard | Built; strict mode wired for `sync/*` branches |

## Phase 5: feature audit ([05-feature-audit.md](05-feature-audit.md), [../feature-matrix.md](../feature-matrix.md))
5.1 matrix, 5.2 entry points, 5.3 automated in the sync script: Built. 5.1's last item (per-release changelog notes) is **ruled out** (it adds nothing the code-based matrix can use). 5.4: the manual checklist stands in for an instrumentation test (needs a device farm and a logged-in test account). **Device:** every "reachable (unverified)" row.

## Phase 6: Graph features ([06-graph-features.md](06-graph-features.md))
Step 1 (behaviour specs from a phone, `docs/graph-spec/`): **You**; all eleven spec files exist as stubs with the concrete questions to answer on the phone (including Retrogram's current behaviour next to each question), so the session only records observations. Step 2 was built from the plan's own designs, everything off by default; where a spec later disagrees with an option or default, the spec wins.
| WP | State |
| :--- | :--- |
| 6.1 per-chat lock | Built (run 34, gaps closed in run 48). Not covered: contacts widget, chats opened as sheets, biometrics, message-storage encryption |
| 6.2 hidden chats | Built (run 35; search and share sheet in run 48). Contacts list intentionally unchanged (not in the plan's row) |
| 6.3 auto-reply | Built (run 32); keywords, hours and cooldown are settings (run 51). **Ruled out:** group replies (the trigger is a behaviour question for the spec); per-chat exceptions (the spec) |
| 6.4 voice changer | Built, seamless streaming (runs 37, 50). **Device:** CPU and sound quality |
| 6.5 download manager | Built: Downloads drawer entry (upstream already has the list with pause/resume), Wi-Fi-only, daily window with alarm that survives a killed app (runs 36, 38, 43, 46). Bulk save: **ruled out**, the Downloads tab's selection mode already has "Save to downloads" |
| 6.6 special contacts | Built (run 33, notification and drawer entry run 48). SQLite store **ruled out** (the capped text log is enough) |
| 6.7 ghost-mode toggle | **Ruled out as already there:** `nkbtnGhostMode` in the drawer toggles ghost mode in one tap (`MainMenuActions.toggleGhostMode`, from NagramXF) |
| 6.8 chat categories | **Ruled out pending the spec:** Telegram's own chat folders cover it; build only if `docs/graph-spec/` shows something folders cannot do (the plan's own condition) |
| 6.9 profile photo download / full-size viewer | **Ruled out as already there:** the photo viewer opens profile photos full size and its Save handler resolves avatar files (`avatarsDialogId` paths in `PhotoViewer`); **Device** check |
| 6.10 theme presets | **Ruled out pending the spec:** the plan builds it only if the spec shows presets Telegram's themes lack; the shipping mechanism (`.attheme` assets) is already used for the 12.1.1 built-in themes |

## Phases 7 and 8 ([07-validation-and-sync.md](07-validation-and-sync.md))
| WP | State |
| :--- | :--- |
| 7 device gate | Tooling built (`tools/perf/perf-gate.sh`, frame-time parser with tests, `tools/apk-size.sh`, release template). **Device:** running the gate. 7.1 baseline profile: **Device** (recorded on a phone; a hand-written profile was **ruled out** because its syntax cannot be validated here and a wrong rule would only fail on the first release build, details in the plan; the recording steps are in `docs/release/baseline-profile.md`) |
| 8.1 sync script | Built and run in a scratch clone: report, conflict stop, TL-class report. First real run needs a NagramXF release newer than the import (`nagramxf/dev` is still at the import base) |
| 8.2 release flow | Documented; needs the first sync PR and the device gate |

## What only the owner can supply
1. Final package and app name (placeholder `app.retrogram.messenger`). 2. Own `api_id`/`api_hash`, Firebase project (`google-services.json`), signing keystore, Maps key.
3. A phone with Graph Messenger and a throwaway account (specs for Phase 6 step 1). 4. A decision on mirroring the `Keeperorowner/*` forks. 5. Launcher icon artwork.
6. A device (the POCO X4 Pro 5G) for everything marked **Device**, using `docs/device-test-checklist.md` and `tools/perf/perf-gate.sh`.
