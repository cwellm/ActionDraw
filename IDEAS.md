# ActionDraw — Ideas

A scratchpad for filters and features. The goal of the app is to improve drawing skills through
timed reference practice.

## Filters — implemented
View modes (mutually exclusive): **None**, **Black & white** (full desaturation),
**Squint** (low contrast + reduced saturation), **Sepia** (warm partial desaturation),
**Posterize** (few value bands), **Pixelate** (coarse blocks), **Warm** / **Cool** (white-balance
shift), **Edge** (Sobel outline), **Silhouette** (threshold).
Independent toggles: **Blur**, **Mirror** (horizontal flip), **Upside down** (180°).
Proportion overlay (**Grid**): **Thirds**, **Phi** (golden section), **Diagonal**, each with a centre cross.

## Filters — backlog
- **Adjustable params** — posterize band count, pixelate block size, silhouette threshold via a slider.
- **Continuous colour-temperature** — a warm↔cool slider instead of the two fixed presets.
- **Notan (2–3 value)** — collapse to two/three values for value-grouping study (posterize covers part of this).

## Best ideas
1. ~~**Gesture-ramp sessions** — predefined life-drawing structures that auto-advance through
   durations.~~ ✅ Implemented (Quick warm-up / Classic gesture / Long studies).
2. ~~**Proportion overlays** — toggleable grid to train placement and proportion.~~ ✅ Implemented —
   **Thirds**, **Phi** and **Diagonal** variants, plus a centre cross. `G` cycles them.
3. ~~**Session log & "redo" flags**~~ ✅ Implemented — per-session stats and a Summary screen, plus a
   per-image **Redo** flag (`R`) persisted in `.actiondraw_redo.txt`; flagged images resurface first
   next session (and the flag clears once redrawn).

## Other notes
- Keyboard: Space play/pause · ←/→ prev/next · 1–0 view mode · B blur · M mirror · U upside-down ·
  G cycle grid · R redo flag · F fullscreen · Esc leave fullscreen / stop.
- Per-folder state files, written inside the selected image folder:
  `.actiondraw_seen.txt` (shown images) and `.actiondraw_redo.txt` (redo flags).
