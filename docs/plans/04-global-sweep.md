# Phase 4 — Global sweep

**Goal:** no glass anywhere, including screens no phase restyled, and a tool that keeps it that way.

## What the code says
Counts on NagramXF `49ae647f` (`TMessagesProj/src/main/java`):

| Pattern | Count |
| :--- | :--- |
| files mentioning `blur3` | 135 |
| `LiteMode.FLAG_LIQUID_GLASS` references | 65 (≈22 files, plus `LiteMode`, `LiteModeSettingsActivity`) |
| direct `BlurredBackgroundSourceRenderNode` construction | 37 |
| `BlurredBackgroundSourceColor` uses | 104 |
| `isLiquidGlass*` helper uses | 69 |

Files with the liquid-glass flag: `MainTabsActivity`, `ChatActivity`, `ProfileActivity`, `StatisticActivity`,
`StarGiftPreviewSheet`, `SettingsActivity`, `PhotoViewer`, `PeerColorActivity`, `ChannelAdminLogActivity`,
`TopicsFragment`, `CallLogActivity`, `LiteModeSettingsActivity`, `ShareAlert`, `ChatAttachAlert`, `EmojiView`,
`SelectAudioAlert`, `DialogsActivity`, `ContactsActivity`, `LiteMode`, and the blur3 colour providers.

### The real choke point
`BlurredBackgroundSource` is an interface with one method that matters, `createDrawable()`. Two implementations:
- `BlurredBackgroundSourceColor.createDrawable()` → flat `BlurredBackgroundDrawableSource` (already solid).
- `BlurredBackgroundSourceRenderNode.createDrawable()` → `BlurredBackgroundDrawableRenderNode` (blur + optional liquid glass).

Everything made through `BlurredBackgroundDrawableViewFactory.create()` goes through `source.createDrawable()`,
so one change covers factories *and* the 37 direct sites that still call `createDrawable()`.

Two caveats the PLAN did not mention:
1. The **colour providers** (`BlurredBackgroundColorProviderThemed`, `BlurredBackgroundProviderImpl`) supply
   translucent tints designed for blur. A solid fallback with a translucent tint looks washed out, so retro
   mode must also return **opaque theme colors** (`key_windowBackgroundWhite`, `key_actionBarDefault`, panel colors).
2. Most GPU cost is in **capturing** the scrolling content into a `RenderNode` and downscaling it every frame
   (`BlurredBackgroundSourceRenderNode`, `DownscaleScrollableNoiseSuppressor`, `ViewGroupPartRenderer`). If the
   source is still fed, jank stays even though nothing is shown. The retro path must also stop the capture.

## Work packages

### WP 4.1 — Choke point (M) — **can start right after WP 0.1**
> **Status: first cut written, not build-verified (2026-11-07).** In retro mode (`RetroConfig.isRetroUi()`, no separate `noBlur` flag):
> - `BlurredBackgroundSourceRenderNode.createDrawable()` returns a `BlurredBackgroundDrawableSource` over a flat source whose color is the live
>   `key_windowBackgroundWhite`; it is not registered in `drawables`. `draw()` paints that flat color; `updateDisplayListIfNeeded()` is a no-op.
>   The color providers' ~0.76-alpha tints composite over it, so panels come out almost opaque (step 5 "opaque variants" is **not** done; check visually).
> - `LiteMode.isEnabled(FLAG_LIQUID_GLASS)` is `false` (toggle in `LiteModeSettingsActivity` still shown).
> - `RichMediaCell.glass` is off: it cast `createDrawable()` to `BlurredBackgroundDrawableRenderNode`, the only unguarded cast in the tree.
> - **`checkBlurEnabled` returns false in retro mode** (`BlurredBackgroundProviderImpl`, the existing "blur off" switch every chat/panel provider consults),
>   so those providers return opaque theme colors: step 5 is done for them (not for `BlurredBackgroundColorProviderThemed`'s default 0.76 alpha).
> - **Triage of the sites that keep their own render nodes (decision: leave for now):** `GroupCallActivity.renderNodeBlur` (call UI, also blurred in 12.1.1),
>   `QrShareView` (the QR feature's own effect, not chrome), `ProfileGalleryBlurView` (profile header; revisit in WP 3.3 together with the classic header),
>   `CommunityPendingRequestCell` (one small cell). `GlassEngine` is left alone too: its capture-triggering side is already inert and it still drives
>   color/position invalidation for the flat drawables; decide after a GPU profile.
> - **Capture stopped centrally (written, built in CI run 12):** the ~15 screens that record content themselves all feed
>   `DownscaleScrollableNoiseSuppressor`, whose `draw`, `drawInline`, `onScrolled` and `invalidateResultRenderNodes` (the hashing and downscaled
>   render-node work) now return immediately in retro mode. Those sites still call `beginRecording` and paint one `drawColor`, which is cheap.
>   Not covered: sites with their own render nodes (`GroupCallActivity`, `ProfileGalleryBlurView`, `QrShareView`, `CommunityPendingRequestCell`).
>   Steps 1 (separate `noBlur` flag), 4 and 7 (`GlassEngine`) also remain; confirm with a GPU profile on the POCO.

1. Add `RetroConfig.noBlur` (= `retroUi`).
2. `BlurredBackgroundSourceRenderNode.createDrawable()`: when `noBlur`, return a flat drawable (reuse
   `BlurredBackgroundDrawableSource` with a `BlurredBackgroundSourceColor` fed by the provider's opaque colour),
   and do not register it in `drawables`.
3. `BlurredBackgroundSourceRenderNode`: when `noBlur`, skip render-node recording (`draw`, invalidation callers).
4. `BlurredBackgroundDrawableViewFactory.setLiquidGlassEffectAllowed` → force `false`.
5. Colour providers: opaque variants under `noBlur`.
6. `LiteMode`: `isEnabled(FLAG_LIQUID_GLASS)` returns `false` under `noBlur`; hide the toggle in
   `LiteModeSettingsActivity`. Same for `FLAG_CHAT_BLUR` default on AVERAGE.
7. `GlassEngine` (`org/telegram/utils/glass/`): no-op under `noBlur`.

Keep this patch under ~150 lines so it survives merges. It also gives a quick win (sheets nobody reviewed
become solid) before Phases 1–3 are finished.

### WP 4.2 — Sweep list (L)
Open each, switch to retro mode, fix layout leftovers (padding reserved for floating bars, "glass" margins):
ShareAlert, ChatAttachAlert (incl. Gram/Rich/Poll layouts), PhotoViewer controls, ContactsActivity,
CallLogActivity, TopicsFragment, PeerColorActivity, StatisticActivity, ChannelAdminLogActivity, SelectAudioAlert,
StarGiftPreviewSheet, NagramXF `BookmarkManagerActivity`, Bulletins, ItemOptions popups, `BlurSettingsBottomSheet`.
Per screen: before/after screenshots in `docs/sweep/`.

> **WP 4.2 first step (2026-11-08, build-verified in CI run 39):** survey of the list by glass-line count (`ShareAlert` 69, `ChatAttachAlert` 146, `PhotoViewer` 75, `PeerColorActivity` 77, `BookmarkManagerActivity` 72,
> `StatisticActivity` 69, `CallLogActivity` 58, `TopicsFragment` 59, `SelectAudioAlert` 55, `ChannelAdminLogActivity` 50, `ContactsActivity` 43, `StarGiftPreviewSheet` 39, `ItemOptions` 18; `Bulletin` and `BlurSettingsBottomSheet` 0).
> Their backgrounds are already flat through WP 4.1; what is left is **geometry** (rounded pills with 9dp insets, fades). New opt-in switch **"Classic sheets (experimental)"** (Settings → Retrogram, off by default, applies to sheets opened
> afterwards) with the share sheet first: caption bar, "Send" bar and timestamp bar lose their 22dp radius and 9dp insets, and the keyboard panel loses its 29dp top corners (`ShareAlert` ~1855-1880). The bottom fade is kept (it fades into
> the opaque background). **Attach sheet (2026-11-08, build-verified in CI run 40):** with the same switch `ChatAttachAlert` draws its bottom tab bar (Gallery, File, Location, ...) as a flat full-width bar (radius 0, no 7dp inset, no side padding in the
> recycler, square outline) instead of the 28dp pill, and its caption bar, top-comment bar and keyboard panel lose their 22/29dp rounding (`ChatAttachAlert` ~2783, ~3128, ~3133, ~3545). Not covered there: the sheet's own top
> corners and drag handle, the fade (kept), the per-tab layouts (Gram/Rich/Poll). **Top panels (2026-11-08, build-verified in CI run 41):** `DialogsActivityTopPanelLayout` (the panel under the action bar that holds the call/music bar, hints and pinned items) is shared by the chat list, `CallLogActivity`, `TopicsFragment` and
> `SharedMediaLayout`; with the switch on (renamed "Classic sheets and panels") its clip path and background are square and run edge to edge (`checkBoundsAndClipping`, side extension and radius 0, the 7dp drawable inset of the four creators compensated).
> **Per-screen status of the WP 4.2 list (2026-11-08, code only: nothing was seen on a device; `docs/sweep/` stays empty until screenshots exist).** Everything below is behind "Classic sheets and panels" unless stated.
>
> | Screen | State |
> | :--- | :--- |
> | `ShareAlert` | flat caption, "Send" and timestamp bars, square keyboard panel (CI run 39) |
> | `ChatAttachAlert` | flat bottom tab bar, caption bar, top-comment bar, keyboard panel (run 40). The sheet's own top corners and drag handle are the standard `BottomSheet` ones, which 12.1.1 also had, so they stay; the Gram/Rich/Poll tab layouts are content, not floating bars |
> | Top panels (chat list, `CallLogActivity`, `TopicsFragment`, profile `SharedMediaLayout`) | one shared class, `DialogsActivityTopPanelLayout`, now flat and full width (run 41) |
> | `StatisticActivity` | full-width flat bottom tab bar (run 42) |
> | `BookmarkManagerActivity`, `ChatHistoryActivity`, chat-list search filters | flat strips through `SearchTabsAndFiltersLayout` (run 42); the 4dp side margins around the Bookmarks strip are now 0 too (this change, not yet built) |
> | `PeerColorActivity` | solid bottom bar instead of the fade (run 42); the gift tab pill is square (this change, not yet built); the premium button keeps its pill shape on purpose (it is the primary action) |
> | `SelectAudioAlert` | read through: search field and list only, no floating bar, nothing to change |
> | `StarGiftPreviewSheet` | read through: its one pill is the tab selector, kept |
> | `PhotoViewer` | read through: its glass round buttons are flat after WP 4.1 and stay round, like the classic viewer's buttons |
> | `ContactsActivity`, `ChannelAdminLogActivity` | read through: no glass factories of their own |
> | `Bulletin`, `BlurSettingsBottomSheet` | no glass code (0 matches) |
> | `ItemOptions` popups | 18 glass lines, popup backgrounds handled by the central blur stop of WP 4.1; no geometry of their own to change |
>
> What is left for this WP is only the before/after screenshots, which need a device.

### WP 4.3 — Theme (M)
> **Findings and first implementation (2026-11-07; checked by diffing, not yet seen on a device):**
> - The plan's assumption was wrong: **no `key_chats_menu*` key was dropped.** Only 10 keys exist in 12.1.1 and not in 13.1.1 (attach-sheet colors
>   `key_chat_attach{Audio,File,Gallery,Location,Poll,Todo}*`, `key_chat_goDownButtonIcon`, `key_dialogCameraIcon`); 72 keys were added (`key_glass_*`, IV rich-message,
>   tags, ...). So the palette is almost a superset.
> - **What changed is the default theme's values** (`ActionBar/ThemeColors.createDefaultColors`): of 734 keys defined in both, **123 have a different default**. The
>   biggest visible one: `key_actionBarDefault` was `0xff527da3` (blue bar, white icons/titles) in 12.1.1 and is `0xffffffff` (white bar, dark icons) in 13.1.1;
>   also `actionBarDefaultIcon/Title/Subtitle/Selector/Search*`, tab colors, `windowBackgroundWhite*Text`, `windowBackgroundGray`, switch/checkbox colors, 33 `chat_*`,
>   12 `chats_*`, profile/avatar/stories/dialog keys. So the classic *look* of the default (Blue) theme needs the old palette, not only layout work.
> - Implemented as the opt-in switch **Settings → Retrogram → "Classic theme colors (experimental)"** (off by default, needs a restart):
>   `app.retrogram.RetroThemeColors.apply()` (generated from the diff, one line per key with the 13.1.1 value in a comment) overrides `Theme.defaultColors` while the table is built.
>   `RetroThemeColors.enabled()` reads the stored preference itself because `Theme`'s static initializer can run before `RetroConfig.init()`.
> - Not covered: other built-in themes (Day, Night, Dark Blue) have their own overrides; the wallpaper and default theme *selection* (12.1.1 started on Blue); the 10 removed
>   attach keys. To check on a device: switch the app to the "Blue" (classic) theme with the switch on and compare the chat list, chat, settings and sheets with 12.1.1 and Graph 12.1.1.0.
Compare `Theme` color keys 12.1.1 vs 13.1.1 (`git diff 9cbf0333 3de37c8c -- …/ActionBar/Theme.java`); restore dropped
`key_chats_menu*` values for built-in themes; make sure the default theme set and wallpapers equal 12.1.1.

> **WP 4.3, built-in themes (2026-11-08, build-verified in CI run 45):** the built-in themes are not code tables but asset files (`assets/{arctic,bluebubbles,darkblue,day,night}.attheme`; `amoled.attheme` did not change), and they differ between
> 12.1.1 and 13.1.1 by 25 to 85 lines each (git diff stat, 150 insertions and 86 deletions in total). The 12.1.1 versions are now shipped as `assets/retro/*.attheme` (taken from commit `9cbf0333`), and with **"Classic theme colors"** on
> `Theme.getAssetFile` loads `retro/<name>` through `RetroThemeColors.themeAssetPath` (cached in the files directory as `retro_<name>`, so switching the option never reuses the other copy). Needs the restart the option already needs.
> Keys that only exist in 13.x fall back to the normal defaults, keys that only existed in 12.1.1 (the 10 removed attach-sheet keys, `key_dialogCameraIcon`, `key_chat_goDownButtonIcon`) are ignored by the theme parser. Not changed: which theme a *new
> install* starts with, and the wallpaper presets. Still only verifiable on a device: that Day, Night and Dark Blue look like 12.1.1 with the option on.

### WP 4.4 — `tools/glass-guard.sh` (S)
> **Status: written (2026-11-07).** `tools/glass-guard.sh` (no args = baseline summary, exit 0; `--strict` or a git range = exit 1 on unallowed hits).
> Allowed: files under `app/retrogram/`, lines mentioning `RetroConfig` or carrying a `glass-guard:ok` comment, files matching `tools/glass-guard.allow`.
> Baseline today: 2280 hits (2018 not allowed) over the tree, which is why CI runs it in baseline mode on ordinary builds.
> **CI wiring (written, not exercised yet because no `sync/*` branch exists):** on `sync/*` branches the workflow checks out full (blobless) history and runs
> `tools/glass-guard.sh origin/<default-branch>...HEAD` strictly, so every glass/blur3/MainTabs line an upstream merge adds must be handled, marked `glass-guard:ok`,
> or allow-listed. A bad or missing range now **fails closed** (exit 2; before, it printed "0 hits"). Checked locally: this branch against the default branch gives
> 8 hits, 0 unallowed. Not wired for PRs (a PR touching those tokens for unrelated reasons would fail without a sweep first).
Input: a git range (e.g. the diff of an upstream merge). Greps added lines for
`blur3`, `GlassTab`, `MainTabs`, `FragmentFloatingButton`, `FragmentSearchField`, `BlurredBackground`,
`LiquidGlass`, `ProfileActivity2`, `FLAG_LIQUID_GLASS`. Fails unless each hit is either under an `app/retrogram`
path, in an allow-list file `tools/glass-guard.allow` with a reason, or guarded by `RetroConfig`. CI wires it
for PRs and `sync/*` branches.

## Acceptance
- `tools/glass-guard.sh HEAD~N..HEAD` passes on the whole tree (baseline = full-tree mode).
- A video of cold start → chat → profile → share sheet → attach → photo viewer shows no blur or glass.
- Adreno 619 check: no `RenderNode` blur work in a GPU profile of chat scroll (Phase 7).
