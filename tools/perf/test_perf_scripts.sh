#!/usr/bin/env bash
# Tests tools/perf/perf-gate.sh and tools/sweep/capture.sh against a fake adb (tools/perf/fake-adb) that prints canned device output.
# No device needed; run from anywhere: tools/perf/test_perf_scripts.sh. Exits 1 if any scenario gives the wrong exit status.
set -uo pipefail
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)
export PATH="$here/fake-adb:$PATH"
export FLING_SECONDS=1
fail=0

expect() { # <name> <expected exit> <command...>
  local name=$1 want=$2; shift 2
  "$@" >/dev/null 2>&1
  local got=$?
  if [ "$got" -eq "$want" ]; then echo "ok   $name (exit $got)"; else echo "FAIL $name: wanted exit $want, got $got"; fail=1; fi
}

gate() { OUT_DIR=$(mktemp -d) env "$@" bash "$root/tools/perf/perf-gate.sh"; }

expect "perf-gate passes with good numbers" 0 gate FAKE_GFX="$here/testdata/gfx_ok.txt"
expect "perf-gate fails on slow frames"     1 gate FAKE_GFX="$here/testdata/gfx_bad.txt"
expect "perf-gate fails on slow cold start" 1 gate FAKE_GFX="$here/testdata/gfx_ok.txt" FAKE_COLD=2100
expect "perf-gate fails on too much memory" 1 gate FAKE_GFX="$here/testdata/gfx_ok.txt" FAKE_PSS=400000

tmp=$(mktemp -d)
mkdir -p "$tmp/tools/sweep" "$tmp/docs"
cp "$root/tools/sweep/capture.sh" "$tmp/tools/sweep/"
expect "capture saves a screenshot"        0 bash "$tmp/tools/sweep/capture.sh" share-sheet light off
[ -s "$tmp/docs/sweep/share-sheet-light-off.png" ] || { echo "FAIL capture wrote no file"; fail=1; }
expect "capture rejects a bad theme"       2 bash "$tmp/tools/sweep/capture.sh" share-sheet sepia off
expect "capture rejects a bad mode"        2 bash "$tmp/tools/sweep/capture.sh" share-sheet light maybe

# apk-size.sh: report only without a baseline, pass within +5%, fail above
sz="$root/tools/apk-size.sh"
apks=$(mktemp -d); mkdir -p "$apks/tools" "$apks/docs/release"
cp "$sz" "$apks/tools/"
head -c 1000000 /dev/zero > "$apks/a-arm64-v8a.apk"
expect "apk-size reports without a baseline"   0 bash "$apks/tools/apk-size.sh" "$apks"
echo 1000000 > "$apks/docs/release/apk-size-baseline.txt"
expect "apk-size passes when unchanged"        0 bash "$apks/tools/apk-size.sh" "$apks"
head -c 1100000 /dev/zero > "$apks/a-arm64-v8a.apk"
expect "apk-size fails above +5 percent"       1 bash "$apks/tools/apk-size.sh" "$apks"
expect "apk-size rejects an empty directory"   2 bash "$apks/tools/apk-size.sh" "$(mktemp -d)"

# check-plan-status.sh: passes on the real docs, fails when a work package loses its STATUS row
chk=$(mktemp -d); mkdir -p "$chk/tools" "$chk/docs/plans"
cp "$root/tools/check-plan-status.sh" "$chk/tools/"; cp "$root"/docs/plans/0*.md "$root/docs/plans/STATUS.md" "$chk/docs/plans/"
expect "check-plan-status passes on the real docs" 0 bash "$chk/tools/check-plan-status.sh"
grep -v -E '^\| 6\.8 ' "$root/docs/plans/STATUS.md" > "$chk/docs/plans/STATUS.md"
expect "check-plan-status fails when 6.8 has no row" 1 bash "$chk/tools/check-plan-status.sh"

exit $fail
