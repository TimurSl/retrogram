#!/usr/bin/env bash
# Fails when a work package named in docs/plans/0*.md has no line in docs/plans/STATUS.md, or when STATUS.md names no state for it.
#   tools/check-plan-status.sh
# A WP counts as named when a plan file has a heading "## WP N.M" / "### WP N.M", a list item "- **WP N.M", or a Phase 6 table row "| 6.N |".
# STATUS.md must mention the id N.M and, on the same line, one of the state words: Built, Device, You, Ruled out, Nothing to change, Documented, Tooling,
# or the id must sit in a row that does (checked per line, so a heading row of a group such as "5.1 matrix, 5.2 ..." covers every id on it).
set -euo pipefail
cd "$(dirname "$0")/.."
status=docs/plans/STATUS.md
[ -f "$status" ] || { echo "missing $status" >&2; exit 2; }
ids=$(grep -h -o -E '^#{2,3} WP [0-9]+\.[0-9]+|^- \*\*WP [0-9]+\.[0-9]+|^\| 6\.[0-9]+ ' docs/plans/0*.md | grep -o -E '[0-9]+\.[0-9]+' | sort -t. -k1,1n -k2,2n -u)
[ -n "$ids" ] || { echo "no WP ids found in docs/plans" >&2; exit 2; }
states='Built|Device|You|Ruled out|ruled out|Nothing to change|Nothing left|Documented|Tooling|built|Tested'
missing=0
count=0
for id in $ids; do
  count=$((count + 1))
  re="(^|[^0-9.])${id//./\\.}([^0-9]|$)"
  line=$(grep -E "$re" "$status" | grep -E "$states" | head -1 || true)
  if [ -z "$line" ]; then
    echo "NO STATE for WP $id in $status"
    missing=1
  fi
done
echo "checked $count work packages: $([ $missing -eq 0 ] && echo 'every one has a state' || echo 'some are missing')"
exit $missing
