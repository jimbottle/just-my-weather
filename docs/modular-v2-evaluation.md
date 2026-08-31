# Modular grid v2 — what it must be judged against

The home glance is becoming a modular flow grid: each data point is a module
with a width on a 4-column grid, modules flow in the user's order, and the
arrangement is edited in place the way an Android home screen is (long-press,
wiggle, drag, resize). This document records the tensions identified when the
direction was approved (2026-08-24), framed as the criteria the new design is
compared against. If v2 loses on one of these, that is a finding, not a
footnote.

## Criteria

### 1. The calm default survives

"One glance answers *what's it like right now*" is the product, and the grid is
machinery in service of it — not a widget board. Judge by opening a fresh
install:

- The default view is still one big temperature and the conditions, readable in
  well under a second.
- The grid's presence is quiet: thin borders and empty trailing columns are the
  only new pixels. No handles, no affordances, no editor chrome until the user
  long-presses.
- Nothing animates, pulses, or invites interaction on the ordinary open.
- **Width is prominence, literally.** A tile's value is *fitted* to its tile
  (`ui/home/FittedText`): it draws at the largest size between a per-span
  ceiling (hero at full, ~40% of that at half, ~25% at quarter — set in
  `DensitySpec.valueCeiling`) and a 14sp floor that fits the width in two
  lines with no word broken. Before this the sizes were a cliff (120 → 28 →
  22sp), a full-width Conditions tile was a five-line tower of 120sp words,
  and "30.12 inHg" spilled out of a quarter tile. Judge by cycling Conditions
  through all three widths: the phrase should re-flow, never break mid-word,
  and never cross its border.
- **The face survives resizing.** Every style in the type scale carries the
  chosen family (`ui/theme/Type.kt`; pinned by `TypographyTest`). Material's
  defaults are hard-wired to sans, and the scale used to re-face only four
  styles — so a Serif user's temperature turned sans the moment they made it
  half-width.

### 2. Grid vs. mode toggle coherence — **resolved, now a standing limit**

The screen is now **two grids and no more**: the glance (arrangeable) and the
forecast (data-driven), both drawn by one engine (`ui/home/TileGrid`). The
screen-wide Now/Hourly/Daily toggle is gone. Its three states split into the two
questions they actually were:

- *Does the forecast show at all?* → `ViewConfig.showForecast`, on the customize
  screen. This is what the old NOW meant.
- *Which framing does it show?* → `ForecastMode`, as a toggle **on the forecast
  grid itself**.

Two settings died in the move: `ForecastLayout` (side-by-side vs stacked) is
subsumed — the grid flows its tiles, so there is no direction left to choose.

What to keep judging:

- **Arrange mode carries one line of chrome, and only then.** Under the
  wiggling grid: "Drag to move · Tap to resize", then Done. Drag-to-move is
  the launcher's and needs no telling, but no launcher resizes on a tap, and a
  gesture nobody would guess is a feature nobody has. Three ways out, all
  launcher-native: Done, Back, or a tap on empty ground.
- **Motion is legible and layer-only.** A dropped tile springs into its slot
  (scale and offset together); a tile whose slot changed — under a drag, or
  from an accessibility Move — slides from where it was to where it is. No
  bounce, ~250ms, driven from the tile's layout callback and applied in its
  graphics layer, so nothing recomposes for it. Slot and width changes tick
  (`TextHandleMove`), the launcher's way of letting the thumb feel the grid
  under itself.
- **Two grids is the ceiling.** A third grid is a design change, not an
  increment; it is the point at which the page stops being "now, and next".
- Does the seam still show? A user can arrange the glance but not the forecast.
  That asymmetry is intended (one is yours, one is NWS's) and the shared tile
  language is what should carry it. If people try to drag forecast tiles, the
  language is over-promising.
- Sun times are now a module too, so nothing sits between the two grids. It
  took the module catalog generalising past `WeatherField` (`ModuleKey`), and
  the two-day table survived: the module is **span-adaptive**, drawing the
  dated rows at full width and condensing to today's pair when narrower, rather
  than being flattened into "next sunrise / next sunset". That is the pattern
  for any future module whose content is not one string.

### 3. Accessibility parity

Every gesture-only operation must have a non-gesture equivalent, or the grid
took customization away from TalkBack and switch-access users that the old
list-based customize screen gave them:

- Reorder: the customize screen's up/down arrows remain, driving the same
  config.
- Resize: the customize screen's span chips are the non-gesture path.
- On the tiles themselves: each module carries accessibility custom actions —
  Move up, Move down, and "Resize to &lt;next width&gt;" — calling the same
  `onMove`/`onCycleSpan` the drag and tap do, with its width announced as
  state. They are offered **at all times, not only while arranging**: a hold
  -and-drag is not a gesture a TalkBack or switch-access user can perform, so
  gating them behind that mode would gate them behind nothing. A move that
  would be a no-op (up from the first tile, down from the last) is absent
  rather than ignored. Asserted in `ModuleGridTest`.

### 4. Implementation legibility

This app is built to be open-sourced; a drag-grid is the least legible thing in
it, so it is held to structural rules:

- Grid math (span model, row packing, reorder transforms) is pure Kotlin in
  `view/`, JVM-tested, no Compose types.
- The gesture/animation machinery lives in one file (`ui/home/ModuleGrid.kt`)
  and does not leak drag state elsewhere; `ui/home/TileGrid.kt` holds only what
  both grids share (packing + the tile shell) and knows nothing about either.
- Judge by whether "add a module size" or "change the wiggle" is a one-file
  change a newcomer can find.

### 4a. What the tests can and cannot reach

Worth stating plainly, because the gaps are where the shipped bugs lived:

- **JVM** covers the geometry (`GridPackingTest`, `ViewConfigTest`) and the
  persistence path including overlapping edits (`HomeViewModelTest`).
- **Instrumented** (`ModuleGridTest`) covers what only a device measures —
  spans in real dp, gaps left empty — and the accessibility actions.
- **Maestro** covers the real touch paths: `06-arrange.yaml` for long-press
  entry, tap-to-cycle, exit and persistence across a restart; `07-forecast.yaml`
  for the framing toggle and for hiding/showing the second grid without
  disturbing the first.
- **The drag reorder is now automatable** — once the tiles wiggle a plain
  swipe moves them, so `06-arrange.yaml` asserts it (added with
  just-my-weather-csa). It has not actually run yet: Maestro's on-device
  driver is broken (just-my-weather-p6t).
- **The fit is measured on-device** (`ModuleGridTest.aLongValueShrinksToStayInsideItsTile`):
  a conditions phrase in a quarter tile must end inside the tile's bounds.
  The motion — lift, settle, slide — has no automated cover: it lives in
  graphics-layer reads of Animatables, which neither semantics nor screenshots
  in this suite observe. **Verify by hand:** drop a tile and watch it land
  rather than appear; drag one across a neighbour and watch the neighbour
  slide, not jump.
- **One path has no automated cover at all: a drag interrupted mid-flight by
  arrange mode ending** (system Back, or a second finger reaching "Done
  arranging"). It cannot be synthesised with the tools here — Compose's test
  gestures do not drive this grid's detectors at all (a plain-swipe control
  test produced no drag), and `adb shell input motionevent` cannot hold a drag
  open across a separate key event. The code is written so the path cannot
  strand state (the detector cleans up in a `finally`, which cancellation
  runs), but that is reasoning, not a measurement. **Verify by hand:** enter
  arrange mode, start dragging a tile, and press Back without lifting — the
  tile should snap back and reordering must still work afterwards.

### 5. Runtime behaviour

Measured on the API 35+ emulator (edge-to-edge enforced), per the device rules:

- Wiggle recomposes tiles, not the screen; entering/leaving arrange mode does
  not stutter.
- A drag writes to DataStore only on slot changes (a handful per drag), never
  per frame.
- The glance renders identically outside arrange mode before and after — the
  grid costs nothing when it is not being edited.

## Deliberate scope cuts (revisit, don't relitigate silently)

| Cut | v2 choice | The fuller version, and when to revisit |
| --- | --- | --- |
| Placement | Flow grid: order + span, rows pack greedily, no gaps | Free 2D placement with persistent empty cells needs a custom layout plus collision/reflow. Revisit only if users ask for deliberate whitespace. |
| Spans | Quarter, half, full (1/2/4 of 4 columns) | Three-quarter width exists on launchers; it earns its place only when a module's content wants it. |
| Resize | Tap a wiggling tile to cycle its span, with the hint line saying so | Launcher-style drag handles. Revisit if cycling feels like a slot machine once spans grow beyond three. |
| Value size | Fitted per tile: span sets the ceiling, content sets the size | A fixed size per span reads more uniformly across tiles of the same width but cannot hold a long phrase. Revisit only if fitted neighbours look ragged in practice. |
| Row heights | Intrinsic — a row is as tall as its tallest tile | Fixed square cells (true "4 square wide"). Revisit when forecast modules arrive and want 2-row heights. |
