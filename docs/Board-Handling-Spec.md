# Idea Board — handling, second round (spec)

*2026-09-20. The board exists and is in daily use; this is what using it asked for. Tracked as
**M5** in [ROADMAP.md](../ROADMAP.md); design history in [IdeaBoard-Shaping.md](IdeaBoard-Shaping.md).*

The purpose, restated, because it decides the shape of everything below: the board is a **free
surface of ideas and inspiration**. Pictures, notes, links, sketches — grouped loosely, moved
often, looked at a lot. Anything that makes it feel like a form or a filing cabinet is wrong.

## 1. Frames shaped to the arrangement

**Today** a group's frame is the bounding box of its cards plus padding. Three cards in an L
get a large empty rectangle; two clusters far apart get one frame spanning nothing.

**Spec.** The frame is the **union of the cards' padded boxes**, drawn as one smooth outline:
each card contributes a rounded rectangle grown by the padding; where those overlap or touch
they merge; where they do not, the frame has a waist or splits into lobes. Visually a soft
"cloud" that hugs what is actually there.

- Built with Skia path ops (`Path.op(UNION)`) over the padded rectangles, then rounded corners
  via a small blur-and-threshold or by stroking with round joins — whichever reads better in the
  first build. Cached per group and recomputed only when a member moves.
- Two cards far apart therefore give **two lobes with a thin bridge** rather than one box. The
  bridge is a straight rounded connector between the nearest edges, so a group never looks like
  two groups.
- Hit-testing for "click the group's area" uses the same path (`Path.contains`), so what you
  see is what you can click — the lesson of §25.
- The label keeps its §25 behaviour (drawn over the cards, slides to stay in view), anchored to
  the top-left of the union's bounds.
- Considered and not chosen: a convex hull (still boxes an L shape), a concave hull (fiddly,
  and unstable as cards move). The union of what is there is the honest shape.

## 2. One level of subgroups

**Spec.** A group may sit inside one other group. Exactly one level: a subgroup cannot hold
subgroups. This is a deliberate limit — deeper trees turn a board into an outliner.

- Model: `BoardGroup.parentId: String? = null`. Validation on load flattens anything deeper
  (a subgroup's `parentId` that names another subgroup is cleared, with a notice).
- **Grid mode:** a subgroup is a section inside its parent's section, indented, with the
  parent's colour as a lighter tint by default and its own colour if picked. Collapsing the
  parent collapses its subgroups.
- **Free mode:** the subgroup's frame (§1) sits inside the parent's frame; the parent's union
  includes the subgroup's padded frame as one more shape, so the parent always encloses it.
  Dragging the parent's area moves everything; dragging the subgroup's area moves only it.
- Membership: a card in a subgroup is *in* the parent too, for every purpose that asks —
  "Draw" on the parent draws the subgroup's cards; the parent's count includes them; selecting
  the parent selects them.
- Making one: *Group the selection* while inside a group offers "as a subgroup of X"; the
  drawer and the context menu get "Move into…" for an existing group. Ungrouping a subgroup
  lifts its cards into the parent, not into the Inbox.
- **Anticipates Concepts** ([Concepts-Ideation.md](Concepts-Ideation.md)): the group model
  also gains `source: String? = null`. A linked concept appears as a group whose `source` names
  it; such a group cannot be ungrouped or have cards moved out, and it may hold a subgroup. The
  field is added now so the sidecar does not change shape twice.

## 3. Notes: proper Markdown

**Today** notes render `**bold**` and `*italic*`. **Spec:** a readable subset of Markdown,
rendered on the card and in the note dialog's preview; the note stays plain text in the sidecar,
which was decision D4 and still holds.

Rendered: `# heading` / `## sub-heading` · `**bold**` · `*italic*` · `- bullet` lists ·
`1. numbered` lists · `[text](url)` links, clickable, opening in the browser · `` `code` `` ·
`---` rule · blank-line paragraphs. Not rendered (shown as typed): tables, images, HTML, nested
lists beyond one level.

- Own renderer over an `AnnotatedString` with paragraph layout, no dependency — the subset is
  small and the rendering target is a card, not a page. `NoteText` grows into `Markdown`.
- Links inside notes are the answer to "links must be possible": a note can carry several,
  each one clickable. Link *cards* stay as they are for a link that is a thing of its own.
- The same renderer is what Concepts will use for `.md` documents, so it lives outside the
  note code.
- Search keeps indexing the plain text (`Markdown.plain`), markers stripped.

## 4. Menus that get out of the way

**Today** the board header is a `FlowRow` of nine outlined buttons and six chips that wraps to
two or three lines. It reads as a settings page sitting on top of the board.

**Spec.** One line, most of the time.

- Left: board name (click = rename) · **Boards ▾** · **↑ Parent** when nested.
- Middle: a **segmented Grid | Free** toggle (one control, not two chips).
- Right: **Search** field · **Contents** · **⋯** overflow.
- Into **⋯**: theme, snap, float strip, contact sheet, immersive, session recipe, close. Things
  used once per session do not need a permanent button.
- Chips lose their outline and gain hover; buttons drop to text style; 6 dp gaps, not 8.
- The action bar under the board (Draw selection / View / Group…) keeps only what acts on the
  selection, and hides when nothing is selected — the empty board should be just the board.
- Immersive mode stays as it is: everything hidden.

## 5. Custom board background

**Spec.** A board can have a wallpaper: any picture, shown behind the cards.

- `BoardFile.wallpaper: Wallpaper?` with `path` (relative, the file copied into
  `_wallpaper/`), `fit` (`cover` · `tile` · `center`), `dim` (0–1, a dark veil so cards stay
  readable), `blur` (0–1). The theme's texture (cork, papyrus, plain) sits underneath and shows
  through when the wallpaper does not cover.
- Set from **⋯ → Wallpaper…**: pick a file, or drop an image onto the board with `Alt` held.
  Remove from the same place.
- Grid mode shows it too, behind the sections; Free mode pans it with the camera at a slight
  parallax (0.3×) so the board feels like a surface rather than a photo.
- In the contact-sheet export the wallpaper is left out on purpose — the sheet is the material.

## 6. Order of work

1. Markdown notes (§3) — self-contained, and Concepts wants it.
2. Subgroups (§2) — model first, with `source` reserved; grid, then free.
3. Shaped frames (§1) — sits on the subgroup frame logic.
4. Wallpaper (§5).
5. Menus (§4) — last, once the new controls exist to be arranged.

Each step ships with its own tests, and the ones about clicking and rendering go through the
real composable, as everything since §19 has.

## 7. Open questions — and what was done (2026-09-20)

Built in the order of §6, all five parts; the decisions taken where the questions were still open:

- §1 frame: built as the cloud — the union of padded boxes with bridges — and hit-tested by the
  same path. A convex hull was not tried; the L-shaped case, which is the one that matters,
  reads right. Inner corners where boxes meet are left sharp in this build.
- §3: notes render the full subset; a **Markdown document card** was *not* added to the board.
  Documents arrive with Concepts, where they are first-class and the same renderer is waiting.
- §4: the overflow list stood as guessed. It is a menu, so moving something back out is a
  one-line change once daily use says otherwise.
- §5: the `Alt`+drop gesture was declined (see IdeaBoard-Shaping §30) — modifier state during an
  external drop is not reliably readable; *Use as wallpaper* on a card and the chooser cover it.

Design history for each part: [IdeaBoard-Shaping.md](IdeaBoard-Shaping.md) §27–§31.
