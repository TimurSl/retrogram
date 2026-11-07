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

### WP 4.3 — Theme (M)
Compare `Theme` color keys 12.1.1 vs 13.1.1 (`git diff 9cbf0333 3de37c8c -- …/ActionBar/Theme.java`); restore dropped
`key_chats_menu*` values for built-in themes; make sure the default theme set and wallpapers equal 12.1.1.

### WP 4.4 — `tools/glass-guard.sh` (S)
> **Status: written (2026-11-07).** `tools/glass-guard.sh` (no args = baseline summary, exit 0; `--strict` or a git range = exit 1 on unallowed hits).
> Allowed: files under `app/retrogram/`, lines mentioning `RetroConfig` or carrying a `glass-guard:ok` comment, files matching `tools/glass-guard.allow`.
> Baseline today: 2280 hits (2018 not allowed) over the tree, which is why CI runs it in baseline mode only. Wire it as a strict range check
> for `sync/*` branches and PRs once the sweep has shrunk the baseline (CI's "Glass guard" step already runs it when the file exists).
Input: a git range (e.g. the diff of an upstream merge). Greps added lines for
`blur3`, `GlassTab`, `MainTabs`, `FragmentFloatingButton`, `FragmentSearchField`, `BlurredBackground`,
`LiquidGlass`, `ProfileActivity2`, `FLAG_LIQUID_GLASS`. Fails unless each hit is either under an `app/retrogram`
path, in an allow-list file `tools/glass-guard.allow` with a reason, or guarded by `RetroConfig`. CI wires it
for PRs and `sync/*` branches.

## Acceptance
- `tools/glass-guard.sh HEAD~N..HEAD` passes on the whole tree (baseline = full-tree mode).
- A video of cold start → chat → profile → share sheet → attach → photo viewer shows no blur or glass.
- Adreno 619 check: no `RenderNode` blur work in a GPU profile of chat scroll (Phase 7).
