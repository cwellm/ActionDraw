# ActionDraw Extension — Reference Board

*Exploration & ideation, 2026-08-30. A thinking document, not a spec.*

> **Update (same day):** the open decisions in §3.7 have been answered — see **§4 Decisions**.
> The feature is named **Idea Board**; the Phase-1 shape lives in
> [docs/IdeaBoard-Shaping.md](docs/IdeaBoard-Shaping.md) and progress is tracked in
> [ROADMAP.md](ROADMAP.md).

## 1. The idea

ActionDraw currently answers one question: *"show me things to draw, one at a time, on a timer."*
The proposed extension answers the question that comes **before** that: *"where do I keep the
material I want to draw from?"*

A **reference board**: a place to collect drawings, photos and studies — as a project collection
(the *Drachenbuch*), as a general *Materialsammlung*, or as a brainstorming surface. With notes and
groupings. Explicitly **not** a drawing surface — collecting only. And the twist that makes it more
than yet another moodboard app: **select pictures on a board and start an ActionDraw session with
exactly those.**

---

## 2. Exploration

### 2.1 What we build on (current architecture)

| Area | Today | Relevant because… |
|---|---|---|
| UI shell | Compose Desktop 1.7.3, single window, `Screen` enum (Menu / Picker / Session / Summary) switched in [App.kt](src/main/kotlin/de/creaflect/actiondraw/App.kt) | a Board screen slots in the same way |
| State | one hoisted [AppState](src/main/kotlin/de/creaflect/actiondraw/AppState.kt), plain Compose state + action methods, pure logic unit-tested | board state can follow the same pattern |
| Image source | exactly one flat folder; [ImageScanner](src/main/kotlin/de/creaflect/actiondraw/image/ImageScanner.kt) scans top-level only, 6 extensions | a board wants subfolders (and maybe several sources) |
| Selection | the picker's `Set<String>` of file names — in memory only, per folder, lost on folder change | the board is, in essence, a **persistent, structured, annotated picker** |
| Persistence | per-folder sidecar text files (`.actiondraw_seen.txt`, `.actiondraw_redo.txt`); app-level `~/.actiondraw/settings.properties` | precedent: state lives *with the images*, plain files, best-effort IO |
| Thumbnails | [Thumbnails.load](src/main/kotlin/de/creaflect/actiondraw/image/Thumbnails.kt) downscales to 192 px on demand — **no cache**, recomputed every time | a board shows hundreds of thumbs → disk cache becomes necessary |
| Filters | ColorMatrix + Skia runtime shaders, applied live in the session | sessions started *from a board* inherit all of it for free |
| Dependencies | none beyond Compose | adding e.g. `kotlinx-serialization-json` is a real (but small) step |

**Gaps the board has to close:** nothing is persisted about collections, nothing spans folders, no
metadata (notes, tags, captions), no drag-and-drop or clipboard import, no free placement, and
thumbnail loading would be too slow at board scale without a cache.

> Side-finding from the exploration: `README.md` is binary garbage on disk **and** in git since
> commit `3364f67`; the last clean version is in `e052c7d` (content is 2 feature-generations stale).
> Worth restoring/refreshing in a separate change.

### 2.2 Tool landscape — what to learn, what to skip

Three archetypes dominate the space:

**A. Spatial reference canvas — PureRef (2.0/2.1), BeeRef, Kuadro.**
PureRef 2.x offers an infinite canvas, always-on-top overlay, image *and* note grouping, a
rich-text note editor (links, checklists), GIF playback, quick paint-over annotations, and in 2.1
grids/lines for aligning items. Its widely-cited holes: **no search, no tags, no cross-project
library** — it's a per-project scratch surface, not a collection.
*Lesson:* spatial arrangement is loved for actively working one subject; a canvas alone doesn't
manage material. *Also:* notes as first-class items next to images (2.0's headline feature) is
exactly the "auch mit Notizen" requirement.

**B. Library / asset manager — Eagle, Billfish, refern.**
Eagle's power features: hierarchical tags, smart folders (rule-based auto-groups), color search,
ratings — but it imports by *copying everything into a proprietary library folder*.
*Lesson:* metadata (tags, flags, search) is what makes a collection scale past ~200 images.
*Anti-lesson:* a copy-into-my-database model conflicts with ActionDraw's folder-first, plain-files
philosophy. refern's pitch ("canvas + searchable library in one") shows the hybrid is what artists
actually ask for.

**C. Brainstorm boards — Milanote, Miro, Are.na.**
Notes, columns, links and images mixed freely; Are.na's "one item can live in many channels".
All cloud-first, subscription, online-only — everything ActionDraw deliberately isn't.
*Lesson:* interleaving notes *between* images is what turns a pile into thinking; and group
membership should be a **reference, not a copy**.

**The gap ActionDraw can own:** none of these tools connect *collecting* with *practicing*.
Nothing in PureRef says "now draw these ten wings, 60 s each, and remember which ones to redo".
The loop **board → timed session → seen/redo stats → back onto the board** is the unique feature.
Everything else should stay deliberately lean — the goal is not to rebuild Eagle.

### 2.3 Technical groundwork (Compose Desktop)

- **External drag-and-drop** — Compose 1.7 ships `Modifier.dragAndDropTarget`; on desktop the drop
  event exposes the native file list. Experimental API (`@OptIn`), but current and workable; AWT
  `DropTarget` on the window is the proven fallback.
- **Clipboard paste** — the AWT clipboard delivers both file lists and raw bitmaps
  (`DataFlavor.javaFileListFlavor` / `imageFlavor`). So Ctrl+V of an image copied in a browser can
  work; a pasted bitmap must be written to disk (PNG) so it has a home.
- **Grouped grid UI** — `LazyVerticalGrid` with section headers: essentially
  [PickerScreen](src/main/kotlin/de/creaflect/actiondraw/ui/PickerScreen.kt) plus structure. Cheap
  and scales well.
- **Freeform pan/zoom canvas** — `graphicsLayer` (translate/scale) + `pointerInput`; needs manual
  hit-testing, z-order and culling. Feasible, but real work compared to the grid.
- **Thumbnail disk cache** — `~/.actiondraw/thumbs/<hash(path,size,mtime)>.png`; also speeds up the
  existing picker. Straightforward.
- **Persistence — three options:**
  - **(a) Sidecar per folder** (`.actiondraw_board.json` in the folder): matches the seen/redo
    precedent; the board travels and syncs *with* the images; relative paths survive moving the
    folder as a whole. Constraint: a board is anchored to one folder tree.
  - **(b) Central store** (`~/.actiondraw/boards/*.json`, absolute paths): boards freely span
    folders and drives — but links break silently when files move, and the board doesn't travel
    with the material.
  - **(c) Hybrid — recommended:** board root = a folder; sidecar JSON with relative paths; anything
    imported from *outside* (drop, paste, other folder) is **copied into the board folder** (e.g.
    an `_inbox/` subfolder). Everything stays plain files + one JSON; sync/backup-friendly; broken
    links impossible by design.
- **Format & dependency** — `kotlinx-serialization-json` for the board file. First non-Compose
  dependency, but justified: hand-rolling JSON for a nested model would be worse than the
  properties/line-file formats used so far. Version the schema (`"version": 1`) from day one.

---

## 3. Ideation

### 3.1 Guiding principles (proposed)

1. **Offline, plain files, no lock-in** — the folder is the truth for pixels; JSON only describes
   arrangement, notes and metadata. Delete the JSON and you've lost organisation, never images.
2. **Collecting, not drawing** — no brush tools on the board. Annotation = text, grouping, flags.
3. **The board feeds the practice** — every feature must either help *find/keep* material or help
   *turn material into sessions*.
4. **Calm and keyboard-friendly** — same ethos as the session screen.

### 3.2 The concept: Boards

A **Board** = one folder tree elevated to a curated collection: image items + note cards, arranged
in named **groups**, with per-item captions/tags/flags, plus a remembered **session recipe**.
Stored as `.actiondraw_board.json` next to the images.

Two possible presentations of the same data:

- **Grouped grid** (structured; sections like "Köpfe", "Flügel", "Posen", masonry-ish thumbnails,
  notes inline between images) — cheap to build on `LazyVerticalGrid`, scales to hundreds of items,
  keyboard-friendly. **Start here.**
- **Freeform canvas** (PureRef-like spatial clusters, pan/zoom) — more evocative for brainstorming,
  noticeably more work. Design the data model so this is a later *layout mode* (optional `pos` per
  item), not a rewrite.

### 3.3 Idea catalog

Effort: **S** ≈ an evening · **M** ≈ a few evenings/weekend · **L** ≈ a week+ of evenings.
Value: ★–★★★ for the stated goal (collect materials, then draw them).

#### Collect — getting things in

| Idea | Effort | Value | Notes |
|---|---|---|---|
| Drag & drop files/folders from Explorer onto the board (into the group under the cursor) | M | ★★★ | Compose 1.7 `dragAndDropTarget`; copies into board folder per 2.3(c) |
| Recursive scan of the board folder (subfolders) | S | ★★★ | prerequisite; today's scanner is top-level only |
| **Inbox** group: everything new/unsorted lands there first | S | ★★★ | sorting becomes a deliberate act, collecting stays frictionless |
| Ctrl+V paste — files *and* raw bitmaps (browser image → PNG in board folder) | M | ★★★ | the "magic moment" for web gathering; no networking needed |
| **Pin from session**: key during practice = "add current image to board X" | S | ★★ | closes the loop in the other direction (collect *while* practicing) |
| Rescan board folder on window focus; new files → Inbox | S | ★★ | folder stays the truth; drop files in via Explorer and they appear |
| URL/link cards with web thumbnail fetch | L | ★ | brings networking into the app — keep out of v1 |

#### Organize — making sense of it

| Idea | Effort | Value | Notes |
|---|---|---|---|
| Named groups with colour + order (Drachenbuch: Köpfe / Flügel / Schuppen / Posen / Farbstudien) | M | ★★★ | the core structure; move items via drag or context menu |
| **Note cards** — free-text items living alongside images, inside groups | S–M | ★★★ | the "Notizen & Brainstorming" half; plain text first |
| Per-image caption (one line under the thumbnail) | S | ★★ | "membrane folds ¾ view" beats `IMG_4711.jpg` |
| One image in several groups (items are references; pixels never duplicated) | S | ★★ | cheap if the data model allows it from day one — Are.na lesson |
| Star/favourite flag | S | ★★ | doubles as "definitely in the next session" |
| Tags + filter chips (AND-filter across the board) | M | ★★ | the Eagle lesson; fine to defer to v2 |
| Search (filename + captions + note text) | M | ★★ | v2, once boards are big |
| Contact-sheet export (group/board → one overview PNG) | M | ★★ | print the Materialsammlung, share it, or pin it up |

#### The bridge — board ⇄ practice (the differentiator)

| Idea | Effort | Value | Notes |
|---|---|---|---|
| **"Draw these"**: start a session from the current selection / a group / the whole board | S–M | ★★★ | the reason this lives in ActionDraw and not in PureRef |
| Per-board **session recipe**: remembered ramp/interval, auto-advance, default filters | S | ★★★ | "Drachenbuch is always 60 s in Notan"; today those settings are global |
| Practice badges on thumbnails: unseen / seen / redo from the existing stores | S–M | ★★★ | the board becomes a **progress map** of the material |
| Virtual smart groups: *Redo*, *Never drawn*, *Least drawn recently* | M | ★★ | rule-based, computed — Eagle's smart folders, practice-flavoured |
| Summary screen → "pin this session's (or its redo-flagged) images to a board" | S–M | ★★ | reflection step after practice |
| Per-image drawn-count & last-drawn date (extend seen store or keep per-board stats) | M | ★ | enables "least drawn"; watch file-format compatibility |

#### Beyond — later & moonshots

| Idea | Effort | Value | Notes |
|---|---|---|---|
| Freeform canvas layout mode (pan/zoom, spatial clusters) | L | ★★ | same data + optional `pos`; brainstorming feel |
| Always-on-top mini reference strip (current group floating over Krita/PS) | L | ★★ | Compose supports extra windows + `alwaysOnTop`; only matters for digital painting |
| Colour-palette extraction per image/group | M | ★★ | lovely for Farbstudien; pure Skia, offline |
| Board templates ("Creature design" starter groups) | S | ★ | nice once boards prove themselves |
| Board list / MRU screen (multiple known boards, à la last-folder memory) | S | ★★ | store known board paths in `settings.properties` |

### 3.4 Data model sketch

```json
// .actiondraw_board.json — v1 sketch
{
  "version": 1,
  "name": "Drachenbuch",
  "groups": [
    { "id": "g0", "name": "Inbox",  "color": null,      "order": 0 },
    { "id": "g1", "name": "Flügel", "color": "#80CBC4", "order": 1 }
  ],
  "items": [
    { "id": "i1", "type": "image", "path": "wings/bat-study-03.jpg",
      "groups": ["g1"], "caption": "membrane folds", "tags": ["wing", "anatomy"],
      "starred": true },
    { "id": "i2", "type": "note",
      "text": "Flügel wirken am besten von schräg hinten — mehr davon sammeln.",
      "groups": ["g1"] }
  ],
  "session": { "plan": "fixed", "intervalSeconds": 60, "viewMode": "NOTAN" }
}
```

Decisions embedded here: paths relative to the board root · items can be in several groups ·
notes are items, not group properties · the session recipe is part of the board · a later freeform
mode adds an optional `"pos": { "x": …, "y": …, "scale": … }` per item — same file, no migration.

### 3.5 UI sketch

- The menu splits into two entry points: **Practice** (today's flow, unchanged) and **Boards**.
- `Screen.Board` (+ maybe `Screen.BoardList`) joins the enum in
  [App.kt](src/main/kotlin/de/creaflect/actiondraw/App.kt); a `BoardState` sits beside `AppState`'s
  session concerns.
- Board screen layout: header (board name, filter chips, later search) · groups as titled sections
  in one scrolling `LazyVerticalGrid` · action bar ("Draw selection", "Draw group…", "New note",
  "New group").
- Cell rendering grows out of `PickerCell` (thumbnail + selection border) plus caption, star and
  practice badge.
- Keyboard: arrows navigate · Space quick-look (large preview) · Enter start session with
  selection · `N` new note · `G` new group · `S` star · `T` tag.

### 3.6 Suggested phasing

**Phase 1 — the useful board (MVP).** Recursive scan → board sidecar JSON
(`kotlinx-serialization`) → groups + Inbox + assignment → note cards + captions → drag-and-drop
from Explorer (copy into board folder) → **"Draw group/selection" bridge** → thumbnail disk cache.
*Cut here and it already beats "folder + picker" for the Drachenbuch.*

**Phase 2 — the scaling board.** Tags + filter + search · practice badges + smart groups
(Redo/Unseen) · per-board session recipe · clipboard paste incl. bitmaps · pin-from-session ·
board list/MRU.

**Phase 3 — the delightful board.** Freeform canvas mode · always-on-top reference strip ·
palette extraction · contact-sheet export · templates.

### 3.7 Open decisions *(answered — see §4)*

1. **Grid-with-groups first, freeform canvas later?** (This doc assumes yes; freeform becomes a
   layout *mode* on the same data.)
2. **External material: copy into the board folder** (recommended — broken links impossible) **or
   reference in place** (no duplication, but fragile)?
3. **One board per folder as sidecar** (recommended) — plus a lightweight MRU list of known boards
   in settings?
4. **Notes: is plain text enough for v1?** (PureRef 2.0-style rich text/checklists feel like
   overkill here.)
5. **Where does drawing happen — paper or tablet?** Paper → contact-sheet export and a
   second-monitor board matter more; digital → the always-on-top strip climbs the list.
6. **Naming**: "Board"? "Sketch board"? "Sammlung"? (Doc says Board.)

---

## 4. Decisions (2026-08-30)

Answers to §3.7, plus additions from the same discussion:

1. **Grid-with-groups first** — yes; the freeform canvas comes later as a layout *mode* on the
   same data.
2. **Copy on import** — yes: pasted or browsed material is copied into the board folder. Copying
   an image online and pasting it onto the board must work.
3. **Sidecar file, "plugin" code** — the board stays a sidecar at the file level, and becomes a
   decoupled module behind a small API at the code level; image folders remain general-purpose
   and are never reorganized by the board.
4. **Plain-text notes** for Phase 1; formatting (bold, colour, …) goes on the roadmap.
5. **Paper *and* Krita** — no cut between contact-sheet and always-on-top; both stay on the
   roadmap (Phase 3).
6. **Name: Idea Board** — default cork look (papyrus/plain as alternatives), changeable per board.

Additions: an **immersive full-screen mode** for the board and **copy-out** (Ctrl+C on the board →
paste into Explorer creates duplicates) join Phase 1; a three-level [ROADMAP.md](ROADMAP.md)
(milestones → features → tasks, with done/in-progress/open states) tracks everything.

Shaping Q&A (same day, [docs/IdeaBoard-Shaping.md](docs/IdeaBoard-Shaping.md) §10): membership is
**explicit** and boards are **created first** (the folder is backing store — adding to the board
writes into the folder, never the reverse) · imports land in `_imported/` (in-folder files are
referenced in place) · practice folders and boards stay two concepts · external renames may break
cards in Phase 1 (fix on roadmap, M2) · **tags moved into Phase 1**.

---

## Sources

- PureRef 2.0 announcement — [pureref.com/blog/pureref2](https://www.pureref.com/blog/pureref2/) · [feature handbook](https://www.pureref.com/handbook/2.0/features/)
- PureRef 2.1 (grids, lines, notes) — [digitalproduction.com](https://digitalproduction.com/2026/02/11/pureref-2-1-adds-grids-lines-notes-and-control/)
- Alternatives landscape (BeeRef, Kuadro, Eagle, Milanote, refern) — [refern.app comparison](https://www.refern.app/alternatives/best-pureref-alternatives) · [kosmik.app blog](https://www.kosmik.app/blog/pureref-alternatives) *(vendor-written; feature claims cross-checked, tone discounted)*
- Eagle feature set — [en.eagle.cool](https://en.eagle.cool/) · [organizing with folders & tags](https://en.eagle.cool/blog/post/how-to-organize-files-with-logic)
- Compose Multiplatform drag & drop — [kotlinlang.org docs](https://kotlinlang.org/docs/multiplatform/compose-drag-drop.html) · [Compose 1.7 release notes](https://blog.jetbrains.com/kotlin/2024/10/compose-multiplatform-1-7-0-released/)
