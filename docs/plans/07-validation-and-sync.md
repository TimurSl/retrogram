# Phases 7 and 8 — Device gate and upstream sync

## Phase 7 — POCO X4 Pro 5G gate (every release)

The test runs on a physical `veux` (Android 13 / HyperOS 1.0). Until one is on hand, use an emulator for
functional checks only; the numbers below are only valid on the device.

### Setup
- Debug build with `FrameMetricsOverlayView` (added in 12.4; confirm it exists: `find . -name FrameMetricsOverlayView.java`).
- Release build with a **baseline profile** for cold start (`androidx.profileinstaller`/Macrobenchmark module; not in
  the NagramXF tree today, so adding it is a WP: 7.1, M).
- Scripts in `tools/perf/` (adb): fling test (30 s `input swipe` loop in chat list and a long chat), `dumpsys gfxinfo <pkg>`
  for jank, `am start -W` for cold start, `dumpsys meminfo <pkg>` for PSS, perfetto trace for GPU.

### Checks (from PLAN.md §6 Phase 7)
| Check | Target | How |
| :--- | :--- | :--- |
| Chat list/chat scroll at 120 Hz | p95 frame ≤ 8.3 ms over a 30 s fling | gfxinfo / FrameMetrics overlay |
| Cold start → chat list | ≤ 1.5 s, arm64 release + baseline profile | `am start -W`, median of 10 |
| PSS, 3 accounts, idle in chat list | ≤ 350 MB | `dumpsys meminfo` after 2 min idle |
| Performance class | AVERAGE; Lite Mode preset MEDIUM | log `SharedConfig.getDevicePerformanceClass()`; see `SharedConfig.LOW_SOC` (SM6375 not in it) |
| HyperOS push | arrives under default battery settings; in-app tip for "No restrictions" | overnight test |
| Calls | voice/video over Wi-Fi and 5G; no ANR with Bluetooth LE audio | manual |
| Media | 1080p60 HEVC/VP9 hardware decode, voice notes (incl. voice changer), round video | manual |
| APK size (arm64, normal) | track; alert > +5% | CI script on `apkanalyzer` |

Also: one Android 15/16 Pixel-class phone and one Android 10 low-end phone (minSdk 27 is the floor).

> **Written (2026-11-08):** `tools/perf/perf-gate.sh` (cold start median of 10 via `am start -W`, a 30 s adb fling with `dumpsys gfxinfo`, PSS from `dumpsys meminfo`; prints PASS/FAIL per limit and exits 1 on a miss),
> `tools/perf/gfxinfo_p95.py` (parses the gfxinfo percentiles; unit-tested by `tools/perf/test_gfxinfo_p95.py`, which CI runs), `tools/apk-size.sh` (sizes of every APK, arm64 compared with
> `docs/release/apk-size-baseline.txt`, fails above +5%; CI runs it after the build and writes the sizes to the job summary; no baseline exists yet, so today it only reports; create one with `--update-baseline` on the first
> release build) and `docs/release/TEMPLATE.md` (the gate table to copy per tag). The shell scripts were syntax-checked and the parsers and size logic exercised with fake input; **the adb scripts have never run against a device; the Python test and the APK size step ran green in CI run 47.**
> `FrameMetricsOverlayView` exists in the tree. **WP 7.1 (baseline profile), decision 2026-11-08: not added.** Two options were checked. (1) A hand-written `src/main/baseline-prof.txt` with wildcard rules for the startup classes: AGP would package it for release builds only, CI never builds release
> (only tags do), and the profile compiler (`profgen`, hosted on `dl.google.com`) is not reachable from the sandbox, so the rule syntax could not be validated here; a wrong line would break the first release build instead of CI. (2) A Macrobenchmark module generating
> the profile: it needs a device or emulator and a logged-in account to reach the chat list (startup alone only exercises the login screen). Both are therefore left for the device round, in this order: record with Macrobenchmark on the POCO against a
> signed-in test account, add `androidx.profileinstaller` and the generated `baseline-prof.txt`, build a release once and compare cold start with `tools/perf/perf-gate.sh`. The steps are written down in `docs/release/baseline-profile.md`.

### Gate
A release tag requires: all rows green or an explicit waiver in `docs/release/<tag>.md`; `glass-guard` clean;
CI green for plugins off and on.

## Phase 8 — Upstream sync

### `tools/sync-upstream.sh` (WP 8.1, M)
> **Status (2026-11-08): run end to end in a scratch clone** (merge into a no-op, a fake upstream commit with new glass code and a layer bump, and a conflicting hunk): the report lists only what the merge brought in (it used to list Retrogram's own
> additions because it diffed from the merge-base; fixed to diff from the pre-merge commit), and on a conflict it stops with exit 2, names the file and pushes nothing. Never run against a real new NagramXF release (`nagramxf/dev` is still at the import base).
> Earlier status: written, `bash -n` only. `tools/sync-upstream.sh <name> [<ref>]` adds the `nagramxf`/`telegram`
> remotes if missing, fetches blobless, creates `sync/<name>`, merges (stops with a conflict list and exit 2; never resolves, never pushes), then prints
> glass-guard hits for the merge, the TL `LAYER` change, the new TL request/type names and new top-level classes. Steps 1-5 below; step 6 (PR) stays manual.
1. `git fetch nagramxf dev --tags` (and `telegram master` for the fallback path).
2. Create `sync/<X>` from `main`; `git merge nagramxf/<tag-or-sha>`.
3. Conflict policy: conflicts live almost entirely in the files with Retrogram hooks (`LaunchActivity`,
   `DialogsActivity`, `ChatActivity`, `ChatActivityEnterView`, `SettingsActivity`, `ProfileActivity`, `build.gradle`,
   blur3 sources). Resolve by re-applying the hook, never by taking "theirs" wholesale.
4. Run `tools/glass-guard.sh <merge-base>..HEAD`; the new hits are the to-do list.
5. Print the TL layer diff (`LAYER` in `TLRPC.java`) and new top-level classes → seed rows for the Phase 5 matrix.
6. Open a PR; CI builds `plugins=false` and `plugins=true`.

### Release flow
sync PR green → POCO checklist (Phase 7) → merge → tag `retrogram-<X>.r<n>` → release APKs.

### Fallback if NagramXF stalls
Merge DrKLO directly (`upstream/telegram` already has history); NagramXF-only features (ghost mode, AyuGram parts,
plugins) then have to be re-applied by hand — keep a list in `docs/nagram-delta.md` of Retrogram's dependencies on
NagramXF-only code (start it in Phase 0, it is cheap to maintain then and costly to reconstruct later).

### Health signals to watch
- **2026-11-07 check:** `git fetch --filter=blob:none --tags nagramxf dev` works (the first half of `tools/sync-upstream.sh`); `nagramxf/dev` is still at `49ae647f`,
  our import base, i.e. 0 commits ahead, so there is nothing to merge yet. The merge/report half of the script is still untested.
Days between DrKLO release and NagramXF merge (NagramXF's latest commit here is 2026-11-06, 13.1.1 landed on
the DrKLO release day); open `glass-guard` hits; size of the Retrogram diff vs NagramXF
(`git diff --stat nagramxf/dev -- TMessagesProj | tail -1`) — if it grows past what a person can re-review in a day,
move code into `app.retrogram.*`.
