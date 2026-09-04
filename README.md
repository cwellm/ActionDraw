# ActionDraw

A small desktop tool for **action drawing**, in two halves:

- **Draw** — point it at a folder of reference images and it shows them one at a time, in random
  order, on a timer, to practice and improve your drawing skills.
- **Idea Boards** — collect material (drawings, photos, studies) into boards with notes, groups
  and tags, then draw from any selection.

Built with Compose for Desktop (Kotlin/JVM), so the same code runs on Windows and Linux
(e.g. ArchLinux).

## Draw — timed reference practice

### Sessions & timing
- Pick a folder; images are shown in **random order**. The folder is **remembered across
  restarts** (`~/.actiondraw/settings.properties`); a folder that has moved or been deleted is
  silently forgotten.
- **Fixed time per image**: 30 s to 10 min in 30 s steps, then up to 60 min in coarser steps.
  Adjust it **while paused** mid-session — the elapsed time is left untouched.
- **Gesture ramps**: predefined life-drawing structures that advance from short poses to longer
  ones — *Quick warm-up*, *Classic gesture*, *Long studies*.
- **Auto-advance** toggle (`A`): on = the timer moves to the next image at 0; off = the countdown
  is informational only, runs into "+overtime", and switching stays manual.
- **Session summary** at the end: poses drawn and total time.

### Reference views & filters
- **View modes** (mutually exclusive; number row `1`–`0`, plus `N`): None, **Black & white**,
  **Squint** (low contrast), **Sepia**, **Posterize**, **Pixelate**, **Warm**, **Cool**,
  **Edge** (outline), **Silhouette** (threshold), and **Notan** (2/3-value study).
- **Independent toggles**: **Blur** (`B`), **Mirror** (`M`), **Upside down** (`U`),
  **Invert** (`I`), and **Defraction** (`D`) — a cubist shard mosaic, re-rolled randomly every
  time it is switched on.
- **Adjustable parameters** via sliders under the view row: blur radius, posterize bands,
  pixelate block size, silhouette threshold, notan values (2/3) and threshold, defraction shard
  size and strength.
- **Proportion grids** (`G` cycles): **Thirds**, **Phi** (golden section), **Diagonal** — each
  with a centre cross.

### Choosing & remembering pictures
- **Picture picker** (menu → "Choose pictures…"): a thumbnail grid to include/exclude images,
  with All/None; sessions draw only from the selection.
- **Remembers what you've drawn**: shown images are recorded per folder in
  `.actiondraw_seen.txt` and skipped next time. Once every (selected) image has been shown, the
  cycle resets — for the current selection only.
- **Redo flags** (`R`): flag an image mid-session and it resurfaces first next session; the flag
  clears once the image has been redrawn. Stored per folder in `.actiondraw_redo.txt`.
- Both state files are plain text inside the image folder — safe to delete for a fresh start.

### Display
- **Fullscreen** (`F`): the image fills the entire screen with only the remaining time in the
  bottom corner. `Esc` first leaves fullscreen; pressed again (windowed) it stops the session.

## Idea Boards — collect your material

A **board** is a folder raised to a curated collection: images and plain-text notes as cards,
organised into groups (or placed freely), described by captions, stars and tags. Everything the
board knows lives in one file next to the pictures — `.actiondraw_board.json`. Delete it and you
still have an ordinary folder of images; the board never renames, moves or deletes your files.

### Getting started
- The menu offers **Draw** and **Boards** as equal entry points. **Boards** opens the board list:
  a tile per board with its cover picture, counts and path, plus **New board…** and **Explore…**
  for a board folder elsewhere.
- New boards are created under the **boards home** (`~/ActionDraw Boards` by default), which the
  picker lets you change.
- Boards are made first and filled afterwards: a card exists because you put it there, never
  because a file happened to be in the folder.

### Collecting
- **Drag & drop** files or folders from Explorer, **paste** with `Ctrl+V` (a file list, or an
  image copied in a browser — it is written out as PNG), or use **Import…**.
- Material from outside is copied into the board's `_imported/` folder, so a board is
  self-contained; files that already live inside the board folder are referenced where they are.
- **`Ctrl+C`** puts the selected pictures on the clipboard as files — paste them in Explorer and
  you get copies. A notes-only selection copies as plain text.

### Organising
- **Two layouts** per board, switchable in the header:
  - **Grid** — cards in collapsible group sections (unassigned cards sit in the **Inbox**), each
    with its own colour and per-group ordering.
  - **Free** — a pan/zoom canvas where every card has its own position, size and rotation.
    `Shift`+drag pulls a rubber band over several cards, and dragged cards snap to their
    neighbours' centre lines (toggle with the **Snap** chip).
    Groups show as a tinted, outlined area with a name label: drag the area to move the whole
    group, drag a card inside it to move just that card, click the label to select the group, and
    right-click the area to draw, rename, recolour or delete it. Grouped cards carry a small dot
    in their group's colour. A group only shows once it holds something.
- **Note cards** (`N`) with `**bold**`/`*italic*`, a paper colour and an optional heading style;
  **link cards** (`L`) that open in your browser; one-line **captions** (`F2`), **stars** (`S`)
  and **tags** (`T`) with an AND-filter chip bar, and a **search box** that matches file names,
  captions, tags, note text and link addresses.
- **Palettes** (`P`): the dominant colours of a picture as swatches with hex values.
- **Grouping**: select cards and press `Ctrl`+`G` (or *Group* in the action bar) to make a group
  of them — the way to group on the canvas. `Ctrl`+`Shift`+`G` takes cards back out, and a group
  left holding nothing disappears by itself.
- **Contents drawer** (`Ctrl`+`D`, or the *Contents* button): everything on the board as a list,
  grouped, with thumbnails. Collapse a group, click a row to select it and bring the camera to
  it, or use a group's *Select · Draw · Rename · Ungroup*.
- **Reorder by dragging** a card onto another within its group, or with the menu and keyboard
  below.
- **View large** (`Space`, or the View button): the selected pictures fill the screen. With more
  than one it is a **carousel** — drag sideways, use the chevrons, the wheel, `←`/`→`, or click a
  thumbnail in the filmstrip. With nothing selected it shows everything on the board.
- **Reordering** via right-click or `Ctrl`+`↑`/`↓` (`+Shift` goes all the way): in Grid this is
  the position within the group, in Free the stacking order (send that note behind the photo).
- **Themes** per board: **cork** (default), **papyrus**, **plain** — generated textures, no
  bundled assets.
- **Immersive** (`F` or ⛶): fullscreen with all chrome hidden, for browsing on a second monitor.
  Whenever the window is not fullscreen, every menu stays visible.
- **Float strip**: a small always-on-top window showing the selection — one picture big with a
  filmstrip underneath — so a reference stays visible while you paint in another program. It is a
  carousel too: drag sideways or use the wheel to flip through the selection.
- **Contact sheet…**: renders the selection (or the whole board) as one printable PNG.
- New boards can start from a **template** (Creature design, Character sheet, Environment,
  Anatomy practice) instead of empty.

### Drawing from a board
- **Draw selection** (`Enter`) or a group's **Draw** button starts an ordinary practice session
  with exactly those pictures — every filter, ramp and grid applies as usual.
- **Practice badges** mark what you have drawn (`✓`) and what you flagged to redo (`⟳`), and two
  **smart sections** — *⟳ Redo* and *Never drawn* — sit above the groups with their own Draw
  buttons. They are views: a card stays in its own group.
- A **session recipe** per board ("Drachenbuch is always 60 s in Notan") remembers interval or
  ramp, auto-advance, view mode and grid, and applies to every session started from that board.
  Without one, the menu's settings are used.
- **Pin ▾** during any session files the picture on screen onto a board; the summary offers to pin
  the pictures you flagged for redo.
- Board sessions open in **their own window**: the board stays visible behind them, closing the
  window aborts the drawing, and the summary offers **Back to board**.
- Seen/redo state for a board lives in the board folder, keyed by relative path.
- Cards remember their picture by content as well as by path, so renaming or moving a file
  outside the app keeps its caption, tags, group and position.

## Keyboard shortcuts

### Session

| Key | Action |
|---|---|
| `Space` | play / pause |
| `←` / `→` | previous / next image |
| `1`–`0` | view mode (None … Silhouette) |
| `N` | Notan view |
| `B` / `I` / `D` / `M` / `U` | blur / invert / defraction / mirror / upside down |
| `G` | cycle proportion grid |
| `R` | toggle redo flag |
| `A` | toggle auto-advance |
| `F` | toggle fullscreen |
| `Esc` | leave fullscreen · stop session · close picker/summary |
| `Enter` | close picker/summary |

### Board

| Key | Action |
|---|---|
| click · `Ctrl`+click · `Shift`+click | select · toggle · range |
| `Ctrl`+`A` / `C` / `V` | select all · copy · paste |
| `←` `→` `↑` `↓` | move focus (Grid) · nudge the selected card (Free) |
| `Ctrl`+`↑`/`↓` | reorder one step (`+Shift`: all the way); drag a card for free placing |
| `Space` | view the selection large (carousel) |
| `Enter` | draw the selection |
| `N` / `L` / `G` | new note · new link · new group |
| `Ctrl`+`G` / `Ctrl`+`Shift`+`G` | group the selection · ungroup it |
| `Ctrl`+`D` | contents drawer |
| `S` / `T` / `P` / `F2` | star · tags · palette · caption |
| `Del` | remove card from the board (the file stays) |
| `F` | immersive mode |
| `Esc` | leave immersive · close the large view · close the board |

In **Free** layout the mouse does the rest: drag a card to move it, drag the corner handle (or
`Ctrl`+wheel) to resize, the top handle (or `Shift`+wheel) to rotate; drag empty space to pan,
`Shift`+drag it to rubber-band a selection, and use the wheel to zoom. In **Grid** layout a card
can be dragged onto another to reorder it, or onto a group header to file it there.

More ideas and the filter backlog live in [IDEAS.md](IDEAS.md); the board's design notes are in
[docs/IdeaBoard-Shaping.md](docs/IdeaBoard-Shaping.md) and planned work in [ROADMAP.md](ROADMAP.md).

## Image formats

JPEG, PNG, GIF, BMP and **WebP** are decoded by the bundled Skia. **AVIF** works too, through an
ImageIO plugin that ships with the app (`io.github.nemanjastokuca:avif-imageio-native-reader`,
libavif/libdav1d as prebuilt JNI binaries, ~7 MB, x86-64 Windows/macOS/Linux).

Decoding runs through `ImageDecoder`: Skia first, ImageIO as a fallback. Formats that need a
plugin are only advertised when a reader is actually installed, so a board never shows a card
whose picture cannot be drawn — drop in a HEIC reader and `.heic` files start working with no code
change. Remove the AVIF line from [build.gradle.kts](build.gradle.kts) and `.avif` files simply
stop being offered; nothing else breaks.

An import that leaves something out (unreadable format, or a picture already on the board) says so
on the board instead of silently ignoring the drop.

## Requirements
- JDK 17 (a `JAVA_HOME` pointing at a JDK 17 install) — only for running from source and
  building. The native installers below bundle their own Java runtime.

## Run
```sh
./gradlew run        # on Windows: gradlew.bat run
```

## Test
```sh
./gradlew test
```

## Build a native installer
```sh
./gradlew packageMsi    # Windows installer
./gradlew packageDeb    # Debian/Linux package
```

Step-by-step guides, including install/upgrade/uninstall verification:
[Windows (MSI)](docs/Packaging-Windows.md) ·
[ArchLinux (pacman package via PKGBUILD)](docs/Packaging-ArchLinux.md).
