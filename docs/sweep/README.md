# WP 4.2 sweep: before/after screenshots (docs/plans/04-global-sweep.md)

One pair per screen and theme: **before** = Settings > Retrogram > "Classic sheets and panels" **off**, **after** = **on** (screens opened afterwards pick the switch up, so reopen each).
Take them with `tools/sweep/capture.sh <screen> <light|night> <off|on>` while the screen is open; the files land here as `<screen>-<theme>-<mode>.png`.
For the comparison with the real 12.1.1 look, add `<screen>-12.1.1.png` from a 12.1.1 install (same account and theme) if you have one.

| screen name | how to open it | what the "after" should show |
| :--- | :--- | :--- |
| `share-sheet` | long-press a message > Share, select a chat so the bottom bar shows | caption bar and "Send" bar flat and full width, no floating pills |
| `attach-sheet` | paperclip in any chat; also with a photo selected (caption bar) | bottom tab row (Gallery, File, Location...) flat and full width; flat caption bar |
| `top-panel-chats` | chat list with a music/voice-chat bar or a hint under the action bar | the panel is a flat strip, not a rounded card |
| `top-panel-calls` | drawer > Calls (with a music bar playing) | same |
| `top-panel-topics` | a forum group's topic list | same |
| `top-panel-profile` | a user or group profile with media | same |
| `statistics` | channel profile > Statistics | bottom tab bar flat and full width |
| `bookmarks` | drawer > Bookmarks (add it in the drawer editor first) | tab strip under the action bar flat |
| `recent-chats` | drawer > Recent chats | tab strip flat |
| `search-filters` | chat list search with filter tabs | filter strip flat |
| `peer-color` | Settings > Appearance > profile color (Premium) | bottom button on a solid bar |
| `profile-tabs` | any profile, with "Classic profile tabs" on | flat strip with an underline |

Screens read through with nothing to change (no screenshot needed unless something looks wrong): `SelectAudioAlert`, `StarGiftPreviewSheet`, `ContactsActivity`, `ChannelAdminLogActivity`, `PhotoViewer`.
