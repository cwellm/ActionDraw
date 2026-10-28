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
[LEARNINGS.md](LEARNINGS.md) and the engine's shape in
[docs/Pencil-Engine-Architecture.md](docs/Pencil-Engine-Architecture.md)) ·
[docs/Concepts-Ideation.md](docs/Concepts-Ideation.md) (M7) ·
[CONCEPT.md](CONCEPT.md) (the vision, the atelier design language and the six rooms — M8, M9) ·
[docs/Architecture.md](docs/Architecture.md) (the modules and their rules — M10).

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

## ✅ M5 — Idea Board: handling, second round

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

### ✅ F6.3 Frames shaped to the arrangement
- ✅ A group's frame is the union of its cards' padded boxes (and its subgroups' frames), drawn as
  one Skia path of rounded rectangles; pieces that do not touch get a thin bridge between their
  nearest edges, neighbour to neighbour, so a group in three clusters gets two bridges
- ✅ The same path is hit-tested: a press outside the shape — the empty corner of an L — is not
  the group's and falls through to whatever is under it; inside, a tap selects and a drag moves
- ✅ The geometry (`FrameShape`) is pure and tested on its own; `FrameClickTest` clicks the notch
  of an L on the real canvas and sees the group *not* selected — checked by letting the whole
  bounding box answer again and watching it fail

### ✅ F6.4 Custom board background
- ✅ A wallpaper per board, copied into `_wallpaper/` (one copy at a time — replacing clears the
  old one, removing deletes it); cover / tile / centre, dim, blur; under the grid and the canvas,
  drifting at a third of the camera's pace there; the look survives a change of picture
- ✅ Set from *Wallpaper…* in the header (into the overflow menu with F6.5) or *Use as wallpaper*
  on a picture card, which copies it so the card and the background are then independent
- ✅ The copy is never offered back as a card: recovery by content id skips `_wallpaper/`, which
  matters exactly when a card's own file goes missing and the wallpaper has the same content
- ✅ *Not built:* dropping a picture with `Alt` held. The modifier state during an external
  drag-and-drop is not reliably readable in Compose Desktop, and two working ways to set a
  wallpaper are enough
- ✅ Covered by `WallpaperTest` (nine cases, one on the real screen); the one-copy rule and the
  recovery exclusion were checked by reverting them

### ✅ F6.6 What using it asked for (remarks of 2026-09-21)
- ✅ Right-click → *Remove from ‹group›*: into the parent for a subgroup's card, else the Inbox;
  a group left empty is tidied away
- ✅ **Settings** and **Hotkeys** as menu points in both places — buttons on the start menu, entries
  in the board's ⋯. Inside a board they show only what pertains to the board; the sheets are the
  same components, so the two cannot drift apart
- ✅ A card is added to a group later by dropping it **on one of the group's cards** (the innermost
  group wins; a card already in the group is never re-filed by moving inside its own frame), with
  the frame brightening while a drop would file and a notice once it has; or by right-click →
  *Add to ‹group›*. Dropping anywhere in the frame was too eager: with the generous bands of
  the previous round, a note let go near a group joined it silently — which is what "my note
  moves with the group" turned out to be (2026-09-22)
- ✅ Links are a title that opens on a click; notes come in two kinds — a **document note** shows
  its title and opens in a popup, a **post-it** shows its text as typed in a written hand, sized
  to the text (2026-09-22)
- ✅ Enter in a name field confirms — new group, new board, rename, caption, tags, a link's
  fields, a concept's name and kind: wherever a name is all there is to type (2026-09-21)
- ✅ Hit boxes where the cards and frames are drawn: a card's and a frame's size and layer
  transform now sit on a box *around* its context-menu area. With the transform inside, the menu
  area stayed at the canvas' top-left and swallowed presses meant for whatever was drawn there —
  the board panned instead, every card moving with the one being dragged, and a card drawn in
  that corner could not be clicked at all. `CanvasHitTest`, written red first (2026-09-21)
- ✅ A frame is a hit only within its shape: its layer clips to the frame path, and Compose
  hit-tests a clipping layer by its outline. Before, a frame's whole rectangle counted, so a
  press on another group's frame lying inside it (a concept group overlapping a regular one) went
  to a box that declined it — and nothing below ever saw it; the canvas panned. Third case in
  `CanvasHitTest`, red first (2026-09-21)
- ✅ A group's name tag drags the group, as its frame does — it only *selected* before, so a drag
  from it fell through to the canvas and panned, which is the gesture behind "everything moves
  with the group". And the frame's gesture is keyed on the group, not its shape: keyed on the
  shape, it restarted as soon as the group moved and dropped the rest of the drag. Pinned by a
  label-drag case and by a replica of the user's own board (positions, a turned card, a big
  post-it, the zoomed-out camera), red first (2026-09-21)
- ✅ The real one, found with a pointer log on the user's machine: the frame's and the tag's
  gestures waited for the *touch* slop (some twenty pixels), while the canvas' pan — and the
  cards — use the drag detector whose slop follows the pointer type, a fraction of a pixel for
  a mouse. A real mouse moves a few pixels per event, so the pan took the first move and every
  one after it. Both gestures now use that detector, selecting on the press. The tests dragged in
  two big jumps, on which both handlers fire at once and the child wins; they now drag in
  mouse-sized steps, on which every tag and frame case went red before this fix. The log stays,
  off unless `ACTIONDRAW_POINTER_LOG` is set (2026-09-21)
- ✅ Frames rounder (a wider corner radius, and the union thickened with a round stroke so its
  inner corners soften too), and the space between separated pictures is always covered: the
  connector is the convex hull of the two pieces, a full band, never a thin bridge
- ✅ Snapping is a preference, **off** by default, remembered across runs; switched in ⋯ or in
  Settings
- ✅ `RemarksTest`, `MenuExtrasTest`, `DropIntoGroupTest` (with a real drag on the canvas), and
  cases in `FrameClickTest` and `BoardChromeTest`; six behaviours checked by reverting them

### ✅ F6.5 Menus that get out of the way
- ✅ One-line header: name (click renames) · Boards ▾ · ↑ Parent · segmented Grid | Free ·
  Search · Contents · + ▾ (note, link, group, import, paste) · ⋯
- ✅ Once-a-session things live in ⋯: theme, snap, float strip, wallpaper, contact sheet, session
  recipe, a shortcuts sheet, immersive, close. Tag chips only when there are tags; the action bar
  only while something is selected, and only with what acts on the selection
- ✅ Buttons drop to text weight and chips lose their outline on the board; the practice side's
  controls are untouched
- ✅ Covered by `BoardChromeTest` on the real screen: the action bar's presence follows the
  selection, the segmented control switches layouts, search sets the query, ⋯ closes the board,
  + ▾ opens the note dialog

---

## ✅ M6 — Live Sketch

Exploration and spec: [docs/LiveSketch-Exploration.md](docs/LiveSketch-Exploration.md); the
engine's shape: [docs/Pencil-Engine-Architecture.md](docs/Pencil-Engine-Architecture.md);
findings as they come in [LEARNINGS.md](LEARNINGS.md). A page, a pencil, a colour, an XPPen.

### ✅ F7.1 Pressure probe
- ✅ Compose Desktop delivers no pen pressure (LEARNINGS L1); the Durchstich hooks the window
  natively: `WindowsPointerSource` subclasses the Compose window's procedure through JNA and
  reads `GetPointerPenInfo` on every `WM_POINTER*` — pressure, tilt, rotation, contact, buttons
  — while calling the original procedure, so the pen stays a mouse for everything else
- ✅ **Live Sketch** on the menu opens the probe: live readouts of the last sample and the rate,
  the three leads, a page that draws through the engine with the pressure that arrives (a mouse
  at pressure 1), and a recorder to `~/.actiondraw/pen-samples.csv` (2026-09-21)
- ✅ First contact (2026-09-21) drew — but through the mouse: `WM_POINTER` goes to the window
  under the pen, and Compose's surface is a heavyweight child with a handle of its own, so the
  frame hook saw nothing (LEARNINGS L6). The hook now takes the frame and every child window;
  the Pen panel says when strokes are still arriving as a mouse (2026-09-24)
- ⬜ The numbers (rate, coordinates at 125 %, the pressure curve) with the pen study; WinTab only
  if Windows Ink falls short

### ✅ F7.2 The engine, as a library
- ✅ `:sketch-engine` module, Kotlin/JVM over skiko 0.8.18 (the one Compose ships), no Compose
  dependency, tested headless (`EngineTest`, `SessionTest`: 21 tests reading pixels back)
- ✅ `InputSample` · One-Euro filter (beta raised for a pen: the paper's cursor value lagged a
  fast stroke by twenty pixels) · speed from timestamps · `Lead`/`PencilModel`/`Pencils` ·
  `Brush` · `StrokeBuilder` · `Resampler` (even dabs along a Catmull-Rom spline, a segment
  placed the moment it exists so live and replay agree) · `StampRenderer` · `SketchSurface`
  (a transparent strokes layer over a paper colour, in 256-px tiles: a stroke touches only the
  tiles under it, the screen re-uploads only those, an undo snapshot shares every tile nothing
  drew on) · `SketchDocument` (`.sketch.json`: the page and every stroke's raw samples) ·
  `SketchSession` (live drawing, undo/redo by snapshot plus replay, cancel, load, PNG export)
  (2026-09-22)

### 🔄 F7.3 The pencil study — first pass done
- ✅ Three leads as parameter sets over one `(pressure, speed) → (width, alpha)` model, each with
  an edge softness; dabs at a third of the width, each the paper grain screened with the
  pressure (`g′ = 1 − (1 − g)(1 − p)`: light pressure marks the tops of the tooth, heavy fills
  it) and modulated by the colour; a paper-space value-noise grain, fixed to the page; per-dab
  alpha calibrated so three overlapping dabs reach the point's darkness; the eraser as the same
  dab taking coverage away with a rubber's own model
- ✅ **Tune** on the toolbar: every tunable of the current lead live, with reset; a replayed
  sketch renders with the changed model (the document keeps samples, not pixels)
- ✅ After the first hands-on (2026-09-24): the paper tooth is 1–3 px, not 8–32 (it read as
  stains); a faint tooth on the page on screen; the mouse presses at 0.7, not the lead's
  heaviest; the eraser is **soft** by default — a pass lifts a rubber's share, two or three
  clear a light mark — with a *hard* mode that takes everything; the strength is in the
  document, so a soft pass replays as one
- ✅ **Tilt** (2026-09-24): the side of the lead — the mark stretches along the lean (`tiltWidth`),
  lightens (`tiltAlpha`) and skims the tooth; dabs are spaced by their extent along the stroke,
  so a band is as dark whichever way it is drawn; the tilt is in every sample of the document
- ✅ **Papers** (2026-09-24): smooth, medium, rough — floor, contrast and cell sizes of the
  grain — chosen with a new sketch, changed under the strokes in Tune, in the document by name
- ✅ **Presets** (2026-09-24): a lead as tuned, under a name, in the settings; a chip beside the
  leads applies it, Tune saves and deletes them
- ✅ Four more tools on the same model (2026-09-25): a 0.5 mm mechanical pencil, charcoal, a
  fineliner, a brush pen — a `grain` per tool (0 ink, 1 pencil, 1.5 charcoal) decides how much
  of the tooth the pressure fills; keys `4`–`7`; zoom by `+`/`−` keys of any kind, Ctrl or not,
  for a dial that sends them; the Pen panel shows the last key and wheel event in words
- ⬜ The numbers that survive the pen in hand go into LEARNINGS

### ✅ F7.4 The screen
- ✅ Entering gives an A4 page at once (then the size chosen last); **Sketch ▾ → New…**: A5/A4/A3
  at 150 or 300 dpi, portrait or landscape, or W × H px; white, cream, grey or toned paper,
  smooth, medium or rough · a thin toolbar: title and page size, leads and presets, eraser, size, colour, undo/redo, zoom (click to fit),
  Pen and Tune panels, Sketch menu, Save, Back · a colour picker (saturation/value square, hue
  strip, hex, recent colours) that keeps its hue through greys · a press is a mark (no drag
  threshold; a tap leaves a dot); the wheel zooms about the cursor, Ctrl+wheel sizes, Space+drag
  or the middle button pans · keys `1 2 3 E Ctrl+Z/Y/S/N/O/0 Esc`, and `[ ] + −` by the
  character typed, so they work on a German layout too (2026-09-22)
- ✅ The toolbar wraps (a `FlowRow`): in a 1120-dp window its tail was clipped off, Back and
  all; the dial's zoom setting sends Ctrl+wheel, so the wheel zooms with or without Ctrl and
  Shift+wheel sizes the lead (2026-09-24)
- ✅ **Save** (`Ctrl+S`) and **Save as…**: `<name>.png` beside `<name>.sketch.json`, in
  `~/ActionDraw Sketches` or any folder; a first save is offered "Sketch <date time>", so Ctrl+S
  then Enter is all it takes; another sketch's name is refused, never overwritten · **Open…**
  (`Ctrl+O`): recent sketches, wherever they were saved, and Browse… · the sketch lives while the
  app runs: Back keeps it; only replacing it or closing the app asks about unsaved strokes

### ✅ F7.5 Into the loop
- ✅ **To board…** saves into the board's folder and puts the PNG on the board (the `.sketch.json`
  beside it to continue later); **To concept…** the same into the concept's folder, so every
  board linking it has the sketch; a new name never overwrites (`name (2)`), the same board
  again saves in place, and Ctrl+S afterwards keeps to that file
- ✅ **Continue in Live Sketch** on a sketched picture's card (board) and **Continue sketch** on a
  concept's page open its strokes again; Back returns to the board or the concept
- ✅ **Sketch** beside *Pin* in a session — in the main window and in a board session's own —
  pauses the session and opens Live Sketch with the picture on screen kept in the corner of the
  page (click to enlarge, ✕ to put away); Back returns to the paused session
- ✅ `SketchStateTest` (15, one capturing the screen to check the tiles reach it),
  `SketchCardTest`, engine `SessionTest` (tiles, cancel); a fake `SketchHost`

---

## ✅ M7 — Concepts

Ideation: [docs/Concepts-Ideation.md](docs/Concepts-Ideation.md) (§8 records what was built and
the answers taken to its open questions). A thing that lives once and is linked onto many boards.

### ✅ F8.1 Concepts as folders
- ✅ `ConceptRegistry` (`~/.actiondraw/concepts.json`, id → folder), the concept folder and its
  sidecar `.actiondraw_concept.json` (id, name, kind, notes, items, documents), a concepts home
  beside the boards home; folders found under it are adopted and given an id
- ✅ The Concepts list (tiles by kind, cover, counts, how many boards link it) and the concept's
  page (pictures, notes, links in a grid; documents rendered beside); Esc goes up
- ✅ Pictures copied into `_imported/` by the board's importer; notes and links as on a board
- ✅ Grid | Free on the concept's page: the concept's own arrangement and viewport, kept in its
  file (`layout`, `camera`, each card's `pos`), never borrowed by a board; the board's placement
  rule and card shapes reused (2026-09-21)
- ✅ Dialogs focus their first field on show and dismiss by pointer only — a Space typed into a
  new note once landed on the scrim and closed it; the same fix on the board's dialogs

### ✅ F8.2 Documents
- ✅ `.md` files in `_docs/`, named from their first heading, rendered with the M5 renderer,
  edited in a text box with a live preview; a document whose file vanished is dropped on load

### ✅ F8.3 Linked onto boards
- ✅ `BoardFile.concepts` by id; one group per link, id and `source` both `concept:<id>`, holding
  *borrowed* cards that point at the concept's files by a `concept:<id>/<path>` path — resolved
  through `ConceptSource`, the board's one seam to the concept side
- ✅ Non-resolvable in the state, not just the menus: no rename, recolour, nesting or dissolving;
  borrowed cards cannot be removed, moved out or grouped; *Unlink* is the one action (group menus
  on the header, the canvas frame and the drawer); the group is marked ⧉ and outlined in dashes
- ✅ Reconciled on every open and after a link: new items appear, vanished ones go, picture,
  caption and text follow the concept; the board's place, stars and tags stay; a renamed concept
  renames its groups
- ✅ **Concepts ▾** in the board header (link one, jump to a linked one, all concepts); **Boards…**
  on the concept's page with a tick per board, working on closed boards too, from disk
- ✅ Dropping the board's own card on a concept group (or *Move to ⧉ …*) adds it to the concept;
  the borrowed card takes the board's card's place
- ✅ Deleting a concept names the boards it is linked on and takes it off them first
- ✅ Covered by `ConceptTest` (14) and `ConceptLinkTest` (15); eleven guards checked by breaking them

### ✅ F8.4 Sketches and per-board opinions
- ✅ Live Sketch saves into a concept (with M6; through the domain's `ConceptSource` since F11.12)
- ✅ Stars, tags and place of a borrowed card live on the board (matched by the card's id; the
  content id is carried along for recovery)
- ✅ Practice memory of a borrowed card is the board's, keyed the way a session started there
  writes it — one folder per session is what the practice core knows; a concept-owned memory
  would need the session to write to several folders (open, see the ideation §8)

---

## ✅ M8 — The atelier: the design realised

The design recap of 2026-10-05: [CONCEPT.md](CONCEPT.md) and its design canvas. A graphite table,
real paper, one lamp, six pigments sprinkled rather than poured, a porcelain palette as the home
screen, and motion that is wet, weighty and drawn. The app changes how it looks and moves, not what
it does — every load-bearing rule from M1–M7 holds (CONCEPT.md, *What stays true*).

### ✅ F9.1 Tokens and type
- ✅ `ui/Atelier.kt`: one table of tokens — ground, board, raised, line, text, muted; paper, paper
  shade, ink, quiet ink; six pigments with *mass*, *glow* and the text that goes on the mass —
  replacing `ActionDrawColors`; Material's `Colors` derived from it (`AtelierColors`) and every
  window wrapped in one `AtelierTheme`, so every screen changed colour before anything else moved
- ✅ A `Room` enum (Practice, Lens, Boards, Concepts, Sketch, Collage) carrying its pigment; every
  `Screen` names its room (the menu none: it is the palette)
- ✅ Bricolage Grotesque and Caveat bundled with their OFL licences — as **static cuts**: Compose
  Desktop 1.7 cannot set a variable font's axes, so `art/fonts/make-instances.py` cuts text
  weights 400–700 (optical size 14), one display cut (800, optical size 48) and Caveat 500/700
  from the variable sources kept beside it. Each cut is renamed and flagged as the static font it
  is: with the variable font's names left in, Windows (DirectWrite) reported Caveat as weight 1
  and Bricolage Regular as 800. `AtelierType.typography` (display cut for h1–h4) and a `Hand`
  style for lettered things
- ✅ `AtelierTest`: every text-on-ground pair the design uses ≥ 4.5:1, Material's palettes
  included, and every font cut loads with the weight it is filed under. Seen red for real twice:
  paper text on viridian reached 4.17:1 (the mass is now `#1A6F60`, a shade deeper than the
  canvas), and the font weights above
- ✅ `Themes.paperColors` and the card backing taken from the paper tokens; the plain board's
  link cards, the strip's ground and the frame tags' ink from the table's
- ✅ The last hard-coded accents moved onto pigments: a board's alignment guides in pale
  ultramarine (the room's own colour for what helps arrange), the star in Naples yellow, the
  practice badges in Practice's cadmium (*redo*) and quiet ink (*drawn*)

### ✅ F9.2 Materials in code
- ✅ `ui/Materials.kt`: a grain over the ground — one SkSL tile of per-pixel noise in two-pixel
  clumps, light and dark at most 6 % strong, made once and drawn as a repeating shader
  (`Modifier.grain()`); no per-frame `ImageBitmap`
- ✅ Cards and sheets get their tooth from `sketch-engine`'s `Paper`, so the board's paper and the
  sketch's paper are the same paper: `Modifier.paperTooth()` draws the engine's grain over a
  sheet's colour — prints, slips, ruled sheets and index cards on boards and concepts, the board
  list's covers, the concept folios (kraft, on rough paper), the exercise cards, the summary's
  prints, the glass's print; Sketch's swatches were already drawn by it (F9.8). `BoardDressTest`
  holds paper to having its tooth and the table to having none
- ✅ One lamp: a `Lift` scale (resting, lifted, held) and `drawLampShadow`/`Modifier.lampShadow`,
  a blurred shadow cast to the bottom right, for everything that lies on the table or over it:
  cards and prints resting, the drawer, the fold-outs and the large reference lifted, every
  dialog held. Material's elevation is gone from the app; `OneLampTest` reads the sources and
  fails on any `elevation =` above zero or `Modifier.shadow`, so a new screen cannot bring a
  second light into the room (seen red with the drawer's old elevation put back)
- ✅ Small `DrawScope` pieces: pigment dab (a seeded blob, wet with the lamp's highlight or dry and
  pale), porcelain well (in shadow along its top), masking tape (torn ends), push-pin, a lacquered
  pencil. `MaterialsTest` (7) draws each into a bitmap and reads the pixels back

### ✅ F9.3 Home: the palette
- ✅ `PaletteScreen` is the new `Screen.Palette`, where the app starts: a porcelain disk under the
  lamp, six wells at 60° steps, each room's name outside the rim with its verb lettered in the
  room's glow, the centre well with the chosen room's one line and its next step; a pencil lies on
  the table. The old start menu stays as `Screen.Menu`, now the Practice room
- ✅ A click chooses a well, a second click or the centre's button opens it; the arrows and Tab turn
  the palette, Enter opens; the chosen dab is wet, with a ripple; the well is remembered in the
  settings (`lastRoom`). The wells take no focus from a pointer — taken on the press, the focus
  chose the well before the release opened it, so **one click opened every room** (the UI test
  caught it)
- ✅ Each room's next step: Practice → the session setup (the exercise screen comes with F9.6);
  Boards → the board opened last, else the list (a board that cannot be read lands on the list,
  which says so); Concepts → the list; Sketch → the open sketch, else a new page. Lens and Collage
  are **dry wells**: matte, lettered *coming*, not choosable, skipped by the keys
- ✅ The room buttons left the start menu; Settings and Hotkeys sit in the palette's top right
  corner as quiet links, and the hotkeys sheet has a palette section
- ✅ `Esc` from the top of every room ends on the palette: Practice and the board list through one
  pure `escapeToPalette`, the others through their own Esc, whose last step now leads home;
  leaving a board, the concept list or a sketch begun at home all land there. A sketch made
  from a pose goes back to the pose, never home over it
- ✅ `PaletteTest` (6): the start and the remembered well, every room's next step, Esc from every
  room's top, a sketch from a pose, the keys passing the dry wells, and the palette on screen
  (click, second click, centre button, arrows, Enter). Four guards broken on purpose and seen red

### ✅ F9.4 Motion
- ✅ `ui/Motion.kt`: the tokens (durations, easings), and **reduced motion** — a box in Settings,
  following Windows' *Show animations* (`SPI_GETCLIENTAREAANIMATION`, read through JNA) until it
  is set; reduced, the bloom is a 120 ms wash, nothing ripples, chrome fades in 120 ms
- ✅ **Bloom** — a room opens under its pigment: an SkSL disk spreading from the well's place on
  screen, its edge wandering with the direction, darker in a drying rim, a little granulated;
  the room is opened beneath once it covers the palette (55 %), then the pigment thins away onto
  the room's 2 px line. Home from any room drains it back into the well. The design's
  ease-out (0.2, 0.8, 0.2, 1) covered the screen in 80 ms — a flash, seen in a capture — so the
  spread starts slower (0.35, 0, 0.25, 1). `MotionTest`: the timelines, and the shader read back
  (pigment inside, nothing outside, an edge that is not a circle, a darker rim)
- ✅ **Settle** — a dragged card lifts (120 ms, 3 % larger, the lamp's shadow growing), leans into
  its sideways speed (at most 4°) and lands on a spring (damping 0.7). Only what is drawn moves:
  the lift sits on a layer *inside* the box that takes the drag, so the drag's arithmetic — which
  already undoes the card's own rotation — never meets the lean, and the hit box stays where M5
  put it (around the `ContextMenuArea`). A real mouse drag in 3-px steps ends exactly where the
  pointer went; a lean on the dragging box itself was tried as a mutation and caught. Frames move
  without it for now
- ✅ **Draw-on** — a group made from a selection has its frame drawn round its cards once
  (360 ms, the outline walked contour by contour), its fill and then its tag fading in after; every
  other frame is simply there. The hand's wobble is baked into the frame's one path
  (`handDrawn`: ±1.4 board px along the normal, a smooth noise of the distance walked in board
  units, so it does not swim when zooming, closing where it began) — drawn, clipped to and
  hit-tested, so all 270 board tests, `CanvasHitTest` among them, pass on the wobbly frames.
  The drawing starts at the path's start, not yet where the pointer let go
- ✅ **Recede** — `RecedingChrome` and `PointerWatch` (watches the pointer, takes nothing): Sketch's
  header, tray and paper step back 1.5 s into a stroke (not while Pen or Tune is open) and stay
  back until the pointer comes up to any of them; a session's controls step back while a pose
  runs and the mouse rests, and come back near the bottom edge or on a pause. In a pose the
  **picture takes the controls' room** as they fade and gives it back as they return — measured
  again in layout, nothing recomposed (`MotionTest`: the whole height the picture's, then exactly
  the bar's room given back, the paused bar being taller). In Sketch the **page keeps still** on
  purpose: the chrome steps back mid-stroke, and a page that moved then would move the line under
  the pen
- ✅ A guard for stroke latency: the chrome recomposes on its own, never the page, and
  `SketchSurface.snapshotsTaken` counts tile pictures made for the screen — none while the
  toolbar fades during a live stroke (`SketchStateTest`). Four guards broken on purpose and seen
  red (chrome that never recedes, a toolbar that never returns, a pause that does not bring the
  controls back, a watercolour without its rim)

### ✅ F9.5 Room chrome and hand-offs
- ✅ `ui/RoomChrome.kt`: `RoomHeader` (back to the palette, the room's dab and name, slots for a
  title and actions) heads the Practice room, the board list, the concept list and Sketch; a
  board and a concept keep their own header, whose title is their name, with the palette's
  `HomeButton` and the room's dab at its start — on the lists in place of their *Back*, which
  went to the same place — and the app shell lays the room's 2 px pigment line along the top of
  every room but a running pose
- ✅ `CrossRoomButton(room, label)`: the target room's dab before the label, lettered in the
  surface's own ink (light on the table, dark on cork). Every hand-off is on it: Sketch's *To a
  board* and *To a concept*; on a board every *Draw* — the selection's, a group's, a smart
  section's — with cadmium's dab, since drawing is Practice's; a session's *Pin* and *Sketch*;
  the summary's *Pin flagged*; a concept's *Boards…*. *Continue in Live Sketch* is the ring's
  *Sketch*, and on a concept's picture a chip with madder's dab
- ✅ The **ring menu** on a board (`board/ui/RingMenu.kt`): a right click on a card opens six
  porcelain wells round it, each with its room's pigment — **Draw these** (Practice), **Sketch
  over** (Live Sketch with the picture beside the page, through a new `BoardHost.sketchOver`; a
  picture Live Sketch made is continued instead), **To a collage** (a dry well, *coming*), **To a
  concept** (which one; linked first if it is not yet — `selectionToConcept`), **Tag**,
  **Group** — and *More…* in the middle for the full menu with every other command. It is a
  `ContextMenuRepresentation`, so `ContextMenuArea` still decides when it opens and what the full
  menu holds, and nothing else on the board changed. Arrows and Tab go round, Enter takes, Esc
  closes. `RingMenuTest` (5); five guards broken on purpose and seen red
- ✅ Lettered names: group tags, post-it notes and the ramp's legs in the `Hand` style (Caveat),
  board and concept names on the lists too

### ✅ F9.6 Practice, re-dressed
- ✅ `PracticeScreen` (the old `MenuScreen`, gone): the exercises as index cards on the table, each
  with an ink drawing from the design — Action drawing, Lens studies, From memory, and Flicker,
  Staged and Compare as cards lettered *coming* that cannot be chosen; the chosen card lifted and
  taped in cadmium. The exercise is not stored: it is what the settings amount to (a plan that
  hides the picture is memory work, a lens to start with a lens study), and choosing a card sets
  just that (`Exercises.kt`)
- ✅ The timing panel: plan chips, the ramp drawn as graphite bars (one per pose, height by its
  length on a log scale, the first in cadmium, each leg lettered), total poses and time, a
  starting lens for lens studies, auto-advance, and Start with the session's length. *Drawing
  from* — folder, unseen count, Change…, Choose pictures… — sits in the room's header
- ✅ The **ensō timer** over the picture's top right corner: a brush arc that closes as the pose runs
  out, a dry-brush line where it began, the time and the pose inside, the ramp's poses as ticks
  under it, a memory pose's beat under those; faint while the controls are back. The first ensō
  animated its sweep and drew nothing at all — a capture showed the empty ring; it now moves on
  with each second, and a test counts its brush pixels
- ✅ The **lens tray**: nine glass slides, each showing the picture on screen through its own lens,
  keys 1–9 under them, the chosen one risen; the adjustments as round knobs with their keys
  (blur, mirror, flip, invert, shards, and grid turning through its kinds); the lens's own
  settings under the tray rather than popping up over it
- ✅ The summary in the same language, without its emoji: the pictures flagged for another go as
  prints ringed in pencil, ready to pin to a board. *A strip of every pose drawn* is not there:
  the session does not keep which pictures it showed
- ✅ **Fixed on the way — redo flags were lost.** Moving on from a pose (or stopping) marked it
  drawn, and drawing a flagged picture cleared its flag — including the flag just set with R, so
  no flag ever reached the next session or the summary's *Pin flagged*. A flag now clears only
  when its picture came into the session flagged (`AppStateTest`, both cases seen red first)
- ✅ `PracticeTest` (7); three more guards broken on purpose and seen red

### ✅ F9.7 Boards, re-dressed
- ✅ Pictures as **prints** on every surface — the picture on paper with a 6 dp border (on the
  plain board too, where it sat on black), in the grid and on the free canvas; on the canvas a
  print is held by an ultramarine **pin**, a sketch that can be continued by **tape**, which is how
  the two are told apart at a glance. A single selected card shows its rotate handle where the pin
  was rather than over it. Post-its are lettered in Caveat
- ✅ Notes are paper on every surface: a post-it a **yellow slip**, a document a **ruled sheet**,
  a colour chosen for a note that paper whole (the plain board drew both dark olive); link cards
  are **index cards**, a red line at the top, faint blue rules, held by a drawn **paperclip** (the
  🔗 emoji gone) or by their preview picture. A card borrowed from a concept wears the concept's
  **viridian tab**, its name lettered, half over the card's top edge, in the grid and on the
  canvas. Their shadows are the lamp's (`Lift.RESTING`), not Material's elevation
- ✅ Group frames drawn in **graphite** (the group's colour only while it is about to take a card),
  their tag a paper label pinned with the group's colour — viridian for a concept — the name
  lettered, the count beside it; the selection marked in ultramarine. The selected card is not
  lifted: lifting is what a held card does (F9.4)
- ✅ Cork and papyrus stay; **plain** is the graphite table, with the table's grain under the cards
- ✅ The board list: covers as prints under the lamp, names lettered; the concept list as **folios**
  — kraft, a viridian tab with the kind, the cover as a print, the name lettered (ink on kraft
  7.6:1, the counts 4.8:1). Both under the room's header. The covers stand on a **shelf**: a
  wooden plank under each, reaching into the gaps, so a row of boards reads as one shelf
- ✅ `BoardDressTest` (2) reads the pixels at a card's top: a pin on a print and none on a sketch,
  tape the other way round, the pin gone under a single selection's handle and back when two are
  selected. Three guards broken on purpose and seen red. The test lays the canvas on the table
  colour: with nothing behind it, the test window kept the last frame's pixels where nothing
  drew, and a removed pin seemed to linger

### ✅ F9.8 Sketch, re-dressed
- ✅ The **pencil tray** on the page's left: the seven tools and the rubber drawn as themselves (three
  lacquered pencils, a mechanical one, a charcoal stick, a fineliner, a brush, a rubber in its
  sleeve), each with its key, the one in hand slid out and underlined in madder. Under it the
  size (a slider), the colour, the presets, and **Pen** and **Tune**, which fold out from there
  over the page's near edge instead of pushing the page down
- ✅ The **paper** on the page's right: the three papers as swatches drawn by the engine's own grain,
  the four shades as chips — the shade, like the tooth, can now be changed under the strokes
  (`setShade`; the next page comes on it too). The reference from a session lies under the paper,
  larger over the page on a click. *Show it underneath* from the design is a feature, M9
- ✅ The page on the graphite table under the lamp's shadow, **taped at its corners**. The shadow and
  the tape are their own layers reading only the view, so a stroke redraws neither
- ✅ A **status line** under the page: a dot and a line for the pen (samples arriving, the mouse
  standing in for it, or how it is hooked), the page's size, and the zoom, which fits on a click
- ✅ The header is the room's: home, the madder dab, the sketch's name; Undo, Redo, *File ▾*
  (New, Open, Save as), Save, *To a board*, *To a concept*, Back. While a stroke goes on, the
  header, the tray and the paper all step back (`ChromeEdge` has left and right now); coming up
  to any one brings them all back
- ✅ `SketchStateTest` (+3, and the recede test extended): the shade under the strokes, the tray and the shade chips on screen,
  the tape off each corner, the side chrome stepping back with the header. Five guards broken
  on purpose and seen red

### ✅ F9.9 Wrap-up
- ✅ `RoomScreenshotTest`: fourteen screens through the whole app shell at the window's starting
  size — the palette (and with a well chosen), the Practice room, a running pose, the glass in
  hand and set down, the summary, a board free and in a cork grid, the board list, a concept,
  the concept list, Sketch at rest and mid-stroke — saved to `build/screenshots` to be looked at,
  and each held to two things: no label cut off at the bottom or losing lines without a "…", and
  the room's line along the top. Pixel goldens left out on purpose: a font or Skia update would
  fail every one with nothing wrong
- ✅ Looked at: the grid's group headings were lettered in the group's colour, and teal vanished
  into cork; now in the surface's own ink, the dot and the hairline carrying the colour
- ✅ README and the Hotkeys sheet: the palette and Esc home, the rooms as re-dressed (prints, pins
  and tape, folios, the pencil tray, the paper), the glass and its keys, Esc's order in a pose.
  The ring menu is documented when it is built (F9.5)
- ✅ The concept page's cards in the same language: pictures as prints (chosen, ringed in
  viridian), notes as slips and sheets, links as index cards; *Continue sketch* carries
  madder's dab
- ✅ A hands-on round with the XPPen: does the table feel like a table, and does anything stand
  between the hand and the page — tried by hand on 2026-10-17: the pen draws and the dial's zoom
  works, as it should


### ✅ F9.10 The design, to the letter
What the design canvas shows and the first pass left out, gathered by laying the archived boards
(`docs/design/previews`) beside the app on 2026-10-16.

- ✅ **A pose's chrome as designed** — the picture gets the room: *Stop · Esc* and what is
  running (*Classic gesture · Drachenbuch*) at the top left with *Auto-advance* and *Fullscreen*
  as quiet switches; under the ensō ‹ › and a column of round buttons — Redo, Hide, Sketch
  (madder's dab), Pin (ultramarine's); one tray along the bottom with the lens slides and the
  knobs — Light, Blur, Mirror, Flip, Invert, Shards, Grid and the Glass. The settings of what was
  just chosen open in a popover right above its control: a lens's, the light's, blur's, shards',
  the glass's optics and magnification; clicking the chosen slide again or × puts them away. A
  click on the ensō pauses; paused, the time per picture is offered under a plain *Paused*. The
  transport buttons and the line of key hints are gone (the keys and the Hotkeys sheet stay).
  Words lying over the picture get a dark wash, so they read on any picture. Caught on the way:
  the popover stretched across the whole window (a weighted spacer in its title row) and took the
  clicks meant for the picture under it. `SessionChromeTest` (4); four guards broken on purpose
  and seen red
- ✅ **The board's header**: *Boards ▾ / parent / Drachenbuch* as a breadcrumb after home and the
  room's dab, the search in the middle (*Search captions, tags, notes*), the surface chosen by a
  swatch of cork, papyrus and the graphite table itself (the theme row left the ⋯ menu), then
  *Concepts ▾*, *+*, *⋯* and *Draw from this board* — every picture on it, its own and its
  concepts'. Grid | Free and, on the free canvas, − 100 % + (the percentage fits every card) sit
  in the bottom left corner; *Contents* in the bottom right. Still one line at 1024 px
- ✅ **A palette as a card**: *Pin to the board* in the palette dialog puts the colours read off a
  picture on the board as a taped card of swatches beside it, lettered *colours of ‹picture›* — a
  note of a new kind (`palette`: its caption, then the colours as `#rrggbb`), which an older app
  shows as a document note titled by the caption
- ✅ **A natural tilt**: a card is put down on the free canvas a degree or two askew, the same for
  the same card (`naturalTilt`, from its id) — its stored rotation, so the drawn card stays the
  clicked card and the rotate handle takes it on from there; existing cards keep theirs, and a
  concept's free layout, which places its cards the same way, lays them down askew too. A group's
  tag is pinned askew too, turned in its layer, which turns its hit area with it. All 290 board
  tests, the canvas's hit tests among them, pass on tilted cards. `BoardChromeTest` (+2),
  `BoardDressTest` (+2); five guards broken on purpose and seen red. The screenshot test caught
  the new search placeholder wrapping in its field
- ✅ **A board as Practice's source**: *Drawing from* is one menu — a folder, or any board, with
  ultramarine's dot. A board gives its own pictures and its linked concepts' (`picturesOf`, read
  from its file without opening it); seen and redo live in its folder, as for a session started on
  the board; it is remembered for the next start and taken up again once the board side has handed
  the boards over (`AppState.PracticeBoards`). **Start with a lens** for every exercise, *None*
  first: a lens chosen makes it a lens study, as the exercise is what the settings amount to
- ✅ **Sketch**: *Shade with the side (tilt)* — off, a leaning pen draws upright (remembered);
  *Preset* as a dropdown, *None* being the tool as it comes; undo and redo as round icons; and the
  session's reference on the page itself, **underneath** — between the paper and the graphite,
  the tiles being clear where nothing is drawn — at a strength, or **laid over** the strokes
  instead to check them against it. On screen only: a saved picture is still the strokes on clean
  paper. F10.5's first part (the picture underneath from a board, a concept or a collage remains)
- ✅ **Motion**: a new group's frame is begun at the point of its outline nearest to where the
  pointer last let go — the canvas notes every release without taking it — and walked round
  from there; a held card leaves its footprint marked, dashed, just outside it, where it will land
- ✅ **Graphite guides**: the snapping guides and the landing mark in graphite — dark on cork and
  papyrus, pencil-light on the graphite table — as the materials board has them
- ✅ `PracticeTest` (+3), `SketchStateTest` (+3), `SettleDrawOnTest` (+3); seven guards broken on
  purpose and seen red. The tilt reached the concept page's free layout too, which a concept test
  noticed

---

## 🔄 M9 — Shaping the features

What the vision names and the app does not do yet, or does only halfway. Feature level on purpose;
tasks are broken out when M9 becomes active. Shapes: [CONCEPT.md](CONCEPT.md) (*The rooms, one by
one*, *How the rooms hand things over*) and the design canvas.

### ✅ F10.1 Lens — filters without a session
- ✅ The Lens room, `:lib:feature:lens`, in Payne's grey: a picture from a file, the clipboard, a
  board's card (*Check the values* on its ring and in its menu) or a sketch's page (*Check the
  values* in Sketch's header), seen through the lens tray — all nine view modes, every adjustment,
  the grids, the glass — with no clock running. Back returns to the board or the sketch it came
  from; with nothing open, the room invites a picture in; the palette's well opens it, or says what
  it is looking at. From the current collage once there is one (F10.6)
- ✅ Side by side: alone, beside the original, or two lenses at once — the second starts as Edge
  (Notan, where the first is Edge) until it is turned; a click on a side's name, or Tab, chooses
  the lens the tray and the keys work
- ✅ Save the view as a PNG where the person chooses, or pin it to a board beside its original —
  both pinned at once, so they land together. The view is drawn off screen by the same `LensView`
  the screen draws with (`lensedPng`, through an `ImageComposeScene`), whole and at the picture's
  own size; the lens's sizes in pixels and the blur grow with it, so it looks as it did on screen,
  and the grid and the glass stay behind. New in the domain: `Pictures.writePng`
- ✅ The lens kit, `:lib:lens`, below the rooms: `Filters.kt` and the shaders as they were, the
  glass, the grids; out of the session the tray and the settings above it (`LensControls`, for the
  session and the room alike), the picture (`LensView`) and the lens's keys (`handleLensKey`,
  `LensHotkeys`) — all over one `LensState`, so tray and keys are one code path. The tray and
  Material's controls are lit in the pigment of the room they lie in; a memory pose's veil stands
  in the picture's place through the view (`veil`), and Esc asks the lens first
  (`LensState.stepBack`)
- ✅ The session keeps its own keys and hands the rest to the lens's; a board's setup is the
  session's lens and comes back out of it
- ✅ Tests: `LensKeysTest`, `LensStateTest`, `LensViewTest`, `LensExportTest` (the picture's own
  size, a pixelate's blocks grown with it, the grid and the glass left out), `LensRoomStateTest`,
  `LensRoomKeysTest`, `LensRoomScreenTest` (opened, laid out, saved and pinned for real),
  `RoomsTest` (a card and a sketch into the room and Back out again, a view pinned), the ring's new
  button, and the room in `RoomScreenshotTest`. Broken on purpose and seen red after a no-op
  control stayed green: in the kit, 17 guards (the session handing its other keys to the lens, Esc
  asking the lens first, the keys 9 and G, stepping back from the glass, the picture through its
  lens, invert, the veil keeping the picture and the glass out, the tray's pigment, a board's setup
  in and out of the session's lens, the menu's starting lens and the lens exercise, the session's
  tray, picture and memory veil on the session's own lens); in the room, 29 guards (the view saved at the screen's pixel sizes and without the grid, the glass kept and taken up again, the room's tray and Material's controls in its own pigment, the view drawn and the original beside it, where a view is saved and what is pinned, two lenses different, the tray on the lens chosen, a pasted picture, Tab, Esc, the lens keys only with a picture, a card's and a sketch's way in, Back, the pin through the app, the palette's well, the session's glass, a PNG written beside another, the optic kept); and
  the build refuses the kit the Practice room, the default design and the files
- ✅ The header in a narrow window (2026-10-28): every action out, all of them in one row, needed
  960 dp — at the width the app opens, the picture's name was squeezed to nothing, and at 720 dp the
  labels were cut. Now the header measures, rather than guesses, which of four arrangements fits
  (`FirstThatFits`), keeping 120 dp for the picture's name: all out; as wide as the app opens,
  *Open ▾* (open, paste); narrower, *Picture ▾* (open, paste, save, pin to each board) and the
  layout's menu (*Two lenses ▾*); narrowest, everything under *More ▾* but Back. With more ways on
  than fit — *Sketch over* and *To a collage*, once the Collage room took pictures (F10.6) — they
  fold under *Send ▾* first, before anything else does. The layouts' chips
  read *Alone · Beside · Two lenses*. The arrangements only measured are kept out of the
  semantics, so what a test or a screen reader finds is what is shown. Tested in
  `LensRoomScreenTest` at 1400, 1120, 1000, 720 and 480 dp — each action reached through its menu,
  the way back inside the window, the name keeping its room — and in `RoomScreenshotTest` at
  720 dp. Broken on purpose and seen red after a no-op control stayed green: 11 guards — the header never folding, no room kept for the name, the arrangements only measured found too, the semantics not following the fold, no fold at the width the app opens, no More at the narrowest; Open, a pin, the layout and the way on under their menus doing nothing, and the layout's menu named for none

### ⬜ F10.2 Flicker (Wechselzeichnung)
- Two or more pictures trade places at an interval (1 s by default, 0.25–5 s), as a hard cut or a
  short cross-fade
- From a board selection of two or more, or as a kind of ramp leg, so a plan can mix flicker with
  ordinary poses
- Variants: a picture and its mirror (asymmetry jumps out), a picture and its notan

### ⬜ F10.3 Staged studies — shaped from F+.4
- The lens changes during the pose — notan for the masses, edge for the contour, then the full
  picture — each stage a share of the pose
- On the ramp-phase seam memory drawing opened (`studySeconds` becomes a list of phases)
- The ensō shows the stages as gaps in its circle

### ⬜ F10.4 Compare — your drawings come back in, shaped from F+.3
- `attempts: [{path, date, seconds}]` on an item, the files in `_drawings/`; a sketch saved from a
  session becomes an attempt by itself
- The attempt at 50 % over the reference (or the reference over it), aligned by two points; your
  drawing mirrored; a flicker between the two (F10.2)
- One subject's attempts over months as a row of prints

### 🔄 F10.5 Sketch — the picture underneath
- 🔄 Any picture under the page (from a session, a card, a concept, a collage, a file) at a chosen
  strength, moved, scaled and turned to fit, never part of the saved PNG — a session's reference
  can lie under the page, fitted whole, at a strength (F9.10); a collage comes into Sketch as its
  reference with *Sketch over* (F10.6); the other sources, and moving, scaling and turning it,
  remain
- ✅ *Lay it over instead*: the reference over the strokes, for checking at the end (F9.10)
- ⬜ In `.sketch.json` by path and placement, so a continued sketch has it again; the reference
  beside the page stays as the other option

### ✅ F10.6 Collage — scissors on a cutting mat
Built on `dev/concept-feature` in steps (2026-10-28).
- ✅ **The file and the room.** `name.collage.json`, its pieces from the bottom of the stack up,
  each with the picture it is cut from — relative to the collage's folder where it lies inside it,
  absolute where it lies on a board or in a concept — its outline in that picture's pixels, its
  middle on the mat, its turn, scale and flip (`CollageJson` in the core's model). In the domain
  `Collage`, `Piece`, `Spot` and the contract `Collages` — the recent ones, read, write, a name no
  other collage there has, a rename that takes the picture along, opened — kept by `FileCollages`
  whole or not at all (`SafeFile`); the collages home beside the boards (`ActionDraw Collages`).
  New in `Pictures`: `sizeOf`, a picture's size from its header, so a big photo laid on the mat is
  not decoded on the UI thread for it. The room, `:lib:feature:collage`: `CollageState` opens the
  collage kept last on coming in, makes a new one's document with its first piece, keeps every
  change at once and undoes any of them; the name is renamed in the header, *Collage ▾* starts a
  new one and opens the recent ones again. It is a receiver of the hand-off seam, so *To a
  collage* is on every board card and its ring, a concept's picture, a session's pose and the
  headers of Sketch and the Lens at once; a page that is no file — a sketch's, a Lens view — is
  written beside the collage and laid on it. The palette's well is wet: *New collage*, or
  *Continue* the one kept last
- ✅ **The mat and the pieces.** A green cutting mat, 1900 × 1300 units, with a grid, a ruled one
  and a ruler along its top; each piece its picture clipped to its outline and lifted off the mat
  in the lamp's shadow. The outline drawn is the outline a click hits — the board's rule again
  (`Pieces.kt`: a piece's place, turn, scale and flip about its middle, and back). *Move*: a press
  takes the chosen piece's handle or the topmost piece under it, a drag moves it, its round handle
  turns it (Shift: in 15° steps), its corner grows it — one change a drag; a press on the bare mat
  lets go. The rail: *Move*, *Flip*, *Forward*, *Backward*. The pieces list on the right, the top
  of the stack first: a click chooses, a double click renames, a drag up or down restacks, × takes
  away. Keys: the arrows (Shift: further), Q and E turn, F flips, PgUp/PgDn or ] and [ restack,
  Del, Ctrl+Z/Y, Ctrl+N/O/V, Esc lets go and then leaves. The pictures are decoded off the UI thread
  and drawn from no more than 2048 pixels
- ✅ Tested: `FileCollagesTest` (kept and read back as laid out, its pictures where they are, a free
  name, renamed with its picture, a broken document), `PiecesTest`, `CollageStateTest` (a picture in
  whole, fitted, apart from the last, kept from the first piece; from a file, the clipboard, a
  paste; handed here onto the collage kept last; a drag one change; nudged, turned, flipped,
  restacked, renamed, taken away and undone; the topmost under a click; renamed, new and opened
  again; coming in and Esc), `CollageKeysTest`, `CollageScreenTest` (the picture's halves where it
  lies and swapped when flipped; dragged, turned by its handle, grown by its corner; the bare mat
  letting go; the list, the rail and the header), `RoomsTest` (a card and a sketch's page onto the
  collage, Back to where they came from, the host's dialogs and clipboard), `RingMenuTest`,
  `PaletteTest` and the room in `RoomScreenshotTest`. Broken on purpose and seen red after a no-op
  control stayed green: 40 guards — a picture beside the collage kept absolute, a relative one read back from nowhere, a new collage named over another, renamed without its picture or over a name that is taken, one kept elsewhere not among the recent; a piece's flip forgotten on the mat, back from the mat turned the wrong way, a click hitting a piece's box rather than its cut, every crossing counted as inside; nothing kept as it changes, a drag not one change to undo, a click taking the piece underneath, a picture laid as large as itself or over the last, a picture handed here onto a new collage, Esc leaving with a piece chosen, a collage opened with the last one's undo, restacking past the top, a paste never laid on the mat; a drag, the round handle or the corner doing nothing, a press on the bare mat keeping the piece, a piece drawn as plain paper or unflipped; the list choosing, renaming or taking away nothing, the rail's Forward, the header's rename, the menu and the invitation doing nothing; Q turning the wrong way, PgUp doing nothing; the Collage room taking no pictures or without the host's dialogs, its well opening nothing; and the Lens's ways on never folding under Send. One I tried was no defect: counting a ray's crossings to the left instead of the right gives the same answer
- ✅ **Scissors.** The tools on the rail: *Move*, *Scissors*, *Tear*, *Tape* (M, S, R, T), one in
  hand at a time, the mat saying in the hand of the design what the one in hand does. A drag on its
  own cuts freehand as it lets go, Shift straight; clicks lay corners, a click on the first cuts all
  the way round, on the last — or Enter — ends the cut there; Esc drops it. The path is dashed in
  Naples yellow and marches (not with reduced motion), the next corner's line follows the pointer,
  and a pair of scissors goes with it. A cut is paper's (`Cuts.kt`, in plain Kotlin and doubles):
  from edge to edge it parts the piece, as often as it goes through; round a part inside, it cuts
  it out and leaves a hole, the scissors coming in from the nearest edge so what is left is still
  one outline — and that way in parts too when it is cut across later. The chosen piece is cut, else
  the topmost picture the cut goes through; tape is not cut. Every part lies where it lay, the
  largest keeps the piece's name and place in the stack, the others lie just above it as
  *‹name›, cut* (*cut 2*, …), and the part cut off — or out — is chosen. One change to undo
- ✅ **Tear and tape.** *Tear* parts a piece the same way along a drag made ragged — a long wander,
  a shorter one and a fine fray, a few mat units either side — as *‹name›, torn*. *Tape* lays a
  strip of the design's masking tape, torn at its ends, along a drag (Shift: in steps of 15°), or a
  short one with a click; it lies over everything and holds the pictures it lies across, kept in
  the file (`tape`, `holds`). What is held moves as one: moved, turned, grown and flipped about the
  piece taken hold of, by hand, by the keys and by the rail; a cut piece is held by whichever of its
  parts lie under the tape, and a piece taken away is held no more. *Flip* turns a piece over as a
  sheet on the table is: one turned one way lies turned the other
- ✅ Tested: `CutsTest` (across, in and out again, freehand, round a part inside and over an edge,
  begun inside, across the scissors' way in, either way round; a tear's reach, a strip of tape,
  overlaps, a place inside an L), `CuttingTest` (a cut's parts where they lay, named and chosen; cut
  round, the larger part cut out; through nothing; the chosen piece first; cut again; torn; tape
  holding, moving, turning, flipping and letting go; tape not cut; Esc's order), `CollageKeysTest`,
  `CollageScreenTest` (the scissors by a drag and by clicks round a part, the path drawn in Naples
  yellow, Shift straight however the pointer wanders, the tear and the tape by the mouse, the tape
  drawn), `FileCollagesTest` (tape read back) and the cutting in `RoomScreenshotTest`. Broken on
  purpose and seen red after a no-op control stayed green: 38 guards — a cut parting only once, a
  cut round a part inside cutting nothing out, crossings at one place taken as they come, the hole
  round the same way as the outline, a ring begun inside cut from there, a stretch outside cut as if
  inside, a tear as straight as a cut, tape cut square, outlines across each other not
  overlapping; a part moved from where it lay, the part cut off not chosen, the smaller part the
  piece still, what a cut went round not cut out, the chosen piece not cut first, tape cut like a
  picture, tape holding the larger part rather than the one under it, tape laid holding nothing,
  taped pieces moving alone or turned about themselves, flipped with its turn kept, a piece taken
  away still held; Esc leaving with a tool in hand, a tool taken up keeping the path, the tear's
  path cut, not torn; a drag on its own waiting for more, the first corner clicked cutting no
  further, each click starting the path again, Shift cutting freehand all the same, the tear in
  hand taking hold instead, the tape laid nowhere, the path or the tape's paper not drawn; the
  rail's Scissors and Tape, R and Enter doing nothing; tape read back as a picture, or without what
  it holds
- ✅ **Through every layer**, as the user asked: *All layers* on the rail (L) sends the scissors and
  the tear through every picture under the cut, not the chosen or the topmost one alone — each
  parted as one would be, its parts above it, the topmost one's part cut off chosen, all of it one
  change to undo; tape still not cut. The mat says so while it is on
- ✅ **The source drawer** under the mat, as the design has it: on its left what it draws from — a
  board or a concept, each with its room's dab and a menu of the others; the first board to begin
  with, kept while the room is left and read again on coming back — and that source's pictures
  beside as small prints on their paper (a concept's pictures only, not its notes). A click lays a
  print on the mat; dragged out, it follows the pointer and lies where it is let go over the mat. As
  often as you like, each a new piece to cut from. The room is given the domain's `BoardSource` and
  `ConceptSource`, as the other rooms are
- ✅ **Out.** The collage drawn flat as one picture (`drawCollage`): its pieces as they lie, without
  their shadows, cut to the space they cover with a margin, on the design's paper, as sharp as its
  sharpest picture allows (at most 4000 px along), drawn off the UI thread where the design is and
  handed back to the room, as the Lens's views are. It is saved as `<name>.png` beside the collage,
  where `Collages` always meant it to be (renamed with it); *Collage ▾ → Save as picture* (Ctrl+S)
  just saves it; *Pin to a board ▾* saves it and pins it; the ways on in the header — the rooms the
  hand-off seam has for a picture, *Sketch over* and *Check the values* — save it and take it there,
  Back returning to the collage. Each saves it again as it is now. *Add a picture…* and *Paste*
  moved into *Collage ▾*, so the header holds the ways out
- ✅ Tested: `CuttingTest` (through every layer, the chosen piece or not, as one change; torn so;
  and one alone again), `WaysInAndOutTest` (new: the drawer from the first board, a concept's
  pictures, as often as you like, read again; saved beside it, pinned, taken on once drawn, saved
  over, renamed with it, nothing on the mat), `CollageKeysTest` (L, Ctrl+S), `CollageScreenTest`
  (All layers on the rail; a print clicked and dragged onto the mat, where it is let go; the
  concepts' tab; the header's way into the Lens drawing the collage — its size, the picture where it
  lies, the paper round it — Pin to a board and Save as picture), `RoomsTest` (the drawer over the
  real boards, into Sketch as its reference and back, into the Lens, pinned onto the board) and the
  drawer in `RoomScreenshotTest`. Broken on purpose and seen red after a no-op control stayed green:
  29 guards — through all layers cutting one alone or stopping at the chosen piece, the parts of
  every piece but the first lost, the lowest piece's part chosen; the drawer starting on nothing,
  showing a concept's notes, never reading a board again; a bare mat asking to be drawn, the picture
  saved under a new name each time, pinned without the picture, taken on before it is drawn or into
  no room, ways out offered from a bare mat; the collage drawn without its margin, flipped or on no
  paper, a way out never drawn; a print let go of over the mat laid nowhere or where the drag began,
  a print clicked laying nothing, the concepts' tab choosing nothing; the header's way into the
  Lens, Pin to a board, Save as picture, the rail's All layers, L and Ctrl+S doing nothing; the room
  given no boards, or no hand-offs. The first of them survived at first — the test had chosen
  nothing before cutting — and was seen red once it chose the piece underneath
- ✅ What the room left open elsewhere (2026-10-28). A pose's side buttons — one more for *To a
  collage* — reached into the lens tray in a window under 770 dp: they now stop above the tray and
  go on in a second column to the left of the first where they do not fit (`ColumnsFromTheRight`).
  `[` and `]` restack by the character typed, as Live Sketch's do, so a German keyboard's AltGr+8
  and AltGr+9 work too (`handleCollageChar`; the window routes the room's typed characters to it).
  The README has a section for the Lens room, and the lens's hotkey sheet names the block and the
  relief among the forms. Tested in `SessionChromeTest` (a low window, every way on: above the
  tray, in two columns, none over another), `CollageKeysTest` and `KeysTest` (new: AltGr+9 as the
  window has it — Ctrl+9, then `]` typed — and a US `]`, pressed and typed, restacking once).
  Broken on purpose and seen red after a no-op control stayed green: 6 guards — the buttons down
  into the tray, never a second column, a second column over the first (survived at first: the test
  looked only for two columns, and now holds every button clear of the others), a typed character
  going nowhere, `]` sending the piece back, a US `]` restacking twice

### ✅ F10.7 One seam for hand-offs
- ✅ The hand-off seam in the domain: `Handed` (a picture on disk, or a page drawn as PNG when it
  is taken), `Route` (the room, and what its button says), `Receiver` (a room that takes things:
  what taking one is called there, and taking it) and `Handoff` (the routes there are, and sending
  along one). `Rooms` builds it from the rooms that take pictures — Sketch (*Sketch over*, or
  *Continue sketch* for a picture Live Sketch saved) and the Lens (*Check the values*, a file or a
  page) — so a new room joins every route at once
- ✅ Every way into another room asks it: the board's ring and card menu, a concept's picture, a
  pose's side buttons, Sketch's header, the Lens's header. New routes it gave for free: a pose's
  picture into the Lens, a concept's picture into Sketch and the Lens, the Lens's picture into
  Sketch. `BoardHost`, `ConceptHost` and `SketchHost` lose their hand-offs; `Rooms.sketchFromSession`
  goes
- ✅ Back returns to where a hand-off came from, through every room on the way: `AppState.enter`,
  `back` and `home` keep one origin per room (Sketch's and the Lens's own origins gave way), and
  home from a room entered from a paused session is that session
- ✅ What a board sends a session (*Draw these*: a pool, a folder for its memory, a setup) stays
  `BoardHost`'s: one room, one window of its own
- ✅ Tested: `RoomsTest` (which room takes what, a picture from the board into the Lens and on into
  Sketch with Back through both, a pose's picture into Sketch with the session waiting), the
  rooms' own hand-off tests (`SketchCardTest`, `ConceptTest`, `PracticeStateTest`,
  `SketchStateTest`, `LensRoomStateTest`), `RingMenuTest`, `PaletteTest` (Back retracing the way,
  home to a waiting session)

### 🔄 F10.8 Concepts across the rooms
- ✅ A concept's picture into Sketch (over it, or on with its sketch) and into the Lens, along the
  hand-off seam (F10.7)
- ⬜ A concept's pictures as a Practice source — a warm-up on the thing you are about to draw
- 🔄 A concept as a Collage source ✅ (the source drawer, F10.6) and as a Sketch underlay ⬜
- F8.4's open question settled: does practice on a concept's picture belong to the concept or to
  the board

### 🔄 F10.9 The glass — drawing through a lens
- ✅ A reading glass over the session's picture (`ui/Glass.kt`, `ui/GlassView.kt`): taken up from
  the tray beside the knobs or with `L`, a round glass with a turned rim, a wooden handle, the
  lamp's light and its shadow follows the pointer (the cursor gone) and shows what is under it
  ×1.25–×12 (wheel; `Shift`+wheel the glass's size; `+` `−`)
- ✅ Six optics, as cross-sections of their glass in the tray (`O` turns): **plain** (a zoom,
  nothing bent), **convex** (the middle swells), **concave** (the middle shrinks away),
  **fisheye** (a far wider field at the edge), **cylinder** (one way stretched), **prism** (the
  colours part towards the rim). One SkSL shader; `opticSource` is its arithmetic in Kotlin, and
  a test renders a picture whose colours are its own coordinates through every optic to hold the
  two together
- ✅ **Set down**: a click — that point, as large as the glass showed it — or a dragged-out
  rectangle, which then fills the view; the zoom there and back takes 380 ms (reduced: 120).
  Down, a drag slides the picture under the glass and the wheel comes nearer about the pointer;
  the bending eases in as it lands
- ✅ **Back to the whole**: a print of the whole picture in the corner, the part ringed — a click
  on it, a right click or `Esc`. Esc steps back one thing at a time: the glass lifted, put away,
  fullscreen left, and only then the session stopped. A new pose starts whole
- ✅ Drawn inside the picture's filter layers, so every lens, the light and the knobs work through
  it; mirrored or upside down, the glass is set down where the picture is seen. The rim, the
  print and the dragged-out frame are over the filters
- ✅ `GlassTest` (9: the state, where the picture lies, the optics against the shader),
  `GlassSessionTest` (5: on screen — drawn where the pointer is, set down by a click, by a part
  dragged out, mirrored, lifted by the print and by a right click), `SessionKeysTest` (+2). Caught
  on the way: the print's click lifted the glass and the same release, passing on to the picture,
  set it straight down again. Seven guards broken on purpose and seen red
- 🔄 The glass in the Lens room ✅ (F10.1); on a board's large view and in Sketch over the
  reference ⬜
- ✅ Kept between runs: the optic, the magnification and the size, as they were when the glass
  was last settled — an optic chosen, or the glass put away (`GlassState.keptIn`,
  `Preferences.glass`) — in a session and in the Lens room alike
- ✅ Its size in its settings, beside the magnification (Shift+wheel as before), the person's wish
  after trying the Lens room
- ✅ **Over the whole picture** (`W`, or *Over the whole picture* in its settings): the optic bends
  all of the picture about a centre a click or a drag moves — anywhere, not only the middle — and
  makes none of it larger. Its reach is the picture's farthest corner, which stays the corner
  (`spreadZoom`: the optic's rim brought back to itself), so the bending happens inside the
  picture. The wheel bends it more or less (*Bend*, 10–100 %, kept between runs); Esc or a right
  click takes it back into the hand. A ring and a dot mark the centre on screen; a saved view keeps
  the bending and leaves the mark — and the grid, the glass in hand — behind (`LensView(marks =
  false)`). In a session and in the Lens room alike
- ✅ Tested: `GlassTest` (laid over, moved, set down, gathered, put away; its size and bend in their
  limits and kept; the far rim kept for every optic and bend), `LensStateTest` (Esc gathers first;
  a saved view takes it along), `LensKeysTest` (`W`), `LensViewTest` (the picture bent about the
  middle, a drag moving the centre, a right click; the settings), `LensExportTest` (saved with the
  view, without its mark), `FileLibTest` (the bend kept), and the Lens room's screenshots. Broken on
  purpose and seen red after a no-op control stayed green: 21 guards — laid over the whole in hand, off any part, given up when set down or put away, its centre on the picture; the size and the bend in their limits and kept, in the glass and on disk; the far rim kept, and the bend softening it; the picture bent, a drag moving the centre, a right click and Esc gathering it; a saved view taking it along and leaving the mark and the grid; W; the settings' size and placing

### ✅ F10.10 Forms — the picture on a surface
The person's wish after the glass (2026-10-27): projections onto a sphere and other figures, one
day one's own. This is step 1; the rest — forms with sliders, a relief, one's own surfaces and
models, panoramas, light speed, diffraction — waits in [IDEAS.md](IDEAS.md).
- ✅ The picture on a form, in the lens kit beside the glass (`Form`, `FormState`): a sphere
  (wrapped once round), a cylinder (as tall as the picture is for its width), a cone, a torus
  (along the ring and round its tube), a plane tilted away (foreshortened, in the picture's
  proportions) and a chrome ball (the picture as the world all round, reflected, the lamp shining
  in it and its rim darker)
- ✅ Turned by a drag, as a globe under the hand — mirrored or upside down as it is seen — and
  tipped no further than its edge; each form first seen from where it reads (a solid from a little
  above, a page tilted well back), and *Straighten* returns there. Lit by the lamp from above to
  the left, or not, to see the picture's own values on the form
- ✅ The *Form* knob and its settings (the forms as small drawings, the lamp, Straighten), `P` for
  the next form; the lenses and the knobs work on the form as on the picture — a sphere in notan.
  The picture is on a form or under the glass, never both: taking one up puts the other away
  (`GlassState.onTakeUp`, `FormState.onShow`). Esc takes the picture off its form. In a session
  and in the Lens room alike
- ✅ One shader for every form (`FORM_SKSL`): a ray from a camera five radii away through each
  pixel, turned into the form's frame, meeting it exactly (a sphere, a can, a cone, a page) or
  marching to it (the ring, stopping where it lands); four rays a pixel for a smooth edge.
  `formRay` is its arithmetic in Kotlin; a test draws a picture whose colours are its own
  coordinates on every form and holds the two together, and another holds the turn to a turn
- ✅ A saved view keeps the form; drawn rather than resampled, it is saved no larger than 1600
  pixels on its long side (`FORM_SAVED`)
- ✅ Tested: `FormTest` (`P` round every form; turned, tipped, straightened, copied; a turn that is
  a turn, and the right way round; the form and the glass taking turns; Esc; every form drawn
  where its twin says and clear around it; the lamp), `LensViewTest` (on screen, a drag turning
  it; the settings), `LensKeysTest` (`P`), `LensExportTest` (saved on it, no larger), and the Lens
  room's forms in `RoomScreenshotTest` — in a smaller window, since in a test every frame is drawn
  on the processor: the first, full-size try took fourteen minutes, and caught *Chrome ball* cut
  off in the settings. Broken on purpose and seen red after a no-op control stayed green:
  24 guards — the ball and the can read upside down, the cone's near side, the ring never reached, the page without an edge, the chrome ball reflecting nothing, the lamp, a skewed turn, tipping past the edge, Straighten, P stopping; the form and the glass not taking turns (chosen, taken up, laid over the whole), Esc, a saved view flat or at full size; a drag the wrong way or turning nothing, the picture drawn flat (caught only once the screen test looked at the corner where the flat picture is light), P, the knob, the settings, the lamp switch

### ✅ F10.11 Block, F10.12 Relief — forms shaped for the thing being drawn
The next two of the forms' ideas (2026-10-28); the rest wait in [IDEAS.md](IDEAS.md).
- ✅ **Block** (F10.11): one family of forms, the superquadrics, between the ball and the box — its
  ends (top and bottom) and its round each from square through round to pointed, and its height
  and depth for its width, on four sliders two to a row (`Block`). Round both ways, the ball; square
  at the ends, a can; square both ways, a box with eased edges; pointed, a spinning top; flattened,
  a cushion. Sized so its farthest point is where the sphere's edge is, so every shape of it fills
  the view as the ball does. Found along each ray by Newton's steps down its gauge, each on the
  slope just behind it, so the steps never pass the surface — the gauge is convex along any ray
- ✅ **Relief** (F10.12): the picture on a page raised by a picture's values, light standing out —
  by default its own values, made small and soft (`softHeights`, 384 px, a slight blur) so a
  photograph's grain is no needles; or a picture chosen for it (*From a picture…*, through the
  room's own dialogs, `HeightsSource`), decoded off the UI thread. Its depth on a slider; first
  seen tilted less than a page, so it stands out; its side walls where it is raised at the page's
  edge, its back plain paper. Each ray is stepped only through the relief's box and halved down to
  the raised page
- ✅ **Saved and on the processor**: a saved view of a form is drawn on the processor at up to
  1600 pixels, where the first block took two minutes and the relief nearly four. Now it is drawn
  with one ray a pixel and in bands side by side, four to a core (`drawnInBands`): every form
  saves in under two seconds on sixteen cores. A window drawn on the processor rather than the
  graphics card — Skia in software, as over a remote desktop, or a test — draws its forms in bands
  too (`LocalDrawnOnProcessor`, set from the window's render API), which took the forms'
  screenshots from nearly four minutes to twelve seconds
- ✅ A picture's far edge read short of its repeat, so a relief's side wall at the page's right
  edge does not read the left one
- ✅ **The first form hung the window** (found by hand, 2026-10-28): the first *Form* clicked in
  the Lens room left the window still for half a minute. On the graphics card (Direct3D) the one
  shader for every form — the block's Newton steps, the relief's steps reading its heights, a copy
  of the ray for each of four rays and one more — was compiled before the first frame: 27 to 38
  seconds, measured in a real window on this machine. Now each form's shader is compiled alone,
  its kind set in the source (`formSource`), and the ray is cast from one place in a loop: the
  first frame of any form comes in 0.2 to 0.4 seconds, and switching to a form not yet shown costs
  40 to 240 ms. `FormTest` holds both — every form's own source, and the ray cast from one place —
  and each, broken on purpose, was seen red after a no-op control stayed green
- ✅ Tested: `FormTest` (every form, a shaped block and a relief among them, drawn where its twin
  says; a round block of even measures the sphere; a can and a cushion told apart from the front
  and from above, a tall block narrower; a flat relief the page, a raised one nearer, its back
  plain on screen too; its flanks lit and shaded; its own heights small and soft; a saved form in
  bands as one draw has it; shape and heights copied with a saved view), `LensViewTest` (the
  block's four sliders and the relief's depth, heights from a picture chosen and back, the form
  drawn as its settings say, a window on the processor drawing alike), `LensRoomStateTest` and
  `PracticeStateTest` (both rooms' lenses choose heights through their dialogs), and the block and
  the relief in `RoomScreenshotTest`, whose first try caught the block's sliders pushing the lamp
  and Straighten out of a low window. Broken on purpose and seen red after a no-op control stayed
  green: 23 guards — a block not fitted to the sphere's round, its ends shaped by its round, Newton's steps on the slope ahead in the shader and in the twin, a block read as if round; a relief flat in the shader and in the twin, its slope upside down, its back drawn as its front; the picture read round its repeat; the own heights neither soft nor small; a saved form's bands misplaced; the view ignoring the block, the depth or the heights chosen; the Ends slider shaping the round, the depth slider doing nothing, a chosen picture never used; a saved view losing the shape; a relief first seen as a page; the Lens room's and the session's lenses choosing heights through no dialog

---

## ✅ M10 — Architecture: modules and layers

Asked 2026-10-17, on `dev/architecture-improvements`: the data layer and the UI were not clearly
apart. The four state holders (`BoardState`, `ConceptState`, `SketchState`, `AppState`) mixed
Compose state, the rules and file access, and some screens read the stores themselves. The app is
split into Gradle modules so that the build holds the boundaries rather than a convention: a module
can use only what it depends on. Built bottom up, each step committed with the full suite green;
package names stay as they are, so moving a file changes no imports.

The shape, revised with the user on 2026-10-22, is in
[docs/Architecture.md](docs/Architecture.md) (with a diagram): three groups.

```
lib      bare:   core (model · io · lifecycle) · network · domain · data · sketch-engine
         UI kit: design (contract · atelier) · lens · feature (practice · board · concept ·
                 sketch · lens · collage)
app      desktop
device   pen
```

- **The bare lib is headless and usable on its own**: no Compose, no Swing, no JNA. The UI kit —
  the design system and the features — is the convenient way of using it from a GUI.
- **`domain` is the API**: the types and contracts callers work with, abstracted from the stored
  model, and the contracts the lib needs from its host (clipboard, choosing files, links, the
  pen). It depends on nothing of ours. **`data`** implements it over `core` and `network`, and is
  the only module that reaches either.
- **Features** — one per room — add to the domain in their own domain layer, hold the room's
  state and logic, and draw their screens on the design system's contract. Nothing in the lib
  depends on a feature, and no feature on another.
- **The design system** is a contract and a default implementation, *atelier*; another can
  replace it, since features use only the contract.
- **`app:desktop` is on top**: windows, navigation, the design system chosen, the desktop's own
  implementations of the host contracts, the pen from `device:pen`.
- **The files stay the source of truth** — a board is a folder plus a sidecar. No database.

### ✅ F11.1 `:core:model` — what the app is made of
- ✅ Moved unchanged: `BoardFile` and everything on a board (groups, cards, camera, wallpaper,
  session recipe, note kinds, layouts, surfaces), `ConceptLink` (borrowed cards: pure functions
  over a board), `ConceptFile`, and the session vocabulary (`SessionPlan`, `RampStep`,
  `SessionSetup`, `ViewMode`, `GridMode`)
- ✅ `ConceptRef`, `ConceptSnapshot` and `BoardLink` out of the `ConceptSource` seam into
  `ConceptRefs.kt`; the seam itself stays with the board, and `PinTargets` stays in the app — a
  seam, not data
- ✅ `CardLayout`: the card size, the strip and palette aspects, the natural tilt and
  `placeMissing`, out of `BoardState`'s companion. Concepts used them and needed `BoardState` for
  nothing else, so `ConceptState` no longer imports it
- ✅ Depends on kotlinx-serialization only. `SessionPlanTest` and the three placement tests
  (now `CardLayoutTest`) run in the module, without Compose
- ✅ Broken on purpose and seen red: a Compose import and a reference to `BoardState` inside the
  model do not compile, and six cards to a row fails `CardLayoutTest`

### ✅ F11.2 `:core:io` — files
- ✅ `AppDirs`: the one place that knows `~/.actiondraw` and the homes boards, concepts and
  sketches start out in. The eight lookups of the user's home, in five files, ask it now; a test
  can stand the whole layout up in a temp folder
- ✅ `SafeFile`: temp file, rename, `.bak`, and a reader that falls back to the backup and drops
  a byte-order mark — written once, where `BoardStore` and `ConceptStore` each had a copy. File
  names and formats are unchanged, so every sidecar on disk reads as before
- ✅ `Folders`: names Windows accepts, the next free folder or file name, moving a folder whole
  or not at all, deleting one — each written once, where boards, concepts, the importer and link
  previews had their own (`sanitizeName` twice, `freeFolder` twice, `collisionFree`, `moveFolder`)
- ✅ Moved in unchanged: listing and decoding pictures (`ImageScanner`, `ImageDecoder`, with the
  AVIF reader now a runtime dependency of this module), `ContentId`, `samePathAs`, `isInside`,
  `relKey`
- ✅ Now true for every caller: a drive root or the user's home is never deleted, whoever asks
  (only boards used to check; deleting a concept's folder did not), and a move never reuses a
  destination that already exists. A board that moved but left its old folder behind now says
  so — that note used to be overwritten by "Moved …"
- ✅ `build-logic`: the shared Gradle setup as two convention plugins — `actiondraw.kotlin-library`
  (Kotlin, Java 17, kotlin.test on JUnit 5) and `actiondraw.skia` (Compose's own Skia, and its
  native runtime for headless tests) — used by `:sketch-engine`, `:core:model` and `:core:io`
- ✅ The homes' folder listings stay in the states until the repositories take them (F11.8)
- ✅ Broken on purpose and seen red, each by its own named test, after a control that changes
  nothing stayed green: ten guards in `Folders` and `SafeFile`. `ImageScannerTest` moved with
  the scanner

### ✅ F11.3 `:core:network` — HTTP
- ✅ `Http`: the JDK's HTTP client, the http/https rule, the 8 MB cap and the timeouts, out of
  `LinkPreview`, with `Fetcher` and `Fetched`. Only a module that depends on `:core:network` can
  go online — the app today, and from F11.8 only `lib:data`
- ✅ The client refuses any other scheme itself, before anything is sent. A page's preview
  picture is an address the page chose; until now only the JDK's own refusal of `file:` stood
  between such an address and the disk
- ✅ What stays in `LinkPreview` until F11.8: reading a page for its `og:image` and keeping the
  picture in the board folder
- ✅ `HttpTest`: the address tests from `LinkPreviewTest`, and the scheme check through a seam
  that records what would have been sent, so no test opens a socket. Broken on purpose and
  seen red

### ✅ F11.4 `:core:platform` — the operating system
- ✅ The pen moved in unchanged (`PenSample`, `PenSource`, `WindowsPointerSource` with its
  winuser.h structures), and JNA with it: the app no longer depends on JNA itself. This is the
  only module that knows it runs on Windows
- ✅ `SystemClipboard`, an interface, with `AwtClipboard` the machine's, out of `BoardClipboard`.
  The board is given one in its constructor, so a test pastes and copies without touching the
  clipboard of the machine it runs on
- ✅ `Browser` (`DesktopBrowser`): one way to open an address, where the board and the concept
  page each spelled it out
- ✅ `FileDialogs`: Swing's folder, file and save dialogs. `Choosers.kt` stays in the app as the
  rooms' own terms over them — which pictures it reads, what a sketch is called
- ✅ `SystemPreferences`: Windows' *Show animations* setting, out of `Motion.kt`
- ✅ Interfaces where something already takes another one (the clipboard, the browser, the
  pen); plain objects where nothing does yet (the dialogs, the preferences)
- ✅ `PlatformTest` (4), and a `BoardStateTest` case for the clipboard the board was given. Five
  guards broken on purpose and seen red, each by its named test, after a control that changes
  nothing stayed green. The pen moved unchanged; only a hand on the XPPen can say it still works

### ✅ F11.5 The three groups: `lib`, `app`, `device`
- ✅ Moves only: `core/model` → `lib/core/model`, `core/io` → `lib/core/io`, `core/network` →
  `lib/network`, `sketch-engine` → `lib/sketch-engine`, Gradle's project paths with them;
  packages unchanged, so no import changed
- ✅ `:core:platform` split: the pen (`PenSample`, `PenSource`, `WindowsPointerSource`) is
  `device/pen`; the clipboard, the dialogs, the browser and the animation setting went to the
  app, until their contracts move to `domain` (F11.7)
- ✅ The root project is `app/desktop`, with the packaging and a build file of its own; the root
  only declares the plugins' versions and the lint. `./gradlew run`, `packageMsi` and
  `packageDeb` still work from the root
- ✅ CI keeps the room screenshots from `app/desktop/build/screenshots`; the README, the packaging
  docs and the PKGBUILD follow the new paths (installers land in
  `app/desktop/build/compose/binaries/main/`)

### ✅ F11.6 `:lib:core:lifecycle` — the lib's run
- ✅ `LibRun`: the run's phase (loading, running, closing, closed), the scope its long-running
  work lives in and the dispatcher for disk and network work, handed to whoever needs them;
  `LibRun.inline()` for tests and tools that want no threads of the lib's own
- ✅ `Writes`: the writes that must reach the disk, off the caller's thread. The newest of a key
  wins, so a burst of edits costs one write; they run one at a time, in the order they came;
  `awaitKey` before one is read back, `flush` for all; a write that throws is reported and the
  others go on
- ✅ `MemoryCache`: memory the lib holds while running, the least recently used out first, given
  back on `trim` and when the run closes
- ✅ Closing down cleanly: the app makes the run at start and closes it when its window goes (the
  JVM's shutdown as a backstop). Pending writes are waited for, and a write handed over after
  closing happens at once rather than being lost
- ✅ The states and screens take it up with `lib:data` (F11.8), when saving leaves the UI thread
- ✅ `LibRunTest` (10). Seven guards broken on purpose and seen red, each by its named test, after
  a control that changes nothing stayed green

### ✅ F11.7 `:lib:domain` — the API
- ✅ The types callers work with, out of `lib:core:model`: `Board` (until now `BoardFile`) and
  everything on it, `Concept` (until now `ConceptFile`), the registries' `BoardEntry` and
  `ConceptEntry`, the borrowed-card rules (`ConceptLink`), `CardLayout`, the session's vocabulary,
  `ConceptSource` and what it speaks in. Plain types: nothing of storage, no serialization
- ✅ `lib:core:model` is now how things are stored — `BoardJson`, `ConceptJson`, the registries'
  entries — field for field, default for default what the files on disk hold. The stores map
  between the two (until F11.8 moves them to `lib:data`)
- ✅ The files do not drift: golden files captured from the code before the split, and
  `StoredFormatTest` holding what is written to them byte for byte, what is read back to them field
  for field, and a sidecar that leaves fields out to the domain's defaults
- ✅ The host contracts: `SystemClipboard` (a pasted picture now arrives as PNG bytes, so no AWT
  image crosses into the lib), `Browser`, `FileDialogs`, `HostPreferences`, and the pen's
  `PenSource` and `PenSample`. The desktop fills them (`AwtClipboard`, `DesktopBrowser`,
  `SwingFileDialogs`, `SystemPreferences`), `device:pen` the pen — a device depends on the domain
  alone
- ✅ The path rules (`samePathAs`, `isInside`, `relKey`) and `newId()` are the domain's;
  `lib:core:io` keeps its own copies internal, so the core still depends on nothing of ours
- ✅ The contracts for getting and changing boards, concepts and the rest come with their
  implementations in F11.8
- ✅ Broken on purpose and seen red after a control that changes nothing stayed green: a stored
  name changed, a field dropped on reading and on writing, three defaults drifting (which the
  first round let through — the sparse-sidecar test is what catches them), a pasted picture
  losing its pixels; and the domain reaching for `io` or for the stored format does not compile

### ✅ F11.8 `:lib:data` — the domain, implemented
- ✅ `Lib`, in the domain: everything there is to get and keep, through one object — its
  `boards`, `concepts`, `pictures`, `practice` history (seen and redo), `sketches`, link `previews`,
  `preferences` and `diagnostics`. Each is a contract in the domain's terms that answers in types
  (`BoardCreation`, `BoardDeletion`, `BoardMove`, `PreviewResult`, `LoadResult` …), leaving the
  words to the room that shows them
- ✅ `FileLib`, in `lib:data`: the contracts over the files. The stores, the registries, the
  settings, the importer, link previews, the contact sheet, the palette and the thumbnail cache
  moved in and are `internal` now; what lived inside the four states moved with them — listing and
  adopting boards, making, deleting and moving them, the wallpaper's copy, a concept's documents,
  saving sketches, the pen's and the pointer's logs
- ✅ Saving leaves the UI thread: a board's or a concept's file is handed to the lib's run and
  written there, the newest of a folder winning. A read waits for its folder's write, deleting or
  moving a folder waits for every write still on its way, and closing the app waits for them all.
  A write that fails is reported, not swallowed. A test's lib writes at once
- ✅ `io`, `network` and `model` are `lib:data`'s own: the app depends on the domain, the data and
  the run, so a screen reaching for a file, the network or the stored format does not compile
- ✅ Pictures come as Skia images; the screens make bitmaps of them (`thumbnailBitmap`, `bitmap`).
  `safeFileName`, `paletteHex` and `oneLevelDeep` are the domain's pure rules now, written once
- ✅ Safer than before: a wallpaper or document path that leads out of its board or concept is
  refused, and a lib in a test's folder still never erases the person's home
- ✅ What F11.7 left undone in the docs, done: the model and the domain described as they are,
  the device rule, the module list
- ✅ Broken on purpose and seen red after a no-op control stayed green: a save made on the
  caller's thread, a read not waiting for its write, a delete and a move overtaking writes still
  on their way, a failed write swallowed, a wallpaper and a document path leading out of their
  folder, thumbnails and settings kept outside the lib's home, a preview fetched without the
  card's title or named after nothing; in the rooms, a failed preview worded wrong, the open board
  left on screen when deleted or behind when moved, an existing board not opened, a moved board
  reported in the wrong place, the old wallpaper copy kept, a concept's folder deleted when only
  its file should go, a document misnamed, a sketch overwriting another, practice history read
  from the wrong folder. Three survived the first round — the move's guard passed by luck of
  timing, and two messages were never checked — and are caught now that the tests are sharper.
  The app reaching `io`, the network, the stored format or a store, the domain reaching `data` or
  `io`, the data reaching Compose: none of it compiles

### ✅ F11.9 `:lib:design` — the contract and the atelier
- ✅ `design:contract`: what a screen may ask of the design it is drawn in. `DesignSystem` holds
  its colours (`DesignColors`), the rooms' pigments, Material's palette and type, the pencil's
  hand, its `Materials` (the grain, paper's tooth, ruled lines, the lamp's shadows, dabs, wells,
  tape, pins, a pencil, a paperclip) and its `Chrome` (the way home, a room's dab, line and
  header, the hand-off to another room). It is read through the theme the way `MaterialTheme`
  is: `Design.colors.ink`, `room.pigment`, `Modifier.lampShadow(…)`. With it, the kit's basics:
  the vocabulary (`Room`, `Pigment`, `Lift`, `Tooth`), motion and the chrome that steps back, a
  chip, focus and Enter in a field. It depends on nothing of ours
- ✅ `design:atelier`: the default implementation — `Atelier`, `AtelierMaterials`,
  `AtelierChrome`, the bloom that opens a room, the fonts — and `AtelierTheme`, which the app
  chooses
- ✅ The screens read the theme: some two hundred colours and every material and piece of
  chrome. A draw scope has no theme to ask, so it takes the design before it draws. Only `Main`
  and `App`, on top, name the atelier
- ✅ `actiondraw.compose` in build-logic: a module of the UI kit gets Compose, its tests the
  desktop runtime and Compose's test rule
- ✅ Tests: the atelier's colours, materials and bloom are tested in the atelier, the receding
  chrome in the contract, a paper board's palette with the board. A screen under test is drawn in
  the atelier's design (`ProvideAtelier`), its layout unchanged. `OneLampTest` reads the design
  system's sources as well as the app's
- ✅ The font script writes into the atelier's resources; it still wrote into `src/`, gone since
  F11.5
- ✅ Broken on purpose and seen red after a no-op control stayed green: the boards' glow darkened past reading, muted text faded below it, the bloom without its darker rim, the lamp's shadow falling up and to the left, a design theme that gives no design, paper without its tooth, a paper board inked in its own paper, a second light in the atelier's sources (the scan now reads them). The contract reaching the atelier or the bare lib, the atelier reaching the app: none of it compiles

### ✅ F11.10 `:lib:feature:*` — the four rooms
- ✅ `practice`, `board`, `concept` and `sketch`, each a module: the room's state and logic over the
  domain's `Lib`, its screens on the design contract. A room depends on the domain, the design
  contract and — Live Sketch — the engine. Not on `data`, which the app hands in as `Lib`; not on
  another room, the atelier or the desktop
- ✅ The Practice room has a state of its own: `AppState` was the app's way between the rooms and
  the room itself in one. The app keeps where it is (`Screen`, the palette's well, reduced
  motion); `PracticeState` is the room, with its pages (menu, picker, session, summary) and the
  window a board's session runs in
- ✅ What one room asks of another is a contract the room owns, filled by the app: `BoardHost`,
  `ConceptHost`, `SketchHost`, `PracticeBoards`, `PinTargets`, and the domain's `ConceptSource`.
  The settings sheet is the app's, made of the rooms' sections; the hotkey sheet is the kit's,
  made of each room's list
- ✅ The desktop's services are the domain's host contracts, handed in: the clipboard, the
  browser, the file dialogs (`None` where a host has none, which is also what a test gets), and
  the pen — hooked by the app and delivered on the UI thread, so Live Sketch only listens
- ✅ What the rooms shared went down a level: Markdown, the notes' paper, the interval selector and
  the hotkey sheet to the design contract; finding a picture's sketch to the lib
  (`Sketches.documentOf`)
- ✅ The tests went with their rooms (now board 231, practice 77, sketch 34, concept 20). Those that
  wire rooms together stay in the app (`ConceptLinkTest`, `RingMenuTest`, `PaletteTest`,
  `RoomScreenshotTest`); a board test that needed a concept now meets the domain's
  `ConceptSource` instead
- ✅ New tests for the seams: the board's import asks the host's dialogs, a link on a board or in
  a concept leaves through the host's browser, the Practice room's folder comes from the host's
  dialogs, Live Sketch listens to the pen it is handed
- ✅ Broken on purpose and seen red after a no-op control stayed green: a board's or a concept's link ignoring the host's browser, the board's import and the Practice room's folder asking no dialog, the board's own settings sheet showing the app's title, the app's sheet leaving out a board section, a board's session taking the main window, Live Sketch not saying how its pen listens or letting its samples go nowhere. A room reaching another room, the desktop's clipboard, the files, the atelier or the pen's device: none of it compiles

### ✅ F11.11 `:app:desktop` — on top
- ✅ `Rooms`: the rooms put together — each room's state built over the lib with the host services
  it asks for, wired to the others through the contracts each room owns. The one place that knows
  which room answers which question; Main builds it over the person's files and the desktop
  (`HostServices`: AWT's clipboard, the system's browser, Swing's dialogs, Windows' animation
  setting), a test over a temp folder and no desktop at all
- ✅ `Main` is the windows — the main one, the reference strip, a board's session — and the pen
  hooked to the main one; `Keys` hands each key to the room on screen; `App` is the palette and
  the room on screen, with the bloom between them; `AppState` the way between the rooms
- ✅ The tests that cross rooms build the same `Rooms` instead of wiring the rooms themselves
  (`PaletteTest`, `RoomScreenshotTest`), and `RoomsTest` follows every route between them: a
  board's session into the Practice room's window, the setup a board remembers, a concept and the
  boards that link it, a sketch saved onto a board, the Practice room drawing from a board, a
  sketch from a session and back, and the host's services reaching every room
- ✅ The links in the docs follow the files to where they live now
- ✅ Broken on purpose and seen red after a no-op control stayed green: every route of Rooms cut, one at a time — a board's session going nowhere, a board remembering a setup of its own, a concept not told which boards link it, a board unable to open a concept, a sketch saved to a board not pinned, the Practice room knowing no boards, a sketch from a session leaving it running — and each of the host's services withheld: the dialogs, the clipboard, the browser, the system's motion preference


### ✅ F11.12 One contract per seam, every room in its colours
- ✅ `BoardSource` in the domain — which boards there are, the pictures on one, pinning onto one —
  answered by the board room (`BoardState`) and used by every room that draws from a board or
  files a picture away on one: it replaces Practice's `PinTargets` and `PracticeBoards`, and the
  board halves of `SketchHost` and `LensHost`. `BoardRef` is the board as they list it
- ✅ `ConceptSource` grows `folderOf`, so Live Sketch saves into a concept through it, not its host;
  `SketchHost` and `LensHost` are down to the way out
- ✅ `Room` moves into the domain — the rooms are the product's vocabulary, which the hand-off
  seam needs; their pigments stay the design's — and the design contract depends on the domain
  alone, for it
- ✅ `RoomTheme` in the design contract: Material's own controls — chips, sliders, switches,
  filled buttons — in the room's pigment. The app shows the room on screen in it, each room's
  dialogs in theirs, the reference strip and a board's session window in theirs; the atelier's
  Material palette still leads with Practice's cadmium, now only on the palette. The Lens room's
  own tint gave way to it
- ✅ Two docs set straight: `LensState`'s note that the Lens room "will" use it, and F8.4's
  "Live Sketch saves into a concept", long done
- ✅ Broken on purpose and seen red after a no-op control stayed green: 31 guards — the board room listing and pinning, a concept's folder, a sketch saved onto a board and into a concept, the Practice room's board and pin, the Lens room's pin; the seam offering a picture back to its own room, not opening the room or not handing over, a receiver left out; Sketch going on with a sketched picture, refusing a page, the Lens taking one; the routes a card, the card's menu, the ring, a concept, a pose, Sketch's and the Lens's headers ask for and send along, a session waiting while its picture is away; Back, home to a waiting session, entering again; the room on screen in its colours, RoomTheme's colour — and the build refuses the domain the design, and the rooms each other

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
- → Shaped further as **F10.4 Compare** in M9

### ⬜ F+.4 Staged studies
- ⬜ The filter changes during a pose: Notan for the value masses, Edge for the contour, then
  full. Shares the ramp-phase seam that memory drawing opened.
- → Shaped further as **F10.3 Staged studies** in M9

### ⬜ F+.5 Smaller things on the list
- ⬜ A watched drop folder per board — Krita saves a PNG into it and the card appears
- ⬜ Step through an animation frame by frame (GIF/WebP, already decodable)
- ⬜ Export a board as a portable bundle or a captioned PDF

Reasoning for all of these, and one idea deliberately **not** taken, is in the *Where next*
section of [IDEAS.md](IDEAS.md).

---

## Housekeeping

- ✅ M8's guards checked for real (2026-10-17, `fix/verify-m8-guards`). The script that broke
  each guard and ran its test had called Gradle in a way this machine's shell could not find, so
  every "seen red" in M8 had been the shell failing in under a second, not a test. All 61 checks,
  recovered from the session's log, were run again with Gradle actually starting (a control that
  changes nothing must stay green first, and a check counts only when a named test fails):
  53 were caught as claimed. Four tests turned out weak and are fixed — the watercolour's rim was
  probed where the rim begins, so it has no darkening there (`MotionTest`); the Sketch tray was
  never tested with a pointer on screen (`SketchStateTest`); the surface swatch clicked was cork,
  which every board already is (`BoardChromeTest`); and a session drawn from the board's folder
  passed for one drawn from the board (`PracticeTest`). Three checks aimed at code F9.10 had
  rewritten and one did not compile; they were written again against the code as it is. With
  that, every M8 guard has been seen red. The checks in M3–M7 ("checked by reverting") were made
  in earlier sessions whose scripts are gone and are not re-verified here
- ✅ A session test that could hang for good (2026-10-17): `PracticeTest` once sat for half an
  hour in a Java-level deadlock inside Compose 1.7.3. In a desktop test the composition runs on
  the test thread while Compose sends its snapshot notifications on Swing's; a `snapshotFlow`'s
  observer, called there with the snapshot observer's lock held, resumed its collector onto the
  dispatcher the test thread was running a click on, and each thread waited for the other's
  lock. Reproduced under CPU load in a stress loop of the same steps. The app's only two
  `snapshotFlow`s are gone: the recede timer hears each move from the pointer handler itself
  (`PointerWatch.moves`, a flow sent on the composition's thread), and the glass's last focus is
  an effect keyed on the focus. An effect keyed on a move counter was tried first and was not the
  same: `MotionTest` caught that its wait began a frame after the move. In the running app both
  threads are Swing's, so the cycle cannot form there
- ✅ Tidy-up pass (2026-09-07): dead `dismissOpenFailed` removed, two unused imports, one
  `File.samePathAs` for the six hand-spelled case-insensitive folder comparisons, imports in
  place of fully-qualified `java.net`/`java.awt` names; the base compiles without a warning
- ✅ Docs aligned (2026-09-17): features renumbered into the order they were built, the work
  after M3 split out as its own milestone, the open backlog listed here rather than only in
  `IDEAS.md`, and the README's group and large-view shortcuts brought back in step with what
  the code does. `File.isInside` joined `samePathAs` as the one rule for path containment.

- ✅ Restore corrupted `README.md` (`6a56d64`, branch `fix/restore-readme`; merged into
  `feat/idea-board` so the board docs could build on it), both long since on `main`
