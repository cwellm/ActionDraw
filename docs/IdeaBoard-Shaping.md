# Idea Board — Phase 1 shaping

*2026-08-30 · Shapes the "useful board" milestone (M1 in [ROADMAP.md](../ROADMAP.md)) precisely
enough to build from. Exploration/ideation background: [ACTIONDRAW_EXTENSION.md](../ACTIONDRAW_EXTENSION.md).
The shaping questions were answered on 2026-08-30 (§10) — this shape is frozen and realization draws from it.*

## 1. Decisions this shape builds on

| # | Decision |
|---|---|
| D1 | Grouped grid first; a freeform canvas comes later as a *layout mode* on the same data |
| D2 | Imported material (paste, drag-in, browse) is **copied into the board folder** — including images copied online and pasted |
| D3 | The board is a **decoupled module** talking to the rest through a small API; image folders stay general-purpose and are never reorganized by the board |
| D4 | Notes are **plain text** in Phase 1; formatting (bold, colour, …) is on the roadmap |
| D5 | Practice happens on paper *and* in Krita → both the contact-sheet idea and the always-on-top strip stay on the roadmap (Phase 3), neither is cut |
| D6 | Name: **Idea Board**. Default look: **cork** (papyrus and plain-dark as alternatives), changeable per board |
| D7 | New in scope for Phase 1: **immersive full-screen mode** for the board, and **copy out of the board** (Ctrl+C → paste files into Explorer as duplicates) |

## 2. The concept in one paragraph

An **Idea Board** is born as a board, not as a folder view: create it, name it, and it gets a
backing folder. Cards on the cork surface exist for exactly the material you *put on the board* —
**explicit membership** (A1). The backing folder is storage, not source: adding a picture to the
board adds its file to the folder, but files that appear in the folder by other means are simply
ignored by the board (the folder stays general-purpose, D3). Cards are organised into named
**groups** (unassigned cards sit in the **Inbox**), mixed with plain-text **note cards**, one-line
captions and **tags**. Material flows *in* by dropping files, pasting from the clipboard (files or
a copied web image), or a file chooser — copied into the board folder (files already inside it are
referenced in place). Material flows *out* by Ctrl+C into Explorer, and — the point of it all — by
**"Draw these"**: any selection or group becomes an ActionDraw session. All board data lives in
one sidecar file, `.actiondraw_board.json`, next to the images.

## 3. UX walkthrough

### 3.1 Menu

The menu gains an **Idea Boards** section below the practice controls. The primary flow is
*create, then collect* (A1):

- **"New board…"** — dialog with the board **name** and the **location**: the backing folder is
  created as `<boards home>/<name>` (boards home defaults to `~/ActionDraw Boards`, is changeable
  in the dialog, and is remembered in `settings.properties`).
- **"Open board…"** — folder chooser for an existing board folder (one that has, or gets, a
  sidecar). Practice folders and boards stay two independent concepts (A3).
- A short **MRU list** of recently opened boards (paths in `settings.properties`), shown as
  clickable chips.

### 3.2 Board screen

```
┌──────────────────────────────────────────────────────────────┐
│  Drachenbuch      [cork ▾]  [⛶]              [× close]      │  header
│  ── Inbox (7) ──────────────────────────────────────────────│
│  [img] [img] [img] [img] [img] [img] [img]                   │
│  ── Flügel (12) ───────────────────────────── [Draw group]──│
│  [img] [note] [img] [img] [img*] [img] …                     │
│  ── Posen (4) ─────────────────────────────── [Draw group]──│
│  [img] [img] [img] [img]                                     │
├──────────────────────────────────────────────────────────────┤
│  [Draw selection (3)]  [New group]  [New note]  [Import…]    │  action bar
└──────────────────────────────────────────────────────────────┘
```

- One scrolling surface; each group is a titled, collapsible section (collapse state persisted).
  Inbox is always first and cannot be deleted or renamed.
- **Cards**: thumbnail on a small paper-like backing (fits the cork look), caption underneath,
  star badge. Note cards are yellow-tinted paper with wrapped text.
- **Selection**: Explorer-style — click selects, `Ctrl`+click toggles, `Shift`+click ranges,
  `Ctrl+A` selects the visible (filtered) set. Selection is what Draw/Copy/Move operate on.
- **Tag filter bar** under the header: chips for every tag in use; active chips AND-filter the
  image cards (note cards show only while no filter is active).
- **Context menu** (right-click on card/selection): Move to group ▸, Caption…, Tags…, Star/Unstar,
  Copy, Remove from board (never touches the file on disk), and for notes Edit….
- **Group header controls**: rename, colour, reorder, collapse, "Draw group", delete group
  (its items return to the Inbox).

### 3.3 Immersive mode

`F` (or the ⛶ button) toggles the OS-level fullscreen already used by sessions: header and action
bar disappear, the board fills the screen — made for browsing the collection on a second monitor
next to Krita or the drawing table. All interaction keeps working; `Esc` leaves immersive first
(same convention as the session screen).

### 3.4 Material in

Nothing is added automatically (A1) — every card is the result of an explicit act:

| Flow | Behaviour |
|---|---|
| File already inside the board folder | importing it (chooser, paste, drop) **references it in place** — no copy |
| Drag & drop from Explorer (files/folders) | copied into `<board>/_imported/`, cards land in the group under the cursor (or Inbox) |
| `Ctrl+V`, clipboard holds files | same as drag & drop |
| `Ctrl+V`, clipboard holds an image (e.g. browser → "Copy image") | written as PNG to `_imported/pasted-YYYYMMDD-HHmmss.png`, card lands in Inbox (or selected group) |
| "Import…" button | file chooser (multi-select) → same copy behaviour |

Name collisions on copy get a ` (2)`, ` (3)`… suffix. Duplicate *content* detection is **not**
Phase 1 (no hashing — see rabbit holes).

### 3.5 Material out

- `Ctrl+C` with a selection puts the images' **files** on the system clipboard
  (`javaFileListFlavor` with the absolute paths). Pasting in Explorer copies them — duplicates,
  originals untouched. Likely bonus: the same clipboard content is understood by many apps
  (file-open-by-paste), though only Explorer is the promised target.
- Note cards are skipped on copy-out (they have no file); if the selection is only notes, Ctrl+C
  copies their text as plain text instead.

### 3.6 The bridge

- **"Draw selection"** (action bar, `Enter`) and **"Draw group"** (group header) start a normal
  ActionDraw session whose pool is exactly those images — ramps, filters, seen/redo all apply.
- After the session, the summary's "back" returns to the board, not the menu.
- Per-board session recipes (remembered interval/filters) stay Phase 2.

### 3.7 Keyboard (board screen)

`←→↑↓` move focus · `Space` quick-look (large preview overlay) · `Enter` draw selection ·
`Ctrl+A/C/V` select all/copy/paste · `N` new note · `G` new group · `S` star · `T` tags ·
`F2` caption · `Del` remove from board · `F` immersive · `Esc` leave immersive / close
quick-look / back to menu.

## 4. Architecture — the "plugin" boundary (D3)

New package `de.creaflect.actiondraw.board`, self-contained:

```
board/
  BoardFile.kt      // serializable schema (v1) + kotlinx-serialization
  BoardStore.kt     // load/save sidecar (atomic: write temp, rename), reconcile with folder scan
  BoardState.kt     // hoisted Compose state + action methods (mirrors AppState's style)
  ui/BoardScreen.kt // grid, cards, context menus, immersive
  ui/Themes.kt      // cork / papyrus / plain surfaces
```

The board talks to the rest of the app **only** through a narrow host interface, wired in `App.kt`:

```kotlin
/** What the practice side offers the board — the board knows nothing else about it. */
interface BoardHost {
    fun startSession(boardRoot: File, images: List<File>)
    fun openFolderDialog(start: File?): File?          // reuse the existing chooser
}
```

- The practice side (`AppState`, session screens) has **zero** knowledge of boards; `AppState`
  implements `BoardHost` (or `App.kt` adapts it).
- Shared low-level services (`ImageScanner`, `Thumbnails`, `Settings`) are used by both sides —
  they are infrastructure, not coupling.
- The board **never renames, moves or deletes** files it didn't create; its only writes inside the
  folder are `.actiondraw_board.json` and copies into `_imported/`. A folder with a sidecar remains
  a perfectly ordinary image folder for every other purpose — delete the sidecar and only
  arrangement/notes are gone.

One necessary touch on existing code: `SeenStore`/`RedoStore` key images by **file name**; with
subfolders in play, keys become **relative paths with `/` separators**. For the flat folders used
until now, name == relative path, so existing `.actiondraw_seen.txt` files keep working unchanged.

## 5. Data model (schema v1)

```json
{
  "version": 1,
  "name": "Drachenbuch",
  "theme": "cork",
  "groups": [
    { "id": "g1", "name": "Flügel", "color": "#80CBC4", "order": 1, "collapsed": false }
  ],
  "items": [
    { "id": "i1", "type": "image", "path": "wings/bat-study-03.jpg",
      "groups": ["g1"], "caption": "membrane folds", "starred": true, "tags": [] },
    { "id": "i2", "type": "note", "text": "Flügel von schräg hinten sammeln!", "groups": ["g1"] }
  ]
}
```

- `path` is relative to the board root, `/`-separated — it is the **identity** of an image item
  (used to reconcile with the folder). `id` exists for stable internal references and notes.
- Items may belong to several groups; empty `groups` = Inbox. Display order within a group =
  item order in the array (manual drag-reordering is Phase 2).
- `tags` is first-class in Phase 1 (A5): tag editor (`T`/context menu) + the AND-filter bar.
  Free-text *search* stays Phase 2.
- Unknown fields are ignored on load (`ignoreUnknownKeys`) so later phases can extend gently;
  `pos` for the freeform mode will be such an addition.

## 6. Membership & validation (folder ⇄ sidecar)

Membership is **explicit** (A1): the sidecar's items are the board — the folder is never scanned
to *add* cards. Validation, on board open and before starting a session from the board:

1. An image item whose file is gone → **dropped on next save** (its caption/groups/tags are
   lost). Accepted Phase-1 tradeoff (A4): an *external* rename loses that card's metadata.
   Rename-proof identity (content hash) is on the roadmap (M2). In-app nothing is ever renamed,
   so normal use never hits this.
2. Note cards are never dropped.

## 7. Look & feel (D6)

- **Themes per board**: `cork` (default), `papyrus`, `plain` (the current dark app surface — the
  choice for night-time immersive browsing). Switcher in the header; stored in the sidecar.
- Textures are **generated, not shipped**: a tileable cork/papyrus fill via Skia noise shaders
  (`RuntimeEffect`, same machinery as the session filters), rendered once per size into an
  `ImageBitmap` and drawn tiled. Fallback if the shader route fights back: a one-off `genTextures`
  tool à la `IconGen` producing small committed PNGs — decided during realization, not here.
- Cards sit on a light paper backing with a subtle shadow (the "pinned to cork" reading); on
  `plain` the backing drops away and cells look like the existing picker.

## 8. Explicitly *not* in Phase 1 (no-gos)

Freeform positions · free-text search (tags *are* Phase 1, A5) · practice badges & smart groups ·
per-board session recipes · pin-from-session · drag-reordering inside groups · rich-text notes
(D4) · URL/link cards & anything networked · content-hash dedup/rename-tracking · deleting image
files from disk (the board only ever *removes cards*).

## 9. Rabbit holes & risks (watch these)

1. **Compose desktop drag & drop is experimental** — if `dragAndDropTarget` misbehaves with
   Explorer file drops, fall back to an AWT `DropTarget` on the window; both deliver a
   `java.io.File` list. Timebox the Compose route to one evening.
2. **Clipboard image formats vary** (browsers offer DIB/PNG differently; transparency in DIBs is
   shaky). Accept whatever AWT's `imageFlavor` decodes, write PNG, don't chase exotic flavors.
3. **Grid with group sections**: `LazyVerticalGrid` + `span = maxLineSpan` header items — known
   pattern, but keep headers simple (no sticky headers in Phase 1).
4. **Thumbnail volume**: hundreds of cards → the disk cache (`~/.actiondraw/thumbs/`, keyed by
   path+size+mtime hash) is part of Phase 1, *before* the board screen lands, so the board is
   never built against slow thumbs.
5. **Sidecar corruption**: always write via temp file + atomic rename; keep a `.bak` of the last
   good version. A board file that fails to parse must never crash the app — show "couldn't read
   board" and offer the backup.
6. **Scope creep via the picker**: the board is *not* a replacement for the session picker in
   Phase 1. They coexist; consolidation is a later decision.

## 10. Shaping questions — answered (2026-08-30)

**A1 — Explicit membership; boards are created first.** The folder's contents do *not*
automatically appear on the board (that would undo the decoupling). The inverse holds: adding a
picture to the board adds its file to the folder. The usual flow is not "open a folder as a
board" but "create the board, then collect into it" — hence the New-board-first menu (§3.1).

**A2 — `_imported/`** confirmed as the landing spot for copies.

**A3 — Two concepts** confirmed: practice folder and boards stay independent.

**A4 — Renaming may break cards in Phase 1** — accepted; the rename-proof fix (content-hash
identity) stays on the roadmap (M2).

**A5 — Tags are pulled into Phase 1** (editor + filter bar); free-text search remains Phase 2.

## 11. Definition of done (Phase 1)

Create the board "Drachenbuch" on cork · collect material: paste a browser image and see it land
as a PNG card, drag files in from Explorer, import via the chooser · create groups, assign cards,
star, caption, tag · filter by tag · write note cards · Ctrl+C two cards and paste them in
Explorer as copies · select five wings and draw them as a 60-second session, redo/seen behaving
as always · flip to papyrus and plain, go immersive on the second monitor · close the app,
reopen from the MRU chip: everything exactly as left, all of it in `.actiondraw_board.json` +
`_imported/` · store/validation/import logic unit-tested, `./gradlew test` green on Windows.

## 12. Realization notes (2026-08-30)

Phase 1 is implemented as shaped, with these deliberate simplifications:

1. **Drops land in the Inbox**, not in "the group under the cursor" (§3.4): the lazy grid's
   group sections don't make practical per-group drop targets. Cards are moved via the context
   menu; revisit hover targeting together with the freeform layout mode.
2. **Arrow keys step linearly** through the visible order (the adaptive grid's column count
   isn't known to the state, so ↑/↓ behave like ←/→).
3. **New notes and pastes land in the Inbox**; assignment to a group is a second step via the
   context menu.
4. **"Draw group" draws the group's *visible* cards** — with an active tag filter that means
   only the matching images, which doubles as "draw everything tagged X in this group".
5. Textured themes flip the board to a **light paper Material palette** so controls stay
   readable on cork/papyrus; `plain` keeps the app's dark palette.

## 13. Feedback round 1 (2026-08-30)

The first hands-on pass reshaped the entry points and pulled the freeform canvas forward:

- **Menu**: "Draw" and "Boards" are two equally sized primary buttons. "Boards" opens the
  **board picker** — a list of available boards (recent ∪ subfolders of the boards home with a
  sidecar, names read from the sidecar), plus **New board…**, **Explore…** (the folder dialog
  moved here) and the changeable **boards home**. No more board chips on the menu.
- **Immersive ≠ fullscreen**: chrome is hidden only in explicit immersive mode (⛶ or `F`);
  whenever the window is not fullscreen, every menu is visible.
- **Sessions from the board run in their own window** ("ActionDraw — Session"): the board stays
  visible, closing the window aborts the drawing back to the board, the summary reads
  "Back to board", and "Go again" replays the same pool. Menu-started sessions are unchanged.
- **Freeform layout mode** (was Phase 3): per-board Grid ⇄ Free toggle. In Free, every card has
  position/scale/rotation (`pos` in the sidecar, exactly as designed in §5), the camera
  (pan/zoom) is persisted, unplaced cards are auto-arranged, and cards are moved by dragging,
  resized via the corner handle or Ctrl+wheel, rotated via the top handle or Shift+wheel;
  "Bring to front" manages z-order; images remember their aspect ratio. Groups remain a
  grid-mode concept (and still feed "Draw group"); the tag filter applies in both modes.
- **Grid mode** additionally got a "Move to ▾" dropdown in the action bar, so getting cards out
  of the Inbox no longer depends on discovering the right-click menu.
- **Ordering (round 1b)**: the items array doubles as the grid's display order and the freeform
  z-order, and both are now controllable. Grid: *Move earlier/later* and *Move to group
  start/end* per card. Free: *Bring forward / Send backward / Bring to front / Send to back*
  (e.g. a note created after a photo can be pushed behind it). Keyboard in both modes:
  `Ctrl+↑/↓` steps, `Ctrl+Shift+↑/↓` goes all the way; stepping skips cards the tag filter hides.
