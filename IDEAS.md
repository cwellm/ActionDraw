# ActionDraw — Ideas

A scratchpad for filters and features. The app began as timed reference practice; since the
design recap its goal is the whole way from an idea to a finished piece ([CONCEPT.md](CONCEPT.md)).
Open ideas for every room are at the end, under *Open ideas from the design recap*.

> Ideas for the **Idea Board** (the collecting half of the app) live elsewhere: exploration in
> [ACTIONDRAW_EXTENSION.md](ACTIONDRAW_EXTENSION.md), the built shape in
> [docs/IdeaBoard-Shaping.md](docs/IdeaBoard-Shaping.md), and what's next in
> [ROADMAP.md](ROADMAP.md). This file stays about filters and the drawing session.

## Filters — implemented
View modes (mutually exclusive): **None**, **Black & white** (full desaturation),
**Squint** (low contrast + reduced saturation), **Sepia** (warm partial desaturation),
**Posterize** (few value bands), **Pixelate** (coarse blocks), **Edge** (Sobel outline),
**Silhouette** (threshold), **Notan** (2/3 value bands).
Independent adjustments, stacking on any mode: **colour temperature** (cool↔warm white balance),
**Blur**, **Mirror** (horizontal flip), **Upside down** (180°), **Invert**, **Defraction**.
Proportion overlay (**Grid**): **Thirds**, **Phi** (golden section), **Diagonal**, each with a centre cross.

## Filters — backlog
- ~~**Adjustable params** — posterize band count, pixelate block size, silhouette threshold via a
  slider.~~ ✅ Implemented — sliders appear under the view row (now also blur radius, notan and
  defraction parameters).
- ~~**Notan (2–3 value)** — collapse to two/three values for value-grouping study.~~ ✅ Implemented —
  view mode (`N`) with a 2/3-value switch and threshold slider.
- ✅ **Defraction** (new) — cubist shard mosaic (jittered-Voronoi, per-shard offset+rotation);
  independent toggle (`D`), re-rolled randomly on every switch-on; shard size + strength sliders.
- ✅ **Invert** (new) — colour inversion of the final image, independent of all other effects (`I`).
- ~~**Continuous colour-temperature** — a warm↔cool slider instead of the two fixed presets.~~
  ✅ Implemented (`,` / `.` / `0`, stacks on any view mode).

## Best ideas
1. ~~**Gesture-ramp sessions** — predefined life-drawing structures that auto-advance through
   durations.~~ ✅ Implemented (Quick warm-up / Classic gesture / Long studies).
2. ~~**Proportion overlays** — toggleable grid to train placement and proportion.~~ ✅ Implemented —
   **Thirds**, **Phi** and **Diagonal** variants, plus a centre cross. `G` cycles them.
3. ~~**Session log & "redo" flags**~~ ✅ Implemented — per-session stats and a Summary screen, plus a
   per-image **Redo** flag (`R`) persisted in `.actiondraw_redo.txt`; flagged images resurface first
   next session (and the flag clears once redrawn).

## Session features (beyond filters)
- ✅ **Memory drawing** — a ramp leg carries a study time (`RampStep.studySeconds`); the reference
  hides for the rest of the pose and returns at the end to compare against. `H` hides or peeks.
  The exercise that matters when the subject does not exist to be photographed.
- ✅ **Picture picker** — thumbnail grid (menu → "Choose pictures…"): click to include/exclude,
  All/None; sessions draw only from the selection, and a fresh cycle resets seen-state only for
  the selected pictures.
- ✅ **Manual mode** — "Auto-advance" toggle (menu + session + `A`): off = the countdown is
  informational only, runs into "+overtime", and switching stays manual.

- ✅ **Remembers the last folder** across restarts (`~/.actiondraw/settings.properties`); the folder
  dialog also opens there. A folder that has moved/been deleted is silently forgotten.
- ✅ **Draw from an Idea Board** — a board selection or group starts a normal session (in its own
  window) with exactly those pictures; every filter and ramp applies as usual.

## Other notes
- Keyboard: Space play/pause · ←/→ prev/next · 1–9 view mode · N notan · ,/. light · H hide/peek · A auto-advance · B blur ·
  I invert · D defraction · M mirror · U upside-down · G cycle grid · R redo flag · F fullscreen ·
  Esc leave fullscreen / stop. (Board shortcuts: see the README.)
- Per-folder state files, written inside the selected image folder:
  `.actiondraw_seen.txt` (shown images) and `.actiondraw_redo.txt` (redo flags). Entries are paths
  relative to that folder — identical to the file name for flat folders, `sub/dir/pic.jpg` for
  board folders with subfolders.
- Thumbnails are cached under `~/.actiondraw/thumbs/` (keyed by path, size and mtime); deleting
  that folder only costs a re-render.

## Where next (2026-09-16)

Both halves of the loop are built — collecting material, and practising from it — but the app has
never seen anything that was actually *drawn*. All state is about the reference: seen, redo, tags,
captions, badges. The output side is where the remaining value is.

- ✅ **Memory drawing** — the reference disappears mid-pose. *Built; see the list above.*
- ⬜ **Your drawings come back in** — an item gains `attempts: [{path, date, seconds}]` in
  `_drawings/`. Unlocks **overlay compare** (your attempt at 50% over the reference, which is how
  proportion errors are actually found), mirroring your own drawing, and a progression view of one
  subject over months. The biggest structural gap; a real data-model change.
- ⬜ **Staged studies** — the filter changes during a pose: Notan for the value masses, Edge for
  the contour, then full. Shares the ramp-phase seam with memory drawing.
- ⬜ **A watched drop folder per board** — Krita saves a PNG into it and the card appears. Feeds
  the attempts idea automatically.
- ⬜ **Step through an animation** frame by frame (GIF/WebP, which ImageIO already decodes) —
  bird-flight and gait loops are the best wing and pose reference there is.
- ⬜ **Export a board** as a portable bundle or a captioned PDF — the Drachenbuch is a book, and
  nothing currently hands you something printable or backup-able.

Considered and deliberately not taken: rating each session to build a "struggled with" smart group.
It would work, but it risks making practice feel like homework, and this app's character is that it
gets out of the way. If ever built: one optional question, and never a streak.

## Open ideas from the design recap (2026-10-05)

None of these is planned. They are ideas that came up while shaping the atelier ([CONCEPT.md](CONCEPT.md));
the ones worth building will move into [ROADMAP.md](ROADMAP.md) when they are chosen. Each idea
should make the creative process better, not just add a feature.

### The table itself
- ⬜ **Wet until saved.** A room's dab in the palette stays glossy while that room holds unsaved
  work, such as strokes on the page or an uncut collage, and dries matte once it is saved. The
  state is visible without a dialog, the way a real palette shows it.
- ⬜ **The table remembers.** The palette's table carries the last few sketches as graphite
  ghosts at 3 %, like smudges on a real drawing table: your own recent work, never stats.
- ⬜ **The lamp follows the evening.** The chrome's light warms a little after dark. The picture
  and the page are never touched, because colour judgement has to stay honest.
- ⬜ **Quiet foley, off by default.** A pencil's scratch keyed to stroke speed in Sketch, paper
  sliding when a card lands, tape tearing in Collage. If it is done, it must be very quiet, and it
  is the first thing to cut if it distracts.
- ⬜ **The dial turns the palette.** On the home screen the XPPen's dial rotates the selection
  round the wells; in Sketch it moves through the leads; in a flicker it sets the interval.

### Practice
- ⬜ **Mirror flicker / value flicker.** Flicker between a picture and its mirror (asymmetries in
  your understanding jump out), or between a picture and its notan (masses against detail).
- ⬜ **Blind contour.** The reference shows, and Sketch hides your strokes until the pose ends.
  This is the classic exercise for trusting the eye over the hand.
- ⬜ **Negative space lens.** The subject is filled flat and only the shapes *between* things
  remain to draw. It could be built on Silhouette with an inverted fill and the outline kept.
- ⬜ **Lens roulette.** Each pose draws a random lens from a set you choose, so a familiar folder
  stays fresh.
- ⬜ **Ink only.** A Sketch constraint for a session: fineliner, no rubber, no undo. It teaches
  commitment to a line.
- ⬜ **Warm up on what you are about to draw.** Before opening a concept's board, a two-minute
  gesture round on that concept's own pictures. (Close to F10.8.)
- ⬜ **Spot the change.** Two near-identical frames (from a GIF or two photos of the same pose),
  flickered slowly, to train observation of small differences.

### Boards
- ⬜ **Strings between pins.** Draw a thread from one pinned card to another, with a short
  lettered label, so relations become visible (this wing goes with that cliff), like a real
  investigation board.
- ⬜ **Lightbox.** Hold a key and every card goes translucent over a lit table, so silhouettes and
  compositions can be compared by stacking.
- ⬜ **Pigments from a picture, onto the page.** The palette-extraction card's swatches become
  real dabs that can be dragged into Sketch's colour well or onto a collage piece as a tint.
- ⬜ **A shelf, not a list.** Boards as spines on a shelf, a little worn where they are opened
  often. You find the active ones by their wear, without a "recent" list.

### Sketch
- ⬜ **Dividers.** A pair of virtual dividers: measure on the underlay or the reference, step the
  same span on the page. This is the traditional sight-size tool, and proportion is what most
  sketches get wrong.
- ⬜ **Plumb line.** Hold a key and a weighted thread hangs from the pointer across both the
  reference and the page, for checking verticals and alignments.
- ⬜ **Sight-size layout.** The reference and the page side by side at the same scale, at the
  same eye height, as in an atelier.
- ⬜ **Kneaded rubber.** A rubber that dabs rather than wipes and lifts highlights out of a tone.
  It is the soft eraser's natural sibling on the same model.

### Collage
- ⬜ **Thumbnail formats.** Three or four small frames (portrait, landscape, square, panorama)
  beside the mat. Drag the composition into each to see which format the scene wants.
- ⬜ **Values on demand.** Hold `V` on the mat to see the collage in notan for as long as the key
  is held. This is the quickest test of whether a scene reads.
- ⬜ **Horizon and vanishing point.** A horizon line and a vanishing point on the mat; a piece
  dragged deeper shrinks to match, so scale errors show before drawing.
- ⬜ **Deckle edges.** A torn edge with the paper's fibres showing light where it tore, not just
  a jagged outline. It is the detail that makes a cut picture read as paper.

### Across the rooms
- ⬜ **The tray.** A shallow tray at the table's edge, shared by every room: drop anything in,
  walk to another room, take it out. A clipboard you can see.
- ⬜ **A sketchbook of the days.** The day's sketches, collages and pinned poses gathered as a
  spread in a sketchbook you can leaf through. It shows only the work, never scores, which keeps
  it in line with the "never a streak" decision above.
- ⬜ **Studio hours.** One switch that makes every room full-screen, mutes the app's own
  notifications and starts a gentle ensō for the whole sitting.
