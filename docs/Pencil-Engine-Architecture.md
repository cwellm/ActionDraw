# The pencil engine — architecture

*2026-09-21, completed 2026-09-22. The drawing engine for Live Sketch
([LiveSketch-Exploration.md](LiveSketch-Exploration.md), **M6**): a library of its own, the
Durchstich that puts an XPPen's pressure on a page, and the screen and the saving built on both.
Findings go to [LEARNINGS.md](../LEARNINGS.md); this is the shape.*

## 1. Two modules, one seam

```
:sketch-engine  (Kotlin/JVM over skiko, no Compose, no window)
    InputSample ─► StrokeBuilder ─► StrokePoint ─► Resampler ─► StampRenderer ─► SketchSurface
                    OneEuroFilter     PencilModel      even dabs     paper grain      256-px tiles
                    speed             Brush(Lead, colour, size)
    SketchSession: the strokes, undo/redo, cancel · SketchDocument (.sketch.json) · PNG export

:app  (ActionDraw)
    PenSource ──► PenSample ──► SketchState ──► InputSample ──► SketchSession
    (Windows Ink via JNA)        (EDT)                            │
    mouse (Compose)  ────────────┘                               ▼
                                         SketchScreen draws the visible tiles onto Skia
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
| `Lead` · `PencilModel` · `Pencils` | H / HB / 4B as three parameter sets over one `(pressure, speed, tilt) → (width, alpha)` model — LEARNINGS L2's numbers; the side of the lead stretches the mark along the tilt (`stretch(tilt)`) and lightens it; serializable, so a tuned lead can be kept as a preset | per lead: `minWidth, maxWidth, gamma, alphaFloor, alphaCeiling, speedK, vRef, edge, tiltWidth, tiltAlpha` |
| `Brush` | a lead, a colour (ARGB), a size in page pixels | — |
| `StrokeBuilder` | samples in, `StrokePoint`s out one at a time: filtered position, lighter-filtered pressure, the tilt through a light filter of its own as a vector in fractions of a full tilt (60°), speed from timestamps smoothed over a short window, the stroke's heading, then the lead's width and alpha | `smoothing` on/off, `FULL_TILT` |
| `Stroke` | a finished stroke: its brush and points | — |
| `Resampler` | points in one at a time, dabs out at even spacing along a Catmull-Rom spline through the neighbours; a segment is placed the moment it exists (the last point doubled as the trailing control), so a stroke drawn live and the same stroke replayed produce the same dabs | `spacing(point)` |
| `Paper` · `PaperGrain` | three papers — smooth, medium, rough — each a tileable value-noise texture in page space (floor, contrast, cell sizes), the paper's tooth, deterministic for a seed; `shade()` draws it as a faint shadow over the page for the screen | `floor`, `contrast`, `octaves`, `SHADE` |
| `StampRenderer` | one dab: a disc of the point's width — an ellipse stretched along the tilt when the pen leans — its edge softened per lead, filled by the grain screened with the pressure (`g′ = 1 − (1 − g)(1 − p)`, less of it on a leaning pen) and modulated by the colour at a per-dab alpha calibrated so three overlapping dabs reach the point's darkness; dabs are spaced by their extent along the stroke's heading, so a band on its side is as dark whichever way it is drawn; the eraser is the same dab with `DST_OUT` and the rubber's model | `DABS_PER_POINT`, `TILT_GRAIN` |
| `Rasterizer` | the path-based pencil of the exploration's step 2 — kept as the lightest hard pencil and as the reference | — |
| `SketchSurface` | the page: a *transparent* strokes layer over a paper colour (so the eraser can take graphite away), kept as 256-px tiles; `paint(bounds) { canvas }` draws into every tile under a rectangle with the canvas in page coordinates; `tileImage(i)` is the same picture until that tile is drawn on; `snapshot()`/`restore()` for undo; `compose()` flattened for export; `darkness()` for tests | size, paper, tile size |
| `SketchDocument` | `.sketch.json`: page size, dpi, paper colour and paper (the tooth, by name), and every stroke as brush plus raw samples with pressure, tilt and time; `PageSize` for A5/A4/A3 at a dpi or pixels | — |
| `SketchSession` | a sketch being made: begin/add/end a stroke (dabs on the page at once), `cancel()` (take a stroke back), undo and redo (a snapshot every 12 strokes, the last 3 kept, plus a replay of what came after), `fromDocument`, `document()`, `exportPng()`, `dirty` | `SNAPSHOT_EVERY`, `KEEP_SNAPSHOTS` |

Every stroke goes through the same pipeline whether drawn live or replayed — samples into the
builder, points into the resampler, dabs onto the page — which is what makes a document loaded
from disk render pixel for pixel as it was drawn, and undo a matter of restoring a snapshot and
replaying the strokes after it. The tests pin exactly that: live equals whole, undo restores
exactly across a snapshot boundary and into the ragged edge tiles, JSON round-trips to the same
fingerprint. One consequence to know: the document keeps samples, not pixels, so a sketch
replayed under a changed lead model (the Tune panel) renders with the changed model.

**Why tiles.** The first screen handed the whole page to Compose as an `ImageBitmap` on every
pen sample — a full pixel copy each time, plus a copy-on-write of the surface because that
snapshot was still held: at A4 300 dpi that is 35 MB, several times per frame. With tiles the
screen draws each visible tile's picture straight onto Skia's canvas; a tile nobody drew on is
the same `Image` as last frame, so the GPU keeps its texture and a stroke re-uploads only the
few tiles it crosses. A tile's picture is released *before* the tile is drawn on, so the draw
does not copy it either. An undo snapshot holds a picture per tile, sharing pixels with every
tile the strokes after it leave alone — a snapshot costs only what changed.

## 3. Input: the pen (`de.creaflect.actiondraw.sketch.input`)

Compose Desktop delivers no pen pressure (LEARNINGS L1), so the app hooks the native window:

- `PenSource` — `start(onSample)` / `stop()`; a `PenSample` carries what the OS knows: window
  pixels, pressure 0..1, tilt and rotation in degrees, contact, barrel and eraser, the pointer
  kind, a timestamp. `PenSources.forWindow(window)` picks the route for the platform, or none.
- `WindowsPointerSource` — Windows Ink through JNA: `SetWindowLongPtr(GWLP_WNDPROC)` subclasses
  the frame's procedure **and every child window's** (`EnumChildWindows`): Windows sends a
  pointer message to the window under the pen, and Compose draws into skiko's `HardwareLayer`, a
  heavyweight `java.awt.Canvas` with a handle of its own — the frame alone saw nothing
  (LEARNINGS L6). On `WM_POINTERDOWN/UPDATE/UP` it asks `GetPointerType`, and for a pen
  `GetPointerPenInfo` (pressure out of 1024, tilt, rotation, flags), converts the screen position
  with `ScreenToClient` against the frame, and hands the sample on. **The original procedure is called
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
and macOS have no source yet and get the mouse at its one fixed pressure.

## 4. The screen (`de.creaflect.actiondraw.sketch`)

`SketchState` holds a `SketchSession`, the brush, the view over the page (zoom and pan, the
page's top-left in view pixels), the pen source with its readouts, and saving. `SketchScreen`
is a thin toolbar over a page: title and page size, leads, eraser, size, colour (a picker with a
saturation/value square, hue strip, hex and recents), undo/redo, zoom (click to fit), the **Pen**
panel (the probe's readouts and the sample recorder), the **Tune** panel (every tunable of the
current lead, live, through `Pencils.set`; the paper under the strokes; **Save as preset…**,
which keeps the lead as tuned under a name in the settings — a `LeadPreset`, a chip beside the
leads), the **Sketch ▾** menu (new, open, save as, to board,
to concept), Save, Back. `SketchDialogs` are mounted at app level like every other dialog.

Input: a pen sample arrives in window pixels, is offset by the view's origin, and mapped
through the view (`toPage`) into page pixels for the engine; the mouse the same, at one fixed middling pressure (`MOUSE_PRESSURE`).
Windows also turns the pen into mouse events, so mouse input is ignored while a pen is on the
page *or near it* — the pen sends samples while it hovers, and a mouse event within 300 ms of
one is the pen seen twice; should a promoted mouse press still begin a stroke before the pen's
contact arrives, the session cancels it and the pen's own stroke, with its pressure, is drawn.
The page reads pointer events itself: a press is already a mark (Compose's drag helpers wait
for a threshold, so a tap left nothing) and they only take the primary button (so the middle
button could not pan). `handleSketchShortcut` is the keys by key; `handleSketchChar` the keys
by the character they type — `[` `]` `+` `−` — because on a German keyboard `[` is AltGr+8,
which also reads as Ctrl; Space is read before the key-down gate so its release is seen.

Saving writes `<name>.png` (the flattened page) beside `<name>.sketch.json` (the document); the
sketch is that file from then on, and Ctrl+S keeps to it. **To board** and **to concept** write
into the board's or the concept's own folder first and then hand the picture over through
`SketchHost` — the app's seam, implemented in `Main.kt` on top of `BoardState.pinTo` and
`ConceptState.addToConcept` — so the picture is referenced in place and the document sits
beside it. That convention is the whole of the way back: `BoardState.sketchOf(item)` and
`ConceptState.sketchOf(item)` look for `name.sketch.json` beside a card's picture, and their
hosts' `openSketch(file)` opens it in Live Sketch. `AppState.sketchOrigin` remembers where Live
Sketch was opened from — the menu, a board, a concept, a session paused by its **Sketch**
button — and Back returns there. The sketch outlives the screen; closing the app with unsaved
strokes asks first.

## 5. What comes next

1. **The pen in hand** (LEARNINGS L5): the numbers — rate, pressure curve, coordinates at 125 % —
   and the leads tuned against them with the Tune panel; the numbers that survive written down,
   and WinTab only if Windows Ink does not deliver.
2. **Saving large pages off the event thread**: an A3 at 300 dpi takes a noticeable moment to
   encode as PNG; the tiles' pictures could be taken on the event thread and encoded beside it.
3. **Tilt, second pass**: the side of the lead is in (the stretch, the lightening, the skimmed
   tooth); its numbers want the pen in hand, and rotation is still unused.
4. **A sketch as its own kind of card** — today a sketched picture is a picture that happens to
   have its strokes beside it; a mark on the card saying so is the obvious next touch.
