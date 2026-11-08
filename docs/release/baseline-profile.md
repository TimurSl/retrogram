# Baseline profile runbook (WP 7.1, docs/plans/07-validation-and-sync.md)

Why this is a runbook and not committed code: a profile has to be recorded on a device while the app is signed in (a start without an account only exercises the login screen),
and the hand-written alternative could not be validated here (decision recorded in the plan). Do this once on the POCO after the first device round; repeat when startup code changes a lot.

1. Use a signed-in **test account** on the device and a release-like build (arm64, `normal` flavor, minified if the release config minifies).
2. Add a Macrobenchmark `com.android.test` module (AGP's "Baseline Profile" template does it) whose `BaselineProfileRule` scenario does: cold start, wait for the chat list, scroll it, open a chat, scroll, go back, open the drawer.
3. Run it with `./gradlew :baselineprofile:generateReleaseBaselineProfile` on the connected device. It produces `baseline-prof.txt`; commit it as `TMessagesProj/src/main/baseline-prof.txt` and add `implementation 'androidx.profileinstaller:profileinstaller:1.4.1'` (sideloaded APKs install the profile on first run through it).
4. Build a release once, install it, run `tools/perf/perf-gate.sh` and compare the cold-start median with the run before (`adb shell cmd package compile -m verify -f app.retrogram.messenger` resets it to "no profile" for the before number).
5. Record both numbers in the release file (`docs/release/TEMPLATE.md`).
