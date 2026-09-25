# LEARNINGS — making a pencil feel like a pencil

*What was found out while working towards Live Sketch ([docs/LiveSketch-Exploration.md](docs/LiveSketch-Exploration.md)).
Findings, not plans: each entry says what was tried or read, what came of it, and what it
decides. Newest at the bottom.*

## L1. Compose Desktop has no pen pressure (2026-09-20, read, not yet measured)

Compose's `PointerInputChange` has a `pressure` field, which is a trap on the desktop: the
AWT-to-Compose bridge (`ComposeSceneMediator.desktop.kt`) builds every pointer event as
`PointerType.Mouse` and never sets it — checked in a version *newer* than the one in use
(1.11 beta against our 1.7.3), so this is not something an upgrade fixes. AWT's own
`MouseEvent` carries no pressure either.

**Decides:** pressure comes from native input, not from Compose. The engine takes an
`InputSample` with pressure from *whoever can supply it*; the Compose side only supplies
position for a mouse. Route to probe first: `WM_POINTER` + `GetPointerPenInfo` through JNA,
which needs no compiler and works with Windows Ink, the XPPen driver's default mode.

**Still to measure:** whether the messages reach the AWT window at all when Compose has
its own render loop on it; whether the coordinates come in physical pixels (expected: yes —
one conversion, in one place); the sample rate the XPPen actually delivers.

## L2. What "reacts like a natural pencil" means, in numbers (2026-09-20, from reading)

The behaviours that make graphite read as graphite, gathered before building so the pencil
study has something to aim at rather than tuning by feel from nothing:

- **Width follows pressure sub-linearly for hard leads, near-linearly for soft.** A 2H barely
  widens; a 6B opens fast. Model: `width = min + (max − min) · pressure^γ` with γ ≈ 1.6 (H),
  1.0 (HB), 0.7 (soft).
- **Darkness follows pressure more than width does**, and its ceiling is the lead: a hard
  pencil pressed hard gets shiny before it gets black. Alpha ceiling ≈ 0.55 (H), 0.8 (HB),
  0.97 (soft).
- **Speed lightens.** Fast strokes deposit less; the effect is strong for soft leads and weak
  for hard ones. `alpha *= 1 − k · clamp(speed/v_ref)` with k ≈ 0.15 (H) … 0.45 (soft).
- **Grain is the paper, not the pencil.** Light pressure marks only the tops of the tooth;
  heavy pressure fills the valleys. That is a *multiply* with a fixed noise texture in paper
  space (not stroke space — the grain must not move with the stroke), with pressure raising
  the floor: `deposit = alpha · (grain · (1 − pressure) + pressure)`.
- **Edges.** Hard leads have a crisp edge; soft leads a halo of loose graphite about 10–20 %
  of the width. A small blur on the stamp for soft, none for hard.
- **Direction does not matter** for a round lead — no tilt shading in the first build. Tilt
  is read from the pen anyway and kept in the sample for later (a chisel edge on the side of
  a soft lead is the obvious use).

**Decides:** the pencil is a per-point `(width, alpha)` function of `(pressure, speed)` with a
lead-specific curve, rendered as stamps multiplied against a paper-space grain. Three leads
are three parameter sets, not three code paths.

## L3. Stamps over paths (2026-09-20, from reading; to be confirmed in the study)

A variable-width vector path gives a clean, dead line — a marker. Dabbing a small grain
texture along the path at a spacing of ~⅓ of the current width gives the broken, textured
edge of graphite, and lets width and alpha vary per dab with no geometry tricks. Cost at a
sketching pace (a stroke of 2000 px at width 6 ≈ 1000 dabs of a 16-px stamp) is trivial for
Skia. Open: whether to dab straight into the layer surface or into a per-stroke scratch
surface composited on stroke end — the latter makes undo and "max alpha per stroke" (no
self-overlap darkening, which real pencils mostly do not show) easy. Leaning scratch surface.

## L4. Smoothing (2026-09-20, from reading)

The **One-Euro filter** (Casiez et al., 2012) — a low-pass whose cutoff rises with speed —
is the standard answer for pen jitter that does not lag a fast hand. Two parameters
(`mincutoff`, `beta`); start at 1.0 / 0.007 as the paper suggests and tune with the pen in
hand. Resample the filtered path to even spacing with Catmull-Rom between samples before
stamping, so dab density does not depend on how fast the pen moved.

## L5. The probe is built; the pen decides (2026-09-21, built, not yet measured)

The Durchstich is in: `:sketch-engine` as a library with the path-based pencil, and in the app a
`WindowsPointerSource` that subclasses the Compose window's procedure through JNA and reads
`GetPointerPenInfo` on every `WM_POINTER*` message — pressure out of 1024, tilt and rotation in
degrees, contact and button flags — and then calls the original procedure, so Windows still
promotes the pen to mouse messages and nothing else in the app changes. **Live Sketch** on the
menu shows the numbers live, draws through the engine, and records every sample to
`~/.actiondraw/pen-samples.csv`.

**What the pen has to answer, in this order:**

1. Does the readout move at all with the XPPen on the page? If the pointer kind reads `pen`
   and pressure varies, `WM_POINTER` arrives through the AWT window under Compose's render loop
   and Windows Ink is the route. If the samples never come (or come as `mouse`), the XPPen
   driver is not in Windows Ink mode or the messages are consumed before the window procedure —
   then WinTab.
2. The rate the readout shows while the pen moves (the driver's spec is 200+ Hz).
3. Whether the stroke lands under the pen tip at 125 % scaling — the one HiDPI conversion is
   `ScreenToClient`, on the assumption that Compose's desktop pixel space is physical.
4. The pressure curve from the CSV: how much of 0..1 a light touch and a firm press actually
   use, which sets the leads' `gamma` and floors for the pencil study.

**Decides, once measured:** the input route (Windows Ink or WinTab), and the pressure range the
pencil models are tuned against.

## L6. The pen's messages go to the child window, not the frame (2026-09-24, from the pen)

First hands-on with the whole tool: the XPPen drew, but every line had the same weight — no
pressure — and the Pen panel would have said why: no pen sample ever arrived. Windows sends a
`WM_POINTER` message to the window *under the pen*, and Compose Desktop does not draw into the
frame: skiko's `HardwareLayer` is a heavyweight `java.awt.Canvas` with a window handle of its own
inside it. The hook subclassed the frame's procedure and waited for messages that went to the
child; the child's `DefWindowProc` promoted the pen to mouse messages, and the sketch was drawn
by the mouse path at pressure 1.

**Decides:** `WindowsPointerSource` hooks the frame *and* every child window
(`EnumChildWindows`), one procedure keyed by handle; positions are made client-relative to the
frame either way, whose client area is where Compose's coordinates start. The Pen panel and the
Pen button now say when strokes come from the mouse and no pen sample has been seen — the other
way this happens is the XPPen driver not in Windows Ink mode, which no hook can fix.

Also from the same session: the XPPen's dial in its zoom setting sends **Ctrl+wheel**, so a
page that reads Ctrl+wheel as "size" does not zoom on the dial — zoom takes the wheel with or
without Ctrl now, Shift+wheel sizes. And 8–32-pixel value noise as "paper" reads as stains; the
tooth is 1–3 pixels (L2 said so; the first grain ignored it). And a grain kept as *white at alpha
g* — what the dab shader wants — shows nothing when multiplied over white paper: the page's
shade on screen is the same tooth as black at alpha 1 − g (`PaperGrain.shade`), tested headless.

**Still to measure, now that samples arrive:** the rate, the pressure curve, coordinates at
125 %, and the leads' numbers under a real hand.

---

*Entries that follow will come from the pencil study: the actual sample rate, measured
latency, and which numbers above survived contact with the pen.*
