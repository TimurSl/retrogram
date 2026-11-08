#!/usr/bin/env bash
# Provenance map (PLAN.md section 4, docs/provenance.md): for each source namespace of the tree, how many Java/Kotlin files it has and how many of them
# carry a copyright or license header, plus the files outside org/telegram that mention a downstream project by name.
#   tools/provenance.sh
set -euo pipefail
cd "$(dirname "$0")/../TMessagesProj/src"
printf '%-34s %6s %8s  %s\n' namespace files headers origin
row() { # <path fragment> <origin>
  local files headers
  files=$(find . \( -path "*/$1/*" \) \( -name '*.java' -o -name '*.kt' \) | wc -l)
  headers=$(find . \( -path "*/$1/*" \) \( -name '*.java' -o -name '*.kt' \) -print0 | xargs -0 grep -l -i -E "copyright|license|licence" 2>/dev/null | wc -l) || true
  printf '%-34s %6s %8s  %s\n' "$1" "$files" "$headers" "$2"
}
row org/telegram        "Telegram for Android (DrKLO), GPL-2.0-or-later, plus NagramXF edits"
row com/exteragram      "exteraGram"
row com/radolyn         "AyuGram"
row tw/nekomimi         "Nekogram / NagramX"
row xyz/nextalone       "Nagram"
row app/retrogram       "Retrogram (this project)"
echo
echo "files outside org/telegram that name a downstream project (count):"
for name in AyuGram Cherrygram OctoGram exteraGram NagramX Nekogram; do
  n=$(grep -rli "$name" --include=*.java --include=*.kt . 2>/dev/null | grep -v "^./main/java/org/telegram/" | wc -l)
  printf '  %-12s %s\n' "$name" "$n"
done
