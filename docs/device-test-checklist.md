# Device test checklist (manual script, WP 5.4 first version)

**Signing (changed in the run after 22):** until run 22 every CI run signed its APK with a freshly generated key, so no CI build could be installed over another one
(Android refuses the update, or you had to uninstall and lose the local login). From the next run on, unofficial CI builds are signed with the fixed, public debug key
`tools/ci-debug.keystore` (alias/password `retrogram-ci`), so later CI APKs update in place. The first install of such a build still cannot update over an APK signed with another key.
That key is for debug builds only; releases use the real keystore from the CI secrets.

How to test a build: Actions tab → latest green "Build" run on the branch → artifact `retrogram-normal` (debug APK, signed with a throwaway key, 7-day
retention). It installs next to nothing else (package `app.retrogram.messenger`) and will not update over a differently signed build. Push notifications will not work
(placeholder `google-services.json`) and login needs a real `api_id` (open decision 2 in `docs/plans/README.md`), so what you can test depends on that.

Send me screenshots (light and night theme if you can) and say which switch was on. Switches live in N-Settings → Retrogram.

## Switches (N-Settings → Retrogram)
| Switch | Default | Applies |
| :--- | :--- | :--- |
| Classic interface | on | after restart |
| Remote update checks | off | after restart |
| Classic settings page | on | next time Settings opens |
| Classic input bar (experimental) | off | chats opened afterwards |
| Classic chat header (experimental) | off | chats opened afterwards |
| Classic emoji panel (experimental) | off | chats opened afterwards |
| Classic round buttons (experimental) | off | chats opened afterwards |
| Classic theme colors (experimental) | off | after restart |

## Checks
**Shell (tested once, 2026-11-07)**
- [x] Drawer opens, rows classic, account switcher works
- [x] Search icon in the action bar; folder pill under it
- [x] FAB: camera on the big button, pencil on the small one
- [ ] Tap search: pill opens, keyboard shows, back closes it; archive; "Connecting…" state; stories row
- [ ] Cold start, account switch and "open from notification" land on the same shell
- [ ] Back: chat → list, list with drawer open → closes drawer, root → exit
- [ ] Navigation bar: 3-button and gesture, list bottom and FAB not hidden

**Settings (classic page on)**
- [x] Page renders; rows present
- [ ] N-Settings card has no blank area (fixed in run 20)
- [ ] Every row opens its screen (`docs/settings-rows.md`); back animation; avatar change; account switcher
- [ ] "My Profile" in the drawer is unchanged and still the 13.x profile
- [ ] Turn the switch off: the new Settings screen opens

**Chat input bar (classic input on)**
- [ ] Bar is flat and full width, flush with the bottom, no gap above the nav bar
- [ ] Keyboard open/close animation, emoji panel switch, no jump
- [ ] Reply / edit / forward bar above the input
- [ ] Attach button, AI and rich-text buttons, send / voice, hold-to-record overlay
- [ ] Bot keyboard, scheduled/silent long-press sheet, search-in-chat bar, "join/mute" bottom bar
- [ ] Turn it off: the floating island returns (new chats)

**Chat header (classic chat header on)**
- [ ] Solid bar with back arrow, avatar, name and subtitle, menu; no floating pills
- [ ] First/last messages are not hidden under the bar; no fade/dim band over the list
- [ ] Pinned / translate panels under the bar, forum topics, search in chat, selection mode bar

**Emoji panel (classic emoji panel on)**
- [ ] Open emoji, GIFs, stickers: a full-width bottom bar with tab icons, search and backspace; no floating pill
- [ ] Tab switching, scrolling under the bar, backspace repeat, sticker settings button

**Round buttons (classic round buttons on)**
- [ ] Page down, mentions and reactions buttons: solid circles with a soft shadow, counter badge still readable, icon visible in light and night themes
- [ ] Reply / forward bar buttons when selecting messages, channel buttons (mute, discuss), loading spinner state on a button

**Message list chrome (retro on)**
- [ ] Sticky date pill while scrolling, "Unread messages" bar, service messages (joined, pinned) on a wallpaper: solid translucent pills like 12.1.1, light and night

**Other profiles (retro on)**
- [ ] Open a user, a group, a channel and a bot profile: header, action row, info rows, shared-media tab strip, three-dot menu; note anything floating, rounded-pill or see-through (send a screenshot)

**Classic theme colors (switch on, restart, theme "Blue" / default)**
- [ ] Chat list: blue action bar with white icons and title, white status text; search icon; folder tabs
- [ ] Chat screen action bar, profile and settings pages, sheets and dialogs: nothing unreadable (dark text on dark, white on white)
- [ ] Switch to a dark theme and back: no leftovers

**Glass leftovers (retro on)**
- [ ] Any blurred or translucent panel you can find: share sheet, attach sheet, photo viewer, profile of another user, chat action bar, top panels (pinned, translate)
- [ ] Scrolling a long chat and the chat list feels smooth (note device and refresh rate)

**Not testable yet**: calls, push, `plugins=true` builds (`-plugins` APK is not uploaded by default; run the workflow manually with `plugins` and `upload` on).
