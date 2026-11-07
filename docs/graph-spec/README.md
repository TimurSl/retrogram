# Graph Messenger behaviour specs (clean-room, Phase 6 step 1)

**Rule (PLAN.md §4, docs/plans/06-graph-features.md):** nothing here may be copied, pasted or derived from decompiled Graph Messenger
code, resources or strings. A spec describes what a person saw and did in the installed app. Prefer that the person who writes a spec is not
the only person who implements it.

None of these files has been filled in: it needs a phone with Graph Messenger (`ir.ilmili.telegraph`, latest known 12.1.1.0) and a throwaway account.

## One file per feature: template

```
# <Feature>
Evidence: listed | reported | verified-on-device (app version, date)
Menu path: Settings → … (screenshots in ./img/<feature>/)
What the user sees / can configure (every option, default value)
Behaviour rules (what exactly happens, edge cases, what is NOT done)
Telegram API it implies (public API only)
Network: does this feature contact anything other than Telegram? (proxy capture; host names only)
Retrogram decision: reuse | build | reject  (+ reason)
```

## Checklist (starting list from PLAN.md §6, to be verified on the device)

| Feature | Evidence so far | Retrogram plan (docs/plans/06-graph-features.md) | Spec file | Status |
| :--- | :--- | :--- | :--- | :--- |
| Per-chat password / PIN lock | listed | build (WP 6.1) | `chat-lock.md` | not written |
| Hidden chats / contacts | listed | build (WP 6.2) | `hidden-chats.md` | not written |
| Auto-reply | listed | build (WP 6.3); compare with Business quick replies | `auto-reply.md` | not written |
| Voice changer for voice notes | listed | build (WP 6.4) | `voice-changer.md` | not written |
| Download manager (queue, pause/resume, save) | listed | build (WP 6.5) | `download-manager.md` | not written |
| Special contacts / contact change log | reported | build (WP 6.6) | `special-contacts.md` | not written |
| Ghost mode drawer toggle | reported | reuse NagramXF ghost mode (WP 6.7) | `ghost-mode.md` | not written |
| Chat categories | reported | compare with folders (WP 6.8) | `chat-categories.md` | not written |
| Profile photo download / full-size viewer | reported | check `PhotoViewer` first (WP 6.9) | `profile-photo.md` | not written |
| Theme presets | listed | only if unique (WP 6.10) | `themes.md` | not written |
| Up to 10 accounts, built-in player, anti-delete / edit history, forward without quote | listed / reported | reuse | – | n/a |
| Ads, analytics, third-party data sharing | reported (single source, unverified) | never | `network.md` | not written |

`network.md`: list which hosts the app talked to during each flow (login, chat list, send, each feature above). Retrogram ships no ads and no
third-party analytics; features that depend on third-party servers become *rejected* or *local-only*.

## Also open (not spec work)

- Send the GPL source request to Databite: draft in `gpl-source-request.md`. Not sent.
- If source ever arrives, add it as `upstream/graph` and reassess; until then everything stays clean-room.
