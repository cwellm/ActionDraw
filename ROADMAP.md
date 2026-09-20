# ActionDraw Roadmap

Three levels of granularity: **Milestones** (`##`) → **Features** (`###`) → **Tasks** (bullets).
States: ✅ done · 🔄 in progress · ⬜ open. A milestone/feature shows the roll-up of what's below
it. Tasks are broken out when their milestone becomes active; later milestones stay at feature
level on purpose.

Background documents: [ACTIONDRAW_EXTENSION.md](ACTIONDRAW_EXTENSION.md) (exploration/ideation) ·
[docs/IdeaBoard-Shaping.md](docs/IdeaBoard-Shaping.md) (the board's design history) ·
[IDEAS.md](IDEAS.md) (practice-side scratchpad). The next phase has its own:
[docs/Board-Handling-Spec.md](docs/Board-Handling-Spec.md) (M5) ·
[docs/LiveSketch-Exploration.md](docs/LiveSketch-Exploration.md) (M6, with findings in
[LEARNINGS.md](LEARNINGS.md)) · [docs/Concepts-Ideation.md](docs/Concepts-Ideation.md) (M7).

---

## ✅ M0 — Practice core (shipped)

### ✅ Timed reference sessions
- ✅ Folder scan, random order, fixed interval (30 s–60 min)
- ✅ Gesture ramps (Quick warm-up / Classic gesture / Long studies)
- ✅ Auto-advance toggle with manual "+overtime" mode
- ✅ Session summary (poses, total time)

### ✅ Views & filters
- ✅ View modes: B&W, Squint, Sepia, Posterize, Pixelate, Edge, Silhouette, Notan
  (Warm/Cool became the colour-temperature slider — F+.1)
- ✅ Toggles: Blur, Mirror, Upside down, Invert, Defraction
- ✅ Adjustable parameters (sliders) · proportion grids (Thirds/Phi/Diagonal)

### ✅ Memory & picking
- ✅ Seen/redo state per folder (`.actiondraw_seen.txt`, `.actiondraw_redo.txt`)
- ✅ Picture picker (thumbnail grid, All/None, per-selection cycles)
- ✅ Last-folder memory across restarts

### ✅ Packaging
- ✅ Windows MSI · Debian package · ArchLinux PKGBUILD + guides

---

## ✅ M1 — Idea Board: the useful board (Phase 1)

*Shape: [docs/IdeaBoard-Shaping.md](docs/IdeaBoard-Shaping.md). Grouped grid on a cork/papyrus
surface, material in & out, "Draw these" bridge.*

### ✅ F1.0 Shaping
- ✅ Exploration & ideation (ACTIONDRAW_EXTENSION.md)
- ✅ Decisions D1–D7 taken
- ✅ Shaping document written
- ✅ Open questions Q1–Q5 answered (shaping §10) → shape frozen: explicit membership,
  boards created first, tags pulled into Phase 1

### ✅ F1.1 Foundations
- ✅ Add `kotlinx-serialization-json` (first non-Compose dependency)
- ✅ Recursive `ImageScanner.scanTree` + `relKey` (skips dot-dirs; `/`-separated keys)
- ✅ Thumbnail disk cache (`~/.actiondraw/thumbs/`, key = hash(path, size, mtime)) — also used by the picker

### ✅ F1.2 Board store
- ✅ Schema v1 (`BoardFile`, groups/items/theme, `ignoreUnknownKeys`)
- ✅ Load/save sidecar — atomic write + `.bak`, parse failure falls back to the backup
- ✅ Validate membership (explicit — no auto-add; missing files → drop on save)
- ✅ Unit tests (store round-trip/backup/validation, importer, board-state behaviour)

### ✅ F1.3 Board screen
- ✅ Menu section: "New board…" (name + location, boards-home default) · "Open board…" · MRU chips
- ✅ Grouped grid with Inbox + collapsible sections (span headers)
- ✅ Explorer-style selection (click / Ctrl / Shift / Ctrl+A)
- ✅ Group CRUD: create, rename, colour, reorder, delete → items to Inbox
- ✅ Card actions: move to group, star, remove from board (file untouched)
- ✅ Quick-look overlay (`Space`) + keyboard navigation (arrows step linearly, see shaping §12)

### ✅ F1.4 Notes, captions & tags
- ✅ Note cards (plain text): create, edit, place in groups
- ✅ One-line captions on image cards (`F2`)
- ✅ Tag editor (`T` / context menu), multi-select aware
- ✅ Tag filter bar (AND chips; notes visible only without filter)

### ✅ F1.5 Material in
- ✅ Drag & drop from Explorer → copy to `_imported/` (drops land in the Inbox, shaping §12)
- ✅ `Ctrl+V` file list → import
- ✅ `Ctrl+V` bitmap (browser "Copy image") → PNG in `_imported/`
- ✅ "Import…" file chooser (multi-select; in-root files referenced in place)

### ✅ F1.6 Material out
- ✅ `Ctrl+C` → `javaFileListFlavor` on the clipboard (paste in Explorer = duplicate)
- ✅ Notes-only selection copies as plain text

### ✅ F1.7 The bridge
- ✅ Seen/redo keys: file names → relative paths (backward compatible for flat folders)
- ✅ `BoardHost.startSession(root, images)` — session pool from explicit file list
- ✅ "Draw selection" / "Draw group" · summary returns to the board · "Go again" replays the pool

### ✅ F1.8 Look & themes
- ✅ Cork (default), papyrus, plain — per-board, stored in sidecar
- ✅ Tileable textures via SkSL value noise (generated at runtime, no bundled assets)
- ✅ Paper-backed card styling + light paper palette on textured themes

### ✅ F1.9 Immersive mode
- ✅ `F` fullscreen board, chrome hidden, `Esc` convention as in sessions

### ✅ F1.10 Wrap-up
- ✅ README covers both halves of the app (Draw + Idea Boards) with both shortcut tables;
  IDEAS.md points at the board documents and notes the relative-path store keys
- ✅ Hands-on passes on Windows by CW — they produced feedback rounds 1 and 1b
- ✅ Smoke test: app launches, menu renders, 66 tests green

---

## ✅ M1-F — Idea Board: feedback round 1

*Seven asks from the first hands-on pass (2026-08-30), all realized; shaping §13 has the details.*

### ✅ F2.1 Menu & entry
- ✅ "Draw" and "Boards" as equally sized primary buttons
- ✅ Board picker dialog: a list of available boards (recent + boards home) instead of menu chips
- ✅ "Explore…" opens the folder dialog only from the picker; "New board…" lives there too
- ✅ Boards home (the root location for boards) shown and changeable in the picker

### ✅ F2.2 Immersive & windows
- ✅ Chrome hidden only in explicit immersive mode — all menus visible whenever not fullscreen
- ✅ Board sessions open in their own window; closing it aborts the drawing, back to the board
- ✅ Summary says "Back to board" and returns there; "Go again" replays the board pool

### ✅ F2.3 Freeform canvas (pulled forward from M3)
- ✅ Grid ⇄ Free layout toggle per board (persisted, camera included)
- ✅ Cards movable (drag; arrows nudge), resizable (corner handle; Ctrl+wheel), rotatable (top handle; Shift+wheel)
- ✅ Pan (drag empty space), zoom about the cursor (wheel), Fit-view button
- ✅ Auto-placement of unpositioned cards · z-order with "Bring to front"
- ✅ Image aspect ratio remembered in the sidecar for stable layout
- ✅ "Move to ▾" dropdown in the action bar (Inbox → group discoverability in grid mode)

### ✅ F2.5 Ordering (feedback round 1b)
- ✅ Grid: reorder cards within a group — Move earlier/later, Move to group start/end (context menu; Ctrl+↑/↓, +Shift = start/end)
- ✅ Free: z-order — Bring forward / Send backward / Bring to front / Send to back (context menu; Ctrl+↑/↓, +Shift = front/back)
- ✅ One items array is both the grid order and the z-order; stepping skips filter-hidden cards

### ✅ F2.6 Large view & formats (feedback round 1c)
- ✅ Viewer: the selection shown big, as a swipeable carousel (drag, chevrons, wheel, `←`/`→`,
  filmstrip, counter); nothing selected = everything on the board; replaces the old quick-look
- ✅ Opened by `Space`, the *View* button, or a card's *View large* menu entry
- ✅ Decoding goes through `ImageDecoder`: Skia first, ImageIO as fallback
- ✅ AVIF/HEIC advertised only when an ImageIO reader is installed (no cards that cannot render)
- ✅ AVIF enabled by shipping the plugin dependency (README → Image formats); imports now report
  what they skipped instead of ignoring a drop silently

### ✅ F2.4 Wrap-up
- ✅ Menu hardened: the Draw/Boards row is pinned below the scrolling settings, so both stay
  reachable at any window size; default window 1120×800 (fits 1080p at 125% scaling)
- ✅ Verified on Windows: app launches, menu renders both primary buttons, 66 tests green

---

## ✅ M2 — Idea Board: the scaling board (Phase 2)

*Everything that keeps a board usable once it holds more than a screenful.*

### ✅ F3.1 Finding things
- ✅ Free-text search over file names, captions, tags and note text, next to the tag chips
- ✅ "Clear filter" clears both the search box and the active tags

### ✅ F3.2 Practice on the board
- ✅ Badges on cards: ✓ drawn, ⟳ flagged to redo (read from the board folder's seen/redo stores)
- ✅ Smart sections above the groups — "⟳ Redo" and "Never drawn", each with its own Draw button;
  they are views, so a card keeps its group
- ✅ Badges refresh when a session started from the board closes

### ✅ F3.3 Per-board session recipe
- ✅ Interval or gesture ramp, auto-advance, view mode and grid stored in the sidecar
- ✅ Applied to every session started from that board; "Use current" captures the menu's settings
- ✅ Without a recipe the menu's settings are left untouched

### ✅ F3.4 Pin from a session
- ✅ "Pin ▾" in the session control bar files the picture on screen onto any board
- ✅ The summary offers to pin the session's redo-flagged pictures
- ✅ Practice code stays board-agnostic — it only sees a `PinTargets` handle

### ✅ F3.5 Drag-reordering
- ✅ Drag a card onto another within its group to reorder (hit-tested against the lazy grid);
  the drop target is outlined and the move happens on release
- ✅ Smart sections stay read-only

### ✅ F3.6 Rename-proof identity
- ✅ Cards carry a content id (length + sampled SHA-1); a picture renamed or moved outside the app
  is found again in the board folder, keeping caption, tags, groups and position
- ✅ Only pictures that are really gone drop off; existing sidecars gain ids on next load

### ✅ F3.7 Board list screen
- ✅ "Boards" opens a real screen: a tile per board with cover picture, counts and path
- ✅ Boards home shown and changeable there, with New board… and Explore…

---

## ✅ M3 — Idea Board: the delightful board (Phase 3)

- ✅ ~~Freeform canvas layout mode~~ — pulled forward into M1-F

### ✅ F4.1 Freeform polish
- ✅ Marquee selection: Shift+drag pulls a rubber band over the canvas (plain drag still pans)
- ✅ Snapping: a dragged card lines up with its neighbours' centres, with guides drawn; toggled
  by the "Snap" chip in the header
- ✅ Group headers are drop targets — drag a card onto a header to file it into that group

### ✅ F4.2 Always-on-top reference strip
- ✅ "Float strip" opens a separate always-on-top window: one picture big, the selection as a
  filmstrip, prev/next — made to sit beside Krita

### ✅ F4.3 Contact-sheet export
- ✅ "Contact sheet…" renders the selection (or the whole board) as one printable PNG with
  captions and a title, drawn with Skia

### ✅ F4.4 Rich note formatting
- ✅ `**bold**` and `*italic*` rendered on the card; the note stays plain text in the sidecar
- ✅ Paper colour per note and a heading style, both in the note dialog and the context menu

### ✅ F4.5 Colour-palette extraction
- ✅ Dominant colours per picture (4×4×4 colour-cube quantisation over a small decode), shown as
  swatches with hex values — context menu, the Palette button, or `P`

### ✅ F4.6 Board templates
- ✅ New board… offers starter groups: Creature design, Character sheet, Environment, Anatomy
  practice, or Empty

### ✅ F4.7 Link cards
- ✅ A card holding a url + title, opened in the system browser; searchable like everything else
- ✅ **Web thumbnails** — *Fetch preview* on a link card downloads the picture the page advertises
  (OpenGraph `og:image`, then `twitter:image`) into `<board>/_previews/` and shows it on the card
  - ✅ The app's only networked code, and it asks first: a dialog states what leaves the machine
    before anything is sent. Never automatic, never in the background, one card at a time
  - ✅ http/https only, 8 MB cap, 10 s/20 s timeouts, no cookies, self-identifying user agent
  - ✅ Once saved, the picture is a normal file in the board folder — the board works offline again
  - ✅ *Remove preview* takes the picture off the card
  - ✅ Covered by `LinkPreviewTest` through a stubbed fetcher, so the suite itself never goes
    online; the http-only guard was checked by removing it and watching two tests fail

---

### ✅ F4.8 Groups on the canvas & strip carousel (feedback round 2)
- ✅ Every group now has a colour, whether or not one was picked: a dot and a hairline under the
  section header in grid mode, a tinted, outlined area with a name label in free mode
- ✅ Dragging a group's area moves the whole group; the selection is left alone, so dragging a
  single card afterwards still moves only that card
- ✅ The group label selects the group as a unit; right-clicking its area offers draw, rename,
  colour and delete
- ✅ A grouped card carries its group's colour as a dot, so it stays recognisable on its own
- ✅ The floating reference strip is a carousel again: drag or wheel to flip, with the neighbours
  sliding in as in the large view

### ✅ F4.9 Grouping, ungrouping and the contents drawer (feedback round 3)
- ✅ **Group the selection** (`Ctrl+G`, the action bar, or the drawer) — the way to make a group
  on the canvas, where there are no sections to drop cards into. An empty group draws nothing,
  which is why groups made with "New group" alone looked invisible.
- ✅ **Ungroup**: `Ctrl+Shift+G` for the selected cards, "Ungroup" on a whole group; groups left
  holding nothing are tidied away
- ✅ **Contents drawer** (`Ctrl+D` or the Contents button): every card listed by group, with
  thumbnails, collapsible groups, and Select / Draw / Rename / Ungroup per group
- ✅ Clicking a row selects it and brings the freeform camera to it
- ✅ Header wraps instead of squeezing its buttons; the ⛶ glyph (which rendered as a box) is gone
- ✅ Group areas use `requiredSize`, so a hull larger than the window is no longer clamped

### ✅ F4.10 Aspect-aware bounds and robustness (feedback round 4)
- ✅ Hulls, marquee and Fit measure a card by its real width *and* height, so a portrait picture
  sits inside its group's area instead of hanging out of it
- ✅ Auto-placement gives rows more room, so tall cards no longer overlap the row below
- ✅ A board file with a UTF-8 byte-order mark loads instead of looking corrupt

### ✅ F4.11 Multi-select on the canvas (feedback round 5)
- ✅ Tap-to-clear moved below the cards; a card click is no longer undone by the release bubbling
  up to the canvas, so Ctrl/Shift multi-select works in free mode
- ✅ Clearing the selection also drops the focus ring; the selected outline is heavier (3 dp)
- ✅ Compose UI tests (`compose.desktop.uiTestJUnit4`) drive the real canvas — verified by
  restoring the old handler and watching them fail

### ✅ F4.12 One meaning for "group" (feedback round 6)
- ✅ `G`, `Ctrl+G`, the action-bar button and the drawer all call `startGrouping()`: it groups the
  selection when there is one, and starts an empty group otherwise
- ✅ The button says which it will do ("Group (2)" vs "New group")
- ✅ Key mapping extracted to a pure `handleBoardShortcut`, covered by `BoardKeysTest` — verified
  by restoring the old binding and watching it fail

### ✅ F4.13 Deleting boards
- ✅ *Delete…* on a board tile, with the default following whose folder it is: a folder
  ActionDraw made for the board is deleted with it, a folder that was already the user's is kept.
  The tick is always there either way (revised 2026-09-05, see F4.14)
- ✅ Guards: refuses a folder that is not a board, the user's home, or the boards home; closes
  the board first if it is the one on screen; drops it from the recent list
- ✅ Covered by `DeleteBoardTest`, whose guard case was checked by weakening the guard

---

## ✅ M4 — Idea Board: living with it

Everything after M3 shipped: the things that only show up once a board is in daily use, and the
two reports that came out of using one.

### ✅ F5.1 Boards are recorded, not discovered
- ✅ `BoardRegistry` (`~/.actiondraw/boards.json`) maps each board to the folder it lives in
- ✅ A board's name is no longer its folder's name: a taken folder name gets `Test (2)` beside it,
  so a deleted board's name is usable again (reported 2026-09-05)
- ✅ Moving the boards home adds a place to look instead of hiding the existing boards; nothing
  on disk is moved
- ✅ Deleting acts on the recorded folder and drops the record; folders that vanish behind the
  app's back are pruned from the list
- ✅ Boards from before the registry are adopted the first time the list is drawn
- ✅ Covered by `BoardRegistryTest`, checked by reverting each behaviour in turn — which is how a
  test that only appeared to prove the home-move guarantee was caught

### ✅ F5.2 Zoom in the large view
- ✅ The wheel — which is what an XPPen dial sends — zooms about the pointer; `+`/`−` (either
  keyboard block, `=` on a US layout) zoom about the centre; `0` fits again (asked 2026-09-07)
- ✅ Once zoomed, dragging pans and stops where empty space would show; flipping to another
  picture starts fitted again; the wheel no longer flips (drag, chevrons, arrows and the filmstrip
  still do)
- ✅ Covered by `ViewerZoomTest` (the arithmetic), `BoardKeysTest` (the keys) and
  `ViewerWheelTest`, which sends a real wheel event through the composable; both the wheel
  handler and the zoom clamp were checked by reverting them

### ✅ F5.3 Selecting a group, wherever it is
- ✅ Clicking a group's area selects the whole group — it used to have exactly one handle, the
  little label at the hull's top-left corner (reported 2026-09-16)
- ✅ That corner is routinely off the edge of the view for a wide group, which took the handle
  with it: the label is now drawn over the cards and slides along to stay in sight, never leaving
  its own group
- ✅ In grid mode the group's name in the section header selects it too, which it never did
- ✅ Covered by `GroupClickTest`, which clicks where the label really is and keeps a guard that
  dragging a group still moves it; both halves were checked by reverting them

### ✅ F5.4 Boards inside boards
- ✅ A board created inside another board's folder is a sub-board of it — derived from the
  registry's recorded paths, with nothing new stored (asked 2026-09-16)
- ✅ Nesting is arbitrarily deep, and a board belongs to the nearest board above it
- ✅ Making a sub-board does not move the boards home; a board merely sharing a name prefix
  (`Drachen2` beside `Drachen`) is not inside anything
- ✅ **Boards ▾** in the header browses the whole tree from inside a board, with *New sub-board
  here…*; a sub-board shows **↑ Parent** for one tap up; the board list shows tree order with a
  parent breadcrumb on each tile
- ✅ Deleting a folder forgets the boards nested in it and says how many first; *Remove board*
  leaves them as roots of their own
- ✅ Covered by `SubBoardTest` and `BoardSwitcherTest`, all five behaviours checked by reverting

### ✅ F5.5 Rearranging the tree
- ✅ **Move…** on a tile and *Move this board…* in the header put a board under any other board,
  or back out to the top level, at any time (asked 2026-09-16)
- ✅ The folder moves with it — pictures and nested boards included — and every record under it is
  repathed in one go, so nothing is left pointing where the board used to be
- ✅ The open board follows its folder, even when it was nested inside the one that moved
- ✅ Renames where the filesystem allows it, copies across drives; a copy that fails part-way is
  cleaned up, so a board either moved or was not touched
- ✅ Refuses a move into itself or its own sub-board, compared canonically and re-checked by the
  mover itself — without that guard the folder is copied into itself until the filesystem gives out
- ✅ A name already taken at the destination gets its own folder rather than merging
- ✅ Covered by `MoveBoardTest`; the behaviours were checked by reverting them, and the self-move
  guard proved itself by producing `Flügel/Membran/Flügel/Membran/…` 28 levels deep when removed

## 🔄 M5 — Idea Board: handling, second round

Spec: [docs/Board-Handling-Spec.md](docs/Board-Handling-Spec.md). The board as a free surface
of ideas and inspiration; what daily use asked for.

### ✅ F6.1 Notes in Markdown
- ✅ Headings, bold, italic, bullet and numbered lists, clickable links, inline code, rules —
  rendered on the card (grid *and* canvas, which used to draw the raw markers) and previewed in
  the dialog once there is markup to show; the sidecar stays plain text; search reads the words
- ✅ One renderer, `Markdown`, outside the note code, for Concepts' documents to reuse; links go
  through `BoardState.openUrl`, replaceable so a test can see a link followed
- ✅ Covered by `MarkdownTest` (the parser) and `NoteLinkTest`, which clicks a link inside a note
  on the real canvas — checked by making the link inert and watching it fail
- ✅ Found on the way: a lone new card was placed two widths off the left of the screen, because
  the placer centred a row of five whatever the count; it now centres over the cards it places

### ✅ F6.2 One level of subgroups
- ✅ `BoardGroup.parentId`, flattened to one level on load (a missing or nested parent is
  dropped, lifting the group rather than losing it); `source` reserved for concept groups and
  shown to round-trip
- ✅ Grid: a subgroup is an indented section under its parent and folds with it · Free: its
  area sits lighter inside the parent's, whose hull takes the child's hull in; dragging the
  parent moves everything, dragging the subgroup only itself
- ✅ A parent counts, draws and selects its subgroups' cards as its own; dissolving a subgroup
  lifts its cards into the parent; deleting a parent lifts its subgroups to the top level; a
  parent holding only a subgroup is not pruned as empty
- ✅ Grouping a selection offers *Inside…* with the group the cards already share under
  pre-picked; *Move into…* / *Make top-level* / *New subgroup…* on a group's menu and the drawer
- ✅ Covered by `SubgroupTest` (fourteen cases) and `SubgroupGridTest` on the real screen; five
  behaviours checked by reverting them — flattening, parent drag, dissolve-into-parent, the
  prune rule and the hull union

### ⬜ F6.3 Frames shaped to the arrangement
- ⬜ A group's frame is the union of its cards' padded boxes, one smooth outline, lobes with a
  bridge when the cards sit apart; recomputed only when a member moves
- ⬜ Hit-testing uses the same path, so what shows is what clicks

### ⬜ F6.4 Custom board background
- ⬜ A wallpaper per board, copied into `_wallpaper/`: cover / tile / centre, dim, blur; behind
  grid sections too; slight parallax on the canvas
- ⬜ Set and removed from the overflow menu, or dropped onto the board with `Alt`

### ⬜ F6.5 Menus that get out of the way
- ⬜ One-line header: name · Boards ▾ · segmented Grid | Free · Search · Contents · ⋯
- ⬜ Once-a-session things move into ⋯; the action bar shows only when something is selected

---

## ⬜ M6 — Live Sketch

Exploration and spec: [docs/LiveSketch-Exploration.md](docs/LiveSketch-Exploration.md);
findings as they come in [LEARNINGS.md](LEARNINGS.md). A page, a pencil, a colour, an XPPen.

### ⬜ F7.1 Pressure probe
- ⬜ Compose Desktop delivers no pen pressure (LEARNINGS L1); probe `WM_POINTER` via JNA on the
  XPPen, then WinTab if needed. Written up whatever the answer.

### ⬜ F7.2 The engine, as a library
- ⬜ `:sketch-engine` module, Kotlin/JVM over Skia, no Compose dependency, testable headless
- ⬜ Input filter (One-Euro, resampling, velocity) · brush model · rasteriser · sketch document

### ⬜ F7.3 The pencil study
- ⬜ Hard / medium / soft as parameter sets over one `(pressure, speed) → (width, alpha)` model;
  stamp rendering against a paper-space grain; eraser
- ⬜ Debug panel with every tunable live; the numbers that survive go into LEARNINGS

### ⬜ F7.4 The screen
- ⬜ New sketch at A4/A5/A3 or W×H px · thin toolbar · colour picker with recents and the
  board's palettes · undo/redo · zoom with the dial · save PNG + `.sketch.json`

### ⬜ F7.5 Into the loop
- ⬜ Save to a board or a concept; open from a session with the reference in the float strip

---

## ⬜ M7 — Concepts

Ideation: [docs/Concepts-Ideation.md](docs/Concepts-Ideation.md). A thing that lives once and
is linked onto many boards.

### ⬜ F8.1 Concepts as folders
- ⬜ `ConceptRegistry`, concept folder and sidecar (id, name, kind, notes, items), the Concepts
  list screen; pictures and notes first

### ⬜ F8.2 Documents
- ⬜ `.md` files in a concept, rendered with the M5 renderer, edited with a live preview

### ⬜ F8.3 Linked onto boards
- ⬜ `BoardFile.concepts` by id; a concept group per link with `source = concept:<id>` —
  non-resolvable, its one action *Unlink*; draw, view, strip, search all work on it
- ⬜ Deleting a concept names the boards it will vanish from, first

### ⬜ F8.4 Sketches and per-board opinions
- ⬜ Live Sketch saves into a concept (needs M6)
- ⬜ Stars and tags on borrowed cards live on the board, keyed by content id

---

## 🔄 M+ — Practice backlog (independent of the board)

### ✅ F+.1 Continuous colour temperature
- ✅ One slider from cool (−1) through neutral (0) to warm (+1), replacing the Warm/Cool presets
- ✅ An adjustment, not a mode: it stacks on any view mode, so a Notan study can still be lit warm
- ✅ Always on screen while drawing; `,` cools, `.` warms, `0` back to neutral
- ✅ The number row is now 1–9 for the nine view modes (Warm/Cool no longer take 7 and 8)
- ✅ A board's session recipe remembers the light; a recipe still naming `WARM`/`COOL` opens at
  ±0.6 rather than silently going neutral
- ✅ Covered by `SessionKeysTest` (a pure `handleSessionShortcut`, since a `KeyEvent` cannot be
  built in a test), `FiltersShaderTest`, and two `BoardStateTest` cases including an old sidecar

---

### ✅ F+.2 Memory drawing
- ✅ `RampStep.studySeconds` turns a ramp leg into memory work: the reference shows for the study
  time, hides for the rest of the pose, and returns at the end to compare against (asked 2026-09-16)
- ✅ A memory pose never auto-advances — the comparison is the point, and one you get no time to
  make is no comparison
- ✅ `H` flips whatever the pose would show: a peek while hidden, cover on an ordinary pose
- ✅ Built-in **From memory** plan (20s/60 · 40s/120 · 80s/240), so a board recipe can name it
- ✅ The menu spells out what the plan does before you start; the session says which beat is running
- ✅ Covered by `MemoryPoseTest` (the beats) and `MemoryVeilTest`, which asks the real screen
  whether the picture is drawn; all five behaviours were checked by reverting them

### ⬜ F+.3 Your drawings come back in
- ⬜ An item gains `attempts: [{path, date, seconds}]`, the files living in `_drawings/`
- ⬜ Overlay compare (your attempt over the reference), mirroring your own drawing, and one
  subject's progression over months
- ⬜ The app has never seen anything actually drawn — all state so far is about the reference.
  The biggest structural gap, and a real data-model change.

### ⬜ F+.4 Staged studies
- ⬜ The filter changes during a pose: Notan for the value masses, Edge for the contour, then
  full. Shares the ramp-phase seam that memory drawing opened.

### ⬜ F+.5 Smaller things on the list
- ⬜ A watched drop folder per board — Krita saves a PNG into it and the card appears
- ⬜ Step through an animation frame by frame (GIF/WebP, already decodable)
- ⬜ Export a board as a portable bundle or a captioned PDF

Reasoning for all of these, and one idea deliberately **not** taken, is in the *Where next*
section of [IDEAS.md](IDEAS.md).

---

## Housekeeping

- ✅ Tidy-up pass (2026-09-07): dead `dismissOpenFailed` removed, two unused imports, one
  `File.samePathAs` for the six hand-spelled case-insensitive folder comparisons, imports in
  place of fully-qualified `java.net`/`java.awt` names; the base compiles without a warning
- ✅ Docs aligned (2026-09-17): features renumbered into the order they were built, the work
  after M3 split out as its own milestone, the open backlog listed here rather than only in
  `IDEAS.md`, and the README's group and large-view shortcuts brought back in step with what
  the code does. `File.isInside` joined `samePathAs` as the one rule for path containment.

- ✅ Restore corrupted `README.md` (`6a56d64`, branch `fix/restore-readme`; merged into
  `feat/idea-board` so the board docs could build on it), both long since on `main`
