# Retrogram — Project Plan

> **Retrogram** = the current Telegram feature set (13.x and newer), with the
> **classic pre‑Liquid‑Glass Android UI**. It builds on the code of **Telegram** and
> **Nagram XF**, plus the feature set of **Graph Messenger** (Databite, formerly
> "Telegraph").
>
> **Minimum supported device (must run well):** Xiaomi POCO X4 Pro 5G (`veux`,
> Snapdragon 695 / Adreno 619, 8 GB RAM, Android 13 / HyperOS 1.0, 1080×2400 @ 120 Hz).

---

## 0. Definitions

| Term | Meaning in this project |
| :--- | :--- |
| **Old UI** | The Telegram Android look **up to 12.1.1** (Oct 2025): side **navigation drawer** with a hamburger button, a solid top action bar, folder tabs under the action bar, a round pencil **FAB**, a full‑width solid **message input bar**, a solid emoji/sticker panel, the classic profile screen, and Settings opened from the drawer. No glass, refraction or floating "pill" chrome. Graph Messenger's latest release (12.1.1.0, based on Telegram 12.1.1) has exactly this look. |
| **Liquid Glass UI** | Started in **12.2.0** (Nov 2025: glass input bar, emoji panel and round chat buttons, `blur3/LiquidGlassEffect`). The full redesign shipped in **12.4.0** (Feb 2026): the drawer was removed, `MainTabsActivity` added glass bottom tabs, and `SettingsActivity` / `ProfileActivity2` / `FragmentFloatingButton` / `FragmentSearchField` arrived. |
| **New functionality** | Everything Telegram added after 12.1.1, up to the current **13.1.1** (API layer **232**, vs 216 in 12.1.1) and future releases. Examples: Gram wallet, QR share, rich‑text / IV editor, AI editor, live comments, communities, gift crafting and auctions, new polls, OAuth sheet, summaries. |

---

## 1. Sources

### Code (cloned into `upstream/` as pinned submodules)

| Path | Upstream | Branch @ pinned commit | Base Telegram version | License | Role in Retrogram |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `upstream/telegram` | [DrKLO/Telegram](https://github.com/DrKLO/Telegram) | `master` @ `3de37c8c` | **13.1.1 (7237)** | GPL‑2.0‑or‑later | Reference for new features. Its **history** is where old‑UI code comes from (`git show 9cbf0333:<path>` = 12.1.1). |
| `upstream/nagramxf` | [Keeperorowner/NagramXF](https://github.com/Keeperorowner/NagramXF) | `dev` @ `49ae647f` | **13.1.1 (7237)** | GPL‑3.0 | **Primary code base** (fork of Nagram X, plus exteraGram and AyuGram features). Its Python plugin system becomes an **opt‑in build option** (§5). |

The clones use `--filter=blob:none`: the full commit history is present and file
contents are downloaded on demand. That keeps the old‑UI archaeology
(`git show <old-commit>:<path>`) cheap.

### Feature reference (no code available)

| App | Package | Latest known | Source code | Role |
| :--- | :--- | :--- | :--- | :--- |
| **Graph Messenger** by Databite (ILMILI), formerly "Telegraph" | `ir.ilmili.telegraph` | 12.1.1.0 (Telegram 12.1.1); last listing update 2026‑01‑13 | **Not published.** No public repo found on GitHub, its listings or the web. | **Functional spec only.** Its features are re‑implemented clean‑room on top of NagramXF (§6, Phase 6). Its old‑style screens are a **visual reference** for Phases 1–4. |

> **Why Graph Messenger is not cloned or decompiled.** It is a Telegram derivative,
> so GPL obligates Databite to provide source to anyone who has the APK. They
> haven't published it. Copying decompiled code would bring in code we hold no
> license to.
>
> **Action:** send Databite a formal GPL source request (via Google Play or
> the developer contact, citing GPL‑2.0 §3). If source arrives, add it as
> `upstream/graph` and port directly. Until then, use only the installed app's
> **behaviour** as the spec.

### Key Telegram version commits (in `upstream/telegram`)

| Version | Commit | Date | Note |
| :--- | :--- | :--- | :--- |
| 12.1.1 | `9cbf0333` | 2025‑10‑15 | **Old UI reference** (last release without any glass; same base as Graph Messenger 12.1.1.0) |
| 12.2.0 | `17b771ef` | 2025‑11‑18 | First glass: input bar, emoji panel, blurred round chat buttons |
| 12.3.1 | `d7deedfa` | 2026‑01‑15 | Last release that still has the drawer |
| 12.4.0 | `fa4e8aa0` | 2026‑02‑14 | Full Liquid Glass redesign (45 files added, 10 deleted, 347 modified) |
| 13.1.1 | `3de37c8c` | 2026‑10‑25 | Current upstream = NagramXF base |

---

## 2. Findings that drive the architecture

1. **Forward‑porting features onto old code is not viable.** From 12.1.1 to 13.1.1,
   **2,150 Java files changed (+338,855 / −347,940 lines)** and the API layer
   moved 216 → 232. `ChatActivity` alone has +8.4k/−5.5k lines and
   `DialogsActivity` +3.4k/−2.5k. This is also why Graph Messenger is stuck on
   12.1.1. **Decision: start from the new code and re‑skin it.**
2. **NagramXF already did part of the work.**
   - It re‑added the drawer that 12.4 deleted (`DrawerLayoutAdapter`,
     `DrawerContainer`, `DrawerProfileCell` and others, used in `LaunchActivity`).
     The switch is `NekoConfig.navigationDrawerEnabled`, which defaults to `false`.
   - The bottom tabs are configurable: `NaConfig.mainTabsDisplayMode`
     (0 = show, 1 = hide, 2 = hide on scroll), `mainTabsOrder`, `mainTabsHideTitles`
     and `MainTabsConfigManager`.
   - It also tracks upstream quickly ("update to 13.1.1" landed the same day as DrKLO).
3. **Turning off "Liquid Glass" in Lite Mode is not enough.**
   `LiteMode.FLAG_LIQUID_GLASS` (`1<<18`) only gates the refraction shader in about
   22 files. The **floating layouts stay** whatever the flag says: pill input bar,
   glass tab bar, `FragmentFloatingButton`, `FragmentSearchField`,
   `ProfileActivity2` / `SettingsActivity`, and the frosted `blur3` backgrounds.
   Real layout work is needed.
4. **Many Graph Messenger features already exist in NagramXF.** Ghost mode,
   keeping deleted messages and edit history (from AyuGram), forwarding without
   quote, 10 accounts, translation and themes are already there. Only the rest
   needs new code (§6, Phase 6).
5. **The plugin system can be cut cleanly.** NagramXF already has `normal` and
   `plugin` product flavors with no‑op stubs in `src/normal/`
   (`com.exteragram.messenger.plugins.*`, an Xposed shim).
   - The `plugin` flavor adds Chaquopy (Python 3.11), LSPlant and ShadowHook
     natives and `-DENABLE_PLUGINS=ON`.
   - The Chaquopy tasks and configurations are registered **unconditionally**
     today. Making plugins optional means gating those registrations too, not
     only skipping the APK.
6. **POCO X4 Pro 5G is in Telegram's "AVERAGE" performance class.** `SM6375` is not
   in `SharedConfig.LOW_SOC`. Its average max CPU frequency is about 1.9 GHz
   ((6 × 1.8 + 2 × 2.2) / 8), which is ≤ 2055 MHz, so the class is AVERAGE and
   Lite Mode starts at `PRESET_MEDIUM`.
   - Android 13 (API 33) is above `minSdk 27`.
   - The device can run 64‑bit and 32‑bit APKs. Ship **arm64‑v8a** as the main
     build and keep armeabi‑v7a in the universal APK.
   - The custom Turnip / Zink / MobileGlues driver stack in the spec sheet does not
     matter here. Telegram uses stock GLES 3.2 and RenderNode / RenderEffect.
   - Removing the glass chrome is also a **performance win** on Adreno 619 at
     120 Hz: no per‑frame RenderEffect blur and no refraction shader.

---

## 3. Architecture

```
retrogram/                      (this repo)
├── PLAN.md
├── upstream/                   read-only reference submodules (pinned)
│   ├── telegram/   nagramxf/
└── (Phase 0) app source = NagramXF history imported at repo root
    ├── TMessagesProj/…                       NagramXF + Telegram code
    │   ├── src/main/java/app/retrogram/      ← all Retrogram-specific code
    │   │   ├── RetroConfig.kt                master switches (retroUi = ON)
    │   │   ├── ui/legacy/…                   old UI components revived from 12.1.1
    │   │   ├── ui/shims/…                    adapters: new feature → old-UI entry point
    │   │   └── graph/…                       Graph Messenger features (clean-room)
    │   ├── src/normal/…                      plugin stubs (existing)
    │   └── src/plugin/…                      Python plugin runtime (built only on opt-in)
    └── tools/
        ├── sync-upstream.sh                  pull new NagramXF tag, rebase Retrogram patches
        └── glass-guard.sh                    flags new glass/blur3/MainTabs uses in upstream diffs
```

**Principles**

- **Base = NagramXF**, imported with its full git history so upstream merges stay
  real merges (`git remote add nagramxf …; git merge`).
- **Keep the old UI as a layer, not a revert.** Each change to upstream files is a
  small hook (`if (RetroConfig.retroUi) { legacy path } else { upstream path }`).
  Larger revived components live in `app.retrogram.ui.legacy`. This keeps
  rebases onto new Telegram/NagramXF releases manageable.
- **Revive old components from history** rather than rewriting them:
  `git -C upstream/telegram show 9cbf0333:TMessagesProj/src/main/java/org/telegram/ui/Components/ChatActivityEnterView.java`,
  then trim them to the visual layer and wire them to the new controllers.
- **Use one choke point for blur.** Make `BlurredBackgroundSourceRenderNode.createDrawable()`
  (the `BlurredBackgroundSource` implementation behind `BlurredBackgroundDrawableViewFactory`)
  return a flat drawable with **opaque** theme colors, and stop its per‑frame render‑node capture,
  when `retroUi` is on. That removes glass everywhere at once, including sheets nobody has
  reviewed yet. Details and caveats: `docs/plans/04-global-sweep.md`.
- **Ship one app and one mode.** Retrogram defaults to the old UI. A hidden
  "upstream UI" toggle stays for debugging and diffing only.

---

## 4. Licensing and compliance

- The combined work is **GPL‑3.0**, because NagramXF is GPL‑3.0. Telegram is
  "GPL v. 2 or later" per its source file headers, so it is compatible. Keep all
  copyright headers and add a `NOTICE`.
- NagramXF says it **contains reverse‑engineered code from closed‑source
  forks**. Record which modules these are and treat them as a legal risk.
  Prefer clean‑room versions where possible.
- **Graph Messenger: clean‑room only.**
  - Never paste decompiled code from it.
  - Write a behaviour spec per feature (screenshots plus steps on the installed
    app), then implement it independently.
  - Pursue the GPL source request (§1).
- **Do not copy Graph Messenger's telemetry.** Reporting (SecurityLab citing RKS
  Global) says it sends data to third‑party servers, including servers in Russia,
  and ships Firebase Analytics and ad SDKs. That is a single source and
  unverified here. Retrogram ships **no ads and no third‑party analytics**.
- Use **your own `TELEGRAM_APP_ID` / `TELEGRAM_APP_HASH`** (my.telegram.org). NagramXF's
  `TMessagesProj/build.gradle` falls back to the official app's `api_id` 6 and its hash when none is
  supplied, so make release builds fail without them. Also use your
  own package name, your own FCM `google-services.json`, signing keys and Maps key.
  Also replace NagramXF's update and metadata channel IDs
  (`BaseRemoteHelper.CHANNEL_METADATA_ID`).
- Features that bypass Telegram's restrictions (anti‑delete, ignoring "restrict
  saving content", ghost mode) can get **accounts limited**. Ship them
  **off by default** with a clear warning.

---

## 5. Build and CI

- This container has **no Android SDK**. Build on **GitHub Actions**, adapting
  NagramXF's `.github/workflows/{canary,staging}.yml`.
  - JDK 17/21, Android SDK 36, NDK and CMake as pinned by NagramXF.
  - `git submodule update --init --recursive --depth=1` (about 24 submodules: td,
    ffmpeg, boringssl, tgcalls and the rest; `lsplant` and `shadowhook` only for plugin builds; three are
    `Keeperorowner/*` forks, so mirror them, see `docs/plans/00-bootstrap.md`).
  - Secrets: `TELEGRAM_APP_ID`, `TELEGRAM_APP_HASH`, keystore, `google-services.json`.

### Plugins as an opt‑in build option

The Gradle property `retrogram.plugins` defaults to `false`. It can be set in
`gradle.properties` or `local.properties`, with `-Pretrogram.plugins=true`, or with
the `RETROGRAM_PLUGINS=1` environment variable.

| | `retrogram.plugins=false` (default) | `retrogram.plugins=true` |
| :--- | :--- | :--- |
| Product flavors | `normal` only; the `plugin` flavor is **not declared** | `normal` + `plugin` |
| Chaquopy / Cython / pip tasks and configurations | **Not registered** (no Python download, faster configure) | Registered as in NagramXF |
| Natives | `-DENABLE_PLUGINS=OFF`; no LSPlant / ShadowHook / Python libs | `-DENABLE_PLUGINS=ON` (arm64‑v8a, armeabi‑v7a) |
| Plugin code | `src/normal` stubs; plugin settings and drawer entries hidden | Full plugin manager and Python SDK |
| CI | Always built | Built when the workflow input `plugins: true` is set, or on release tags |

Work items:
1. Wrap the `plugin` flavor, `sourceSets.plugin.*`, the `chaquopy*`
   configurations and task registrations in `TMessagesProj/build.gradle` in
   `if (retrogramPlugins) { … }`.
2. Make sure nothing in `src/main` references `src/plugin`‑only classes.
   Compiling `normal` with the flag off proves it.
3. Hide the plugin UI entry points through a `BuildConfig.PLUGINS_ENABLED` field.
4. Expose the switch as a workflow‑dispatch input.

### Outputs
- `retrogram-<ver>-arm64-v8a.apk` (`normal`) is the main APK for the POCO.
- armeabi‑v7a and universal APKs are also built.
- `-plugins` APKs are built when the flag is on.
- Stable, beta and debug builds use separate application IDs so they can be
  installed side by side.

---

## 6. Phased roadmap

Each phase ends with a build installable on the POCO X4 Pro and an acceptance
checklist.

### Phase 0 — Bootstrap (≈1 week)
- Import NagramXF into the repo root with its history and add remotes
  (`nagramxf`, `telegram`).
- Rebrand:
  - application ID (placeholder `app.retrogram.messenger`, plus `.beta` and
    `.debug`; final name to be decided)
  - app name "Retrogram", launcher icon, about screen and links
  - remove NagramXF update checks and donation links, or point them at Retrogram
    channels
- Add `RetroConfig` (`retroUi`, `graph*` toggles) and a "Retrogram" settings section.
- Implement the `retrogram.plugins` build switch (§5).
- Get CI green and sign APKs.
- **Accept when:**
  - The CI APK installs on the POCO, logs in, and chats, calls and push
    notifications work.
  - Builds pass both with and without `retrogram.plugins`.

### Phase 1 — Old main shell (≈2 weeks)
- `LaunchActivity` root is `DialogsActivity` again, as in 12.1.1, instead of
  `MainTabsActivity`.
  - Drawer is on by default (`navigationDrawerEnabled = true`, locked on in retro mode).
  - No bottom tabs (`mainTabsDisplayMode = 1` and the tab host is removed from the
    hierarchy, not just hidden).
- `DialogsActivity`:
  - Hamburger/back toggle in the action bar.
  - Classic title / "Connecting…" status. Replace
    `DialogsActivityStatusLayout` / `DialogsActivityTopPanelLayout` with the 12.1.1 header.
  - Search as an action‑bar item instead of `FragmentSearchField`.
  - `FilterTabsView` folder tabs under the action bar, with the 12.1.1 stories
    header behaviour.
  - Round pencil **FAB**, plus the camera mini‑FAB for stories, instead of
    `FragmentFloatingButton`.
- The drawer is the home for everything the 12.4 tabs and overflow menu hold:
  - Contacts, Calls, Saved, Settings, New group / channel, Archived, accounts
  - new entries such as Wallet, QR share and Communities
  - NagramXF `drawerItem*` toggles keep working
- **Accept when:** side‑by‑side screenshots match on the same theme for the chat
  list, drawer, search and archive. Compare against Telegram 12.1.1 and Graph
  Messenger 12.1.1.0. No glass is visible.

### Phase 2 — Old chat screen (≈3 weeks)
- `ChatActivityEnterView`:
  - Full‑width solid input panel from 12.1.1, with attach, emoji, send/voice and
    the bot keyboard button.
  - Every new feature stays reachable: AI editor, rich text, scheduled/silent,
    paid messages, suggested posts and the rest.
- `EmojiView` / sticker / GIF panel uses the solid 12.1.1 style (no glass tabs).
- Old round page‑down, mentions and reactions buttons replace
  `ChatActivityBlurredRoundButton`.
- Classic stacked top panels: pinned, group call, translate, action bar, and the
  "join / mute" bottom bar. These replace `ChatActivityTopPanelLayout` and
  the glass top fade in `ChatActivity`.
- Solid action bar. The optional 12.1‑style blur (`FLAG_CHAT_BLUR`) defaults off
  on AVERAGE devices.
- **Accept when:** a regression matrix passes. It covers private, group, channel,
  forum topic, bot, secret chat, saved messages, reply/quote, edit, voice/video
  notes, polls, paid media, and the new 13.x message types.

### Phase 3 — Old profile and settings (≈2 weeks)
- Your own profile and Settings get the 12.1.1 layout, opened from the drawer.
  The classic `ProfileActivity` still exists in 13.1.1 (used for other users and chats),
  so this is mostly a re‑skin of `SettingsActivity` (decided in a spike, see
  `docs/plans/03-profile-settings.md`). `ProfileActivity2` is no longer opened.
- Other users' and chats' profiles use the classic expanding‑avatar layout.
  - New rows (gifts, wallet address, business info, music, rating and so on) are
    inserted as classic `TextCell` / `TextDetailCell` rows.
- **Accept when:** every row in upstream's `SettingsActivity` has a home in the
  old layout. A generated checklist from that file is kept in `docs/`.

### Phase 4 — Global sweep (≈2 weeks)
- Apply the blur3 choke point (§3) and force `FLAG_LIQUID_GLASS` off in retro
  mode, hiding the toggle in Lite Mode settings.
- Go through every file that references glass, blur3 or tabs:
  - ShareAlert, ChatAttachAlert (including the new Gram / Rich / Poll layouts)
  - PhotoViewer controls, ContactsActivity, CallLogActivity, TopicsFragment,
    PeerColorActivity, StatisticActivity, ChannelAdminLogActivity, SelectAudioAlert
  - NagramXF `BookmarkManagerActivity`
- Restore the classic default theme set and colors: compare the `Theme` keys of
  12.1.1 and 13.1.1 and add `key_chats_menu*` colors where they were dropped.
- Add `tools/glass-guard.sh`. It greps any upstream merge diff for new `blur3`,
  `GlassTab*`, `MainTabs*`, `FragmentFloatingButton` and `FragmentSearchField`
  uses and fails CI until each one is handled.
- **Accept when:** `glass-guard.sh` passes on the whole tree.

### Phase 5 — New functionality audit (ongoing, ≈1 week initially)
- Build a feature matrix from the DrKLO "update to" commits 12.2.0 → 13.1.1:
  feature, new‑UI entry point, old‑UI entry point, status.
- Every feature must be reachable and styled the old way. Notable ones: Gram
  Wallet, QR share/scan, IV / rich editor, AI editor, Communities, Live comments,
  gift crafting and auctions, polls v2, Markdown parser, OAuth sheet, summarize,
  stories albums.
- **Accept when:** the matrix is 100% "reachable" and ≥ 90% "old‑styled".

### Phase 6 — Graph Messenger features, clean‑room (≈3–4 weeks)

**Step 1 (≈2 days): write the spec.**
- Install Graph Messenger on a test phone with a throwaway account. Record every
  Graph‑specific screen and setting in `docs/graph-spec/`: screenshots, menu path,
  behaviour, defaults.
- The table below is the starting list and must be verified on device.
  - *Listed* = in its store/SourceForge listing.
  - *Reported* = widely attributed to the app; confirm it exists.

**Step 2: implement in `app.retrogram.graph`.** Each feature sits behind a toggle
under Settings → Retrogram → Graph features.

| Graph Messenger feature | Evidence | NagramXF already has it? | Plan |
| :--- | :--- | :--- | :--- |
| Up to 10 accounts | Listed | ✅ `MAX_ACCOUNT_COUNT = 10` | Reuse |
| Theme engine / many themes | Listed | ✅ (themes, Material You) | Reuse; add Graph‑style presets if the spec shows unique ones |
| **Password / PIN‑locked chats** (per chat) | Listed | ❌ (app‑wide passcode only) | **Build**: per‑dialog lock, biometric option, re‑lock timeout |
| **Hidden chats / contacts** (removed from the list, opened with a secret gesture or passcode) | Listed | ❌ | **Build**: hidden dialog set excluded from lists, search and notifications preview |
| **Auto‑reply** (auto‑responder) | Listed | ❌ | **Build**: rules per chat type and schedule; send through the normal `SendMessagesHelper` path; rate‑limited |
| **Voice changer** for voice notes | Listed | ❌ | **Build**: DSP filters (pitch / robot / echo) applied to the Opus recording before upload |
| **Download manager** (queue, pause/resume, save to device) | Listed | partial (`showDeleteDownloadedFile`, save to Downloads) | **Build**: downloads screen in the drawer, queue, Wi‑Fi‑only and scheduled downloads, bulk save |
| Built‑in audio/video player | Listed | ✅ (Telegram player) | Reuse; check for Graph‑only extras in the spec |
| Ghost mode (hide online / read / typing) | Reported | ✅ Ghost mode | Reuse; add a drawer toggle like Graph's |
| Anti‑delete / edit history | Reported | ✅ AyuGram "save deleted" | Reuse |
| **Special contacts / contact change log** (alerts when chosen contacts come online or change name, photo or number) | Reported | ❌ | **Build**: local watcher over `updateUserStatus` / `updateUserName` / photo updates, with a log screen |
| **Chat categories** (custom tabs beyond Telegram folders) | Reported | partial (folders, NagramXF filters) | Map to local folders; build only if the spec shows a real difference |
| Forward without quote / multi‑forward | Reported | ✅ `NoQuoteForward` | Reuse |
| Profile photo download / full‑size viewer | Reported | partial | **Build** if missing after the spec |
| Ads, analytics, data sent to third parties | Reported | — | **Never** |

**Accept when:** every feature in `docs/graph-spec/` is marked reused, built, or
consciously rejected, and each built one has a toggle and a test.

### Phase 7 — Device validation on POCO X4 Pro 5G (continuous, gate for every release)
| Check | Target |
| :--- | :--- |
| Chat list and chat scroll at 120 Hz | No janky frames in a 30 s fling test. Use the upstream `FrameMetricsOverlayView` (added in 12.4) in debug builds; p95 frame ≤ 8.3 ms. |
| Cold start to chat list | ≤ 1.5 s (arm64 release, baseline profile) |
| RAM (PSS) with 3 accounts, idle in chat list | ≤ 350 MB |
| Performance class detection | Reports **AVERAGE**; medium Lite Mode preset applied |
| HyperOS background behavior | Push arrives with autostart/battery defaults; show an in‑app tip for "No restrictions" |
| Calls | Voice and video work over Wi‑Fi and 5G; no ANR on Bluetooth LE audio routing |
| Media | 1080p@60 hardware decode (HEVC/VP9), voice notes (including voice changer), round video with the selfie camera |
| APK size (arm64, `normal`) | Track per release; alert on > +5% growth. The `-plugins` APK is tracked separately. |

Test matrix beyond the minimum device: one Android 15/16 Pixel‑class phone and one
Android 10 low‑end phone (the minSdk sanity check).

### Phase 8 — Upstream sync process (ongoing)
1. DrKLO publishes "update to X". NagramXF usually merges it within days.
2. `tools/sync-upstream.sh` fetches the new NagramXF commit and merges it into
   `main` on a `sync/X` branch.
3. CI runs the build (plugins off **and** on), `glass-guard.sh` and unit tests.
   Any new glass UI is handled in retro mode before merging.
4. Smoke test on the POCO (Phase 7 checklist), then tag `retrogram-X.r<n>`.
5. Fallback if NagramXF stalls: merge DrKLO directly. Telegram history is already
   in `upstream/telegram`.

---

## 7. Risks

| Risk | Impact | Mitigation |
| :--- | :--- | :--- |
| Upstream churn in huge files (`ChatActivity`, `DialogsActivity`, `ChatActivityEnterView`) | Painful merges every release | Hooks‑and‑legacy‑package design; `glass-guard.sh`; small upstream diffs |
| Graph Messenger source never published | Its features must be rebuilt by us | Clean‑room spec (Phase 6); GPL source request |
| Accidental use of decompiled Graph code | Copyright claim | Clean‑room rule; review checklist in PRs |
| NagramXF becomes inactive | Lose fast upstream syncs and its feature work | Keep a DrKLO merge path; Telegram history is local |
| Reverse‑engineered code in NagramXF | Legal / takedown | Inventory those modules; consider clean‑room versions |
| Plugin build rots because it is opt‑in | Broken `-plugins` builds discovered late | CI builds both variants on every sync and release |
| Telegram account restrictions for mod features (ghost, anti‑delete, auto‑reply spam) | Users limited | Off by default, warnings, rate limits, nothing that spoofs the client |
| New Telegram features built only for the glass layout | Missing entry points in the old UI | Phase 5 matrix, maintained on every sync |
| GPU cost of leftover blur on Adreno 619 | Jank at 120 Hz | blur3 choke point; AVERAGE‑class defaults; Phase 7 frame gate |

---

## 8. Decisions

| Question | Decision |
| :--- | :--- |
| What is "Telegraph"? | **Graph Messenger by Databite** (`ir.ilmili.telegraph`), used as a feature spec |
| Ship NagramXF's Python plugins? | **Yes, as an opt‑in build option** (`retrogram.plugins`, default off) |
| Final package name | **Open.** Placeholder `app.retrogram.messenger` |

### Detailed plans
Work packages per phase, with the verification findings: [`docs/plans/`](docs/plans/README.md).

### Immediate next steps
1. Phase 0: import NagramXF history into the repo root, rebrand, add `RetroConfig`
   and the `retrogram.plugins` switch, and port the CI workflow with your API
   ID/hash and signing secrets.
2. Phase 6, step 1, in parallel: install Graph Messenger and write
   `docs/graph-spec/`. Send the GPL source request to Databite.
3. Phase 1 spike: flip the drawer on, remove the tab host, and revive the 12.1.1
   FAB and header in `DialogsActivity`. Screenshot‑diff against 12.1.1 and
   Graph Messenger on the POCO.

### Useful commands
```bash
git submodule update --init                     # fetch the reference repos (not recursive)
git -C upstream/telegram show 9cbf0333:TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java   # 12.1.1 source
git -C upstream/telegram diff --stat d7deedfa fa4e8aa0 -- TMessagesProj/src/main/java   # what 12.4 changed
```
