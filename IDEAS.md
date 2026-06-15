# ActionDraw — Ideas

A scratchpad for filters and features. The goal of the app is to improve drawing skills through
timed reference practice.

## Filters — implemented
View modes (mutually exclusive): **None**, **Black & white** (full desaturation),
**Squint** (low contrast + reduced saturation), **Edge** (Sobel outline), **Silhouette** (threshold).
Independent toggles: **Blur**, **Upside down** (180°), **Grid** (proportion overlay).

## Filters — backlog
- **Horizontal mirror flip** — classic trick to spot proportion/symmetry errors.
- **Sepia / partial desaturation** — reduce color distraction without going fully grey.
- **Posterize** — collapse to a few value bands, training value grouping.
- **Pixelate / low-res** — force big-shape thinking, ignore detail.
- **Color-temperature shift** — practice drawing under warm/cool light.

## Claude's 3 best ideas
1. **Gesture-ramp sessions** — predefined life-drawing structures (e.g. 10×30s → 5×2min → 2×5min)
   that auto-advance through durations. The single most-requested feature for serious gesture
   practice; it turns the app from a plain timer into a structured warm-up tool.
2. ~~**Proportion overlays** — toggleable rule-of-thirds grid and a center cross drawn over the
   image to train placement and proportion.~~ ✅ Implemented (the **Grid** toggle). Future: add a
   diagonal/Φ ("phi grid") variant and pair it with a mirror-flip filter.
3. **Session log & "redo" flags** — building on the per-folder seen-tracking that is now core:
   record images drawn and total time per session, and let you flag an image to "redo" so it is
   resurfaced first next time (stored in a separate `.actiondraw_redo.txt`).

## Other notes
- Keyboard shortcuts: Space = play/pause, ←/→ = prev/next, F = fullscreen, Esc = exit fullscreen
  (or stop when windowed). Fullscreen hides all controls — image only, time in the corner.
- Seen-tracking file: `.actiondraw_seen.txt`, written inside the selected folder.
