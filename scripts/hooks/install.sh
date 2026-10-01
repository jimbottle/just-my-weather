#!/bin/sh
# Make sure the tracked pre-commit gate (scripts/hooks/pre-commit) is what git
# runs, wherever git looks for hooks.
#
# bd sets core.hooksPath to .beads/hooks, so .git/hooks/ is ignored entirely —
# a symlink there (the old installer's approach) ran nothing for months. The
# gate is therefore CALLED from the tracked .beads/hooks/pre-commit, and this
# script only verifies that wiring (or, on a clone without core.hooksPath,
# installs the symlink in the hooks dir git actually resolves). Idempotent;
# refuses to clobber a hook it does not recognise.
set -e
root=$(git rev-parse --show-toplevel)
cd "$root"
chmod +x scripts/hooks/pre-commit
hooks_dir=$(git rev-parse --git-path hooks)
dest="$hooks_dir/pre-commit"

if grep -q 'BEGIN PROJECT GATE' "$dest" 2>/dev/null; then
    echo "ok: $dest calls scripts/hooks/pre-commit (core.hooksPath=$(git config core.hooksPath))"
    exit 0
fi
if [ -L "$dest" ] && [ "$(readlink "$dest")" = "$root/scripts/hooks/pre-commit" ]; then
    echo "ok: $dest -> scripts/hooks/pre-commit"
    exit 0
fi
if [ -e "$dest" ]; then
    echo "error: $dest exists and is not the project gate. If it is bd's hook, run" >&2
    echo "       'bd hooks install' then re-check; otherwise move it aside and re-run." >&2
    exit 1
fi
mkdir -p "$hooks_dir"
ln -s "$root/scripts/hooks/pre-commit" "$dest"
echo "installed: $dest -> scripts/hooks/pre-commit"
