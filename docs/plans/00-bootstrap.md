# Phase 0 — Bootstrap

**Goal:** a CI-built, signed APK of NagramXF 13.1.1 under the Retrogram name that logs in
and chats, with `retrogram.plugins` (default off) and a `RetroConfig` stub in place. No UI change yet.

**Exit gate:** APK installs on the POCO, login + chat + call + push work; CI is green for
plugins off *and* on.

---

## WP 0.1 — Import NagramXF with history (S)

> **Status: done except step 5 (fork the `Keeperorowner/*` submodules; needs the project org) and the
> submodule-status check (submodules are deliberately not initialised in this checkout).**
> Imported as `2af55562`; `git merge-base HEAD nagramxf/dev` = `49ae647f`. `.gitattributes` and `NOTICE` added.
> Gradle only includes `:TMessagesProj` and the media modules, so `upstream/` is invisible to the build.

The repo already has 3 commits of its own (`PLAN.md`, README, submodule pins).

1. `git remote add nagramxf https://github.com/Keeperorowner/NagramXF.git`
   `git remote add telegram https://github.com/DrKLO/Telegram.git`
2. `git fetch nagramxf dev` (full history is large; the blobless filter used for `upstream/`
   works here too: `git fetch --filter=blob:none nagramxf dev`).
3. `git merge --allow-unrelated-histories 49ae647f -m "Import NagramXF dev @ 49ae647f (13.1.1)"`.
   Resolve the two expected conflicts: `README.md` (keep Retrogram's, append an "Upstream" section) and
   `.gitmodules` (union of both files).
4. Keep `upstream/telegram` and `upstream/nagramxf` as read-only references. **Do not** let Gradle see
   them: confirm `settings.gradle` includes only `:TMessagesProj` (+ media module), and add
   `upstream/` to `.gitattributes` `linguist-vendored`.
5. **Submodules.** NagramXF has about 24 (td, tgcalls, ffmpeg, boringssl, dav1d, libvpx, opus*, ogg,
   openh264, tlottie, wallet-engine, boost-regex, libprisma, absl, libyuv, jlatexmath,
   `TMessagesProj_Modules/media`, plus plugin-only `lsplant` and `shadowhook`). Three are
   `Keeperorowner/*` forks (`tgcalls`, `LSPlant`, `android-inline-hook`). Fork them to the project org
   and rewrite those URLs in `.gitmodules`, so a deleted upstream repo cannot break builds.
6. Add `NOTICE` (GPL-3.0 combined work; Telegram "GPL-2.0-or-later" headers kept; list of third-party
   submodules and licenses) and keep `LICENSE`.

**Accept:** `git log --oneline nagramxf/dev` is reachable from `HEAD`; `git submodule status` clean;
`git merge-base HEAD nagramxf/dev` exists (so Phase 8 merges are real merges).

## WP 0.2 — Rebrand (M)

> **Status: partly done, not build-verified.**
> Done: `APP_PACKAGE=app.retrogram.messenger` (placeholder, open decision 1) and the matching account type,
> contact MIME types, `auth_menu` target package and `:retrogram` process in manifest/`res/xml`; output file name is
> `retrogram-<ver>-<abi>.apk`; official-app API credentials removed (`build.gradle` now fails release/staging/bundle
> tasks without `TELEGRAM_APP_ID/HASH`, and uses a no-login placeholder for debug); hard-coded pair removed from `BuildVars`.
> Open:
> - The debug fallback is a placeholder, not a working dev pair: needs the project's own `api_id` (open decision 2).
> - `applicationIdSuffix` for `.debug`/`.beta` is **deferred**: the account type and contact MIME types in
>   `res/xml/{auth,contacts,sync_contacts,auth_menu}.xml` are static strings while `ContactsController:2313` builds the MIME type from
>   `BuildConfig.APPLICATION_ID`; template them with manifest placeholders first, otherwise contact sync breaks on suffixed builds.
> - Done (not build-verified): remote update/metadata switch. `BaseRemoteHelper.load()` returns early (calling `onError` for a
>   non-null delegate) unless `RetroConfig.remoteMetadata` is on, so `UpdateHelper`, `EmojiHelper`, trusted-plugins and
>   page-preview rules make no requests to the NagramXF metadata channel by default. Consequence: remote emoji packs and
>   update prompts are off until a Retrogram channel exists (decide per feature, see risks below).
> - Still open: app name/strings (`NagramX` string resource used in ~10 places), launcher icons, `RETROGRAM_REV`,
>   `google-services.json`.
> - `.github/workflows/*` still use the NagramXF artifact names (handled in WP 0.4).

Only identity changes; keep `org.telegram.*` and `tw.nekomimi.*` Java packages untouched
(renaming them would make every future merge conflict).

| Item | Where | Change |
| :--- | :--- | :--- |
| Application ID | `gradle.properties` `APP_PACKAGE=fork.risin42.nagramx` | `app.retrogram.messenger`; `.beta` / `.debug` suffixes via `applicationIdSuffix` per build type (check the existing `staging`/`debug` types in `TMessagesProj/build.gradle`) |
| App name, launcher icon | `res/values/strings*.xml` (`AppName`), mipmaps, adaptive icon | "Retrogram" + new icon; keep one monochrome layer for themed icons |
| Output file name | `TMessagesProj/build.gradle` `androidComponents` block (`gramName = 'NagramXF'`) | `retrogram-<ver>-<abi>.apk`; matches PLAN.md §5 outputs |
| Version | `gradle.properties` `APP_VERSION_NAME/CODE` | Keep `13.1.1` / `7237` for the import; Retrogram revisions as `13.1.1.r1` via a `RETROGRAM_REV` property |
| Update checks, metadata and donate links | `tw/nekomimi/nekogram/helpers/remote/UpdateHelper.java`, `BaseRemoteHelper.CHANNEL_METADATA_ID` and callers (`EmojiHelper`, ...), about/donate cells in Neko settings | Disable the remote update check and metadata fetch behind `RetroConfig.remoteMetadata = false` (default). Point at Retrogram's own channel only after one exists. This also removes calls to channels Retrogram does not own. |
| Manifest | `AndroidManifest.xml`, `src/plugin/AndroidManifest.xml` | `authorities` / provider names containing the old package; deep-link hosts stay Telegram's |
| FCM | `google-services.json` | Project's own file from CI secret. Without it push will not work; document the failure mode in the README. |

**Telegram API credentials — do before the first release build.** `TMessagesProj/build.gradle:189-191`
falls back to `appId = '6'` and a hard-coded hash when no `TELEGRAM_APP_ID/HASH` is supplied.
Those are the credentials of the official Telegram app, and Telegram's API terms do not allow reusing
them in a fork. Change the fallback so **release builds fail** when the secrets are absent and debug
builds use a clearly marked dev pair. `BuildVars.java:29-30` has another hard-coded pair that
`BuildConfig` overwrites at line 47-48; remove it.

**Accept:** `./gradlew :TMessagesProj:assembleNormalDebug` output has `applicationId app.retrogram.messenger.debug`
(`aapt dump badging`), no string `nagramx` in `AndroidManifest.xml`, and the launcher shows "Retrogram".

## WP 0.3 — `retrogram.plugins` build switch (L)

> **Status: implemented, not yet build-verified.** The sandbox has no Android SDK (dl.google.com is blocked),
> so the four acceptance commands below still have to be run, locally or in CI (WP 0.4). Only a Groovy syntax
> check and a byte-for-byte check of the moved block were done.
> What changed: the flag is read in `TMessagesProj/build.gradle` (`-P`, then `local.properties`, then
> `RETROGRAM_PLUGINS`); the `plugin` flavor is declared only when it is on; all Chaquopy configurations/tasks,
> `sourceSets.plugin.*` and the `pluginImplementation`/`chaquopy*` dependencies moved verbatim into
> `TMessagesProj/plugins.gradle`; `jni/CMakeLists.txt` now defaults `ENABLE_PLUGINS` to OFF;
> `BuildConfig.PLUGINS_ENABLED` is `false` for `normal` and `true` for `plugin` (per flavor, because with the flag on
> both flavors exist). Step 5 needed no source change: plugin UI entry points are already behind
> `PluginsController.isPluginEngineSupported()`, which the `normal` stub returns `false` for.
> Not yet done: step 7 and `settings.gradle`/root re-check after rebrand.

Verified structure of `TMessagesProj/build.gradle` (line numbers for 49ae647f):

- 78–84: `pluginAbis`, `chaquopy*Version`, `chaquopyAbis` — safe to keep (plain values).
- 166–181: `productFlavors { normal {…} plugin {…} }`.
- 282–288: `sourceSets.plugin.*` — **fails at configure time if the `plugin` flavor is undeclared.**
- 294–301: `configurations { chaquopyTargetJni; chaquopyStdlibPyc; chaquopyGradlePlugin }` and the
  `getByName` lookups.
- 303–512: all Chaquopy / Cython / pip `tasks.register`.
- 514–540: `androidComponents.onVariants` — the file-name rewrite must stay for both; the
  `dependsOn(prepareChaquopy…)` part is plugin-only.
- 644–645: `pluginImplementation …` dependencies — **the configuration does not exist without the flavor.**
- `jni/CMakeLists.txt:351`: `if (NOT DEFINED ENABLE_PLUGINS) set(ENABLE_PLUGINS ON)`. Change the default to `OFF`
  so a forgotten argument can never pull LSPlant/ShadowHook/Python in.

Steps:

1. At the top of the file define
   ```groovy
   def retrogramPlugins = (findProperty('retrogram.plugins') ?: System.getenv('RETROGRAM_PLUGINS') ?: 'false')
           .toString().toLowerCase() in ['1', 'true', 'yes']
   ```
   Property lookup order Gradle gives us: `-P` > `~/.gradle/gradle.properties` > project `gradle.properties`.
   `local.properties` is *not* read by Gradle for `findProperty`; load it explicitly (the file already loads
   `properties` for `TELEGRAM_APP_ID`, reuse that object).
2. Move lines 294–512 (configurations + tasks) and the plugin half of `onVariants` into
   `apply from: 'plugins.gradle'` (new file) guarded by `if (retrogramPlugins)`. A separate file keeps the
   diff against NagramXF to a handful of lines, which matters for merges.
3. `productFlavors`: declare `normal` always, `plugin` only when the flag is on. Keep the flavor
   dimension even with a single flavor so `src/normal` stays wired and variant names do not change.
4. Wrap `sourceSets.plugin.*` and the `pluginImplementation` lines in the same condition.
5. `buildConfigField 'boolean', 'PLUGINS_ENABLED', retrogramPlugins.toString()` in `defaultConfig`
   (note: with the `plugin` flavor the value is `true` only if the flag is on, which is always the case there).
   Hide the plugin entry points behind it: `SettingsHelper` / drawer item for plugins, `PluginsActivity`
   links, the `NotificationCenter.pluginMenuItemsUpdated` registration in `LaunchActivity` (harmless, but skip).
6. `root build.gradle` and `settings.gradle`: grep for `plugin`/`chaquopy` (only `plugins {}` blocks match
   today; re-check after import).
7. CI (WP 0.4) passes `-Pretrogram.plugins=${{ inputs.plugins }}`.

**Accept (all four must pass):**

| Command | Expect |
| :--- | :--- |
| `./gradlew :TMessagesProj:tasks --all` (flag off) | no `chaquopy*` / `stageChaquopy*` tasks; no Python / pip network access during configure |
| `./gradlew :TMessagesProj:assembleNormalDebug` (flag off) | succeeds; `unzip -l` shows no `libchaquopy_java.so`, `liblsplant`, `libshadowhook`, `assets/chaquopy` |
| `./gradlew :TMessagesProj:assemblePluginDebug -Pretrogram.plugins=true` | succeeds; plugin APK contains the above |
| `./gradlew :TMessagesProj:assemblePluginDebug` (flag off) | fails fast with "variant not found", not a confusing error |

Also check that nothing in `src/main` imports a class that exists only in `src/plugin` (list in
`find src/plugin -name '*.java'` that has no twin under `src/normal`: `PythonPluginsEngine`, `PipController`,
`PluginsWatchdog`, `SdkSignature`, `ClassProxy`, `UpdateAppAlertDialog`, `PythonSdkUpdateDialog`, the `models/*`,
`client_utils`, `android_utils`). Compiling `normal` is the proof.

## WP 0.4 — CI (M)

> **Status: workflow written, never run.** `.github/workflows/build.yml` replaces `staging.yml` and `canary.yml`
> (both deleted; they only uploaded artifacts). YAML parses; nothing else is verified: there is no runner or Android SDK here.
> Differences from the steps below:
> - Triggers: every branch push, PRs, `v*` tags, manual dispatch. Matrix is `[false]`, or `[false, true]` on tags and `sync/*`.
> - PRs set `NATIVE_TARGET=arm64-v8a`. Non-tag builds run `assemble{Normal,Plugin}Debug`; tags run `...Release`.
> - Submodules are initialised by explicit path list (no `upstream/*`; no `lsplant`/`shadowhook` unless plugins).
> - Python and Cython are installed only for `plugins=true`.
> - Without signing secrets the workflow swaps `TMessagesProj/release.keystore` (a committed NagramXF key) for a throwaway
>   key, so PR builds still sign. Tags fail if a secret is missing.
> - **New finding:** `TMessagesProj/google-services.json` is NagramXF's file and only has a client for `fork.risin42.nagramx`.
>   Since WP 0.2 changed the application id, the google-services plugin fails every build locally. CI writes the
>   `GOOGLE_SERVICES_JSON_B64` secret, or a clearly fake placeholder for the new package id. Locally, do the same or
>   provide your own file. The committed file (with NagramXF's Firebase key) should be removed once the project has its own.
> - `MAPS_API_KEY` is **not** wired: the manifest hard-codes a Maps key (`AndroidManifest.xml:867`), a leftover for WP 0.2.
> - `tools/glass-guard.sh` is run when the file exists (Phase 4). Plugin `*Debug` builds are not unit-tested.
> - **First runs (2026-11-07):** unit tests (which compile all Java/Kotlin) pass in ~7 min; the all-ABI debug APK build was killed by
>   a runner shutdown after 38 min in the native step. Branch pushes now build `arm64-v8a` only (like PRs), with a 120 min job timeout;
>   tags and manual runs still build every ABI.
> - **Run 12 (2026-11-07, commit `20325c48`): unit tests passed, `assembleNormalDebug` succeeded (30 min, arm64 only, cold ccache), 110 MB debug APK.**
>   The run still went red at `upload-artifact`: "Artifact storage quota has been hit" (account-wide; this repo has no artifacts). The upload is now
>   `continue-on-error` off tags, with a 7-day retention and a warning step. To get APKs again: free storage under the account's Actions artifacts, or wait 6-12 h.
> - Unverified assumptions: `--depth=1` submodule updates work for the pinned commits; `testNormalDebugUnitTest` exists
>   under the `normal` flavor; `NATIVE_TARGET` alone is enough for a one-ABI build.

Base: NagramXF `.github/workflows/staging.yml` (ubuntu-24.04, JDK 21 Temurin, ccache, Gradle cache,
`ndk;27.2.12479018`, `build-tools;36.0.0`, `platforms;android-36`, writes `sdk.dir` to `local.properties`).

1. Copy to `.github/workflows/build.yml`. Remove the Python/Cython setup steps unless `inputs.plugins`
   (the existing workflow always installs Python 3.11.9 + Cython 3.0.11 for the Chaquopy bridge).
2. Inputs: `plugins` (bool, default false), `upload` (skip upload). Matrix: `plugins: [false]` on push,
   `[false, true]` on release tags and on `sync/*` branches.
3. Submodules: `git submodule update --init --recursive --depth=1`, but **skip** `lsplant` and
   `shadowhook` when `plugins` is false. (Do not use `actions/checkout submodules: recursive` blindly:
   it also pulls `upstream/*` here. Use `submodules: false` + the explicit command with a pathspec list
   excluding `upstream/`.)
4. Secrets: `TELEGRAM_APP_ID`, `TELEGRAM_APP_HASH`, `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
   `KEY_PASSWORD`, `GOOGLE_SERVICES_JSON_B64`, `MAPS_API_KEY`. Fail the job on tag builds if one is missing.
5. Publish `retrogram-<ver>-arm64-v8a.apk` (main), v7a, universal, as artifacts. Upload a GitHub Release
   only on tags.
6. `canary.yml` only uploads an artifact (`upload-artifact@v4`), so there is no channel-posting step to remove; keep one workflow and drop the other.
7. First job also runs `./gradlew :TMessagesProj:testNormalDebugUnitTest` (there is a `src/test` dir) and,
   from Phase 4, `tools/glass-guard.sh`.

**Accept:** push to a branch → green run → downloadable APK, for flag off; manual dispatch with `plugins=true`
→ green run with plugin APK.

## WP 0.5 — `RetroConfig` + settings stub (S)

> **Status: implemented, not compiled.** (No Android SDK or `kotlinc` in the sandbox.)
> - `TMessagesProj/src/main/kotlin/app/retrogram/RetroConfig.kt` (the module's Kotlin dir is `src/main/kotlin`, where `NaConfig`
>   lives). Items: `retroUi` (true), `upstreamUiDebug` (false, no settings row), `remoteMetadata` (false). They are plain
>   `ConfigItem`s in the existing `nkmrcfg` prefs, **not** registered in `NaConfig`, so there is no edit to that 2k-line file.
>   `graph*` toggles come with Phase 6.
> - Cold start: `RetroConfig.init()` runs in `ApplicationLoader.onCreate` right after `NaConfig.init()` and freezes
>   `RetroConfig.isRetroUi()` (`retroUi && !upstreamUiDebug`). Hooks in Phases 1-4 should call `isRetroUi()`, not read the item.
> - Settings → "Retrogram" (`RetrogramSettingsActivity`, deep link `retrogram`), two switches that show the restart tooltip.
>   `remoteMetadata` is only stored for now: nothing reads it until the update-check and metadata switch is done (WP 0.2).
> - Test: `src/test/java/app/retrogram/RetroConfigTest.java` (defaults, stored values after reload, snapshot only changes
>   after restart, debug override). Not run yet.

1. `app/retrogram/RetroConfig.kt` (Kotlin is already enabled: there are `.kt` files under `tw/nekomimi/nekogram/ui/`). Fields, all backed by the existing NagramXF config store
   (`NaConfig`/`NekoConfig` `ConfigItem`, which persists and has a settings UI helper):
   `retroUi` (default **true**), `upstreamUiDebug` (default false, hidden), `remoteMetadata` (false),
   `graph*` toggles (added in Phase 6).
2. Settings → new "Retrogram" section. Find where Neko settings entries are registered:
   `SettingsHelper.java` (line 144/234 reference drawer items) and `NekoSettingsActivity`.
3. Make `retroUi` a **cold-start decision**: read once in `ApplicationLoader.onCreate`, cached in a
   `static final`-like field, and require a restart to change. Mixing both UIs live would double every
   code path in Phases 1–4 and it is only a debugging toggle.

**Accept:** unit test that `RetroConfig.retroUi` defaults to true and survives a config reload.

## Risks specific to this phase

| Risk | Mitigation |
| :--- | :--- |
| CI memory/time: the NDK build of td + ffmpeg + tgcalls is long | ccache + Gradle cache as in the NagramXF workflow; build `arm64-v8a` only on PRs via the existing `NATIVE_TARGET` environment variable (`TMessagesProj/build.gradle:77`; it also drives the `chaquopyAbis` filter) |
| Merge of unrelated histories brings thousands of files into one commit | Do it as a dedicated commit with no other edits; follow-up rebrand commits are separate |
| Removing remote metadata hides features NagramXF loads from its channel (emoji packs, etc., `EmojiHelper`) | Inventory first (`grep -rn CHANNEL_METADATA_ID`), ship with local fallbacks, decide per feature |
