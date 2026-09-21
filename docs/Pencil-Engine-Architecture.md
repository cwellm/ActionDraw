# The pencil engine — architecture

*2026-09-21. The drawing engine for Live Sketch ([LiveSketch-Exploration.md](LiveSketch-Exploration.md),
**M6**), as built in its first cut: a library of its own, and the Durchstich that puts an XPPen's
pressure on a page. Findings go to [LEARNINGS.md](../LEARNINGS.md); this is the shape.*

## 1. Two modules, one seam

```
:sketch-engine  (Kotlin/JVM over skiko, no Compose, no window)
    InputSample ─► StrokeBuilder ─► StrokePoint ─► Rasterizer ─► SketchSurface (Skia)
                    OneEuroFilter     PencilModel
                    speed             Brush(Lead, colour, size)

:app  (ActionDraw)
    PenSource ──► PenSample ──► SketchState ──► InputSample ──► engine
    (Windows Ink via JNA)        (EDT)                            │
    mouse (Compose)  ────────────┘                               ▼
                                                    SketchScreen shows SketchSurface.snapshot()
```

The engine knows nothing about pens, windows, threads or Compose. It takes an `InputSample` —
a position on the page in pixels, pressure 0..1, tilt, a timestamp — and produces pixels on a
Skia surface. That is the whole contract, and it is why the engine can be tested headless (draw
a stroke, read back pixels), published on its own, and replaced without touching the app. The
app owns everything either side of it: where samples come from, and where the surface is shown.

`:sketch-engine` depends only on `org.jetbrains.skiko:skiko-awt` at the version Compose ships
(0.8.18), so there is one Skia in the process, not two. Its tests pull the native skiko runtime
for the building machine.

## 2. The engine's parts (`de.creaflect.sketch`)

| Part | Does | Tunables |
|---|---|---|
| `InputSample` | one reading: `x, y, pressure, tiltX, tiltY, timeNanos, source` | — |
| `OneEuroFilter` | adaptive low-pass on one value; still hand steady, fast hand faithful | `minCutoff`, `beta` |
| `Lead` · `PencilModel` · `Pencils` | H / HB / 4B as three parameter sets over one `(pressure, speed) → (width, alpha)` model — LEARNINGS L2's numbers | per lead: `minWidth, maxWidth, gamma, alphaFloor, alphaCeiling, speedK, vRef` |
| `Brush` | a lead, a colour (ARGB), a size in page pixels | — |
| `StrokeBuilder` | samples in, `StrokePoint`s out one at a time: filtered position, lighter-filtered pressure, speed from timestamps smoothed over a short window, then the lead's width and alpha | `smoothing` on/off |
| `Stroke` | a finished stroke: its brush and points | — |
| `Rasterizer` | puts points on a `Canvas`: today round-capped segments at the mean width and alpha of each pair (the path-based pencil of exploration step 2); the stamp rasteriser with grain and paper tooth replaces it in the pencil study, same interface | — |
| `SketchSurface` | the page: a raster surface, `draw(stroke)` / `drawSegment(brush, a, b)` live, `snapshot()` for the UI, `pixel()` / `darkness()` for tests | size, background |

A stroke is drawn **live, segment by segment**, as samples arrive — the builder hands back each
new point and the surface draws the segment from the previous one — and a test pins that this
gives the same pixels as drawing the finished stroke in one go. Undo, the sketch document and
snapshots (exploration §6) come with the pencil study; nothing here forecloses them, since a
`Stroke` is already the replayable unit.

## 3. Input: the pen (`de.creaflect.actiondraw.sketch.input`)

Compose Desktop delivers no pen pressure (LEARNINGS L1), so the app hooks the native window:

- `PenSource` — `start(onSample)` / `stop()`; a `PenSample` carries what the OS knows: window
  pixels, pressure 0..1, tilt and rotation in degrees, contact, barrel and eraser, the pointer
  kind, a timestamp. `PenSources.forWindow(window)` picks the route for the platform, or none.
- `WindowsPointerSource` — Windows Ink through JNA: `SetWindowLongPtr(GWLP_WNDPROC)` subclasses
  the Compose window's procedure; on `WM_POINTERDOWN/UPDATE/UP` it asks `GetPointerType`, and for
  a pen `GetPointerPenInfo` (pressure out of 1024, tilt, rotation, flags), converts the screen
  position with `ScreenToClient`, and hands the sample on. **The original procedure is called
  for every message afterwards**, so Windows still promotes the pen to mouse messages and AWT,
  Compose and the rest of the app keep seeing the pen exactly as before. The pen data runs
  alongside, not instead. Pure Java: no compiler, no build step, nothing to package.
- Threads: the window procedure runs on AWT's toolkit thread. `SketchState.attach` wraps the
  callback in `EventQueue.invokeLater`, so the page is only ever drawn on the event dispatch
  thread. A test calls `onPen` directly.
- HiDPI: Windows gives physical pixels; Compose's own pixel space on the desktop is physical
  too, so `ScreenToClient` is the one conversion. The page subtracts its own origin in the
  window (`onGloballyPositioned`). Whether this holds on a scaled display is one of the things
  the probe measures.

WinTab is the fallback if `WM_POINTER` does not arrive through the AWT window; Linux (XInput2)
and macOS have no source yet and get the mouse at pressure 1.

## 4. The probe screen (`SketchScreen`)

Live Sketch's first screen is the pressure probe, the Durchstich: **Live Sketch** on the menu,
a header with the three leads and the size, readouts of the last sample (pressure, tilt,
rotation, contact, pointer kind, buttons, position, samples per second, count), a **Record
samples** switch that appends every sample to `~/.actiondraw/pen-samples.csv`, and a page that
fills the rest and draws through the engine with whatever pressure arrives. A mouse draws at
pressure 1; while a pen is in contact the promoted mouse events are ignored, or every stroke
would be drawn twice. The pen hook is attached when the screen is entered and released when
it is left; `Esc` and *Back* leave.

What the probe answers, into LEARNINGS L5: whether `WM_POINTER` reaches the window at all under
Compose's render loop; the sample rate the XPPen delivers; whether coordinates line up with
the page at 125 % scaling; latency by eye; and what the raw pressure curve looks like, from the
CSV.

## 5. What comes next, in this order

1. **The probe's numbers** (LEARNINGS L5) — the pen in hand decides whether Windows Ink is the
   route or WinTab is needed.
2. **The pencil study** — the stamp rasteriser (grain, paper tooth, the three leads as they
   really look), a debug panel with every tunable live, resampling to even spacing with
   Catmull-Rom between samples. The interface above does not change.
3. **The sketch document** — `.sketch.json`, undo by replay from snapshots, sizes (A4/A5/A3,
   150/300 dpi, or W×H), a tiled or bounded view for large pages.
4. **The screen** — toolbar, colour picker, keys, zoom, save; then **into the loop**: to a
   board, to a concept, from a session with the reference in the float strip.
