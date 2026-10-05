# ActionDraw - Konzept

Eine App, die DER Helfer im Workflow der graphischen Erstellungen ist: Lerntool, Moodbord, ein paar 
Zeichen-und Maloptionen, schnelle Filter zum Ausprobieren etc. Dies soll ActionDraw darstellen.

Durch ein wunderschönes und intuitives Interface gelingt es, den Schaffenden auf dem Weg der Kreativität 
wundervoll zu unterstützen. Es gibt Zeichenübungen wie

- Action Drawing
- Zeichnungen mit Filtern wie Umrisse, schwarz-weiß, überkopf etc.
- Gedächtniszeichnungen
- Wechselzeichnungen (z.B. 2 Bilder, die sich jede Sekunde abwechseln)
- vieles mehr - die Ideen sind hier offen

Es gibt ein Mood Board mit dynamischem, kreativen Feel, wie als wäre man in einer Zeichenwerkstatt. Funktionen wie Gruppierungen, Views und Draw verfeinern das Erlebnis und machen es greifbarer. Daneben 
gibt es nicht nur Fotos, sondern auch Konzepte, die man dort einspeichern und wiederverwenden kann.

Nicht zuletzt gibt es das Drawing Board, wo mit ausgefeilten, realen Stiften nachempfundenen Einstellungen, gute Skizzen schnell gemacht werden können. Bilder können dort optional druntergelegt 
werden, um dies zu vergleichen.

Mit einer Schere lassen sich wiederum im Puzzle / Collage - Tool Bilder zurechtschneiden und 
zusammenfügen - perfekt um auszuprobieren, wie eine bestimmte Szene funktionieren kann. Bilder und 
Konzepte können auch hier gleichermaßen Einfluss finden.

Die Tools sind einzeln großartig, wirken aber auch perfekt zusammen. Am Ende kommt es darauf an, dass 
ein kreativer, hilfreicher und individueller Workflow gefunden werden kann, der den Künstler von der 
Idee bis zur Umsetzung unterstützt.

---

*The vision above is the brief, kept as it was written. What follows carries it out: what the app
is, how it looks and moves, and how the pieces join. Work items are in [ROADMAP.md](ROADMAP.md)
(M8 realises the design, M9 shapes the features), and loose ideas are in [IDEAS.md](IDEAS.md).*

**Design canvas:** [ActionDraw Design Recap](https://claude.ai/artifact/3xcFe4wp1GsbdemkbU3zn4)
has eight boards: the palette home screen (clickable), the foundations, motion, and one screen per
room. The link is private until it is shared from the canvas's Share menu. A copy is kept in
[docs/design/](docs/design/README.md): the canvas's files as a zip, plain HTML versions, and a
preview image of every board.

## In one sentence

ActionDraw is a drawing table: you collect material, keep what matters, train your eye and hand on
it, and make something from it, and the app always looks like a workshop rather than software.

## The six rooms

The app has six rooms, one per stage of the way from an idea to a finished piece. Each room has a
pigment of its own. It marks the room's things and nothing else (see *Colour is pigment* below).

| Room | Pigment | What happens there | Today |
|---|---|---|---|
| **Boards** | Ultramarine | Collect: the mood board, with pictures, notes, links, groups and frames, on cork, papyrus or plain | ✅ M1–M5 |
| **Concepts** | Viridian | Keep: a creature, a place, a character, collected once and linked onto every board that needs it | ✅ M7 |
| **Practice** | Cadmium orange | Train: timed poses, filtered studies, memory drawing, and the new *Flicker*, *Staged* and *Compare* exercises | ✅ M0, M+ (new exercises open) |
| **Lens** | Payne's grey | See: any picture through notan, edge, squint, mirror or upside down, with no session around it | ⬜ new; filters exist inside sessions |
| **Sketch** | Madder rose | Draw: the drawing board with real-feeling leads, paper tooth, and a picture laid underneath to compare | ✅ M6 (the underlay is new) |
| **Collage** | Naples yellow | Compose: scissors on a cutting mat. Cut pictures and concepts apart and test whether a scene holds | ⬜ new |

The order is a cycle, not a hierarchy: **collect → keep → train → see → draw → compose**, and
back to collecting with whatever came out.

### How the rooms hand things over

The rooms are fine on their own, but the vision asks that they *work together*. Each room can pass
material to the others in one step:

| From | To | Hand-off | Today |
|---|---|---|---|
| Board selection or group | Practice | *Draw from this board / these* | ✅ |
| Practice (a pose) | Sketch | *Sketch* pauses the session; the picture comes along | ✅ (corner reference; underlay new) |
| Board card / concept | Sketch | *Continue in Live Sketch*; new: *Sketch over* with the card as underlay | ✅ / ⬜ |
| Sketch | Board / Concept | *To a board* / *To a concept* | ✅ |
| Practice | Board | *Pin* | ✅ |
| Board / Concept | Collage | *To a collage*; the Collage's source drawer lists boards and concepts | ⬜ |
| Collage | Sketch | *Sketch over it*: the composition becomes the underlay | ⬜ |
| Collage, Board, Sketch | Lens | *Check the values*: the current picture seen through a lens | ⬜ |
| Anything | Compare | Your drawing laid over its reference | ⬜ (F+.3) |

The rule that makes this readable: **a button that leads into another room carries that room's
dab of pigment.** *Draw from this board* has a cadmium dab, *To a board* an ultramarine one. You
can see where a button goes before you read it.

## The design language: the atelier

ActionDraw should look like the table it is used at. The canvas's *Foundations* board shows all of
the following.

- **Ground and paper.** The ground is graphite (`#171513`), with board (`#211E1B`) and raised
  (`#2C2824`) for trays, drawers and lifted menus. Anything drawn on or pinned is paper (`#F3EDE2`,
  shade `#E7DECF`), with ink (`#27221D`, quiet `#6A6054`) on it. The warm dark of today
  (`#121212`, amber, teal) carries on, made warmer and more physical.
- **Six pigments.** Each has a *mass* (on paper and porcelain) and a *glow* (the same pigment on
  graphite, light enough for text): cadmium orange `#E0782F / #F5A25E`, Payne's grey
  `#46525F / #A3B0BE`, ultramarine `#3446B8 / #8E9EF4`, viridian `#1A6F60 / #5CC4AE` (a shade
  deeper than on the canvas: paper text on `#1F7F6E` reached only 4.2:1), madder rose
  `#B8324A / #EE8094`, Naples yellow `#D9A43A / #F0CB72`. Today's amber and teal are already on
  this spectrum.
- **Type: one typeface, one hand.** *Bricolage Grotesque* for display and interface: it has a
  slightly hand-cut warmth, and an optical-size axis makes it work from 12 to 96 px. *Caveat* is
  the pencil's voice, used for whatever the person wrote: notes, tags, a group's name, the ramp's
  labels. It is never used on a button, or for a number that must be read exactly. Both fonts are
  OFL and get bundled.
- **Materials.** Paper tooth (smooth, medium, rough, the same three papers as the sketch engine),
  graphite lines, masking tape, cork and push-pins, the green cutting mat, and porcelain for the
  palette and its wells. All of them are drawn in code (SkSL noise, as cork and papyrus already
  are), never shipped as bitmaps.

### Five principles

1. **Real before flat.** Every surface is a material, and a single lamp at the top left casts
   every shadow.
2. **Colour is pigment.** It is sprinkled, not poured. A room's pigment marks what you hold, what
   is selected and what is running, and never fills a whole surface.
3. **Things have weight.** Cards lift, lean into a drag and land. Nothing teleports.
4. **The work is louder.** While the pen is down, the chrome recedes. The page, the pose or the
   picture fills the room.
5. **Hands make marks.** Whatever the person names is lettered by hand; whatever the app says is
   set in type.

### Motion: wet, weighty, drawn

There are four movements, each with one job (the *Motion* board):

- **Bloom: choosing a room** (420 ms). The pigment spreads from the well you pressed with a
  watercolour's soft, darker rim, then draws back into a 2 px line along the room's top edge.
  `Esc` drains it back into its well.
- **Settle: moving a card.** The card lifts in 120 ms, leans into its speed while dragged (up to
  4°), and lands on a spring (damping 0.7) with a 2 % overshoot, its shadow tightening as it
  touches down.
- **Draw-on: a group's frame** (360 ms). The graphite frame draws itself from where the pointer
  let go, then its tag is lettered. The wobble is part of the path itself, so **the drawn path
  stays the hit-tested path**, which is the load-bearing rule from M5.
- **Recede: while you draw.** 1.5 s after the pen goes down, the bars fade out over 600 ms. Moving
  near an edge brings that edge's bar back at 70 %. The ensō timer never disappears; it stays at
  40 %.

With reduced motion on (the system setting or ActionDraw's own), every movement becomes a 120 ms
cross-fade.

## Home: the palette

The start menu becomes a **round porcelain mixing palette** on the table under the lamp. It has
six wells around the rim, one per room, each holding a dab of its pigment. The **centre well is
the mix**: it shows the selected room in a sentence and its next step, such as *Choose an
exercise*, *Open Drachenbuch* or *New sketch*.

- Click a well (or move around the rim with the arrow keys or `Tab`) to select it; the dab goes wet
  and a ripple runs round it. `Enter` or the centre's button opens the room, and the pigment blooms
  into it.
- `Esc` in any room brings you back to the palette. This is the one way home, everywhere.
- **Settings** and **Hotkeys** sit quietly in the top right corner, as they sit quietly under the
  big buttons today.
- The session settings now on the start menu (folder, plan, timing, auto-advance) move into the
  Practice room, next to the exercise they belong to.

## The rooms, one by one

- **Practice: choosing an exercise.** Exercises are index cards taped to the table, each with an
  ink drawing of what it does: Action drawing, Lens studies, From memory, Flicker, Staged,
  Compare. The chosen card is lifted and taped in cadmium. Next to it, the timing panel draws the
  ramp as a row of graphite bars, one per pose, with heights by duration and labels in Caveat
  (`1 min × 10`, `2 × 5`, …). *Drawing from* names the folder or board. The large *Start* button
  shows the total time.
- **Practice: in a pose.** The picture is centred on near-black. The timer is an **ensō**, a brush
  circle that closes as the pose runs out, with dry-brush edges, so the remaining time can be read
  without reading a number. The pose count is a row of ticks under it. Filters become a **lens
  tray** of glass slides that show their effect in miniature, with their keys (1–9) underneath.
  The selected slide rises and opens its parameters (Notan: 2 or 3 values, threshold) just above
  it. Adjustments (light, blur, mirror, flip, invert, shards, grid) are small round knobs. All of
  this recedes while you draw.
- **Boards.** Pictures are prints with a white border, pinned with ultramarine push-pins.
  Sketches are taped. Notes are yellow slips lettered in Caveat. Concepts carry a viridian folder
  tab. A group is a graphite loop drawn round its cards, with a paper tag on a pin. The context
  menu is the palette again, small: a **ring of porcelain buttons** round the selection, with
  pigment dabs on the ones that lead to other rooms (*Draw these*, *Sketch over*, *To a collage*,
  *Add to concept*) and plain glyphs on the rest (*Tag*, *Group*). The right-click menu stays
  for everything else.
- **Sketch.** The page is a sheet taped to the table at the four corners. The tools lie in a
  **pencil tray** as objects: H, HB and 4B as lacquered hex pencils, the 0.5 mechanical, a
  charcoal stick, a fineliner, a brush pen and a rubber. The chosen one slides out of the tray.
  On the right are the three papers as real swatches of tooth, the shades, and the new
  **Underneath** panel: the reference under the page at a chosen strength, with *Lay it over
  instead* for checking at the end.
- **Collage.** The table is a **green cutting mat** with a grid and a ruler. Pieces are cut
  pictures with straight scissor edges or torn ones, lifted a little off the mat, some taped.
  Scissors cut along a dashed Naples-yellow path that moves like marching ants; holding `Shift`
  cuts straight. On the right is a pieces list (top to bottom); at the bottom, a source drawer for
  boards and concepts. *Check the values* sends the composition to the Lens, and *Sketch over it*
  sends it to Sketch as the underlay.
- **Lens** (designed in the Practice session's tray; no screen of its own yet). Open any picture
  (a file, the clipboard, a board card, the current collage) and look at it through the same
  tray, with no clock running. It is the vision's "schnelle Filter zum Ausprobieren".

## What stays true

The re-skin changes how things look and move, not the rules that make them work. These are the
load-bearing rules from building M1–M7 (see [LEARNINGS.md](LEARNINGS.md)):

- **The drawn path is the hit-tested path.** A hand-drawn, wobbly frame keeps its wobble in the
  path itself, never in a separate decoration.
- **Canvas gestures use `detectDragGestures`** and key on the item's id, not on geometry that
  changes. A *settle* animation is a draw-time transform of the card, so the hit box stays on the
  box around its `ContextMenuArea`.
- **Skia images, not `ImageBitmap`s per frame.** Grain, blooms and paper are SkSL shaders or
  cached tiles, drawn through the native canvas, as the cork and the sketch engine already are.
- **Animations never touch the drawing surface.** While the pen is down, nothing animates except
  the ensō, so stroke latency stays what M6 made it.
- **Keyboard first.** Every room keeps its shortcuts, and the palette can be driven entirely from
  the keyboard.
- **It gets out of the way.** No streaks, scores or badges (see *Where next* in
  [IDEAS.md](IDEAS.md)). The table's materials set the atmosphere and never block the way to a
  tool. Reaching a pencil never means opening a drawer first.