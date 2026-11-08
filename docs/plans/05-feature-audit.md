# Phase 5 — New functionality audit

**Goal:** a living feature matrix. Every post-12.1.1 feature is *reachable* in retro mode and, where it has UI,
*styled the old way*. Closes the "features built only for the glass layout" risk.

## Method
1. List releases between 12.1.1 (`9cbf0333`) and 13.1.1 (`3de37c8c`):
   `git -C upstream/telegram log --oneline 9cbf0333..3de37c8c --grep='^update to'`.
2. For each release, diff `TL` layer additions (`TLRPC.java`, `layer` constant 216 → 232) and new top-level classes
   (`git diff --diff-filter=A --name-only <a> <b> -- TMessagesProj/src/main/java`). The API-layer diff is the most
   reliable list of *user-visible* features, since each needs a client entry point.
3. Cross-check with Telegram's public release notes for each version.
4. Write `docs/feature-matrix.md` with one row per feature:

| Feature | Since | New-UI entry point | Retro entry point | Reachable | Old-styled | Owner WP |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |

Seed rows (from PLAN.md): Gram Wallet, QR share/scan, IV / rich-text editor, AI editor, Communities, live comments,
gift crafting and auctions, polls v2, Markdown parser, OAuth sheet, summaries, stories albums.
Add NagramXF-only entries too (feed, ghost mode, bookmarks, AyuGram features): those must stay reachable as well.

## Work packages
- **WP 5.1 (M)** build the matrix, using the method above, before Phase 1 finishes, so Phase 1's drawer and
  Phase 2's menus are designed against the real list.
- **WP 5.2 (S per feature)** for each "not reachable" row, add an entry in the drawer, an overflow menu, a long-press
  menu or the attach menu, and update the row.
- **WP 5.3 (S)** on every upstream sync (Phase 8), diff the layer and add rows.
- **WP 5.4 (M)** an instrumentation test (or a manual script) that opens each entry point once.

## Status (2026-11-08)
- **WP 5.1:** `docs/feature-matrix.md` has 40 rows. Every `?` entry point was traced by grep (launch sites), and the API diff was done by comparing the sets of `TL_*` classes in `org/telegram/tgnet` at 12.1.1 and 13.1.1 (git's own
  diff is useless here because 13.x moved many TL classes between files): 306 new classes, of which the request types gave eight new rows. Nothing was run on a device, so "reachable" means "a launch site exists in the code".
- **WP 5.2:** no row needs a new entry point: every row is reachable from an existing screen or is `n/a` (system-driven, link-driven, or without any UI in 13.1.1: the bot-username requests have no caller at all). The entries Retrogram added
  itself are Wallet, Invite, Telegram Features, QR code, Downloads and the contacts log (drawer).
- **WP 5.3:** automated in `tools/sync-upstream.sh`: after a merge it prints the new TL request/type names (same class-set comparison, legacy `_layer<N>` classes filtered out) next to the new top-level classes; tested in a scratch clone against a fake upstream commit.
- **WP 5.4:** `docs/device-test-checklist.md` is the manual script (one block per switch plus the shell, chat, profile and settings checks). An instrumentation test that opens each entry point needs a device farm and a logged-in test account, so it
  is not written; the checklist is the substitute.
- **Privacy finding from the audit:** Telegram 13.x reports reading metrics (`messages.reportReadMetrics`, groups and channels) and music listening time (`messages.reportMusicListen`) to its servers. Retrogram now sends neither unless
  Settings → Retrogram → "Send usage statistics to Telegram" is on (off by default).

## Acceptance
Matrix 100% reachable and ≥ 90% old-styled; rows marked skipped carry a reason.
