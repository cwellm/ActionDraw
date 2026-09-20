# Live Sketch — exploration and spec

*2026-09-20. A third tool beside Draw and Board: a surface to actually sketch on, with an XPPen.
Tracked as **M6** in [ROADMAP.md](../ROADMAP.md). What was learned while finding out how to
make a pencil feel like a pencil goes in [LEARNINGS.md](../LEARNINGS.md); this document is the
shape of the thing.*

## 1. What it is, and is not

A page of a chosen size, a pencil, a colour, an XPPen. Sketch, undo, save — and hand the result
to a board or a concept. Not a painting program: **no layers, no selection tools, no filters, no
brushes beyond pencils.** Krita exists for that. Live Sketch is for the thing you do on paper
while the reference is up: the thumbnail, the gesture, the note-to-self in line.

The one thing it must get right is the pencil. A pencil that does not respond to pressure and
speed is a felt-tip, and a felt-tip is not worth building a tool around.

## 2. The engine is a library

The drawing engine is a separate Gradle module, `:sketch-engine`, with **no Compose
dependency** — pure Kotlin/JVM over Skia (skiko). The app's Live Sketch screen feeds it input
samples and shows the surface it renders; nothing else talks to it. Because it is a library it
can be tested headless (draw a stroke, read back pixels), published on its own later, and
replaced without touching the app.

Boundary:

```
InputSample(x, y, pressure 0..1, tiltX?, tiltY?, timeNanos, source: PEN|MOUSE)
        │  Stroke input filter (§4)
        ▼
Brush(kind: HARD|MEDIUM|SOFT, colour, size)  →  Stroke(points with width+alpha per point)
        │  Rasteriser (§5)
        ▼
Layer surface (Skia)  ──►  Sketch document (§6)  ──►  PNG · .sketch.json
```

The app owns: the window, size dialog, colour picker, tool bar, undo/redo keys, saving to a
board/concept. The engine owns: everything between a pen sample and a pixel.

## 3. Input — the finding that shapes everything

Compose Desktop **does not deliver pen pressure**. Its AWT bridge builds every pointer event
with `type = PointerType.Mouse` and never reads a pressure (`ComposeSceneMediator.desktop.kt`,
checked against a version newer than the one in use; ours is no better). AWT itself has no
pressure in `MouseEvent`. So the engine's `InputSample.pressure` has to come from somewhere
else, and that somewhere is native:

- **Windows Ink / `WM_POINTER`** — with Windows Ink enabled in the XPPen driver (its default),
  the window receives `WM_POINTER*` messages, and `GetPointerPenInfo` returns pressure, tilt and
  rotation. Reachable from Java through **JNA** (pure Java, no compiler): subclass the Compose
  window's `WndProc`, read pen info on each pointer message, and publish samples to the engine.
  Preferred route: first probe.
- **WinTab** — the older tablet API, which XPPen also implements. More setup, works without
  Windows Ink. Fallback if `WM_POINTER` is unavailable through the AWT window.
- Fallback for a mouse: pressure = 1, and speed does the work of expression. Live Sketch must
  stay usable without a tablet; it just stops feeling like a pencil.

The probe (§8) settles which route before any engine work starts. Everything above the input
layer is independent of it, which is the point of the boundary in §2.

## 4. Stroke input filter

Raw pen samples arrive at 130–250 Hz with jitter. Before they become a stroke:

- **One-Euro filter** on position (adaptive low-pass: still hand steady, fast hand faithful).
  Two tunables, both exposed in a debug panel during exploration, then fixed.
- **Pressure smoothing**, lighter than position — pressure edges are expression, not noise.
- **Resampling to even spacing** along the path (e.g. every 1.5 px at current zoom), so the
  rasteriser sees points at a known density. Between samples: Catmull-Rom, so a fast stroke
  through three points still curves.
- **Velocity** per point, from the timestamps, smoothed over a short window.

## 5. Pencils

Three, and they differ in more than width. The model per point:

| | Hard (H) | Medium (HB) | Soft (4B) |
|---|---|---|---|
| Width vs. pressure | narrow range, mostly thin | full range | wide, opens up quickly |
| Alpha vs. pressure | low ceiling, grainy | mid | reaches solid dark |
| Speed → alpha | little effect | lighter when fast | much lighter when fast |
| Grain | fine, sparse | medium | coarse, fills at pressure |
| Edge | crisp | slight | soft |

Rendering is **stamp-based**: a small grain texture dabbed along the resampled path at
sub-width spacing, each dab scaled by width and multiplied by alpha, composited with a paper
tooth so that light strokes catch only the tops of the grain. Stamps give the graphite look
that a variable-width vector path never does; the cost is fine at sketching speeds (a few
thousand dabs per stroke). A path-based mode stays available for the lightest hard pencil,
where the stamp look adds nothing.

Colour is free: the stamp is a mask, the colour comes from the brush. A red pencil is the
medium pencil with red.

Erasing: the same stamp with `DstOut`. One eraser, medium.

## 6. The sketch document

`.sketch.json` beside the exported PNG: page size and DPI, background, and the **stroke list**
— each stroke its brush, colour, and points with pressure and time. The PNG is the picture;
the JSON is the undo history and the way to re-render at another size later. Undo is popping
the stroke list and re-rasterising from the last cached snapshot (a snapshot every N strokes
keeps that quick).

Sizes on offer: A4 / A5 / A3 at 150 or 300 dpi, portrait or landscape; or width × height in
pixels. A4 at 300 dpi is 2480 × 3508 — the surface must be tiled or the zoom bounded so the
full-resolution raster is never drawn at 1:1 into a 4K window per frame. Render the visible
region only.

## 7. The screen

- Menu: **Live Sketch** beside Draw and Board. New sketch (size dialog) / open recent.
- Toolbar, thin, top: pencil H · HB · 4B · eraser · size · colour (swatch opens a picker with
  a hue ring, value/saturation square, recent colours, and the board's palettes when opened
  from a board) · undo · redo · save · **to board…** / **to concept…**.
- The whole rest is page. Zoom with the wheel (the dial), pan with space+drag or middle drag;
  `Ctrl+0` fits.
- Keys: `1` `2` `3` pencils, `E` eraser, `[` `]` size, `Ctrl+Z`/`Ctrl+Y`, `Ctrl+S`.
- Opened **from a session** (a *Sketch* button beside *Pin*): the reference stays in the
  floating strip, the sketch gets the screen — the paper-beside-the-monitor setup, digitised.

## 8. Exploration plan, in order

1. **Pressure probe** — a 50-line window with a JNA `WM_POINTER` hook that prints pressure
   and tilt from the XPPen. Answers whether the preferred route works at all. Written up in
   LEARNINGS.md whatever the answer.
2. **Engine skeleton** — `:sketch-engine` with the input filter and a path-based pencil;
   headless test draws a stroke and checks pixels. No Compose anywhere in it.
3. **The pencil study** — stamp rendering, the three pencils, paper tooth; a debug panel with
   every tunable live. This is where the time goes, and where LEARNINGS.md fills up.
4. **The screen** — size dialog, toolbar, colour picker, undo, save.
5. **Into the loop** — save to a board, save to a concept, open from a session.

Steps 2–4 do not wait on step 1; only the pressure source does.

## 9. Risks

- **No pressure reaches Java at all** through the Compose window — then a separate native
  input window layered over the page, or WinTab, or as a last resort a tiny helper process
  streaming samples. Ugly options exist; the exploration decides which is least ugly.
- **Latency.** Pen-to-pixel over AWT → Compose → Skia. Target under 20 ms; measure it in
  step 3. If Compose's frame pacing is the bottleneck, the engine draws straight to a skiko
  layer and Compose only frames it.
- **HiDPI.** Pen coordinates arrive in physical pixels; Compose works in logical ones. One
  place converts, and the DPI-awareness lesson from the screenshot episode applies.

## 10. Open questions

- Module in this repository (`:sketch-engine`, published later if wanted) or a separate
  repository from day one? Assumed: **module here** — one build, one test run, and splitting
  out is cheap once it is stable.
- Pencil only, as specified — confirm that an eraser is wanted (assumed yes) and that a pen
  (ink, no pressure width) is not.
- Should a sketch saved to a board also keep its `.sketch.json` beside the PNG (so it can be
  reopened and continued)? Assumed yes.
