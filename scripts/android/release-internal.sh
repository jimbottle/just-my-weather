#!/usr/bin/env bash
# Orchestrator: cut a Play "internal testing" build in one command.
#
#   1. refuse a dirty working tree    — the build must be a committed state
#   2. scripts/verify.sh              — the JVM gate (tests, ktlint, APKs)
#   3. bump-version-code              — Play's monotonic-int rule
#   4. bundle-release                 — signed AAB
#   5. upload-play --track internal   — live for testers in minutes, no review
#
# Afterwards: commit the versionCode bump and log the cut in docs/RELEASES.md.
# Only --notes is accepted; anything else (a different track, an explicit
# AAB) contradicts the intent of this script — run upload-play.sh directly.
#
# Agents: CLAUDE.md "Release confirmation gate" — every run needs Evan's
# go-ahead for THIS upload, in the current exchange.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"

forwarded_args=()
while [ $# -gt 0 ]; do
  case "$1" in
    --notes) shift; [ $# -gt 0 ] || die "--notes requires a value"; forwarded_args+=(--notes "$1"); shift ;;
    --notes=*) forwarded_args+=("$1"); shift ;;
    *) die "release-internal.sh only accepts --notes <text>. For other tracks or AAB paths, run upload-play.sh directly." ;;
  esac
done

cd "$PROJECT_ROOT"
if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
  die "working tree has uncommitted changes; commit (or stash) first so the cut is a known commit."
fi
# The Maestro flows are not run here (they need an emulator); CI's ui job
# covers the commit being released. Check it is green before cutting.
scripts/verify.sh

NEW_VC="$(bash "$SCRIPT_DIR/bump-version-code.sh" | tail -n 1)"
echo "==> versionCode bumped to $NEW_VC (versionName $(read_version_name))" >&2

# From here the tree is dirty (the bump). A failure must say how to get back
# to a state this script's own clean-tree guard will accept.
uploaded=0
on_fail() {
  [ "$uploaded" = 1 ] && return
  cat >&2 <<MSG

!! release-internal.sh failed after bumping versionCode to $NEW_VC.
   Nothing was uploaded, so the number is NOT burned:
     git checkout app/build.gradle.kts        # undo the bump, fix, re-run
   If upload-play.sh got as far as "uploaded: versionCode=$NEW_VC" before
   failing, the number IS burned — commit the bump instead and re-run.
MSG
}
trap 'on_fail' EXIT
bash "$SCRIPT_DIR/bundle-release.sh"
bash "$SCRIPT_DIR/upload-play.sh" --track internal ${forwarded_args[@]+"${forwarded_args[@]}"}
uploaded=1
trap - EXIT

cat >&2 <<MSG

==> Internal release pipeline complete: versionCode $NEW_VC is on the internal track.
    Now: git commit app/build.gradle.kts -m "release: versionCode $NEW_VC to internal"
         and add the cut to docs/RELEASES.md.
MSG
