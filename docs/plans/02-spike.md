# Phase 2 spike: where the chat screen's glass lives (first pass)

Read from NagramXF `49ae647f` on 2026-11-07 (grep and code reading only; nothing was run). Counts are lines matching
`blur3|BlurredBackground|GlassEngine|FLAG_LIQUID_GLASS|isLiquidGlass|LiquidGlass|iBlur3|glass` (case-insensitive).

| Area | File (glass-line count) | Where the glass is built | Drawing swap enough? |
| :--- | :--- | :--- | :--- |
| Input bar | `ChatActivityEnterView` (56), `Components/chat/ChatInputViewsContainer` (new in 12.4) | `ChatActivity.createView` ~5270 creates the "island" drawable `glassBackgroundDrawableFactory.create(chatInputViewsContainer, blurredBackgroundColorProvider)`; the container pads it 7dp, radius 22dp (`INPUT_BUBBLE_RADIUS`), floats 9dp above the bottom (`INPUT_BUBBLE_BOTTOM`), plus an under-keyboard drawable (radius 29dp) and optional left/right side bubbles (`NekoConfig.iOSMessageInputField`) | **No: layout differs** (floating island with margins vs a full-width bar). Drawing becomes flat/opaque via WP 4.1; the geometry constants in `ChatInputViewsContainer` need retro values (radius 0, bottom 0, padding 0) plus a check of the horizontal margins applied by `ChatActivityEnterView`/`ChatActivity` |
| Input extras | `ChatActivityEnterView` ~3019 (AI button), ~3097 (rich-text button): each gets its own `glassBackgroundDrawableFactory.create(...)` pill | | Yes (flat after WP 4.1); position them in the classic bar order (WP 2.1) |
| Emoji / sticker panel | `EmojiView` (93) | glass tab strip | Mostly drawing, plus tab-strip layout |
| Round buttons | `Components/chat/buttons/ChatActivityBlurredRoundButton` (15), `ChatActivityBlurredRoundPageDownButton`, `chat/layouts/ChatActivity{Actions,Channel,SideControls}ButtonsLayout` | blurred circles | Drawing (solid circle + shadow) |
| Top panels | `ChatActivityTopPanelLayout` (3), `ChatActivityFadeView`, `actionBar.setupGlass(...)` (`ChatActivity` ~5250, `BlurredBackgroundProviderImpl.topPanelChatActivity`) | action bar draws a glass pill menu and a top fade | Drawing + remove the fade (`fadeDrawable.setFadeHeight`) |
| Attach sheet | `ChatAttachAlert` (146) | glass sheet and layouts | Phase 4 sweep (WP 4.2) |
| Whole screen | `ChatActivity` (170) | factories at ~3041-3064, color providers ~4077 | |

## Findings that change the plan

1. **`BlurredBackgroundProviderImpl.checkBlurEnabled(...)` is the existing "blur off → opaque colors" switch.** Every chat provider
   (input bar, top panels, messageMenu, ...) checks it and returns the theme color at alpha 255 when it is false. `RetroConfig` now makes
   it return false, which is WP 4.1 step 5 ("opaque variants") for all of them at once; `ChatActivity.blurredBackgroundColorProvider`
   (~4077) uses it too. Not covered: providers that use `BlurredBackgroundColorProviderThemed`'s default alpha (0.76) directly.
2. **The island's geometry is concentrated in `ChatInputViewsContainer`** (constants `INPUT_BUBBLE_RADIUS = 22`, `INPUT_KEYBOARD_RADIUS = 29`,
   `INPUT_BUBBLE_BOTTOM = 9`, `INPUT_SIDE_BUBBLE_SIZE = 44`, drawable padding 7dp). That is a good hook point for WP 2.1: retro
   values for these, instead of editing the 15k-line `ChatActivityEnterView`.
3. The keyboard path (`AdjustPanLayoutHelper`, `maxBottomInset`, `imeBottomInset`) lives in the same container, so retro geometry must be
   tested with Gboard and the HyperOS keyboard (risk in the Phase 2 plan).

## Next steps

- WP 2.1a: retro constants in `ChatInputViewsContainer` and a look at the horizontal margins; run on a device before widening.
- WP 2.3: classic round buttons (`ChatActivityBlurredRoundButton` first).
- WP 2.4: remove the top fade and the glass menu pill in the chat action bar.

## First implementation: opt-in "Classic input bar" (WP 2.1a, 2026-11-07, not yet run on a device)

Settings → Retrogram → "Classic input bar (experimental)", **off by default**, applies to chats opened afterwards.
When on (`RetroConfig.isClassicInput()`):
- `ChatInputViewsContainer.bubbleBottom()` is 0 instead of 9dp (the bar sits flush with the bottom; the two uses in `ChatActivity` that pad the message list follow),
- `bubbleRadius()` is 0 instead of 22dp (flat corners),
- `bubbleMargin()` is 0 instead of 7dp: the side margin of the enter view, the "bottom overlay" and the chat search container in `ChatActivity`,
  and the padding/inset of the island drawable.
Colors come from the opaque provider (WP 4.1), so the bar should read as a solid panel. Not touched: `INPUT_KEYBOARD_RADIUS` (the under-keyboard bubble),
the 12.4 inner layout of `ChatActivityEnterView` (attach/emoji/send pills), `RichEditor` which animates from the old radius, side bubbles.
What to look at on a device: bar edges against the screen edges, gap above the navigation bar (3-button and gesture nav), keyboard open/close animation,
reply/edit bars, voice/video record overlay, attach button and AI/rich-text buttons, bot keyboard, scheduled/silent long-press sheet.

