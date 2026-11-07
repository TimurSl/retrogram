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

## Acceptance
Matrix 100% reachable and ≥ 90% old-styled; rows marked skipped carry a reason.
