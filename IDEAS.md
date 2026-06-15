# ActionDraw — Ideas

A scratchpad for filters and features. The goal of the app is to improve drawing skills through
timed reference practice.

## Filters — implemented
- **Black & white** (full desaturation)
- **Blur**
- **Upside down** (180° rotation)

## Filters — backlog
- **Horizontal mirror flip** — classic trick to spot proportion/symmetry errors.
- **Sepia / partial desaturation** — reduce color distraction without going fully grey.
- **Low-contrast "squint" mode** — flatten values to study big light/shadow shapes.
- **Edge / outline mode** (Sobel) — see contours only, for line study.
- **Posterize** — collapse to a few value bands, training value grouping.
- **Silhouette / threshold** — pure black shape, for gesture and negative space.
- **Pixelate / low-res** — force big-shape thinking, ignore detail.
- **Color-temperature shift** — practice drawing under warm/cool light.

## Claude's 3 best ideas
1. **Gesture-ramp sessions** — predefined life-drawing structures (e.g. 10×30s → 5×2min → 2×5min)
   that auto-advance through durations. The single most-requested feature for serious gesture
   practice; it turns the app from a plain timer into a structured warm-up tool.
2. **Proportion overlays** — toggleable rule-of-thirds grid and a center cross drawn over the image
   to train placement and proportion. Pairs naturally with the mirror-flip filter.
3. **Session log & "redo" flags** — building on the per-folder seen-tracking that is now core:
   record images drawn and total time per session, and let you flag an image to "redo" so it is
   resurfaced first next time (stored in a separate `.actiondraw_redo.txt`).

## Other notes
- Keyboard shortcuts: Space = play/pause, ←/→ = prev/next, F = fullscreen, Esc = exit fullscreen
  (or stop when windowed). Fullscreen hides all controls — image only, time in the corner.
- Seen-tracking file: `.actiondraw_seen.txt`, written inside the selected folder.
