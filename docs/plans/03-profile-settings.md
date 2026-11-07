# Phase 3 — Old profile and settings

**Goal:** own profile / Settings and every other profile use the classic 12.1.1 look; each row that
13.x added still has a home.

## What the code says (13.1.1 / NagramXF)
- The classic `ProfileActivity` **still exists** (17,053 lines; 83 `new ProfileActivity(` call sites).
  It is used for other users, groups and channels. So the "other profiles" half of this phase is a *skin*
  change on a class that is already live, not a revival.
- The **self** profile / Settings is `SettingsActivity` (8 `new SettingsActivity(` / `ProfileActivity2` call sites).
  `ProfileActivity2` is referenced only by itself and `SharedMediaLayout`: it is the 12.4 "new profile" layout.
- 12.1.1: the self settings page was `ProfileActivity` itself with `args "user_id" = self` plus the settings
  rows. Check how 12.1.1's drawer "Settings" item opened it:
  `git -C upstream/telegram show 9cbf0333:…/LaunchActivity.java | grep -n "Settings\|new ProfileActivity"`.
- Nagram adds its own settings screens under `tw/nekomimi/nekogram/settings/` (own list UI); these are not
  glass-affected but must stay reachable from the new Settings entry.

## Work packages

### WP 3.1 — Spike (1–2 days): can classic `ProfileActivity` host the self-settings layout?
Diff `ProfileActivity` 12.1.1 vs the current file and list what 12.4 changed
(`git diff --stat 9cbf0333 3de37c8c -- TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java`). Decide:
- **(a) Re-skin `SettingsActivity`**: keep it, replace its floating header and glass sections with the
  12.1.1 expanding-avatar header and `TextCell` rows. Smaller diff, since new rows already live there.
- **(b) Route self to `ProfileActivity` (self mode)**: closest to 12.1.1, but every post-12.1 row must be re-added.
Recommendation: (a) unless the spike shows the header is not separable.

### WP 3.2 — Self profile/settings (L)
- Header: big avatar, name, phone/username, camera and edit buttons, collapsing on scroll — 12.1.1 behaviour.
- Rows in the order of 12.1.1 (Account → Settings groups → Help); then new rows grouped under sensible
  headers: Wallet, Stars/Gifts, Business, Premium, Birthday, personal channel, music, rating, AI/summaries.
- Opened from the drawer (`DrawerLayoutAdapter` "Settings"); no profile tab.
- Generated checklist `docs/settings-rows.md` from `SettingsActivity` row constants
  (`grep -n "Row = rowCount++" SettingsActivity.java`) with status per row (placed/where/skipped+reason).

### WP 3.3 — Other users, groups, channels (M)
Apply the classic top-of-profile layout: no floating pill buttons; action buttons row styled as 12.1.1
(call/video/mute/search) or as 13.x equivalents drawn solid. Gift rows, wallet address, business info,
music, rating inserted as `TextCell` / `TextDetailCell` rows in the "info" section.

### WP 3.4 — Shared media / tabs (S)
`SharedMediaLayout` references `ProfileActivity2`; make sure the classic profile uses the plain
tab strip, not the 12.4 glass tab strip (`ProfileStoriesCollectionTabs` is the new stories-collections tabs; keep
the feature, restyle).

### WP 3.5 — Remove dead 12.4 routes in retro mode (S)
All `new ProfileActivity2(` / `new SettingsActivity(` call sites route through one `RetroNav` helper that picks
the classic path. No code path should open the glass version.

## Acceptance
- Row-by-row checklist (`docs/settings-rows.md`) has no "missing".
- Screenshot-diff vs 12.1.1 for: self profile, user profile, group profile, channel profile, bot profile, with
  and without stories/gifts rows.
- Admin tools (edit profile, permissions, admin log, statistics) reachable from the classic menu.

## Risks
| Risk | Mitigation |
| :--- | :--- |
| 12.4 moved logic (gifts, music, rating) into `ProfileActivity2`/`SettingsActivity`-only helpers | Spike lists these; extract to shared helpers rather than duplicating |
| Nagram's own profile hooks (`ProfileActivity` is also patched by NagramXF: AyuGram menu, ghost, ids) | `git log --oneline nagramxf/dev -- …/ProfileActivity.java` before editing to see which hooks exist |
