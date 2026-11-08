#!/usr/bin/env bash
# WP 4.2 screenshot helper (docs/plans/04-global-sweep.md): saves what is on the connected device's screen as
#   docs/sweep/<screen>-<theme>-<mode>.png
#
#   tools/sweep/capture.sh <screen> <light|night> <off|on>
#
#   <screen>  a name from docs/sweep/README.md (share-sheet, attach-sheet, ...)
#   <mode>    "off" = Settings > Retrogram "Classic sheets and panels" OFF (the 12.4 look, the "before"),
#             "on"  = the same switch ON (the "after")
# Needs `adb` and one connected device. It does not drive the app: open the screen yourself, then run this.
set -euo pipefail
screen=${1:?usage: tools/sweep/capture.sh <screen> <light|night> <off|on>}
theme=${2:?theme: light or night}
mode=${3:?mode: off or on}
case "$theme" in light|night) ;; *) echo "theme must be light or night" >&2; exit 2 ;; esac
case "$mode" in off|on) ;; *) echo "mode must be off or on" >&2; exit 2 ;; esac
command -v adb >/dev/null || { echo "adb not found" >&2; exit 2; }
adb get-state >/dev/null 2>&1 || { echo "no device connected" >&2; exit 2; }
out="$(cd "$(dirname "$0")/../.." && pwd)/docs/sweep"
mkdir -p "$out"
file="$out/$screen-$theme-$mode.png"
adb exec-out screencap -p > "$file"
echo "saved $file ($(stat -c %s "$file") bytes)"
