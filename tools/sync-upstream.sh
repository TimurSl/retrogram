#!/usr/bin/env bash
# sync-upstream: merge a new NagramXF release into a sync/<name> branch (docs/plans/07-validation-and-sync.md, WP 8.1).
#
#   tools/sync-upstream.sh <name> [<nagramxf-ref>]
#
#   <name>          branch suffix, e.g. 13.1.2  -> branch sync/13.1.2
#   <nagramxf-ref>  tag, branch or sha to merge (default: nagramxf/dev)
#
# Steps: fetch, branch from the current branch (normally main), merge, then print what to do next:
# glass-guard hits for the merge, the TL layer change and new top-level classes (seed rows for docs/feature-matrix.md).
# It never resolves conflicts: re-apply the Retrogram hook in each conflicting file instead of taking "theirs" wholesale.
# It never pushes.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

name=${1:?usage: tools/sync-upstream.sh <name> [<nagramxf-ref>]}
ref=${2:-nagramxf/dev}
branch="sync/$name"
JAVA=TMessagesProj/src/main/java

if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "error: working tree has uncommitted changes" >&2; exit 1
fi

if ! git remote get-url nagramxf >/dev/null 2>&1; then
  git remote add nagramxf https://github.com/Keeperorowner/NagramXF.git
fi
if ! git remote get-url telegram >/dev/null 2>&1; then
  git remote add telegram https://github.com/DrKLO/Telegram.git
fi

echo ">> fetching nagramxf (blobless: history is complete, file contents come on demand)"
git fetch --filter=blob:none --tags nagramxf dev

base_branch=$(git rev-parse --abbrev-ref HEAD)
before=$(git rev-parse HEAD)
merge_base=$(git merge-base HEAD "$ref")
echo ">> base: $base_branch @ ${before:0:8}; merge-base with $ref: ${merge_base:0:8}"

if git show-ref --verify --quiet "refs/heads/$branch"; then
  echo "error: branch $branch already exists" >&2; exit 1
fi
git switch -c "$branch"

echo ">> merging $ref"
if ! git merge --no-ff "$ref" -m "Sync NagramXF $name ($ref)"; then
  cat >&2 <<MSG

Merge stopped with conflicts. Conflicts are expected in files that carry Retrogram hooks
(LaunchActivity, DialogsActivity, ChatActivity, ChatActivityEnterView, SettingsActivity,
ProfileActivity, TMessagesProj/build.gradle, blur3 sources). Re-apply each hook; then
  git add <files> && git commit
and run the report below by hand:
  tools/glass-guard.sh ${merge_base:0:8}..HEAD
MSG
  git diff --name-only --diff-filter=U >&2
  exit 2
fi

echo
echo ">> glass-guard on what the merge brought in ($merge_base..HEAD)"
bash tools/glass-guard.sh "$merge_base..HEAD" || echo "(each hit above is a to-do: guard it for retro mode, or allow-list it with a reason)"

echo
echo ">> TL layer"
old=$(git show "$merge_base:$JAVA/org/telegram/tgnet/TLRPC.java" 2>/dev/null | sed -n 's/.*int LAYER = \([0-9]*\);.*/\1/p' | head -1)
new=$(sed -n 's/.*int LAYER = \([0-9]*\);.*/\1/p' "$JAVA/org/telegram/tgnet/TLRPC.java" | head -1)
echo "layer: ${old:-?} -> ${new:-?}"

echo
echo ">> new top-level classes (candidates for docs/feature-matrix.md)"
git diff --diff-filter=A --name-only "$merge_base..HEAD" -- "$JAVA" | sed "s#^$JAVA/##" | grep -v '^org/telegram/tgnet/' | head -80

echo
echo "Next: open a PR from $branch; CI builds plugins off (and on for sync/* branches). Nothing was pushed."
