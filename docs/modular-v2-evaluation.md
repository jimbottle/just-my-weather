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

### 2. Grid vs. mode toggle coherence

v2 grids only the Now glance; the Hourly/Daily framings and the sun table keep
their existing rendering. That split is deliberate scoping, and it is also the
first thing to re-evaluate:

- Does the seam show? A user who can drag the temperature around but not the
  hourly strip has been given two mental models on one screen.
- The end state to test toward: forecast surfaces and sun times as grid modules
  (just-my-weather-8zo), at which point the Now/Hourly/Daily toggle may retire.
- Judge by whether the toggle starts feeling like a second, competing
  customization system.

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
  and does not leak drag state elsewhere.
- Judge by whether "add a module size" or "change the wiggle" is a one-file
  change a newcomer can find.

### 4a. What the tests can and cannot reach

Worth stating plainly, because the gaps are where the shipped bugs lived:

- **JVM** covers the geometry (`GridPackingTest`, `ViewConfigTest`) and the
  persistence path including overlapping edits (`HomeViewModelTest`).
- **Instrumented** (`ModuleGridTest`) covers what only a device measures —
  spans in real dp, gaps left empty — and the accessibility actions.
- **Maestro** (`06-arrange.yaml`) covers the real touch path: long-press entry,
  tap-to-cycle, exit, and persistence across a restart.
- **Nothing automated covers the drag reorder.** Maestro 2.5.0 has no
  hold-then-move gesture, and a slow coordinate swipe never satisfies the
  long-press (probed on-device). Verify it by hand with `adb shell input
  draganddrop`; just-my-weather-csa proposes the UX change that would close
  this.

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
| Resize | Tap a wiggling tile to cycle its span | Launcher-style drag handles. Revisit if cycling feels like a slot machine once spans grow beyond three. |
| Row heights | Intrinsic — a row is as tall as its tallest tile | Fixed square cells (true "4 square wide"). Revisit when forecast modules arrive and want 2-row heights. |
