# Phase 1 — Old main shell (drawer + chat list)

**Goal:** after login the app opens the 12.1.1-style chat list: hamburger, solid action bar,
folder tabs, round FAB, side drawer. No bottom tabs, no floating search pill, no glass.

**Reference screens:** Telegram 12.1.1 (`9cbf0333`) and Graph Messenger 12.1.1.0, same theme.

## What is already in the code (13.1.1 / NagramXF)

- `LaunchActivity` builds `sideMenuContainer` + `DrawerLayoutAdapter` already and
  `syncHomeDrawer()` (≈line 1618) toggles it from `NekoConfig.navigationDrawerEnabled`.
  `bindHomeDrawerToDialogs()` (1368) attaches the drawer to the `DialogsActivity` that lives *inside*
  `MainTabsActivity` (`getHomeDrawerDialogsActivity()`, 1349).
- `DialogsActivity` is created by `MainTabsActivity.prepareDialogsActivity()` (line 744) with
  `bundle.hasMainTabs` and a `MainTabsActivityController`. So the chat list is a *child* of the tab host.
- `DialogsActivity` still contains the drawer-aware paths (lines 14375–14456, 14840) and
  `FoldersHelper.moveFoldersToBottom()` (`foldersAtBottom`, line 557).
- New 12.4 chrome used inside `DialogsActivity`: `FragmentSearchField` (524), `FragmentFloatingButton`
  (`floatingButton3`, `floatingButtonStories`, 549–550), `DialogsActivityStatusLayout` (564),
  `DialogsActivityTopPanelLayout`, `topBubblesFadeView`.
- Still present in 13.1.1 and reusable as is: `FilterTabsView`, `DrawerLayoutAdapter`, `DrawerProfileCell`.

## Decision: how to remove the tab host (spike first, ½ day)

| Option | Change | Pros | Cons |
| :--- | :--- | :--- | :--- |
| **A. Keep `MainTabsActivity` as a one-tab host, tab bar not attached** | `MainTabsConfigManager` returns only `CHATS` in retro mode; `MainTabsLayout` is never added to the hierarchy; `hasMainTabs=false` | Zero edits to the ~20 `instanceof MainTabsActivity` sites in `LaunchActivity` (687, 1295, 1326, 4133, 4146, 4310, 4623, 5375, 8032, 8053, 8837, 9527, 9644, 9757) and to `ActionBarLayout`, `TopicsFragment`, `ContactsActivity`, `CallLogActivity`, `MessagesController`, `LinkManager` | An extra pager/host layer between `ActionBarLayout` and the chat list: cost in memory and transitions; swipe gestures between tabs must be off |
| **B. Root is `DialogsActivity` again (12.1.1 behaviour)** | New `RetroShell.createRoot()` and `LaunchActivity.isMainRoot(BaseFragment)` replace every `instanceof MainTabsActivity` | Matches 12.1.1 exactly; leaner view tree | Touches the sites above; they are merge-conflict magnets |

**Recommendation:** spike A. If the screenshot-diff and a scroll/transition profile on the POCO match 12.1.1,
ship A in Phase 1 and leave B as a later optimisation. Only do B if A shows extra frame cost or gesture bugs.
The PLAN's "tab host removed from the hierarchy" is satisfied by A as long as `MainTabsLayout` is never added.

## Work packages

### WP 1.1 — Shell switch (M, after spike)
1. `RetroConfig.retroUi` read in `LaunchActivity` at startup (WP 0.5).
2. Force in retro mode: `navigationDrawerEnabled=true` (hide the toggle in `SidebarMenuActivity`, line 206/360 and
   `NekoAppearanceSettingsActivity`, 277), `mainTabsDisplayMode=1`, `foldersAtBottom=false`.
3. Implement option A/B as decided. Keep `bindHomeDrawerToDialogs()` working (it finds the dialogs fragment).
4. Account switching (`switchToAccount`, 1295/1326) and push/intent cold starts (4133–4151) must create the same shell.

### WP 1.2 — `DialogsActivity` header (L)
Compare against `git show 9cbf0333:…/DialogsActivity.java` (13,524 lines vs 14,483 now).
1. Action bar: `ActionBar` menu icon (hamburger / back-arrow morph via `MenuDrawable`), title, "Connecting…/Updating…"
   status via the old `setTitle` + `actionBar.setTitleAnimated` path. Gate `dialogsActivityStatusLayout`
   creation on `!retroUi`.
2. Search: classic `ActionBarMenuItem` with `setIsSearchField(true)` in place of `FragmentSearchField`;
   re-wire to the same search `ViewPage` the new field drives (`SearchViewPager`/`searchItem` flow).
3. Stories header: classic `DialogStoriesCell` in the action bar area (check which of the two exists
   in 13.1.1 and whether 12.4 changed its container).
4. `FilterTabsView` under the action bar (`foldersAtBottom` forced off); check the 12.4 changes to its
   background drawing (`git diff 9cbf0333 3de37c8c -- …/FilterTabsView.java`).
5. Remove `topBubblesFadeView` / `DialogsActivityTopPanelLayout` glass in retro mode, keep the *function*
   (archive hint, proxy/connection banner, birthdays, etc.) as solid panels.

### WP 1.3 — FAB (S)
Round pencil FAB plus the camera mini-FAB for stories, replacing `floatingButton3` / `floatingButtonStories`.
In 12.1.1 this was a plain `FrameLayout floatingButtonContainer` (`DialogsActivity:419`, no `FloatingActionButton`
class exists in either tree), built with `Theme.createSimpleSelectorCircleDrawable` and animated by
`hideFloatingButton`. Port that block under `retroUi`, or (smaller diff) make `FragmentFloatingButton` draw a solid
circle in retro mode — check its API first and pick whichever touches fewer `DialogsActivity` lines.

### WP 1.4 — Drawer content (M)
`DrawerLayoutAdapter` already lists NagramXF items (ghost mode, browser, restart, N-settings: ids
`nkbtn*`; `NaConfig.drawerItem*`). Required entries: accounts, My Profile/Stories, New Group/Secret/Channel,
Contacts, Calls, Saved Messages, Settings, Invite, Telegram Features, plus the post-12.1 items that used to
live on the tab bar: **Archived chats** (if not elsewhere), **Wallet**, **QR share**, **Communities**, **Feed**
(`com.exteragram.messenger.feed.ui.FeedActivity` is NagramXF's). Add each new item with a feature flag so
Phase 5 can tick it off.

### WP 1.5 — Cleanup of 12.4 leftovers on this screen (S)
Hide in retro mode the search/FAB glass layers that remain in the layout `onMeasure/onLayout` code
(lines ~1073, ~1102, ~1353) so no empty offsets remain at the top or bottom.

## Acceptance
- Screenshot-diff (same theme, both light and night) of: chat list, chat list with folder tabs, drawer closed/open,
  search open, archive, "Connecting…" state, story row visible/hidden, empty state — vs 12.1.1 and Graph 12.1.1.0.
- Cold start, account switch and open-from-notification each land on the same shell.
- `grep -n "FragmentSearchField\|FragmentFloatingButton\|MainTabsLayout" ` shows them only on non-retro paths.
- Back-button/gesture behaviour: back from chat → list, back from list with drawer → closes drawer, back on root → exit.

## Risks
| Risk | Mitigation |
| :--- | :--- |
| Edge-to-edge insets / navigation bar color: 12.4 changed insets handling for the floating bars | Compare `fitsSystemWindows`/`setOnApplyWindowInsetsListener` code in `DialogsActivity` and `ActionBarLayout` between 12.1.1 and now; test gesture and 3-button nav |
| Tablet/landscape split view (`layersActionBarLayout`, `rightActionBarLayout`) | Test on a tablet emulator; 12.1.1 behaviour is the target |
| `ContactsActivity`/`CallLogActivity` expect `hasMainTabs`-style padding | Pass `false` in retro mode; verified in Phase 4 sweep |
