# Phase 6 — Graph Messenger features (clean-room)

**Hard rule:** no decompiled or copied Graph Messenger code, resources or strings. Work from behaviour specs
written by someone who used the app (PLAN.md §4). Whoever writes the spec should not also be the only implementer
if that can be avoided (a second pair of eyes on the review checklist).

## Step 1 — Spec (needs a phone, ≈2 days)
Deliverable: `docs/graph-spec/<feature>.md`, one per feature, template:

```
# <Feature>
Evidence: listed | reported | verified-on-device (app version, date)
Menu path: Settings → … (screenshots in ./img/)
What the user sees / can configure (every option, default value)
Behaviour rules (what exactly happens, edge cases, what is NOT done)
Telegram API it implies (public API only)
Retrogram decision: reuse | build | reject  (+ reason)
```

Also: record **what the app sends over the network** (use a proxy on a test account) to confirm the SecurityLab
telemetry report. Retrogram ships none of it, but the spec should say which Graph features depend on third-party
servers (these become *rejected* or *local-only*).
In parallel send the GPL source request to Databite (PLAN.md §1). If source arrives, add it as `upstream/graph`
and reassess; until then everything below is clean-room.

## Step 2 — Build (each behind a toggle: Settings → Retrogram → Graph features, default off)

Verified against NagramXF (grep for the names in the first column returned nothing in `src/main`):
auto-reply, voice changer and per-chat lock do not exist. The only "hidden chats" strings are Telegram's own
community section (`CommunitySectionHiddenChats`) and the exteraGram Feed integration, which are unrelated to
Graph-style hidden chats.

| WP | Feature | Design | Size | Notes / hooks |
| :--- | :--- | :--- | :--- | :--- |
| 6.1 | **Per-chat lock** | Store a set of locked `dialogId` per account in `app.retrogram.graph.ChatLockStore` (encrypted via Android Keystore-backed `EncryptedSharedPreferences` or the app's existing `SharedConfig` passcode hash, not plain prefs). On opening `ChatActivity` for a locked dialog show a passcode/biometric gate (reuse `PasscodeView`) before `presentFragment` completes; re-lock after timeout/app background. | L | Hook points: `LaunchActivity.presentFragment`, notification tap intents, share targets, widgets. Notification previews of locked chats are redacted. Does **not** encrypt message storage; document that honestly. |
| 6.2 | **Hidden chats** | `HiddenDialogs` set; filter in `MessagesController` dialog lists / `DialogsAdapter` / search / forward & share lists; reveal by passcode or gesture on the drawer header; notifications suppressed or redacted via `NotificationsController`. | L | Interaction with folders, archive, pinned order, and unread badges must be defined in the spec. Must still receive messages. |
| 6.3 | **Auto-reply** | Rules (per chat type: private/group, schedule, keyword, once per sender per N hours) in a local DB; sender goes through the normal `SendMessagesHelper` path; global rate limit + "only when away" flag; never replies in groups unless explicitly enabled. | L | Telegram also has server-side "quick replies / away message" for Business accounts (`QuickRepliesSettingsActivity` exists): compare in the spec; for Premium users prefer reusing the business feature and only build the local one for the rest. Account-restriction risk: off by default, banner at enable time. |
| 6.4 | **Voice changer** | DSP on the recorded PCM before Opus encode: pitch shift (WSOLA/phase vocoder), robot (ring modulator), echo. Preview before send. Implement in native code beside `audio.c` (`jni/audio.c`, `genann` already there) or in Kotlin first and port if CPU-bound. | L | Hook: `MediaController` recording → `AudioRecorderSettingsActivity` for the setting. Validate CPU on SM6375 (Phase 7). |
| 6.5 | **Download manager** | `DownloadsActivity` listing `FileLoader` operations (queue, pause/resume, cancel), Wi-Fi-only and scheduled downloads, bulk save to Downloads (SAF/MediaStore). | XL | Builds on `DownloadController` and `FileLoader` (both in `org/telegram/messenger`); pause/resume needs checking what `FileLoader` supports; add a drawer entry. |
| 6.6 | **Special contacts / change log** | Watch `updateUserStatus`, `updateUserName`, photo and phone updates for a chosen set; local log (SQLite) and optional notification; screen from the drawer. | M | Subscribe via `NotificationCenter` events (`updateInterfaces` flags `UPDATE_MASK_STATUS/NAME/AVATAR/PHONE`) rather than polling. Only sees what Telegram already sends for users you share a chat with. |
| 6.7 | Ghost-mode drawer toggle | Reuse NagramXF `GhostModeActivity` (already in the drawer as `nkbtnGhostMode`); add the one-tap toggle if the spec shows one. | S | |
| 6.8 | Chat categories | Compare with folders; build only if different (spec). | — | |
| 6.9 | Profile photo download / full-size viewer | Check `PhotoViewer` save action for avatars first. | S | |
| 6.10 | Graph-style theme presets | Only if spec shows unique ones; ship as normal `.attheme` assets. | S | |

> **WP 6.3 progress (2026-11-08): decision core only.** `app.retrogram.graph.AutoReplyEngine` (pure Java, no Android deps) implements the design above: per-rule chat kind
> (groups only when explicitly on), schedule window with midnight wrap, case-insensitive keywords, once per sender per N hours, a global sliding-window rate limit, "only when away", and
> never answers itself, bots or service messages. `AutoReplyEngineTest` (7 tests) compiled and passed on the JVM with `javac` + JUnit 4.13.2 (not through Gradle). **Not done:** rule storage,
> the `SendMessagesHelper` hook, the settings screen and enable-time banner, the Business quick-replies comparison, and reconciling options/defaults with `docs/graph-spec/auto-reply.md`
> (not written, needs the phone). Nothing in the app calls it, so the feature is not reachable yet.

> **WP 6.1 and 6.6 progress (2026-11-08): decision cores only, same status as 6.3.** `app.retrogram.graph.ChatLockPolicy` (locked set, gate needed or not, relock on timeout / chat closed / app
> background, preview redaction flag) and `app.retrogram.graph.ContactChangeDetector` (per watched user snapshot diff: online/offline, name, username, photo, phone; first snapshot is a baseline).
> `ChatLockAndContactsTest` (6 tests) passes on the JVM together with the auto-reply tests (13 in total; `javac` + JUnit, not Gradle). **Not done:** Keystore-backed storage, `PasscodeView` gate and the
> `presentFragment` / notification / share / widget hooks (6.1); `NotificationCenter` feed, SQLite log, notifications and the drawer screen (6.6); toggles; specs. None of it is reachable from the app.

Reuse as-is (no work beyond toggles/doc): 10 accounts, themes, player, ghost mode, anti-delete/edit history, forward without quote.

Each built feature needs: a toggle, a unit/instrumentation test for the core logic (lock store, hidden filter,
auto-reply rule matcher + rate limiter, DSP golden-file test, watcher), and a line in the PR-review checklist:
*"Implemented from `docs/graph-spec/…`; no Graph code or assets copied."*

## Acceptance
Every row in `docs/graph-spec/` is reused / built / consciously rejected; each built one has toggle + test;
no network call to third-party analytics or ad hosts (check with `NetworkSecurityConfig` allow-list and a proxy capture).
