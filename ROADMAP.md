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
- ✅ View modes: B&W, Squint, Sepia, Posterize, Pixelate, Warm, Cool, Edge, Silhouette, Notan
- ✅ Toggles: Blur, Mirror, Upside down, Invert, Defraction
- ✅ Adjustable parameters (sliders) · proportion grids (Thirds/Phi/Diagonal)

### ✅ Memory & picking
- ✅ Seen/redo state per folder (`.actiondraw_seen.txt`, `.actiondraw_redo.txt`)
- ✅ Picture picker (thumbnail grid, All/None, per-selection cycles)
- ✅ Last-folder memory across restarts

### ✅ Packaging
- ✅ Windows MSI · Debian package · ArchLinux PKGBUILD + guides

---

## 🔄 M1 — Idea Board: the useful board (Phase 1)

*Shape: [docs/IdeaBoard-Shaping.md](docs/IdeaBoard-Shaping.md). Grouped grid on a cork/papyrus
surface, material in & out, "Draw these" bridge.*

### ✅ F1.0 Shaping
- ✅ Exploration & ideation (ACTIONDRAW_EXTENSION.md)
- ✅ Decisions D1–D7 taken
- ✅ Shaping document written
- ✅ Open questions Q1–Q5 answered (shaping §10) → shape frozen: explicit membership,
  boards created first, tags pulled into Phase 1

### ⬜ F1.1 Foundations
- ⬜ Add `kotlinx-serialization-json` (first non-Compose dependency)
- ⬜ Recursive `ImageScanner` (skip dot-dirs and `.actiondraw*`; relative-path aware)
- ⬜ Thumbnail disk cache (`~/.actiondraw/thumbs/`, key = hash(path, size, mtime))

### ⬜ F1.2 Board store
- ⬜ Schema v1 (`BoardFile`, groups/items/theme, `ignoreUnknownKeys`)
- ⬜ Load/save sidecar — atomic write + `.bak`, parse failure never crashes
- ⬜ Validate membership (explicit — no auto-add; missing files → drop on save)
- ⬜ Unit tests (store round-trip, reconcile, collision-safe import naming)

### ⬜ F1.3 Board screen
- ⬜ Menu section: "New board…" (name + location, boards-home default) · "Open board…" · MRU chips
- ⬜ Grouped grid with Inbox + collapsible sections (span headers)
- ⬜ Explorer-style selection (click / Ctrl / Shift / Ctrl+A)
- ⬜ Group CRUD: create, rename, colour, reorder, delete → items to Inbox
- ⬜ Card actions: move to group, star, remove from board (file untouched)
- ⬜ Quick-look overlay (`Space`) + keyboard navigation

### ⬜ F1.4 Notes, captions & tags
- ⬜ Note cards (plain text): create, edit, place in groups
- ⬜ One-line captions on image cards (`F2`)
- ⬜ Tag editor (`T` / context menu), multi-select aware
- ⬜ Tag filter bar (AND chips; notes visible only without filter)

### ⬜ F1.5 Material in
- ⬜ Drag & drop from Explorer → copy to `_imported/` (Compose DnD, AWT fallback)
- ⬜ `Ctrl+V` file list → import
- ⬜ `Ctrl+V` bitmap (browser "Copy image") → PNG in `_imported/`
- ⬜ "Import…" file chooser (multi-select)

### ⬜ F1.6 Material out
- ⬜ `Ctrl+C` → `javaFileListFlavor` on the clipboard (paste in Explorer = duplicate)
- ⬜ Notes-only selection copies as plain text

### ⬜ F1.7 The bridge
- ⬜ Seen/redo keys: file names → relative paths (backward compatible for flat folders)
- ⬜ `BoardHost.startSession(root, images)` — session pool from explicit file list
- ⬜ "Draw selection" / "Draw group" · summary returns to the board

### ⬜ F1.8 Look & themes
- ⬜ Cork (default), papyrus, plain — per-board, stored in sidecar
- ⬜ Tileable texture via Skia noise shader (fallback: `genTextures` tool)
- ⬜ Paper-backed card styling on textured themes

### ⬜ F1.9 Immersive mode
- ⬜ `F` fullscreen board, chrome hidden, `Esc` convention as in sessions

### ⬜ F1.10 Wrap-up
- ⬜ README + IDEAS.md updated, shortcuts documented
- ⬜ Manual test pass on Windows (definition of done, shaping §11)

---

## ⬜ M2 — Idea Board: the scaling board (Phase 2)

- ⬜ Free-text search (filenames, captions, notes, tags)
- ⬜ Practice badges on cards (unseen/seen/redo) + smart groups (Redo, Never drawn)
- ⬜ Per-board session recipe (interval/ramp/filters remembered)
- ⬜ Pin-from-session ("add current image to board …") + summary → pin to board
- ⬜ Drag-reordering inside groups
- ⬜ Rename-proof identity (content hash) — fixes the missing-file tradeoff
- ⬜ Board list screen (beyond MRU chips)

---

## ⬜ M3 — Idea Board: the delightful board (Phase 3)

- ⬜ Freeform canvas layout mode (pan/zoom, `pos` per item — same sidecar)
- ⬜ Always-on-top reference strip (for Krita work)
- ⬜ Contact-sheet export (for paper work)
- ⬜ Rich note formatting (bold, colour, …) — per decision D4
- ⬜ Colour-palette extraction per image/group
- ⬜ Board templates · URL/link cards (first networked feature — separate decision)

---

## ⬜ M+ — Practice backlog (independent of the board)

- ⬜ Continuous colour-temperature slider (replaces Warm/Cool presets — IDEAS.md)

---

## Housekeeping

- 🔄 Restore corrupted `README.md` — fixed on branch `fix/restore-readme` (`6a56d64`), ⬜ merge to `main`
