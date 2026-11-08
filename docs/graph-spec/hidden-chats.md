# Hidden chats / contacts

Evidence: not observed yet (this is a stub with the questions to answer; fill it in from the installed app, never from decompiled code)
Retrogram plan and state: WP 6.2 (built: `app.retrogram.graph.HiddenChats`)
Menu path: ______ (screenshots in ./img/hidden-chats/)

## Answer these on the phone
1. How is a chat hidden and revealed (menu entry, gesture on the header, a search keyword, a settings screen)? Retrogram: chat menu to hide, a settings row (after the PIN) to reveal.
   - Observed: ______
2. Is the secret the same as the chat lock's or its own? Is revealing temporary, and until when (screen off, app background, a timer)? Retrogram: the lock PIN; until the app is backgrounded.
   - Observed: ______
3. Do hidden chats still receive messages and notifications? What does a notification show for a hidden chat? Do unread counters (folders, app badge) include them? Retrogram: receives, notification 'New message', folder/badge counters still count them (known gap).
   - Observed: ______
4. Where do hidden chats show up anyway: search, contacts, forward/share lists, archive, calls list, widgets, shortcuts, 'people nearby'?
   - Observed: ______
5. Are contacts (people) hidden as well, or only chats? What happens to a hidden contact's calls and incoming messages?
   - Observed: ______
6. How do pinned order, folders and the archive treat a hidden chat when it is revealed again?
   - Observed: ______

## What the user sees / can configure (every option, default value)
______

## Behaviour rules (what exactly happens, edge cases, what is NOT done)
______

## Telegram API it implies (public API only)
______

## Network: does this feature contact anything other than Telegram? (host names only)
______

## Retrogram decision: reuse | build | reject (+ reason)
______
