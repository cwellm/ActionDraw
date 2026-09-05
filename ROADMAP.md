# ActionDraw Roadmap

Three levels of granularity: **Milestones** (`##`) → **Features** (`###`) → **Tasks** (bullets).
States: ✅ done · 🔄 in progress · ⬜ open. A milestone/feature shows the roll-up of what's below
it. Tasks are broken out when their milestone becomes active; later milestones stay at feature
level on purpose.

Background documents: [ACTIONDRAW_EXTENSION.md](ACTIONDRAW_EXTENSION.md) (exploration/ideation) ·
[docs/IdeaBoard-Shaping.md](docs/IdeaBoard-Shaping.md) (Phase-1 shape) · [IDEAS.md](IDEAS.md)
(filter scratchpad).

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
- ✅ *Delete…* on a board tile: removing the board keeps the folder and its pictures (default);
  a separate tick also erases the folder
- ✅ Guards: refuses a folder that is not a board, the user's home, or the boards home; closes
  the board first if it is the one on screen; drops it from the recent list
- ✅ Covered by `DeleteBoardTest`, whose guard case was checked by weakening the guard

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

## Housekeeping

- ✅ Restore corrupted `README.md` (`6a56d64`, branch `fix/restore-readme`; merged into
  `feat/idea-board` so the board docs could build on it) — ⬜ merge both to `main`
