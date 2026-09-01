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
**Posterize** (few value bands), **Pixelate** (coarse blocks), **Warm** / **Cool** (white-balance
shift), **Edge** (Sobel outline), **Silhouette** (threshold).
Independent toggles: **Blur**, **Mirror** (horizontal flip), **Upside down** (180°).
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
- **Continuous colour-temperature** — a warm↔cool slider instead of the two fixed presets.

## Best ideas
1. ~~**Gesture-ramp sessions** — predefined life-drawing structures that auto-advance through
   durations.~~ ✅ Implemented (Quick warm-up / Classic gesture / Long studies).
2. ~~**Proportion overlays** — toggleable grid to train placement and proportion.~~ ✅ Implemented —
   **Thirds**, **Phi** and **Diagonal** variants, plus a centre cross. `G` cycles them.
3. ~~**Session log & "redo" flags**~~ ✅ Implemented — per-session stats and a Summary screen, plus a
   per-image **Redo** flag (`R`) persisted in `.actiondraw_redo.txt`; flagged images resurface first
   next session (and the flag clears once redrawn).

## Session features (beyond filters)
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
- Keyboard: Space play/pause · ←/→ prev/next · 1–0 view mode · N notan · A auto-advance · B blur ·
  I invert · D defraction · M mirror · U upside-down · G cycle grid · R redo flag · F fullscreen ·
  Esc leave fullscreen / stop. (Board shortcuts: see the README.)
- Per-folder state files, written inside the selected image folder:
  `.actiondraw_seen.txt` (shown images) and `.actiondraw_redo.txt` (redo flags). Entries are paths
  relative to that folder — identical to the file name for flat folders, `sub/dir/pic.jpg` for
  board folders with subfolders.
- Thumbnails are cached under `~/.actiondraw/thumbs/` (keyed by path, size and mtime); deleting
  that folder only costs a re-render.
