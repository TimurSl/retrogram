# Phase 2 — Old chat screen

**Goal:** `ChatActivity` looks and behaves like 12.1.1, with every post-12.1 capability still reachable.

Sizes (lines): `ChatActivity` 44,779 → 47,699; `ChatActivityEnterView` 13,452 → 15,750; `EmojiView` 9,280 → 10,351.
Because the diffs are large (PLAN.md §2: ChatActivity +8.4k/−5.5k), the approach is **"re-skin the new
controllers, don't restore the old file"**. Revive 12.1.1 *layout and drawing* code only.

Real class names (verified): `ChatActivityEnterView`, `Components/chat/buttons/ChatActivityBlurredRoundButton`,
`ChatActivityTopPanelLayout`, `FragmentFloatingButton`, `GlassEngine` (`org/telegram/utils/glass`), and the
`blur3` package. There is **no** `ChatActivityTopFadeView` class in this tree (PLAN.md named it by mistake).

## Spike (2 days) — measure before committing
For each of the four areas below, list: (a) the glass-specific code (grep `blur3`, `GlassEngine`,
`BlurredBackground*`, `FLAG_LIQUID_GLASS`, `isLiquidGlass*`), (b) the new feature code interleaved with it,
(c) whether a *drawing-only* swap is enough (solid `Theme.key_chat_messagePanelBackground` + shadow line) or the
**layout** differs (pill with margins vs full-width). Output: a table in `docs/plans/02-spike.md`.

## Work packages

### WP 2.1 — Input panel (XL)
- Full-width solid bar: background `key_chat_messagePanelBackground`, top divider/shadow as drawn in the 12.1.1 `ChatActivityEnterView` (find it with `git show 9cbf0333:…/ChatActivityEnterView.java | grep -n Shadow`),
  edit-text area without pill margins/radius, attach button left, emoji left, send/voice right (12.1.1 order).
- Keep new controls reachable: AI editor, rich-text toolbar, scheduled/silent (long-press send), paid messages,
  suggested posts, bot keyboard, "send as". Put them where 12.1.1 put the nearest equivalent
  (attach menu, long-press menu, `ActionBarMenu` overflow) and record each placement in the Phase 5 matrix.
- Keep Nagram's input extras (`NekoConfig` send options, formatting bar) and verify they align to the new bar.
- Approach: add `if (RetroConfig.retroUi)` branches in the view construction/measure paths of
  `ChatActivityEnterView` and move 12.1.1-only drawing helpers to `app.retrogram.ui.legacy`.

### WP 2.2 — Emoji / sticker / GIF panel (L)
Solid 12.1.1 tab strip (`EmojiView`'s bottom/top tab bar), no glass tabs; search field in-panel as before.
Check the new content types added since (stickers/emoji packs, "profile" tab) are still reachable in the tab strip.

### WP 2.3 — Round buttons (M)
Replace `ChatActivityBlurredRoundButton` (page down, mentions, reactions) with the classic
`ChatActivity` floating buttons (solid circle, shadow, counter badge). Keep the new ones (e.g. any "live comments"
or "AI" buttons that exist in 13.x) as small solid circles stacked above.

### WP 2.4 — Top panels and bars (L)
`ChatActivityTopPanelLayout` → classic stacked panels: pinned message, group-call, translate, "report spam",
"join/mute" bottom bar, topic header. Solid backgrounds, divider lines; no top fade.
Action bar solid; `FLAG_CHAT_BLUR` (12.1-style blur) default **off** on AVERAGE-class devices.

### WP 2.5 — Message list chrome (M)
Date pill ("sticky date"), unread-messages bar, scroll-to-reply highlight and the blurred "service" bubble
backgrounds: keep the 12.1.1 drawing (`Theme.chat_actionBackgroundPaint`, not blur3-based ones).

## Regression matrix (acceptance)
Run each on the POCO, light + night, with a rich theme and the default:

| Chat type | Things to check |
| :--- | :--- |
| Private, Saved Messages | input bar, send, voice/video note, attach, reply/edit/forward bar, search in chat |
| Group, Supergroup | mentions/reactions buttons, pinned bar, group call bar, admin tools |
| Channel | join/mute bar, comments button, discussion, live comments (13.x) |
| Forum topic | topic header/tabs, topic list, general topic |
| Bot | menu button, inline keyboard, web app button, bot start |
| Secret chat | TTL, screenshot notice |
| Polls, paid media, stories, gifts, 13.x message types (summaries, AI-edited, rich text) | render + send flow |
| Scheduled / silent / "send when online" | long-press send sheet |
| Emoji/sticker/GIF panel | open/close animations, keyboard switch, search |

Frame gate: p95 ≤ 8.3 ms in a 30 s chat fling (Phase 7).

## Risks
| Risk | Mitigation |
| :--- | :--- |
| `ChatActivityEnterView` changes weekly upstream | Keep Retrogram branches in dedicated methods; `glass-guard.sh` flags new uses |
| Keyboard/IME animations tied to the pill layout (`AdjustPanLayoutHelper`, 12.4 changes) | Compare insets code and test Gboard + HyperOS keyboard |
| Features that exist only as buttons in the pill | Phase 5 matrix is the checklist; no WP closes until each is reachable |
