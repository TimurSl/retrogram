# Auto-reply

Evidence: not observed yet (this is a stub with the questions to answer; fill it in from the installed app, never from decompiled code)
Retrogram plan and state: WP 6.3 (built: `AutoReplyEngine`, `AutoReplyHook`; settings: one text, keywords, hours, cooldown)
Menu path: ______ (screenshots in ./img/auto-reply/)

## Answer these on the phone
1. Where is it set up (settings path)? How many replies/rules can exist? Screenshot every option and its default.
   - Observed: ______
2. What triggers a reply: every first message, a keyword list, a schedule, 'when offline', 'when busy', a calendar? Which of these exist?
   - Observed: ______
3. Does it ever reply in groups, to channels, to bots, to people not in contacts? Is there a per-chat or per-person exclusion or allow list? Retrogram: private chats only, never groups, never bots.
   - Observed: ______
4. How often does it answer the same person (once, once per N hours, every message)? Retrogram: once per 6 hours by default, configurable.
   - Observed: ______
5. Does the reply look automatic to the recipient (a marker, a prefix)? Does it use Telegram's own Business quick replies / away message when the account is Premium?
   - Observed: ______
6. Does it work when the app is not running (push-triggered) or only while the app process is alive? What does the app say about account-restriction risk?
   - Observed: ______
7. Network: does the feature contact anything other than Telegram?
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
