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
> **Findings (2026-11-07, code reading only) and a first implementation, not run on a device:**
> - 12.1.1's drawer "Settings" was `ProfileActivity` with `user_id` = self (`LaunchActivity.openSettings`); `SettingsActivity` did not exist (added whole, +2156 lines, in 12.4).
> - 13.1.1's `ProfileActivity` **still contains the whole self-settings page** (Chat, Privacy, Notifications, Data, Lite mode, Folders, Devices, Language, Premium, Stars,
>   TON, NagramX rows, `nekoRow`, `idDcRow`): it is built when `isUserSelf && !myProfile`. It is dead code only because `onFragmentCreate` forces `myProfile = true` for the own user.
>   (NagramXF keeps extending that branch, so it is probably still maintained.) So option (b) "route self to `ProfileActivity`" is mostly a routing change.
> - Implemented as an **opt-in** switch, off by default: Settings → Retrogram → "Classic settings page". `app.retrogram.RetroNav.settingsFragment()` returns
>   `ProfileActivity(user_id=self)` instead of `SettingsActivity` at the drawer, deep links (`LinkManager`, 2 sites), `LaunchActivity` open_settings and the profile's settings
>   button; `ProfileActivity` skips the `myProfile` override for the own user in that mode. `my_profile=true` callers (chat header, Stars sheets, drawer "My profile") are unchanged.
>   Left alone: the saved-state restore of `SettingsActivity` ("settings2" in `LaunchActivity`).
> - **First device test (2026-11-07, run 17 APK, screenshots from the project owner):** the classic page works. It shows the 13.x profile header (peer-color
>   background, avatar, name, status, camera button), the Account block (phone, username, DC/ID row, bio), the N-Settings card and the full "Settings" list:
>   Chat Settings, Privacy and Security, Notifications and Sounds, Data and Storage, Power Saving, Chat Folders, Devices, Language, Telegram Premium, My Stars,
>   My Grams, Telegram Business, Send a Gift. So `docs/settings-rows.md` is covered on this page. It is **not** the 12.1.1 header (no expanding avatar yet).
>   One bug seen: the N-Settings card had a large empty block under its row. Cause: `nekoSectionRow` (the divider after it) was missing from the shadow-cell list
>   in `ProfileActivity.getItemViewType`; fixed. The switch is now **on by default** (Settings → Retrogram → Classic settings page turns it off).
>   Still untested: back/transition animations, stories row, account switcher, and "My profile" unchanged.
> - To check on a device (original list): the page renders (header, avatar, phone/username/bio rows, every row of `docs/settings-rows.md`), back/transition animations, stories row, account switcher,
>   and that "My profile" is unchanged. If it works, flip the default and move on to the header re-skin; if not, the switch can stay off.
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

> **WP 3.3 findings (code reading, 2026-11-08, nothing run):** `ProfileActivity` builds its glass factory `iBlur3FactoryLiquidGlass` (~2192) but the liquid-glass effect
> flag is forced off in retro mode (`LiteMode.isEnabled(FLAG_LIQUID_GLASS)` returns false), and the other profile surfaces are plain classic views (`TextCell`/`TextDetailCell` rows, the
> classic avatar header and action row). The remaining glass sites are the three-dot menu (`otherItem.setBlurredBackgroundFactory(scrimBlur3Factory, ...)`, ~4217: popup, handled by the
> central blur stop of WP 4.1) and the bottom "add post" buttons of the own profile (~3681), which use a solid `BlurredBackgroundSourceColor` already. So no code branch is planned until a
> device screenshot of a user, group and channel profile shows a leftover. Added to `docs/device-test-checklist.md`.

> **WP 3.5 (2026-11-08, build-verified in CI run 39):** `ProfileActivity2` has no constructor call anywhere outside its own file, so it is already unreachable. The remaining direct constructions of `SettingsActivity`
> now go through `RetroNav.settingsFragment()`: the drawer-helper "Settings" item (`MainMenuActions`), the saved-state restore ("settings2" in `LaunchActivity`) and the Settings tab of `MainTabsActivity` (when classic settings is on).
> `SettingsActivity` itself stays reachable only with the "Classic settings page" switch off.
> **WP 3.2 (header), 2026-11-08, written, not build-verified:** the classic Settings page is `ProfileActivity`'s own header, shared with all profiles, so a header change applies to all of them. The one difference the owner's screenshots showed
> against 12.1.1 is the peer-color background (the person's profile color or collectible-gift backdrop, with white icons) instead of the theme's action-bar colors. New opt-in switch **Settings → Retrogram → "Plain profile header (experimental)"**
> (off by default, applies to profiles opened afterwards): `ProfileActivity` sets `peerColor = null` at its two assignment sites (user and chat) when it is on, which is the same state as a person with no profile color, so the header, status text,
> action icons and buttons all fall back to the theme colors through the code that already handles that case. Not changed: the avatar expand/collapse animation and the layout of the header (both already as in the classic profile). If other header
> differences matter, send a Settings screenshot next to a 12.1.1 one.

### WP 3.4 — Shared media / tabs (S)
`SharedMediaLayout` references `ProfileActivity2`; make sure the classic profile uses the plain
tab strip, not the 12.4 glass tab strip (`ProfileStoriesCollectionTabs` is the new stories-collections tabs; keep
the feature, restyle).

> **WP 3.4 (2026-11-08, build-verified in CI run 39):** the profile's shared-media tab strip (`ScrollSlidingTextTabStrip` inside `SharedMediaLayout`) was the 12.4 rounded floating pill (clip path, glass background, pill selector).
> New opt-in switch **Settings → Retrogram → "Classic profile tabs (experimental)"** (off by default, applies to profiles opened afterwards): `ScrollSlidingTextTabStrip.setClassicStyle(true)` restores the commented-out 12.1.1
> drawing (underline selector with `key_actionBarTabLine`, no pill, no stroke), and `SharedMediaLayout` adds the strip full width with a solid `windowBackgroundWhite` background at the same 50dp height. Only strips that ask for it
> change; the chat-list folder tabs and others using the class are untouched. Not covered: the pill-shaped `topPanelLayout` (call/music bar) and the search-tags list in `SharedMediaLayout`, which keep their glass drawables.

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
