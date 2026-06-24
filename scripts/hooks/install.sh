#!/bin/sh
# Symlink the tracked hooks into .git/hooks so they survive checkouts and stay
# version-controlled. Run from the repo root: scripts/hooks/install.sh
set -e
repo_root=$(git rev-parse --show-toplevel)
ln -sf ../../scripts/hooks/pre-commit "$repo_root/.git/hooks/pre-commit"
chmod +x "$repo_root/scripts/hooks/pre-commit"
echo "Installed pre-commit hook -> scripts/hooks/pre-commit"
