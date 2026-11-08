#!/usr/bin/env bash
# Phase 7 device gate numbers (docs/plans/07-validation-and-sync.md). Needs `adb` and one connected device (the POCO X4 Pro 5G for real numbers).
#
#   tools/perf/perf-gate.sh [package]        default package: app.retrogram.messenger
#
# Steps (each prints one line and the script exits 1 when a hard limit is missed):
#   1. cold start: 10 x `am start -W`, median TotalTime, limit 1500 ms
#   2. fling:      30 s of swipes (FLING_SECONDS=n shortens it, used by the script's own test) in whatever screen is open (open a long chat or the chat list first), p95 frame time limit 8.3 ms
#   3. memory:     PSS after you let the app idle (run with IDLE_SECONDS=120 for the official check), limit 350 MB
#   4. performance class and Lite Mode preset are not scriptable here: read them from the app log (SharedConfig.getDevicePerformanceClass).
set -uo pipefail
pkg=${1:-app.retrogram.messenger}
here=$(cd "$(dirname "$0")" && pwd)
out=${OUT_DIR:-$(mktemp -d)}
mkdir -p "$out"
fail=0

command -v adb >/dev/null || { echo "adb not found" >&2; exit 2; }
adb get-state >/dev/null 2>&1 || { echo "no device connected" >&2; exit 2; }
launcher=$(adb shell cmd package resolve-activity --brief "$pkg" | tail -1 | tr -d '\r')
[ -n "$launcher" ] || { echo "cannot resolve the launcher activity of $pkg (is it installed?)" >&2; exit 2; }

echo "== cold start ($launcher), 10 runs"
times=()
for i in $(seq 1 10); do
  adb shell am force-stop "$pkg"
  sleep 2
  t=$(adb shell am start -W -n "$launcher" | tr -d '\r' | sed -n 's/^TotalTime: *//p')
  times+=("${t:-0}")
  sleep 3
done
median=$(printf '%s\n' "${times[@]}" | sort -n | awk '{a[NR]=$1} END {print a[int((NR+1)/2)]}')
echo "cold start median: ${median} ms (limit 1500)"
[ "${median:-99999}" -le 1500 ] || fail=1

echo "== fling, ${FLING_SECONDS:-30} s"
adb shell dumpsys gfxinfo "$pkg" reset >/dev/null
end=$((SECONDS + ${FLING_SECONDS:-30}))
size=$(adb shell wm size | tr -d '\r' | sed -n 's/.*: *//p' | tail -1)
w=${size%x*}; h=${size#*x}
while [ $SECONDS -lt $end ]; do
  adb shell input swipe $((w / 2)) $((h * 3 / 4)) $((w / 2)) $((h / 4)) 120
  adb shell input swipe $((w / 2)) $((h / 4)) $((w / 2)) $((h * 3 / 4)) 120
done
adb shell dumpsys gfxinfo "$pkg" > "$out/gfxinfo.txt"
python3 "$here/gfxinfo_p95.py" "$out/gfxinfo.txt" || fail=1

echo "== memory (idle ${IDLE_SECONDS:-0} s)"
sleep "${IDLE_SECONDS:-0}"
pss=$(adb shell dumpsys meminfo "$pkg" | tr -d '\r' | sed -n 's/.*TOTAL PSS: *\([0-9]*\).*/\1/p' | head -1)
[ -n "$pss" ] || pss=$(adb shell dumpsys meminfo "$pkg" | tr -d '\r' | awk '/^ *TOTAL +[0-9]/ {print $2; exit}')
echo "PSS: $((${pss:-0} / 1024)) MB (limit 350)"
[ $((${pss:-0} / 1024)) -le 350 ] || fail=1

echo "files in $out"
exit $fail
