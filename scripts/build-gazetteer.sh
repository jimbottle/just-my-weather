#!/usr/bin/env bash
# Rebuild the bundled place list: the US Census Gazetteer for the United
# States and its territories, then GeoNames for every other country.
#
# WHY THIS EXISTS: the app needs to turn "Louisville" into a coordinate, and it
# has no geocoder to ask. NWS does not geocode, and every online geocoder wants
# an API key — which would break the two promises this project makes about
# itself: no key to get started, and it builds from source anywhere. So the
# lookup table ships in the APK.
#
# WHY THE CENSUS GAZETTEER: it is public domain (a work of the US government),
# needs no attribution or licence notice, and covers incorporated places plus
# census-designated places across the states, DC, PR, VI, GU, AS and MP — which
# is exactly the footprint NWS forecasts for.
#
# WHY GEONAMES ABROAD: outside NWS territory the forecast comes from MET
# Norway, and a place abroad needs the same lookup. GeoNames' cities15000
# (every place of 15,000 people or more, ~30k outside the US) is CC BY 4.0 —
# the attribution sits in App settings beside MET's — and each row carries
# the two things a foreign place cannot get from NWS: its ISO country code
# (which is also how the app tells which REGION a coordinate is in; see
# docs/REGIONS.md) and its IANA time zone.
#
# Row format, tab-separated:
#   US rows:     name  state  lat  lon
#   world rows:  name  ""     lat  lon  country  timezone
# A row with no country column is a US place; NWS supplies its zone.
#
# The output is checked in, so contributors do not need to run this. Run it to
# pick up a newer vintage; the asset it writes is the only thing the app reads.
#
#   scripts/build-gazetteer.sh [census-year]
#
# Requires: curl, unzip, python3. Writes app/src/main/assets/places.tsv.
set -euo pipefail

YEAR="${1:-2023}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/app/src/main/assets/places.tsv"
URL="https://www2.census.gov/geo/docs/maps-data/data/gazetteer/${YEAR}_Gazetteer/${YEAR}_Gaz_place_national.zip"
GEONAMES_URL="https://download.geonames.org/export/dump/cities15000.zip"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "→ downloading ${YEAR} gazetteer"
curl -sSfL --max-time 300 -o "$WORK/gaz.zip" "$URL"
unzip -q -o "$WORK/gaz.zip" -d "$WORK"
SRC="$(find "$WORK" -name '*.txt' | head -1)"
[ -n "$SRC" ] || { echo "!! no .txt in the archive — did the layout change?" >&2; exit 1; }

echo "→ downloading GeoNames cities15000"
curl -sSfL --max-time 300 -o "$WORK/geonames.zip" "$GEONAMES_URL"
unzip -q -o "$WORK/geonames.zip" -d "$WORK"
WORLD="$WORK/cities15000.txt"
[ -f "$WORLD" ] || { echo "!! no cities15000.txt in the archive — did the layout change?" >&2; exit 1; }

echo "→ trimming"
mkdir -p "$(dirname "$OUT")"
python3 - "$SRC" "$OUT" "$WORLD" <<'PY'
import csv, sys

src, out, world = sys.argv[1], sys.argv[2], sys.argv[3]

# The NAME column carries the legal/statistical type as a trailing word ("Dover
# city", "Kiryas Joel village", "Ocean City CDP"). People do not say those, and
# on a phone-width row they cost more than they explain — so they come off.
# Stripping can collide two entries onto one name (a "town" and a "city" of the
# same name in one state), so the larger by land area wins: it is the one
# somebody typing that name almost certainly means.
SUFFIXES = (
    " city", " town", " village", " borough", " municipality", " CDP",
    " township", " plantation", " comunidad", " zona urbana", " government",
    " metro government", " unified government", " consolidated government",
    " urbana", " county", " parish", " district", " metro township",
)
# Longest first, or a short suffix eats the tail of a longer one: " government"
# would match "…metro government" and leave a dangling "metro" behind.
SUFFIXES = tuple(sorted(SUFFIXES, key=len, reverse=True))


def clean(name: str) -> str:
    changed = True
    while changed:
        changed = False
        # Re-strip inside the loop, not just at the end: removing "(balance)"
        # leaves a trailing space, and the suffix tests below are anchored to
        # the end of the string — so without this, "Louisville/Jefferson County
        # metro government (balance)" stops at "... metro" instead of reducing
        # to the name anyone would type.
        name = name.replace("(balance)", "").strip()
        for suffix in SUFFIXES:
            if name.endswith(suffix) and len(name) > len(suffix):
                name, changed = name[: -len(suffix)].strip(), True
    return name.strip()


best: dict[tuple[str, str], tuple[float, str, str, str, str]] = {}
with open(src, encoding="latin-1", newline="") as handle:
    for row in csv.DictReader(handle, delimiter="\t"):
        row = {k.strip(): (v.strip() if v else "") for k, v in row.items()}
        name, state = clean(row["NAME"]), row["USPS"]
        lat, lon, land = row["INTPTLAT"], row["INTPTLONG"], row["ALAND"]
        if not (name and state and lat and lon):
            continue
        key = (name.lower(), state)
        area = float(land or 0)
        # Two decimals is ~1.1 km, comfortably inside the 2.5 km NWS grid cell
        # the coordinate is only ever used to look up — more precision would be
        # bytes the forecast cannot tell apart.
        entry = (area, name, state, f"{float(lat):.2f}", f"{float(lon):.2f}")
        if key not in best or area > best[key][0]:
            best[key] = entry

rows = sorted(best.values(), key=lambda e: (e[2], e[1]))

# GeoNames, for everywhere the Census does not cover. The US and the
# territories NWS forecasts are skipped — the Census rows above are richer
# there (every incorporated place, not just the 15,000-people ones).
CENSUS_COUNTRIES = {"US", "PR", "GU", "VI", "AS", "MP"}
# Feature codes that are not a place someone would pick: a neighbourhood or
# section of a city (PPLX — London alone has dozens), and places that are
# historical, abandoned or destroyed.
SKIP_FEATURES = {"PPLX", "PPLH", "PPLQ", "PPLW"}
abroad: dict[tuple[str, str], tuple[int, str, str, str, str, str]] = {}
with open(world, encoding="utf-8") as handle:
    for line in handle:
        f = line.rstrip("\n").split("\t")
        if len(f) < 18:
            continue
        name, lat, lon, feature, country, population, zone = f[1], f[4], f[5], f[7], f[8], f[14], f[17]
        if country in CENSUS_COUNTRIES or feature in SKIP_FEATURES or not (name and country and zone):
            continue
        key = (name.lower(), country)
        pop = int(population or 0)
        # Same name twice in one country: the larger is almost certainly
        # the one meant, as with the Census collisions above.
        entry = (pop, name, f"{float(lat):.2f}", f"{float(lon):.2f}", country, zone)
        if key not in abroad or pop > abroad[key][0]:
            abroad[key] = entry
world_rows = sorted(abroad.values(), key=lambda e: (e[4], e[1]))

with open(out, "w", encoding="utf-8") as handle:
    # Tab-separated, not comma: place names contain commas and quoting rules
    # are a parser bug waiting to happen. Tabs cannot appear in a name.
    for _, name, state, lat, lon in rows:
        handle.write(f"{name}\t{state}\t{lat}\t{lon}\n")
    for _, name, lat, lon, country, zone in world_rows:
        handle.write(f"{name}\t\t{lat}\t{lon}\t{country}\t{zone}\n")

print(f"   {len(rows)} US places, {len(world_rows)} elsewhere")
PY

echo "→ wrote $OUT ($(du -h "$OUT" | cut -f1))"
