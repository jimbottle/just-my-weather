#!/usr/bin/env bash
# Check the region registry against the Play Console — read-only.
#
# The registry (app/src/main/java/io/raylytics/justmyweather/region/Regions.kt)
# records each prepared region's Play status; only a human changes Play
# (docs/REGIONS.md). This catches the two ways they drift:
#   - a region marked LIVE in code that Play's production track doesn't list
#     (the console change was never made, or was undone), and
#   - a country Play's production track lists that code doesn't call LIVE
#     (the console was changed but the registry wasn't updated).
#
#   scripts/check-regions.sh            # exit 0 when they agree
#
# Needs play.properties (docs/PLAY_STORE.md); opens and discards one edit.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REGISTRY="$ROOT/app/src/main/java/io/raylytics/justmyweather/region/Regions.kt"

# Prepared("GB", PlayStatus.LIVE, ...) — possibly across lines.
code_live=$(python3 - "$REGISTRY" <<'PY'
import re, sys
text = open(sys.argv[1], encoding="utf-8").read()
for code, status in re.findall(r'Prepared\(\s*"([A-Z]{2})",\s*PlayStatus\.([A-Z]+)', text):
    if status == "LIVE":
        print(code)
PY
)
play_live=$("$ROOT/scripts/android/play-countries.sh" production)

code_sorted=$(printf '%s\n' $code_live | sort -u)
play_sorted=$(printf '%s\n' $play_live | sort -u)
missing_on_play=$(comm -23 <(echo "$code_sorted") <(echo "$play_sorted"))
missing_in_code=$(comm -13 <(echo "$code_sorted") <(echo "$play_sorted"))

echo "LIVE in code: $(echo $code_sorted)"
echo "On Play (production): $(echo $play_sorted)"
status=0
if [ -n "$missing_on_play" ]; then
  echo "!! LIVE in Regions.kt but not on Play: $(echo $missing_on_play) — make the console change, or set them back to READY"
  status=1
fi
if [ -n "$missing_in_code" ]; then
  echo "!! On Play but not LIVE in Regions.kt: $(echo $missing_in_code) — mark them LIVE (docs/REGIONS.md step 9)"
  status=1
fi
[ "$status" -eq 0 ] && echo "ok: the registry and Play agree"
exit "$status"
