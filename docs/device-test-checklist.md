# Device test checklist (manual script, WP 5.4 first version)

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

**Classic theme colors (switch on, restart, theme "Blue" / default)**
- [ ] Chat list: blue action bar with white icons and title, white status text; search icon; folder tabs
- [ ] Chat screen action bar, profile and settings pages, sheets and dialogs: nothing unreadable (dark text on dark, white on white)
- [ ] Switch to a dark theme and back: no leftovers

**Glass leftovers (retro on)**
- [ ] Any blurred or translucent panel you can find: share sheet, attach sheet, photo viewer, profile of another user, chat action bar, top panels (pinned, translate)
- [ ] Scrolling a long chat and the chat list feels smooth (note device and refresh rate)

**Not testable yet**: calls, push, `plugins=true` builds (`-plugins` APK is not uploaded by default; run the workflow manually with `plugins` and `upload` on).
