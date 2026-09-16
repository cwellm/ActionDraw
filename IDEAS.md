# ActionDraw — Ideas

A scratchpad for filters and features. The goal of the app is to improve drawing skills through
timed reference practice.

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

- 🔄 **Memory drawing** — the reference disappears mid-pose. *Implemented; see above.*
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
