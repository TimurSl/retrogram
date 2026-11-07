#!/usr/bin/env bash
# glass-guard: keep Liquid Glass / floating-chrome code from creeping back in (docs/plans/04-global-sweep.md, WP 4.4).
#
#   tools/glass-guard.sh                 baseline: count hits over the whole tree, print a summary, exit 0
#   tools/glass-guard.sh --strict        same scan, but exit 1 if any hit is not allowed
#   tools/glass-guard.sh <git-range>     scan only lines ADDED in the range (e.g. an upstream merge:
#                                        `tools/glass-guard.sh HEAD^1..HEAD`), exit 1 on any unallowed hit
#
# A hit is allowed when
#   * its file is under app/retrogram/,
#   * the line itself mentions RetroConfig or carries a `glass-guard:ok` comment (handled elsewhere), or
#   * its file matches a regex in tools/glass-guard.allow (format: <path-regex><TAB><reason>).
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

PATTERN='blur3|GlassTab|MainTabs|FragmentFloatingButton|FragmentSearchField|BlurredBackground|LiquidGlass|ProfileActivity2|FLAG_LIQUID_GLASS'
SRC='TMessagesProj/src/main/java TMessagesProj/src/main/kotlin'
ALLOW_FILE=tools/glass-guard.allow

mode=baseline
range=
case "${1:-}" in
  "") ;;
  --strict) mode=strict ;;
  -h|--help) sed -n '2,15p' "$0"; exit 0 ;;
  *) mode=range; range=$1 ;;
esac

allowed_file() {  # $1 = path
  case "$1" in */app/retrogram/*) return 0 ;; esac
  [ -f "$ALLOW_FILE" ] || return 1
  while IFS=$'\t' read -r re _reason; do
    case "$re" in ''|'#'*) continue ;; esac
    if [[ "$1" =~ $re ]]; then return 0; fi
  done < "$ALLOW_FILE"
  return 1
}

hits=$(mktemp); trap 'rm -f "$hits"' EXIT

if [ "$mode" = range ]; then
  # file<TAB>added line, for added lines only
  git diff -U0 --no-color "$range" -- $SRC | awk '
    /^\+\+\+ b\// { file = substr($0, 7); next }
    /^\+\+\+ /    { file = ""; next }
    /^\+/ && file != "" { print file "\t" substr($0, 2) }' \
    | grep -E -- "	.*($PATTERN)" > "$hits" || true
else
  # shellcheck disable=SC2086
  git grep -n -E "$PATTERN" -- $SRC | awk -F: '{ f=$1; $1=""; $2=""; sub(/^ +/, ""); print f "\t" $0 }' > "$hits" || true
fi

bad=0
total=0
declare -A per_file
while IFS=$'\t' read -r file line; do
  [ -n "$file" ] || continue
  total=$((total + 1))
  if allowed_file "$file" || [[ "$line" == *RetroConfig* || "$line" == *glass-guard:ok* ]]; then continue; fi
  bad=$((bad + 1))
  per_file["$file"]=$(( ${per_file["$file"]:-0} + 1 ))
done < "$hits"

echo "glass-guard ($mode${range:+ $range}): $total hit(s), $bad not allowed"
if [ "$bad" -gt 0 ]; then
  for f in "${!per_file[@]}"; do printf '%6d  %s\n' "${per_file[$f]}" "$f"; done | sort -rn | head -$([ "$mode" = range ] && echo 200 || echo 25)
fi

if [ "$mode" = baseline ]; then
  [ "$bad" -gt 0 ] && echo "baseline only: run with --strict or a git range to fail on these."
  exit 0
fi
if [ "$bad" -gt 0 ]; then
  echo "Handle each hit for retro mode (guard with RetroConfig / add to app/retrogram), or list the file in $ALLOW_FILE with a reason." >&2
  exit 1
fi
