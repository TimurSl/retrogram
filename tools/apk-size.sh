#!/usr/bin/env bash
# APK size tracking (docs/plans/07-validation-and-sync.md, Phase 7 table): prints the size of every APK in a directory,
# compares the arm64 one with docs/release/apk-size-baseline.txt (one number, bytes) and fails when it grew by more than 5%.
#
#   tools/apk-size.sh <apk-dir> [--update-baseline]
#
# No baseline file = report only (exit 0). --update-baseline writes the arm64 size as the new baseline.
set -euo pipefail
dir=${1:?usage: tools/apk-size.sh <apk-dir> [--update-baseline]}
baseline_file="$(dirname "$0")/../docs/release/apk-size-baseline.txt"
limit_percent=5

summary() { if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then echo "$@" >> "$GITHUB_STEP_SUMMARY"; fi; }

arm=""
summary "### APK sizes"
for f in "$dir"/*.apk; do
  [ -e "$f" ] || { echo "no APK in $dir" >&2; exit 2; }
  size=$(stat -c %s "$f")
  echo "$(basename "$f"): $size bytes ($((size / 1024 / 1024)) MB)"
  summary "- \`$(basename "$f")\`: $((size / 1024 / 1024)) MB ($size bytes)"
  case "$f" in *arm64*|*arm64-v8a*) arm=$size ;; esac
done
[ -n "$arm" ] || { echo "no arm64 APK found; nothing to compare" ; exit 0; }

if [ "${2:-}" = "--update-baseline" ]; then
  echo "$arm" > "$baseline_file"
  echo "baseline updated to $arm bytes"
  exit 0
fi

if [ ! -f "$baseline_file" ]; then
  echo "no baseline (docs/release/apk-size-baseline.txt): report only"
  summary "No baseline yet; run \`tools/apk-size.sh <dir> --update-baseline\` on a release build."
  exit 0
fi

base=$(tr -d '[:space:]' < "$baseline_file")
growth=$(( (arm - base) * 100 / base ))
echo "arm64: $arm bytes, baseline $base bytes, change ${growth}% (limit +${limit_percent}%)"
summary "arm64 change vs baseline: ${growth}% (limit +${limit_percent}%)"
[ "$growth" -le "$limit_percent" ] || { echo "APK grew by more than ${limit_percent}%" >&2; exit 1; }
