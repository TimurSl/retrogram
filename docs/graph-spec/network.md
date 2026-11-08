# Network behaviour of the app

Evidence: not observed yet (this is a stub with the questions to answer; fill it in from the installed app, never from decompiled code)
Retrogram plan and state: no Retrogram feature depends on it; this settles the 'rejected / local-only' rule
Menu path: ______ (screenshots in ./img/network/)

## Answer these on the phone
1. With a proxy on a throwaway account (mitmproxy or a VPN capture), record the host names only (no payloads that contain account data) contacted during: first start, login, opening the chat list, opening a chat, sending a message, and each feature above.
   - Observed: ______
2. Which hosts are not Telegram's (analytics, ads, crash reporting, update checks, config/feature flags, 'premium' or licence servers)?
   - Observed: ______
3. Does any of that traffic carry account identifiers, phone numbers, contact lists or message metadata? (The SecurityLab report claims some of it does; verify or refute.)
   - Observed: ______
4. Result feeds the decision for each feature: features that need a third-party server become 'rejected' or 'local-only' in Retrogram.
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
