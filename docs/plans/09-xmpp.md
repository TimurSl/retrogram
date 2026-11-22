# Feature track — XMPP client inside Retrogram

**Goal:** chat over XMPP from inside the app, next to Telegram, with several accounts. Independent of the UI phases.

Code: `TMessagesProj/src/main/java/app/retrogram/xmpp/`

| Package | What |
| :--- | :--- |
| `core` | No Android types. Smack session, controller (storage rules, de-duplication, read state, notification rule), store interface, certificate pinning. Runs on a JVM. |
| `app.retrogram.xmpp` | Android glue: `XmppAccounts` (JSON in private prefs, password encrypted with an Android Keystore AES-GCM key), `XmppDatabase` (SQLite), `XmppManager` (one controller per account, network callback, UI-thread event fan-out), `XmppService` (foreground service, `specialUse`), `XmppNotifications`, `XmppFiles`, `XmppAvatars`. |
| `ui` | Screens: `XmppChatsActivity` (chat list, entry point), `XmppChatActivity`, `XmppContactsActivity`, `XmppAccountsActivity`, `XmppAccountEditActivity`, `XmppParticipantsActivity`, `XmppRoomsActivity`, plus cells and dialogs. `XmppLinks` handles notification taps and `xmpp:` links. |

Entry points: drawer item **XMPP** (id `nkbtnXmpp`; on in the default layout, hidden for existing installs until enabled in the sidebar manager), Settings → Retrogram → XMPP, `xmpp:` links, notifications. The drawer only exists once a Telegram account is logged in.

Library: Smack 4.5.0 (`smack-android`, `smack-android-extensions`, `smack-tcp`, `smack-experimental`), Maven Central. R8 keep rules are in `proguard-rules.pro`.

## What works (by code path)

| Area | Detail | Standard |
| :--- | :--- | :--- |
| Accounts | several at once; add with a test login before saving; optional in-band sign-up; edit, disconnect, remove (from the app, or also on the server); change password; profile name and picture (vCard) | XEP-0077, XEP-0054 |
| Connection | STARTTLS required (default), direct TLS, STARTTLS optional; SRV lookup or explicit host/port; SCRAM/PLAIN; stream management with resumption; ping; reconnect with back-off, and at once when the network returns; permanent failures (bad password, refused certificate) do not retry | RFC 6120, XEP-0198, XEP-0199, XEP-0368 |
| Certificates | a certificate the platform refuses is shown with its SHA-256 fingerprint and can be trusted for that account only; the pin is dropped when the host or security mode changes | – |
| Contacts | roster, presence (show + status), add, rename, remove, groups, accept/decline requests (a request from someone you added is accepted automatically), block/unblock | RFC 6121, XEP-0191 |
| Chat | send/receive, edit (correction), reply with quote, delivery receipts, "displayed" markers, typing, carbons, offline queue (sent after the next login), local delete | XEP-0184, XEP-0333, XEP-0085, XEP-0280, XEP-0308, XEP-0461 |
| History | archive catch-up after each login (de-duplicated against live messages), "load earlier" from the archive | XEP-0313 |
| Groups | join, create, password, nickname, subject, participants (role/affiliation), invitations in and out, private messages to occupants (kept in their own chat), bookmarks synced and auto-joined on all devices, public room browser, mention-only notifications by default | XEP-0045, XEP-0048 |
| Files | send any file through the server's upload service with progress; links to images load in the chat (automatically only from contacts, from your own server, or when you sent them; otherwise tap to load) | XEP-0363, XEP-0066 |
| Notifications | per-conversation, muted, mention rules, tap opens the chat, ongoing "connected" notification while the background service runs (setting) | – |

## Not built

End-to-end encryption (OMEMO, OpenPGP), voice/video calls, message retraction and reactions, MIX, voice-message recording (a recorded file can be attached), push (FCM/UnifiedPush) for a stopped app, room administration (kick, ban, affiliations), a global message search, drafts, forwarding. Messages are encrypted in transit only.

## How it was checked

| Check | Result |
| :--- | :--- |
| `core` against a real server (Prosody 0.12 on localhost: STARTTLS and direct TLS, registration, carbons, MAM, MUC, `http_file_share`, blocklist, vCard, bookmarks): `XmppIntegrationTest`, 22 tests | all pass in the sandbox. They are **skipped** unless `XMPP_TEST_SERVER` is set, so CI does not run them. |
| Pure logic: `XmppAccountConfigTest` | passes |
| SQLite schema and the queries `XmppDatabase` runs | executed against SQLite 3.53 with Python; **not** run on Android |
| Android and UI code | type-checked with `javac` against the Android 16 framework jar, Smack, and the Telegram sources (with a generated `R`): 0 errors. **Never compiled by Gradle, never run on a device or emulator.** Kotlin edits (`DrawerMenuHelper.kt`, `MainMenuActions.kt`) are one-liners and not compiled at all. |
| Gradle dependency resolution, R8 keep rules, `specialUse` service on Android 14+, notification and foreground-service behaviour | **not checked** |

First thing to do on a device: add an account, send a message both ways, rotate, kill the app and reopen, tap a notification, upload a file.

## Notes for later

- Smack's `smack-experimental` registers a provider for `<fallback xmlns="urn:xmpp:fallback:0"/>` that drops the XEP-0428 `for` attribute and body ranges, so reply quotes are read from the leading `> ` lines of the body instead. Outgoing replies still carry the full XEP-0461 fallback element.
- Message order uses the whole second of the timestamp, then insertion order: servers stamp archive entries with one-second resolution.
- `XmppDatabase` has no migrations yet (version 1).
- Contact avatars are fetched with vCard-temp on demand and cached for a day; XEP-0153 hashes in presence are not used.
