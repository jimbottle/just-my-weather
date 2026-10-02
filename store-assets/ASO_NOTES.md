# ASO notes — Just My Weather

What the Play listing is optimised to be found for, and the rules that keep
that honest. Decided 2026-10-02 by Evan.

## Target searches

| Phrase | Where it lives |
|---|---|
| **modular weather app** | short description (verbatim), full description opening line and section header |
| **custom weather** | short description ("custom view"), full description ("custom weather view", twice) |
| customizable weather app | full description, closing line |
| modular · custom · customizable (single words) | full description throughout; the Play tags where its fixed list offers a match |

The app name is plain **Just My Weather**. The working title "Just My Weather -
Modular Weather App" is 37 chars against Play's 30-char cap, so the descriptor
moved into the copy, where Play also indexes it.

## How Play ranks, in one paragraph

Play weighs the app name most, then the short description, then the full
description, with install velocity, ratings, and retention on top. Tags are
chosen from Play's own fixed list (up to five) and mainly affect which
"similar apps" clusters the listing joins, so they are worth setting but do not
carry free-text keywords. The full description is indexed, so natural repeated
use of the target phrases matters; stuffing does not (see rules).

## Rules

- **Natural language only.** Play's metadata policy rejects keyword lists,
  repeated phrases, and claims of ranking ("#1", "best"). Every use of a target
  phrase has to read as a sentence someone would write.
- **No competitor names, no "free" or "download" bait in the name**, no emoji
  or ALLCAPS words in the name or short description.
- **Measure before changing copy**: `python3 -c` over the two text files to
  check the 80/4000-char caps and that each target phrase still appears.
- **Name stays plain.** If a descriptor is ever reconsidered it must fit 30
  chars including the name, so at most "Just My Weather: Modular" (24).

## Checklist for each listing revision

1. Short description ≤ 80 chars and contains "modular weather app".
2. Full description ≤ 4000 chars, contains "modular weather app" and "custom
   weather", reads as prose.
3. Tags: Weather, Forecast, plus the closest Play-offered customisation tag;
   record the exact picks in `play-store-listing.md`.
4. Screenshot captions (when added) can carry "modular" and "custom" too.
5. Note the sync date in `play-store-listing.md`.
