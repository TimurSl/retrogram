# Device test checklist (manual script, WP 5.4 first version)

**Signing (changed in the run after 22):** until run 22 every CI run signed its APK with a freshly generated key, so no CI build could be installed over another one
(Android refuses the update, or you had to uninstall and lose the local login). From the next run on, unofficial CI builds are signed with the fixed, public debug key
`tools/ci-debug.keystore` (alias/password `retrogram-ci`), so later CI APKs update in place. The first install of such a build still cannot update over an APK signed with another key.
That key is for debug builds only; releases use the real keystore from the CI secrets.

How to test a build: Actions tab → latest green "Build" run on the branch → artifact `retrogram-normal` (debug APK, signed with a throwaway key, 7-day
retention). It installs next to nothing else (package `app.retrogram.messenger`) and will not update over a differently signed build. Push notifications will not work
(placeholder `google-services.json`) and login needs a real `api_id` (open decision 2 in `docs/plans/README.md`), so what you can test depends on that.

Since 2026-11-22 all the classic switches below are on by default (fresh installs; a stored choice is kept). Send me screenshots (light and night theme if you can) and say which switch was on. Switches live in N-Settings → Retrogram.

## Switches (N-Settings → Retrogram)
| Switch | Default | Applies |
| :--- | :--- | :--- |
| Classic interface | on | after restart |
| Remote update checks | off | after restart |
| Classic settings page | on | next time Settings opens |
| Classic input bar (experimental) | on | chats opened afterwards |
| Classic chat header (experimental) | on | chats opened afterwards |
| Classic emoji panel (experimental) | on | chats opened afterwards |
| Classic round buttons (experimental) | on | chats opened afterwards |
| Classic theme colors (experimental) | on | after restart |

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
- [ ] Inline search box (top of each page): normal grey box, readable hint/text in light and night; tab, category-strip and sticker-set-name colors

**Round buttons (classic round buttons on)**
- [ ] Page-down, mentions and reactions: solid circle (white on the default theme) with a soft shadow, arrow/icon visible, counter badge above it
- [ ] Press feedback, loading spinner while jumping to a far message, search-in-chat up/down arrows, reply/forward buttons in selection mode, channel buttons
- [ ] Night theme: circle and icon contrast; turn it off: glass circles return (new chats)

**Classic theme colors (switch on, restart, theme "Blue" / default)**
- [ ] Chat list: blue action bar with white icons and title, white status text; search icon; folder tabs
- [ ] Chat screen action bar, profile and settings pages, sheets and dialogs: nothing unreadable (dark text on dark, white on white)
- [ ] Switch to a dark theme and back: no leftovers

**Top panels, attach and share sheets (retro on; added 2026-11-22, not yet seen)**
- [ ] Chat list: call bar / archive hint / auth hint under the folder tabs are a flat full-width strip, no rounded card, no fade band under the tabs
- [ ] Attach sheet (paperclip): bottom tab bar is a full-width solid strip, no floating pill, no fade behind it; tabs still tappable and scroll if many
- [ ] Share sheet: caption bar, "Send" bar and the emoji panel under the caption are flat and full width, no fade at the bottom

**Restored chat-list changes and message side buttons (added 2026-11-23, not yet seen)**
- [ ] Opening the side panel moves and dims the chat list a little (phones); closing returns it
- [ ] Pinned chats have a slightly lighter band; status dot on avatars (grey offline, accent online)
- [ ] Three-dots menu on the chat list lists Saved Messages, Archive, Feed, Hashtags+, Favorites, Games, Scan QR, In-app browser
- [ ] Classic input on: the area behind the system navigation bar is the input bar colour, not the wallpaper
- [ ] Settings > Retrogram > "Edit button beside your messages" on: a pencil sits left of your own editable messages; tap starts editing; no pencil on polls, stories, messages past the edit window
- [ ] "Save button beside messages" on (default): a bookmark sits right of other people's messages in groups and private chats (not in channels, which keep the forward arrow, nor in Saved Messages or no-forward chats); tap forwards to Saved Messages and shows the undo bar
- [ ] Message menu "Add to Favorites" (star) on a message; open Favorites from the three-dots menu, the message is listed. Delete or clear that chat, reopen Favorites: the text/caption copy and the chat name are still there (media file only if still cached)

**Classic tab strips and header colour (retro on; added 2026-11-27, not yet seen)**
- [ ] Chat list with folders: tabs sit on a full-width strip in the action bar colour (blue with Classic theme colors), a white 4dp line under the selected folder's name, no rounded pill; switching folders slides the line; unread counters readable
- [ ] Chat list header (status bar, action bar, stories row) is one solid colour: blue with Classic theme colors, white with them off; icons and title readable; the "+" ring on your story avatar blends in
- [ ] Select a chat (long press): header and tabs turn to the selection colours; cancel: back to blue, counters still readable
- [ ] Archive: grey (archived) header with white icons (Classic theme colors on)
- [ ] Search in the chat list: white header, Chats / Media / Links ... tabs with an underline (no pill), full width
- [ ] Profile of a user or group, scroll down to media: Media / Files / Links / Voice ... tabs fill the width on a white strip with a thin line under it and an underline under the selected tab; no floating pill; the first row of media is not hidden under the tabs
- [ ] Settings > Data and Storage > Data usage, and a channel's Boosts page: underline under the selected tab

**Glass leftovers (retro on)**
- [ ] Any blurred or translucent panel you can find: share sheet, attach sheet, photo viewer, profile of another user, chat action bar, top panels (pinned, translate)
- [ ] Scrolling a long chat and the chat list feels smooth (note device and refresh rate)

**Not testable yet**: calls, push, `plugins=true` builds (`-plugins` APK is not uploaded by default; run the workflow manually with `plugins` and `upload` on).
