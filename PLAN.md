# Retrogram — Project Plan

> **Retrogram** = the current Telegram feature set (13.x and newer), with the
> **classic pre‑Liquid‑Glass Android UI**. It is built on the code of **Telegram**,
> **Nagram XF** and **Telegraher** (plus Telegra.ph tooling).
>
> **Minimum supported device (must run well):** Xiaomi POCO X4 Pro 5G (`veux`,
> Snapdragon 695 / Adreno 619, 8 GB RAM, Android 13 / HyperOS 1.0, 1080×2400 @ 120 Hz).

---

## 0. Definitions

| Term | Meaning in this project |
| :--- | :--- |
| **Old UI** | The Telegram Android look **up to 12.1.1** (Oct 2025): side **navigation drawer** with a hamburger button, a solid top action bar, folder tabs under the action bar, a round pencil **FAB**, a full‑width solid **message input bar**, a solid emoji/sticker panel, the classic profile screen, and Settings opened from the drawer. No glass, refraction or floating "pill" chrome. |
| **Liquid Glass UI** | Started in **12.2.0** (Nov 2025: glass input bar, emoji panel and round chat buttons, `blur3/LiquidGlassEffect`). The full redesign shipped in **12.4.0** (Feb 2026): the drawer was removed, `MainTabsActivity` added glass bottom tabs, and `SettingsActivity` / `ProfileActivity2` / `FragmentFloatingButton` / `FragmentSearchField` arrived. |
| **New functionality** | Everything Telegram added after 12.1.1, up to the current **13.1.1** (API layer **232**, vs 216 in 12.1.1) and future releases. Examples: Gram wallet, QR share, rich‑text / IV editor, AI editor, live comments, communities, gift crafting and auctions, new polls, OAuth sheet, summaries. |

---

## 1. Source repositories (cloned into `upstream/` as pinned submodules)

| Path | Upstream | Branch @ pinned commit | Base Telegram version | License | Role in Retrogram |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `upstream/telegram` | [DrKLO/Telegram](https://github.com/DrKLO/Telegram) | `master` @ `3de37c8c` | **13.1.1 (7237)** | GPL‑2.0 | Reference for new features. Its **history** is where old‑UI code comes from (`git show 9cbf0333:<path>` = 12.1.1). |
| `upstream/nagramxf` | [Keeperorowner/NagramXF](https://github.com/Keeperorowner/NagramXF) | `dev` @ `49ae647f` | **13.1.1 (7237)** | GPL‑3.0 | **Primary code base** (fork of Nagram X, plus exteraGram and AyuGram features). |
| `upstream/telegraher` | [nikitasius/Telegraher](https://github.com/nikitasius/Telegraher) | `graher_12.92.1` @ `0f8f9e78` | 12.9.2 (6991) | GPL‑2.0 | Feature donor: PIN/duress hardening, keep‑on‑ban, TTL media, de‑Googling. |
| `upstream/telegraph-x` | [sergpetrov/telegraph-android](https://github.com/sergpetrov/telegraph-android) | `master` @ `dc1be256` | n/a (Telegra.ph client) | GPL‑3.0 | Optional donor for a built‑in **Telegra.ph** composer (see §6, Phase 7). |

The clones use `--filter=blob:none`: the full commit history is present and file
contents are downloaded on demand. That keeps the old‑UI archaeology
(`git show <old-commit>:<path>`) cheap.

> **"Telegraph" interpretation.** I read "Telegraph" as **Telegraher**, the
> Telegram fork. If you meant the Telegra.ph publishing platform, Phase 7 covers
> it. Both are cloned. Confirm which one you meant.

### Key Telegram version commits (in `upstream/telegram`)

| Version | Commit | Date | Note |
| :--- | :--- | :--- | :--- |
| 12.1.1 | `9cbf0333` | 2025‑10‑15 | **Old UI reference** (last release without any glass) |
| 12.2.0 | `17b771ef` | 2025‑11‑18 | First glass: input bar, emoji panel, blurred round chat buttons |
| 12.3.1 | `d7deedfa` | 2026‑01‑15 | Last release that still has the drawer |
| 12.4.0 | `fa4e8aa0` | 2026‑02‑14 | Full Liquid Glass redesign (45 files added, 10 deleted, 347 modified) |
| 12.9.2 | `b7561f0c` | 2026‑08‑03 | Telegraher base |
| 13.1.1 | `3de37c8c` | 2026‑10‑25 | Current upstream = NagramXF base |

---

## 2. Findings that drive the architecture

1. **Forward‑porting features onto old code is not viable.** From 12.1.1 to 13.1.1,
   **2,150 Java files changed (+338,855 / −347,940 lines)** and the API layer
   moved 216 → 232. `ChatActivity` alone has +8.4k/−5.5k lines and
   `DialogsActivity` +3.4k/−2.5k. **Decision: start from the new code and re‑skin
   it.**
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
4. **Telegraher's diff is small and portable.** It changes 228 files in `src/`
   (+6.8k/−7.1k lines) relative to 12.9.2. Many of its features **already exist in
   NagramXF**: disable ads, save deleted messages, ignore content restrictions,
   ghost mode, date of forwarded message, delete downloaded file, 10 accounts.
   Only the remainder needs porting (§6, Phase 6).
5. **POCO X4 Pro 5G is in Telegram's "AVERAGE" performance class.** `SM6375` is not
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
│   ├── telegram/   nagramxf/   telegraher/   telegraph-x/
└── (Phase 0) app source = NagramXF history imported at repo root
    ├── TMessagesProj/…                       NagramXF + Telegram code
    │   └── src/main/java/app/retrogram/      ← all Retrogram-specific code
    │       ├── RetroConfig.kt                master switches (retroUi = ON)
    │       ├── ui/legacy/…                   old UI components revived from 12.1.1
    │       ├── ui/shims/…                    adapters: new feature → old-UI entry point
    │       └── graher/…                      ported Telegraher features
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
- **Use one choke point for blur.** Make the `blur3` factories
  (`BlurredBackgroundDrawableViewFactory` / `BlurredBackgroundSourceRenderNode`)
  return a solid theme‑color source when `retroUi` is on. That removes glass
  everywhere at once, including sheets nobody has reviewed yet.
- **Ship one app and one mode.** Retrogram defaults to the old UI. A hidden
  "upstream UI" toggle stays for debugging and diffing only.

---

## 4. Licensing and compliance

- The combined work is **GPL‑3.0**, because NagramXF is GPL‑3.0. Telegram and
  Telegraher are "GPL v. 2 or later" per the Telegram source file headers, so they are compatible.
  Keep all copyright headers and add a `NOTICE`.
- NagramXF says it **contains reverse‑engineered code from closed‑source
  forks**. Record which modules these are and treat them as a legal risk.
  Prefer clean‑room versions where possible.
- Use **your own `TELEGRAM_APP_ID` / `TELEGRAM_APP_HASH`** (my.telegram.org), your
  own package name, your own FCM `google-services.json`, signing keys and Maps key.
  Also replace NagramXF's update and metadata channel IDs
  (`BaseRemoteHelper.CHANNEL_METADATA_ID`).
- Features that bypass Telegram's restrictions (anti‑delete, ignoring "restrict
  saving content", keeping TTL media, ghost mode) can get **accounts limited**.
  Ship them **off by default** with a clear warning.

---

## 5. Build and CI

- This container has **no Android SDK**. Build on **GitHub Actions**, adapting
  NagramXF's `.github/workflows/{canary,staging}.yml`.
  - JDK 17/21, Android SDK 36, NDK and CMake as pinned by NagramXF.
  - `git submodule update --init --recursive --depth=1` (tdlib, ffmpeg, boringssl,
    tgcalls, and the rest).
  - Secrets: `TELEGRAM_APP_ID`, `TELEGRAM_APP_HASH`, keystore, `google-services.json`.
- Flavors:
  - **`normal`** is the default release (no Python plugin runtime, smaller APK).
  - `plugin` (Chaquopy) is optional.
  - A future **`foss`** flavor would apply Telegraher's de‑Googling (no FCM, no
    Maps, no billing) and use UnifiedPush or a background connection instead.
- Outputs:
  - `retrogram-<ver>-arm64-v8a.apk` is the main APK for the POCO.
  - armeabi‑v7a and universal APKs are also built.
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
  - application ID such as `app.retrogram.messenger`, plus `.beta` and `.debug`
  - app name "Retrogram", launcher icon, about screen and links
  - remove NagramXF update checks and donation links, or point them at Retrogram
    channels
- Add `RetroConfig` (`retroUi`, `graher*` toggles) and a "Retrogram" settings section.
- Get CI green and sign APKs.
- **Accept when:** the CI APK installs on the POCO, logs in, and chats, calls and
  push notifications work.

### Phase 1 — Old main shell (≈2 weeks)
- `LaunchActivity` root is `DialogsActivity` again, as in 12.1.1, instead of
  `MainTabsActivity`.
  - Drawer is on by default (`navigationDrawerEnabled = true`, locked on in retro mode).
  - No bottom tabs (`mainTabsDisplayMode = 1` and the tab host is removed from the
    hierarchy, not just hidden).
- `DialogsActivity`:
  - Hamburger/back toggle in the action bar.
  - Classic title / "Connecting…" status. Replace
    `DialogsActivityStatusLayout` / `TopPanelLayout` with the 12.1.1 header.
  - Search as an action‑bar item instead of `FragmentSearchField`.
  - `FilterTabsView` folder tabs under the action bar, with the 12.1.1 stories
    header behaviour.
  - Round pencil **FAB**, plus the camera mini‑FAB for stories, instead of
    `FragmentFloatingButton`.
- The drawer is the home for everything the 12.4 tabs and overflow menu hold:
  - Contacts, Calls, Saved, Settings, New group / channel, Archived, accounts
  - new entries such as Wallet, QR share and Communities
  - NagramXF `drawerItem*` toggles keep working
- **Accept when:** side‑by‑side screenshots against 12.1.1 on the same theme match
  for the chat list, drawer, search and archive; no glass is visible.

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
  `ChatActivityTopFadeView`.
- Solid action bar. The optional 12.1‑style blur (`FLAG_CHAT_BLUR`) defaults off
  on AVERAGE devices.
- **Accept when:** a regression matrix passes. It covers private, group, channel,
  forum topic, bot, secret chat, saved messages, reply/quote, edit, voice/video
  notes, polls, paid media, and the new 13.x message types.

### Phase 3 — Old profile and settings (≈2 weeks)
- Your own profile and Settings use the 12.1.1 `ProfileActivity`
  "settings" layout, opened from the drawer. They replace `SettingsActivity` and
  `ProfileActivity2`.
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

### Phase 6 — Telegraher features (≈2 weeks)
Ported into `app.retrogram.graher`, each behind a toggle in Retrogram settings.
Toggles are off by default unless marked otherwise.

| Telegraher feature | NagramXF already has it? | Plan |
| :--- | :--- | :--- |
| Disable ads | ✅ `DisableAds` | Reuse |
| Disable remote deletions / keep deleted | ✅ Ayu "save deleted messages" | Reuse |
| Full access in "restrict saving" chats | ✅ `ignoreContentRestrictions` | Reuse |
| Real forwarded date, delete downloaded file | ✅ | Reuse |
| "Save to Downloads / Gallery" on **every** element | partial | **Port** |
| Keep cached chats after ban/kick | ❌ | **Port** |
| Keep TTL / view‑once media (private + secret) | ❌ | **Port** (warning) |
| Screenshots in secret chats | ❌ | **Port** (warning) |
| Scrambled PIN keypad | ❌ | **Port** (on by default) |
| PIN length 4–16, 3 s retry, wipe after N failures | ❌ | **Port** |
| **Duress PIN** → local wipe ("KABOOM") | ❌ | **Port** |
| Disable biometric unlock option | ❌ | **Port** |
| Same phone logged in multiple times / unlimited accounts | 10 accounts | Port the "same number" flow; raise the limit with care, since more accounts mean more RAM |
| Remove trackers / Google Vision / ML Kit / Huawei / SafetyNet / billing | ❌ | **Port into the `foss` flavor only** |
| Monet theme (Android 12+) | partial | Merge with NagramXF's Material You support |
| Remove countries from the phone list | — | **Do not port.** It breaks phone parsing for real users. |
| Emulator‑detection bypass / "legit phone" spoofing | — | **Do not port.** High ban risk, no user benefit. |

### Phase 7 — Telegra.ph integration (optional; depends on what "Telegraph" means, ≈2 weeks)
- A new Gradle module `:telegraph` derived from Telegraph X's `editor` and `base`
  modules (Kotlin, GPL‑3.0). Raise its minSdk and targetSdk to match the app.
- Entry points:
  - drawer → "Telegra.ph articles"
  - chat attach menu → "Publish article"
  - long‑press a long message → "Publish to Telegra.ph"
- Auth through the official @telegraph bot flow, using the logged‑in account.
  Store drafts locally.

### Phase 8 — Device validation on POCO X4 Pro 5G (continuous, gate for every release)
| Check | Target |
| :--- | :--- |
| Chat list and chat scroll at 120 Hz | No janky frames in a 30 s fling test. Use the upstream `FrameMetricsOverlayView` (added in 12.4) in debug builds; p95 frame ≤ 8.3 ms. |
| Cold start to chat list | ≤ 1.5 s (arm64 release, baseline profile) |
| RAM (PSS) with 3 accounts, idle in chat list | ≤ 350 MB |
| Performance class detection | Reports **AVERAGE**; medium Lite Mode preset applied |
| HyperOS background behavior | Push arrives with autostart/battery defaults; show an in‑app tip for "No restrictions" |
| Calls | Voice and video work over Wi‑Fi and 5G; no ANR on Bluetooth LE audio routing |
| Media | 1080p@60 hardware decode (HEVC/VP9), voice notes, round video with the selfie camera |
| APK size (arm64, `normal`) | Track per release; alert on > +5% growth |

Test matrix beyond the minimum device: one Android 15/16 Pixel‑class phone and one
Android 10 low‑end phone (the minSdk sanity check).

### Phase 9 — Upstream sync process (ongoing)
1. DrKLO publishes "update to X". NagramXF usually merges it within days.
2. `tools/sync-upstream.sh` fetches the new NagramXF commit and merges it into
   `main` on a `sync/X` branch.
3. CI runs the build, `glass-guard.sh` and unit tests. Any new glass UI is handled
   in retro mode before merging.
4. Smoke test on the POCO (Phase 8 checklist), then tag `retrogram-X.r<n>`.
5. Fallback if NagramXF stalls: merge DrKLO directly. Telegram history is already
   in `upstream/telegram`.

---

## 7. Risks

| Risk | Impact | Mitigation |
| :--- | :--- | :--- |
| Upstream churn in huge files (`ChatActivity`, `DialogsActivity`, `ChatActivityEnterView`) | Painful merges every release | Hooks‑and‑legacy‑package design; `glass-guard.sh`; small upstream diffs |
| NagramXF becomes inactive | Lose fast upstream syncs and its feature work | Keep a DrKLO merge path; Telegram history is local |
| Reverse‑engineered code in NagramXF | Legal / takedown | Inventory those modules; consider clean‑room versions |
| Telegram account restrictions for mod features | Users limited | Off by default, warnings, nothing that spoofs the client |
| New Telegram features built only for the glass layout | Missing entry points in the old UI | Phase 5 matrix, maintained on every sync |
| GPU cost of leftover blur on Adreno 619 | Jank at 120 Hz | blur3 choke point; AVERAGE‑class defaults; Phase 8 frame gate |

---

## 8. Immediate next steps

1. **You decide:**
   - "Telegraph" = Telegraher (assumed) or Telegra.ph (Phase 7)?
   - Final package name.
   - Whether to ship the Python `plugin` flavor.
2. Phase 0: import NagramXF history into the repo root, rebrand, add `RetroConfig`,
   and port the CI workflow with your API ID/hash and signing secrets.
3. Phase 1 spike: flip the drawer on, remove the tab host, and revive the 12.1.1
   FAB and header in `DialogsActivity`. Screenshot‑diff against 12.1.1 on the POCO.

### Useful commands
```bash
git submodule update --init                     # fetch the four reference repos (not recursive)
git -C upstream/telegram show 9cbf0333:TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java   # 12.1.1 source
git -C upstream/telegram diff --stat d7deedfa fa4e8aa0 -- TMessagesProj/src/main/java   # what 12.4 changed
git -C upstream/telegraher diff b7561f0c HEAD -- TMessagesProj/src                     # all Telegraher changes
```
