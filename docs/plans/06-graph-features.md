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
> never answers itself, bots or service messages. `AutoReplyEngineTest` (7 tests) compiled and passed on the JVM with `javac` + JUnit 4.13.2 (not through Gradle). **Not done at that time (superseded: the settings, hook and rule options were added later, see the 6.3 notes below and STATUS.md):** rule storage,
> the `SendMessagesHelper` hook, the settings screen and enable-time banner, the Business quick-replies comparison, and reconciling options/defaults with `docs/graph-spec/auto-reply.md`
> (not written, needs the phone). Nothing in the app calls it, so the feature is not reachable yet.

> **WP 6.1 and 6.6 progress (2026-11-08): decision cores only, same status as 6.3.** `app.retrogram.graph.ChatLockPolicy` (locked set, gate needed or not, relock on timeout / chat closed / app
> background, preview redaction flag) and `app.retrogram.graph.ContactChangeDetector` (per watched user snapshot diff: online/offline, name, username, photo, phone; first snapshot is a baseline).
> `ChatLockAndContactsTest` (6 tests) passes on the JVM together with the auto-reply tests (13 in total; `javac` + JUnit, not Gradle). **Not done at that time (superseded: 6.1 was wired in run 34 and its gaps closed in run 48, see below; no Keystore: the PIN is a salted hash in private preferences, decided in the wiring note):** Keystore-backed storage, `PasscodeView` gate and the
> `presentFragment` / notification / share / widget hooks (6.1); `NotificationCenter` feed, SQLite log, notifications and the drawer screen (6.6); toggles; specs. None of it is reachable from the app.

> **WP 6.4 and 6.5 progress (2026-11-08): cores only.** `app.retrogram.graph.VoiceEffects` (pure Java, 16-bit mono PCM): ring modulator ("robot"), echo with decay/repeats and clipping, pitch shift by WSOLA time-stretch +
> linear resampling (length-preserving). `VoiceEffectsTest` checks them with golden signals (Goertzel power: the ring modulator moves a 1 kHz tone to 700/1300 Hz, a 400 Hz tone shifted by 1.5 lands at 600 Hz,
> an echoed impulse has exact decaying copies). `app.retrogram.graph.DownloadQueuePolicy` holds the queue rules: parallel limit, priority, pause/resume/cancel, Wi-Fi only, roaming block, scheduled window
> (wraps midnight), running entries returned to the queue when the network or window stops allowing them. 24 JVM tests pass in total (`javac` + JUnit, not Gradle).
> **Not done at that time (superseded: 6.4 was wired in run 37 and made seamless in run 50, 6.5 got its drawer entry, Wi-Fi-only, window and schedule in runs 36-46; see below):** the `MediaController` recording hook, preview and setting (6.4); CPU cost on SM6375 is unmeasured and the pitch shifter is unoptimised (quality by ear is unjudged). For 6.5, finding from the code:
> `FileLoader` has `cancelLoadFile(..., deleteFile)` and `isLoadingFile` but no public pause; `FileLoadOperation` has an internal `pause()`/resume that keeps its partial file. Whether cancel-without-delete resumes from
> the partial file must be tested before wiring pause/resume. The `DownloadsActivity` list, the drawer entry and saving to Downloads are also not done. Nothing is reachable from the app.

> **WP 6.3 wiring (2026-11-08, build-verified in CI run 32, never run on a device):** Settings → Retrogram → "Extra features": "Auto-reply (experimental)" (off by default) and "Auto-reply text".
> `app.retrogram.graph.AutoReplyHook.onNewMessages` is called at the top of `NotificationsController.processNewMessages` (so it also runs for push-delivered messages in the background) and feeds
> `AutoReplyEngine` with one rule: private chats only, groups off, any text, always, once per sender per 6 hours, at most 20 replies per hour per account, only while `ApplicationLoader.mainInterfacePaused`.
> Extra guards in the hook: real users only (no bots, service/support accounts, yourself, deleted), only messages from the last 120 s (no replies to a backlog after reconnecting), no edited, scheduled or service messages.
> The reply goes out through `SendMessagesHelper.sendMessage(SendMessageParams.of(text, dialogId))` on the UI thread. Not done at that time (superseded: keywords, hours and cooldown settings exist since run 51; group replies and per-chat exceptions are ruled out, see the 6.3 rule-settings note and STATUS.md): the one-time warning
> banner when enabling, a Business quick-replies comparison, and the reply is not marked as automatic. Cooldowns live in memory, so a process restart forgets who was already answered.

> **WP 6.6 wiring (2026-11-08, build-verified in CI run 33, never run on a device):** Settings → Retrogram → "Extra features": "Special contacts log (experimental)" (off by default), "Contact user IDs"
> (comma-separated numeric ids) and a "Special contacts log" row that opens the log in a dialog (with a Clear button). `app.retrogram.graph.ContactWatchHook` is installed at startup (`ApplicationLoader`, after
> `RetroConfig.init()`), observes `NotificationCenter.updateInterfaces` on every account and, when the mask has name/avatar/status/phone, feeds each listed user's current fields to `ContactChangeDetector`.
> Changes are written to a 200-line text log in the `retro_contact_log` SharedPreferences (newest first). Changing the id list resets the baselines. Not done at that time (superseded: the notification and drawer entry exist since run 48; the SQLite store is ruled out, see the 6.6 gap-closing note): a drawer screen, picking
> contacts from a list instead of typing ids, and per-user options. The user must already receive updates for the id (shared chat or contact); Telegram's "last seen" privacy can hide status.

> **WP 6.4 wiring (2026-11-08, build-verified in CI run 37, JVM-tested DSP, never run on a device; supersedes the earlier "findings only" note):** Settings → Retrogram → "Extra features": "Voice changer (experimental)" with
> Off (default) / Robot / Echo / Higher voice / Deeper voice, read when a recording starts. With an effect selected, `MediaController.recordRunnable` no longer sends each PCM chunk to the Opus encoder; the chunk goes to a
> bounded buffer (`bufferForEffect`, at most 30 s = 2.9 MB), which `flushEffect` runs through `VoiceEffects.apply` and writes to the already-open encoder with `writeFrame` in 20 ms frames (zero-padded last frame). Full
> segments flush while recording; the rest flushes in `stopRecordingInternal` before `stopRecord()` and, on pause, through `postAfterEffectFlush` before the pause path's `stopRecord()` / `joinRecord`. Recording time keeps counting as before
> (echo adds its tail to `recordTimeCount` on the last segment). Cancelling drops the buffer. **Update (2026-11-08, build-verified in CI run 50): the per-segment limits are gone.** The recorder now uses `VoiceEffectStream`, a stateful processor: the robot carrier keeps its phase, the echo keeps its history across segments (the streamed result equals the whole-signal echo
> sample for sample, tail included; JVM-tested with 97-, 1337- and whole-recording pieces), and the higher/deeper voices process each piece together with the last 60 ms of the previous one and crossfade that overlap (JVM test: the largest sample jump at the
> seams is not worse than in the rest of the signal; length is preserved). Output is written in whole 20 ms frames, the rest is carried over to the next flush and padded with silence only at the very end. The existing "locked recording, send as preview" flow uses the processed file, so it doubles as the preview. **Risks to check on a device:** CPU/battery (pitch shift is a plain WSOLA with a
> correlation search, unoptimised, and now runs on 60 ms more audio per 30 s segment; SM6375 is unmeasured), sound quality by ear, pause/resume, cancel, very short notes, the round-video recorder (not touched). No separate "preview effect before recording" screen was built.

> **WP 6.5 remainder (2026-11-08, build-verified in CI run 38, never run on a device): "Large files on Wi-Fi only (experimental)"** (Settings → Retrogram → Extra features, off by default). `FileLoader`'s private document `loadFile` first asks
> `app.retrogram.graph.WifiOnlyDownloads.maybeHold`: a document of 25 MB or more requested at normal or higher priority (a tap) while the phone is not on Wi-Fi is held with a toast ("Waiting for Wi-Fi. Tap again to download over mobile
> data."), and `FileLoader.onNetworkChanged` starts the held downloads once Wi-Fi is connected. A second tap on a held file downloads it right away. Streaming, photos, stickers, voice notes, low-priority (auto) downloads and uploads are not
> affected. The hold list is in memory only. **Decisions I took (change them if you disagree):** 25 MB threshold, fixed; the existing `DownloadQueuePolicy` rules (parallel limit, time window, roaming block) are *not* used by this hook yet;
> **Bulk "save to Downloads": nothing to build (checked 2026-11-08; this corrects an earlier note).** It exists in both places: the chat's selection mode has a multi-message "Save to downloads" / "Save to music" action
> (`ChatActivity`, `saveItem`, ~20932), and the search screen's selection mode, which the Downloads tab uses, has its own `saveItem` ("Save to downloads", `SearchViewPager.saveItemId`, shown when the selection can be saved; handler
> ~995, `MediaController.saveFilesFromMessages`). So multi-select, then Save, works from the Downloads tab too. Ruled out as duplicate.
> **Time-of-day window (2026-11-08, build-verified in CI run 43, never run on a device):** Settings → Retrogram → Extra features: "Download window for large files (experimental)" (off by default) with "Window starts at" / "Window ends at" (hour
> pickers, default 01:00 to 06:00, may wrap midnight, equal hours mean all day). `WifiOnlyDownloads` now holds a tapped 25 MB+ download while *either* rule is unmet (Wi-Fi rule, window rule) and releases it when both are met: on network changes and
> from an in-process timer (`AndroidUtilities.runOnUIThread` with the delay until the window opens, computed by the JVM-tested `DownloadWindow`). A second tap downloads right away.
> **Background schedule that survives the app being killed (2026-11-08, build-verified in CI run 46, never run on a device):** a held file is also registered in Telegram's own persistent download queue
> (`DownloadController.startDownloadFile`: it appears under "Downloading" in the Downloads tab and is stored in the `downloading_documents` table), and a note (account, time, file name) goes into the
> `retro_held_downloads` preferences. An inexact alarm (`AlarmManager.setAndAllowWhileIdle`, no exact-alarm permission) is set for the window start and fires `DownloadWindowReceiver` (declared in the manifest), which starts the
> held files; the same check runs a few seconds after every app start and on every network change. Starting a note means: load the account's download queue (Telegram only does this when the chat list opens), find the file by name,
> call `FileLoader.loadFile` for it; notes whose file is no longer queued are dropped after two days. **Remaining limits:** the alarm is inexact and may be late; Android may stop a background process during a long download (no foreground service
> is used); a download that already started is not paused when the window closes; only downloads whose parent is a `MessageObject` (chat files) are persisted, other held loads stay in memory only.

> **Gap closing for 6.1, 6.2 and 6.6 (2026-11-08, build-verified in CI run 48, never run on a device):**
> - *6.2 hidden chats:* `DialogsSearchAdapter.filter` (the single filter for local results, global results and recent searches) and the message-search result loop now drop concealed chats; `ShareAlert.fetchDialogs` leaves them out of the share sheet;
>   the archive was already covered because folder 1 also goes through `getDialogsArray`. Contacts are not part of the plan's row (it names dialog lists, search, forward and share lists), so a hidden person stays in the Contacts list by design.
> - *6.1 chat lock and 6.2 together:* no reply action in notifications of locked or hidden chats (`NotificationsController`, the reply action would bypass the PIN); the home-screen chats widget (`ChatsWidgetService`) and the direct-share
>   shortcuts (`MediaDataController.buildShortcuts`, from the top-peers list) leave them out. Not covered: the contacts widget, in-app deep links that open a locked chat as a sheet, and shortcuts the system already pinned.
> - *6.6 special contacts:* new switch "Notify about changes" (off by default; a notification in its own channel "Special contacts log", tap opens the app) and a **drawer entry "Special contacts log"** (`nkbtnContactsLog` = 1018, available in the
>   drawer layout editor) that opens the log dialog (`ContactWatchHook.showLog`, shared with the settings row). The plan's SQLite store is replaced on purpose by the 200-line capped text log: the log is small and only read as a whole;
>   a database would add a schema and migrations for no query that exists.
> - *6.4 preview:* the plan's "preview before send" is the existing locked-recording preview (send as preview), which plays the processed file; no separate effect screen is planned.

> **WP 6.3 rule settings (2026-11-08, build-verified in CI run 51):** the engine's rule fields now have settings under Extra features: "Auto-reply only for these words" (comma separated, case-insensitive, empty = every message), "Auto-reply from / until (hour)" (equal hours =
> always, wraps midnight; the hour is local time) and "Hours between replies to the same person" (1, 3, 6, 12, 24; default 6). Changing any of them starts a fresh engine (it forgets who was answered). **Ruled out: replying in groups.** The plan says "never in groups unless explicitly
> enabled"; an auto-reply there would answer ordinary group traffic and what should trigger it (mentions? replies to me?) is a behaviour question for `docs/graph-spec/auto-reply.md`, so the group path of `AutoReplyEngine` stays unused.

Reuse as-is (no work beyond toggles/doc): 10 accounts, themes, player, ghost mode, anti-delete/edit history, forward without quote.

Each built feature needs: a toggle, a unit/instrumentation test for the core logic (lock store, hidden filter,
auto-reply rule matcher + rate limiter, DSP golden-file test, watcher), and a line in the PR-review checklist:
*"Implemented from `docs/graph-spec/…`; no Graph code or assets copied."*

## Acceptance
Every row in `docs/graph-spec/` is reused / built / consciously rejected; each built one has toggle + test;
no network call to third-party analytics or ad hosts (check with `NetworkSecurityConfig` allow-list and a proxy capture).
