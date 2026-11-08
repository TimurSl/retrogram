# Release gate: retrogram-<version>.r<n>

Copy this file to `docs/release/<tag>.md` for every release tag and fill it in
(docs/plans/07-validation-and-sync.md). A row is **green**, or carries an explicit **waiver** with a reason.
A tag needs: every row green or waived, `tools/glass-guard.sh` clean for the sync range, CI green with plugins off and on.

| Check | Target | Result | Status (green / waived) | Notes or waiver reason |
| :--- | :--- | :--- | :--- | :--- |
| Chat list / chat scroll at 120 Hz | p95 frame <= 8.3 ms, 30 s fling | | | `tools/perf/perf-gate.sh` |
| Cold start to chat list | <= 1.5 s median of 10 | | | `tools/perf/perf-gate.sh` |
| PSS, 3 accounts, idle in chat list | <= 350 MB | | | `IDLE_SECONDS=120 tools/perf/perf-gate.sh` |
| Performance class / Lite Mode preset | AVERAGE / MEDIUM | | | app log, `SharedConfig.getDevicePerformanceClass()` |
| HyperOS push | arrives under default battery settings | | | overnight |
| Calls | voice and video on Wi-Fi and 5G, no ANR with Bluetooth LE audio | | | manual |
| Media | 1080p60 HEVC/VP9 hardware decode, voice notes (with each voice effect), round video | | | manual |
| APK size (arm64, normal) | within +5% of `apk-size-baseline.txt` | | | CI "APK size" step |
| Android 15/16 Pixel-class phone and Android 10 low-end phone | functional pass | | | manual |
| Device checklist | `docs/device-test-checklist.md` run for the switches shipped on | | | |
| glass-guard | clean for the sync range | | | `tools/glass-guard.sh <merge-base>..HEAD` |
| CI | green, plugins off and on | | | |

Device: ______  Android/HyperOS: ______  Build: ______  Date: ______  Tester: ______
