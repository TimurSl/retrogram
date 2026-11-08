# Per-chat password / PIN lock

Evidence: not observed yet (this is a stub with the questions to answer; fill it in from the installed app, never from decompiled code)
Retrogram plan and state: WP 6.1 (built: `app.retrogram.graph.ChatLockGate`, `ChatLockPolicy`)
Menu path: ______ (screenshots in ./img/chat-lock/)

## Answer these on the phone
1. Where is a chat locked and unlocked from (chat menu, long-press in the list, a settings list)? Screenshot each.
   - Observed: ______
2. Is the secret a numeric PIN, a password, a pattern, the phone's screen lock, or a biometric? Can it differ per chat or is it one secret for all locked chats? Retrogram: one numeric PIN (4+ digits), no biometric.
   - Observed: ______
3. What does the locked chat look like in the chat list (normal row, hidden text, lock icon, blurred preview)? Retrogram: unchanged row with its last message.
   - Observed: ______
4. How long does an unlocked chat stay unlocked: until leaving the chat, N seconds, until the app is backgrounded, until the app is killed? Is the timeout configurable? Retrogram: 60 s, and on app background.
   - Observed: ______
5. What do notifications of a locked chat show (sender, text, nothing, 'New message')? Is the reply action still there? Retrogram: 'New message', no reply action.
   - Observed: ______
6. Which other entry points are gated: search results, forwarding to the chat, share sheet, widgets, links, shortcuts, the chat's profile page, media of the chat in the global media view?
   - Observed: ______
7. What happens after N wrong attempts (delay, lock-out, wipe)? Is there a recovery path for a forgotten secret?
   - Observed: ______
8. Does the app claim that messages are encrypted at rest? (Retrogram does not encrypt storage and says so.)
   - Observed: ______
9. Network: does setting or checking the secret contact any server besides Telegram's?
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
