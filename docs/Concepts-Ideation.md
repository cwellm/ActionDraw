# Concepts — ideation

*2026-09-20. A fourth tool beside Draw, Board and Live Sketch. Tracked as **M7** in
[ROADMAP.md](../ROADMAP.md). Ideation, not yet a frozen spec: the open questions in §7 are real.*

## 1. The idea

A board is a *place*: this project, this theme, this week's material. A **concept** is a
*thing*: this character, this landscape, this creature, this prop. Things recur across places —
the same dragon turns up on the Drachenbuch board, the anatomy board and next month's colour
study — and today the only way to have it there is to copy the pictures three times, after
which the three copies drift apart.

So: a concept lives once, in its own folder, and is **linked** onto any number of boards. On
each board it shows as a group of its own — a concept group — that is the concept, not a copy
of it. Add a sketch to the concept and every board that links it has the sketch.

## 2. What a concept is

- A **folder**, exactly as a board is, under a *Concepts home*, with a sidecar
  `.actiondraw_concept.json`: `id`, `name`, `kind` (character · creature · landscape · prop ·
  colour · other — a label, not a schema), `notes`, and its items.
- Its items are the board's card types plus one: **pictures**, **sketches** (Live Sketch output,
  reopenable while the `.sketch.json` is there), **notes**, **links**, and **documents** —
  `.md` files rendered with the board's Markdown renderer ([Board-Handling-Spec.md](Board-Handling-Spec.md) §3),
  edited in a plain text box with a live preview.
- A `ConceptRegistry` beside `BoardRegistry`, same shape: `concepts.json` mapping id → folder,
  so a concept keeps its identity wherever its folder goes. Boards refer to concepts **by id**,
  never by path — a moved concept still resolves.
- No nesting of concepts. A concept is flat; structure inside it is the board's job.

## 3. On a board

- `BoardFile.concepts: List<String>` — the ids of linked concepts.
- Each linked concept appears as a **group with `source = "concept:<id>"`** — the field the
  subgroup work reserves now ([Board-Handling-Spec.md](Board-Handling-Spec.md) §2). Its cards
  are the concept's items, read from the concept folder, shown with the concept's name and a
  distinct frame style (dashed outline, or a small ⧉ mark on the label) so it reads as
  *borrowed*.
- **Non-resolvable:** the group cannot be ungrouped, renamed or recoloured from the board,
  and its cards cannot be moved into another group or removed one by one. The one action on
  the group itself is **Unlink** — which removes it from this board and touches nothing in the
  concept.
- Everything a board does *with* cards works on a concept group: draw from it, view it large,
  put it in the float strip, star and tag (stored on the board's side, since the concept is
  shared — see §7), search, select, contact sheet.
- In free mode the concept group has a frame and a position like any group; its cards are
  placed on first link and moved as a unit. Cards inside can be arranged.

## 4. The Concepts screen

- A list of concepts, tiles as the board list has, grouped by `kind`, with a cover picture and
  counts — and on each tile, which boards link it.
- A concept opened: its items in a simple grid, documents rendered, a notes column; buttons for
  add pictures, new document, new sketch (opens Live Sketch), and **Link to board…** listing
  every board with a tick for the ones already linked.
- From a board: **Boards ▾** gains a sibling **Concepts ▾** — link one, jump to one.

## 5. Sharing, honestly

A concept linked to three boards is one folder. The consequence to be plain about: **deleting
a concept** removes it from every board at once. The dialog says which boards, by name, before
it agrees. *Unlink* from a board is the gentle action and the default one offered on a board.

The other consequence: a concept has no idea which board is looking at it, so per-board
opinions about its cards (starred here, tagged there) cannot live in the concept. They live on
the board, keyed by the card's content id — the rename-proof identity from M2 — which is what
makes the same picture recognisable from two boards.

## 6. Order of work

1. `ConceptRegistry`, the concept folder and sidecar, the Concepts list screen. A concept
   with pictures and notes, no board involvement yet.
2. Documents: `.md` in the concept, rendered, editable with preview. Uses the Markdown renderer
   from M5.
3. Linking: `BoardFile.concepts`, the concept group on a board, Unlink, the deletion dialog
   that names its boards.
4. Sketches: Live Sketch saving into a concept (needs M6).
5. Per-board stars and tags on borrowed cards.

## 7. Open questions

- **Adding to a concept from a board.** Should dropping a picture onto a concept group add it
  to the concept (so every board gets it), or is a concept only edited on the Concepts screen?
  Assumed: **drop adds to the concept**, with the card showing that this is what happened.
  Editing documents stays on the Concepts screen.
- **Where concepts live.** A *Concepts home* folder next to the boards home, or inside it?
  Assumed: beside it, `~/ActionDraw Concepts`, changeable as the boards home is.
- **Practice memory.** Seen/redo state lives in the folder of the pictures — so drawing a
  concept's card from any board records it once, in the concept. That seems right: it is the
  same dragon. Confirm.
- **Kinds.** Is the fixed list above enough, or should kinds be free text with suggestions?
  Assumed: free text with those as suggestions.
