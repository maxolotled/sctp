#!/bin/bash
# Usage: ./gather-snapshots.sh "6 hours ago" [path-to-repo]
#
# Pulls out every version of data/listings.json committed within the given
# time window (plus the one commit immediately before it, as a safety
# baseline), and saves each to snapshots/<timestamp>_<shorthash>.json
#
# Run this from inside your cloned repo, or pass the repo path as $2.

set -e

SINCE="${1:-6 hours ago}"
REPO_DIR="${2:-.}"
FILE_PATH="data/listings.json"
OUT_DIR="snapshots"

cd "$REPO_DIR"
mkdir -p "$OUT_DIR"

echo "Finding commits touching $FILE_PATH since: $SINCE"

# Get commits in the window, oldest first.
COMMITS=$(git log --since="$SINCE" --reverse --format="%H" -- "$FILE_PATH")

# Also grab the one commit right before the window, so we have a safety
# baseline even if the window boundary lands mid-corruption.
BASELINE=$(git log --before="$SINCE" -1 --format="%H" -- "$FILE_PATH")

ALL_COMMITS=""
if [ -n "$BASELINE" ]; then
	ALL_COMMITS="$BASELINE"
fi
ALL_COMMITS="$ALL_COMMITS $COMMITS"

COUNT=0
for hash in $ALL_COMMITS; do
	[ -z "$hash" ] && continue
	SHORT=$(git rev-parse --short "$hash")
	TS=$(git show -s --format=%cI "$hash" | tr -d ':')
	OUT_FILE="$OUT_DIR/${TS}_${SHORT}.json"

	if git show "$hash:$FILE_PATH" > "$OUT_FILE" 2>/dev/null; then
		COUNT=$((COUNT+1))
		echo "  saved $OUT_FILE"
	else
		echo "  (skipped $hash — file didn't exist at this commit)"
		rm -f "$OUT_FILE"
	fi
done

echo ""
echo "Saved $COUNT snapshots to $OUT_DIR/"
echo ""
echo "Next step:"
echo "  node recover-all.js $OUT_DIR/*.json > merged-listings.json"
