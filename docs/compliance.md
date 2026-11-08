# Licensing and compliance record (PLAN.md section 4)

State on 2026-11-08. Everything here comes from reading the tree and running `tools/provenance.sh`; it is **not legal advice and not a traffic capture**: the network section lists hosts found in the code, not hosts seen on the wire.
Open legal questions are marked **Owner**.

## 1. License of the combined work
- Retrogram is distributed under GPL-3.0 (`LICENSE`, `NOTICE`). Telegram's headers grant "GPL v2 or later", which allows the combination; NagramXF is GPL-3.0.
- Retrogram's own source files (`app/retrogram/**`, 25 Java/Kotlin files including their tests) now start with `// SPDX-License-Identifier: GPL-3.0-or-later`. Upstream headers are untouched. Files revived from older Telegram releases must keep the "source commit and path" comment (rule in `docs/plans/README.md`).
- **Owner:** confirm GPL-3.0-or-later (not "3.0 only") is what the project wants for its own files.

## 2. Provenance map (the "record which modules" item)
NagramXF's README says it "reverse-engineered and uses some code from closed-source projects" and thanks the original developers, but it does **not** say which files. So the repository cannot list them; what can be recorded is where each namespace comes from. Output of `tools/provenance.sh`:

```
namespace                           files  headers  origin
org/telegram                         2023      487  Telegram for Android (DrKLO), GPL-2.0-or-later, plus NagramXF edits
com/exteragram                        127        0  exteraGram
com/radolyn                            63       22  AyuGram
tw/nekomimi                           187        6  Nekogram / NagramX
xyz/nextalone                          21        0  Nagram
app/retrogram                          25       25  Retrogram (this project)

files outside org/telegram that name a downstream project (count):
  AyuGram      80
  Cherrygram   1
  OctoGram     4
  exteraGram   134
  NagramX      19
  Nekogram     230
```

How to read it: "headers" counts files that mention copyright or license at all. Only 22 of the 63 AyuGram files, 6 of 187 Nekogram/NagramX files and none of the 127 exteraGram or 21 Nagram files carry one, so license status of those namespaces rests on the upstream repositories, not on the files.
Downstream projects named in the acknowledgments: NagramX, AyuGram, Cherrygram, exteraGram, OctoGram.
- **Owner:** ask the NagramXF maintainer which features are reverse-engineered, or have counsel review the namespaces above before a public release; until then they are a legal risk (as PLAN.md says) and Retrogram prefers clean-room replacements for anything new.
- Retrogram-added Graph features are clean-room (`docs/graph-spec/`, no Graph code or assets copied).

## 3. Features that bypass Telegram's restrictions (ship off, with a warning)
| Feature | NagramXF default | Retrogram default | Warning |
| :--- | :--- | :--- | :--- |
| Ignore "restrict saving content" (forward, copy, save from protected chats; re-send protected, secret, self-destructing and paid media via AyuForward) | **on, hard-coded** (`MessagesController.isChatNoForwards` always returned false) | **off**: stock check restored; opt-in switch "Bypass restrict saving content" (Settings > Retrogram) | confirmation dialog when switched on, with "Turn off" |
| Ignore content restrictions (`ignoreContentRestrictions`: regional and store restrictions on channels) | on | **off** | none yet (it is an older Neko setting) |
| Ghost mode (no read receipts, offline status, typing) | off (the `send*Packets` options default to sending) | off | the ghost-mode screen is NagramXF's; no extra warning added |
| Save deleted messages / edit history (anti-delete) | off | off | none yet |
| Message saving of media (`SaveMediaIn...`) | on, but only acts when "save deleted messages" is on | same | none yet |
- **Gap:** the plan wants "a clear warning" for every row. Only the first has one; warnings for ghost mode, anti-delete and content restrictions need wording from the owner (they are NagramXF screens with their own text).

## 4. Analytics, ads, crash reporting
- No ad SDK and no Firebase Analytics in the dependencies; the manifest sets `firebase_analytics_collection_deactivated=true`.
- **Fixed:** Firebase Crashlytics is a dependency and NagramXF enabled collection at runtime by default (`setCrashlyticsCollectionEnabled(!disableCrashlyticsCollection)` with the setting defaulting to false, which overrode the manifest's "disabled"). It now defaults to **disabled**; turning collection on is the user's choice in the existing NagramX setting.
- **Fixed:** usage reports to Telegram (`messages.reportReadMetrics`, `messages.reportMusicListen`) are off unless "Send usage statistics to Telegram" is on.
- NagramXF update/metadata channels are off (`RetroConfig.remoteMetadata`), and the `@NagramXF` join prompt is gated behind it.

## 5. Credentials and project identifiers that must not be NagramXF's
| Item | State |
| :--- | :--- |
| Telegram `api_id`/`api_hash` | removed; release builds fail without your own (`TELEGRAM_APP_ID/HASH`); debug uses a no-login placeholder. **Owner:** own pair |
| Google Maps key in the manifest | **Fixed:** NagramXF's key is gone; the manifest reads `${MAPS_API_KEY}` (set it in `local.properties` or the environment; empty means maps do not load). **Owner:** own key |
| `google-services.json` | **Fixed:** the committed file was NagramXF's Firebase project; it is now a neutral placeholder for `app.retrogram.messenger` (CI also builds one when no secret is set). **Owner:** own Firebase project for push |
| Signing key | CI signs unofficial builds with a fixed debug key; **Owner:** release keystore |
| Package id | `app.retrogram.messenger`, a placeholder (open decision 1) |
| Keys inherited from upstream Telegram | a static-map key in `MessagesController` / `AndroidUtilities` is Telegram's own and ships in Telegram's source too; the Google Translate web key in `GoogleTranslator.kt` is Google's public one for the unofficial web endpoint. Left as they are; **Owner:** decide whether to keep the Google Translate path |

## 6. Third-party hosts found in the code (feature-bound, not a capture)
Found by grepping URLs: Google Maps static images (`maps.googleapis.com`), OpenStreetMap / OpenFreeMap tiles (the "OpenFreeMap" setting is on by default in NagramXF), Google Generative Language and OpenRouter (the AI features, with the user's own key),
Google Translate web endpoint, `dpaste.com` (regex-filter export, only when the user shares), Fragment/TON viewers (opened as links), `last.fm` (music metadata). None is contacted before the feature is used as far as the code shows, but only a proxy capture can prove it:
that is the `network.md` spec step (`docs/graph-spec/network.md`) and the privacy check in `docs/device-test-checklist.md`.
