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
| Classic profile tabs (experimental) | off | profiles opened afterwards |
| Classic sheets and panels (experimental) | off | screens opened afterwards |
| Plain profile header (experimental) | off | profiles opened afterwards |
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

**Auto-reply (Extra features; use a second account to write to yours)**
- [ ] With the switch on and a text set, put the app in the background and send a private message from the second account: one reply arrives; a second message within 6 hours gets none
- [ ] App in the foreground: no reply. Group message: no reply. Message to/from a bot: no reply. Switch off: no reply

**Special contacts log (Extra features)**
- [ ] Switch on, enter the numeric id of a contact who often goes online, wait: the log row shows online/offline lines; change a test account's name or photo: a name/photo line appears. Clear works

**Per-chat lock (Extra features)**
- [ ] Switch on, set a PIN, open a chat menu → Lock chat. Leave and reopen the chat from the list, from search and from a notification: PIN dialog each time (after the first correct PIN, no prompt for 60 s); wrong PIN shows a message and does not open it
- [ ] Put the app in the background and return: asked again. Notification text for the locked chat reads "New message". Unlock chat from the menu: opens freely again

**Hidden chats (Extra features; needs the chat lock PIN)**
- [ ] Switch on, open a chat → menu → Hide chat: it leaves the chat list and the forward picker; send it a message from another account: notification says "New message" and the chat stays hidden
- [ ] Settings → Retrogram → Show or hide hidden chats → PIN: the chat is back; going to the background hides it again. Unhide chat from its menu (PIN) puts it back for good
- [ ] Note any place it still shows (search, folders' unread counters, app badge) and send it back

**Downloads drawer entry**
- [ ] Drawer editor (long-press the drawer header or Settings → Retrogram/Drawer): add "Downloads"; tapping it opens the search screen on the Downloads tab; pause/resume an active file and "Pause all"

**Voice changer (Extra features)**
- [ ] Pick Robot, record a short voice note and send it: plays back as a robot voice at normal speed and length; repeat with Echo (tail after the last word), Higher and Deeper
- [ ] Pause and resume while recording, lock-record then preview before sending, and cancel: no crash, no garbage audio; a 2-minute note; note any phone heat or lag while recording

**Large files on Wi-Fi only (Extra features)**
- [ ] On mobile data, tap download on a file of 25 MB or more: toast, nothing downloads; tap again: it starts. Hold another, switch to Wi-Fi: it starts by itself. On Wi-Fi, small files, videos you play and photos behave as before

**Profile tabs (classic profile tabs on)**
- [ ] Open a user, group and channel profile with media: full-width flat tab strip, underline under the selected tab, swipe between Media/Files/Links works, long tab lists scroll

**Share sheet (classic sheets on)**
- [ ] Share a message: the caption bar and the "Send" bar are flat and full width, keyboard open/close, selecting several chats, with and without a caption; note anything cut off or too tall

**Attach sheet (classic sheets on)**
- [ ] Tap the paperclip: the Gallery/File/Location/... row is a flat full-width bar at the bottom (not a floating pill); pick photos so the caption bar appears: flat and full width, keyboard open/close; every tab still opens

**Top panels (classic sheets and panels on)**
- [ ] Start a group call or play music, then open the chat list, Calls, a forum's topic list and a profile: the call/music bar under the action bar is a flat full-width strip, not a floating rounded card

**Tab bars and bottom bars (classic sheets and panels on)**
- [ ] Channel statistics: the bottom tab bar is full width and flat; Bookmarks and Recent chats (Nagram drawer entries): the tab strip under the action bar is flat; chat search with filters; Appearance → profile colors: the bottom button sits on a solid bar

**Download window (Extra features)**
- [ ] Set the window to start a few minutes from now, tap download on a 25 MB+ file now: toast with the hours, nothing downloads; when the window opens it starts by itself (app kept open); tap again before that: starts at once
- [ ] Hold a file, then swipe the app away (kill it) and wait for the window start: the alarm wakes the app and the file downloads (it also shows under Downloading in the search → Downloads tab while held); may be a few minutes late

**Plain profile header (switch on)**
- [ ] Open your Settings page and the profile of someone with a profile color or a gift background: the header uses the theme's colors (no colored backdrop), name, status and icons are readable in light and night themes; avatar expand/collapse still works

**Gap closing (hidden chats, chat lock, contacts log)**
- [ ] A hidden chat does not show in search (type its name, and a word from its messages), recent searches or the share sheet; a hidden or locked chat is missing from a home-screen chats widget and from the system share sheet's direct-share targets; its notification has no Reply button
- [ ] Contacts log: turn on "Notify about changes", change the watched account's name: a notification appears (silence it from the system channel); the drawer entry "Special contacts log" (add it in the drawer editor) opens the log

**Classic theme colors (switch on, restart, theme "Blue" / default)**
- [ ] Chat list: blue action bar with white icons and title, white status text; search icon; folder tabs
- [ ] Chat screen action bar, profile and settings pages, sheets and dialogs: nothing unreadable (dark text on dark, white on white)
- [ ] Switch to a dark theme and back: no leftovers
- [ ] Settings → Appearance: switch between Day, Night, Dark Blue, Arctic and Blue bubbles with the option on and compare with 12.1.1 (these now load the 12.1.1 theme files); with the option off they must look as before

**Glass leftovers (retro on)**
- [ ] Any blurred or translucent panel you can find: share sheet, attach sheet, photo viewer, profile of another user, chat action bar, top panels (pinned, translate)
- [ ] Scrolling a long chat and the chat list feels smooth (note device and refresh rate)

**Not testable yet**: calls, push, `plugins=true` builds (`-plugins` APK is not uploaded by default; run the workflow manually with `plugins` and `upload` on).
