# ActionDraw

A small desktop tool for **action drawing**, in three parts:

- **Draw** — point it at a folder of reference images and it shows them one at a time, in random
  order, on a timer, to practice and improve your drawing skills.
- **Idea Boards** — collect material (drawings, photos, studies) into boards with notes, groups
  and tags, then draw from any selection.
- **Concepts** — a thing that lives once (a character, a creature, a landscape) in a folder of
  its own, with pictures, notes, links and Markdown documents, linked onto any number of boards.

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
- **Memory drawing** (*From memory* ramp): the reference is up for a study time, then goes away
  and you draw from what you kept. It comes back when the drawing time is up so you can see what
  you missed — that comparison is the point, so a memory pose waits for you rather than advancing
  on its own. `H` peeks while it is hidden, and covers an ordinary reference so any picture can be
  worked this way.

### Reference views & filters
- **View modes** (mutually exclusive; number row `1`–`9`): None, **Black & white**,
  **Squint** (low contrast), **Sepia**, **Posterize**, **Pixelate**, **Edge** (outline),
  **Silhouette** (threshold), and **Notan** (2/3-value study, also on `N`).
- **Colour temperature**: one slider from cool daylight through neutral to warm lamplight
  (`,` cooler, `.` warmer, `0` neutral). It is an adjustment rather than a mode, so it stacks on
  whatever else is on — a Notan study can still be lit warm.
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
- The menu offers **Draw**, **Boards** and **Concepts** as equal entry points. **Boards** opens the board list:
  a tile per board with its cover picture, counts and path, plus **New board…** and **Explore…**
  for a board folder elsewhere.
- New boards are created under the **boards home** (`~/ActionDraw Boards` by default), which the
  picker lets you change.
- **Deleting a board** (the *Delete…* link on its tile) removes the board but leaves the folder
  and every picture in it — the board file is ActionDraw's, the pictures are yours. Ticking
  *Also delete the folder* erases them too, which is the one destructive thing the app does; it
  refuses to touch a folder that is not a board, your home folder, or the boards home itself.
- Boards are made first and filled afterwards: a card exists because you put it there, never
  because a file happened to be in the folder.

### Collecting
- **Drag & drop** files or folders from Explorer, **paste** with `Ctrl+V` (a file list, or an
  image copied in a browser — it is written out as PNG), or use **Import…**.
- Material from outside is copied into the board's `_imported/` folder, so a board is
  self-contained; files that already live inside the board folder are referenced where they are.
- **`Ctrl+C`** puts the selected pictures on the clipboard as files — paste them in Explorer and
  you get copies. A notes-only selection copies as plain text.

### Settings and hotkeys
- **Settings** and **Hotkeys** are buttons on the start menu, under *Draw* and *Boards*, and
  entries in every board's **⋯** menu. On the start menu, Settings holds the boards home, the
  reference folder, and whether dragged cards snap to their neighbours' centre lines — **off**
  unless you switch it on — and Hotkeys lists every shortcut for the session and the board.
  Inside a board, both show only what pertains to the board: *Board settings* (boards home,
  snapping) and the board's hotkeys.
- Pressing **Enter** in a name field — a new group, a rename — confirms it.

### The header
- One line: the board's **name** (click it to rename) · **Boards ▾** · **Concepts ▾** · **↑ Parent** when nested ·
  **Grid | Free** · **Search** · **Contents** · **+ ▾** (new note, new link, group, import,
  paste) · **⋯** (theme, snap, float strip, wallpaper, contact sheet, session recipe, shortcuts,
  immersive, close). Tag chips appear under it only when the board has tags, and the action bar
  at the bottom — draw, view, group, palette, copy, move to — appears only while something is
  selected. An empty board is just the board.

### Wallpaper
- **⋯ → Wallpaper…** gives a board a picture behind the cards: choose one, or
  right-click a picture already on the board and *Use as wallpaper*. It is copied into the board's
  `_wallpaper/` folder, so it moves with the board and is never offered back as a card. Fit it as
  *cover*, *tile* or *centre*; dim it so the cards stay readable; blur it if it is busy. On the
  canvas it drifts at a third of the camera's pace, so the board reads as a surface the cards lie
  on. The theme's texture shows through wherever it does not reach, and the contact sheet leaves
  it out — the sheet is the material.

### Boards inside boards
- A board made **inside another board's folder** is a sub-board of it. Nothing extra is stored —
  it simply follows from where the folders are — and nesting goes as deep as you like, each board
  belonging to the nearest one above it.
- **Boards ▾** in a board's header lists every board, nested under the one it belongs to: jump
  straight there, make a **new sub-board here**, or go to the full list. A sub-board also shows
  **↑ Parent** for one tap back up.
- **Move…** on a board tile (or *Move this board…* in **Boards ▾**) puts a board anywhere in the
  tree at any time — under another board, or back out to the top. Its folder moves with it, taking
  its pictures and any boards nested inside; the board on screen follows its folder, so nothing
  has to be reopened. A board cannot be moved into itself or into one of its own sub-boards, and
  a name already taken at the destination gets a folder of its own rather than merging.
- Deleting a board's folder takes any board nested inside it, and the dialog says how many before
  you confirm. *Remove board* (keeping the folder) leaves them alone — they simply become boards
  in their own right.

### Where boards live
- A board is a folder plus a `.actiondraw_board.json` sidecar, and ActionDraw records which folder
  belongs to which board in `~/.actiondraw/boards.json`. The board's name is therefore not tied to
  its folder's name: if the folder name is taken, the board gets one beside it (`Test (2)`) and
  keeps the name you gave it.
- **Change home…** sets where *new* boards are created. Boards you already have stay exactly where
  they are and stay in the list — moving the home adds a place to look, it never hides or moves
  anything.
- **Delete…** on a board tile follows whose folder it is. A folder ActionDraw created for the board
  is deleted along with it; a folder that was already yours — one you opened with *Explore…* — is
  kept, and only stops being a board. The tick in the dialog lets you choose the other way in
  either case, and the destructive path refuses anything that is not a board folder, your home
  folder, the boards home itself, or a drive root.

### Organising
- **Two layouts** per board, switchable in the header:
  - **Grid** — cards in collapsible group sections (unassigned cards sit in the **Inbox**), each
    with its own colour and per-group ordering.
  - **Free** — a pan/zoom canvas where every card has its own position, size and rotation.
    `Shift`+drag pulls a rubber band over several cards, and dragged cards snap to their
    neighbours' centre lines (toggle with the **Snap** chip).
    Groups show as a tinted, outlined frame with a name label. The frame is shaped to the cards
    — the union of their padded boxes, rounded off, so an L-shaped arrangement gets an L-shaped
    frame — and the space between pictures of one group is always covered: pull a picture away
    and the frame stretches with it as a full band, never thinning to a line. Only the frame
    answers: click it
    (or the label) to select the whole group, drag it to move the group as one, drag a card
    inside it to move just that card, and right-click it to draw, rename, recolour or delete
    it. A click in the empty corner of an L lands on the board, not the group. The label stays
    in view even when the group runs off the edge of the window. Grouped cards carry a small dot
    in their group's colour. A group only shows once it holds something.
- **Two kinds of note** (`N`, or **+ ▾**). A **document note** shows only its title on the board
  — its first `# heading`, else its first line — and a click opens the whole note in a popup,
  rendered in a small Markdown: `# headings`, `**bold**`, `*italic*`, `` `code` ``, `-` and `1.`
  lists, `---` rules, and `[text](url)` links that open in your browser. A **post-it** shows all
  of its text exactly as typed, in a written hand, on paper sized to the text. Either way the
  note stays plain text in the board file, with a paper colour and an optional heading style;
  **link cards** (`L`): the title, underlined, and a click opens it in your browser (select one
  with Ctrl+click, a drag, or right-click); *Fetch preview* on the right-click menu saves the
  picture the page advertises beside the title; one-line **captions** (`F2`), **stars** (`S`)
  and **tags** (`T`) with an AND-filter chip bar, and a **search box** that matches file names,
  captions, tags, note text and link addresses.
- **Palettes** (`P`): the dominant colours of a picture as swatches with hex values.
- **Grouping**: select cards and press `G` (or *Group (n)* in the action bar) to make a group of
  them. In grid mode a section's name selects that group's cards
- **Adding a card to a group later**: on the canvas, drag it onto one of the group's *pictures*
  and let go — the frame brightens while a drop would file, and a line at the top says what
  happened. Letting go merely near a group, or in the space between its pictures, files nothing:
  a card only joins a group when you put it *on* the group. Or right-click → **Add to ‹group›**
  (*Move to* when it is in another group already). In grid mode, drag it onto the group's header.
- **Right-click a card → Remove from ‹group›** takes it out of its group: into the parent when it
  was in a subgroup, otherwise into the Inbox.
- **Subgroups**, one level deep: when you group a selection or make a new group, the dialog
  offers *Inside…* a top-level group; a group's menu has *Move into…* and *Make top-level*. A
  subgroup is an indented section under its parent in grid mode and a lighter area inside the
  parent's on the canvas. The parent counts, draws, selects and drags its subgroups' cards as
  its own; collapsing the parent folds them away. *Dissolve* on a subgroup lifts its cards into
  the parent; deleting a parent lifts its subgroups to the top level. Anything deeper than one
  level in a board file is flattened on load — the way to group on the canvas. With nothing selected the same command starts an empty
  group. `Ctrl`+`Shift`+`G` takes cards back out, and a group left holding nothing disappears by
  itself.
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

## Concepts — things that live once

A **board** is a place; a **concept** is a thing: this character, this creature, this landscape.
Things recur across places, and copying their pictures onto every board lets the copies drift
apart. A concept lives once, in a folder of its own, and is **linked** onto any number of boards.

### A concept
- **Concepts** on the start menu opens the list: tiles grouped by *kind* (character, creature,
  landscape, prop, colour — a label with suggestions, not a fixed set), each with a cover, its
  counts, and how many boards link it. **New concept…** makes a folder under the concepts home
  (`~/ActionDraw Concepts` by default; **Change home…** moves it, and existing concepts stay where
  they are and stay listed).
- A concept holds **pictures** (copied into its `_imported/` folder, exactly as a board does),
  **notes**, **links** and **documents** — Markdown files in its `_docs/` folder, rendered on the
  concept's page and edited in a text box with a live preview. **+ ▾** adds any of them.
- Everything the concept knows lives in `.actiondraw_concept.json` next to its files, and
  `~/.actiondraw/concepts.json` records which folder belongs to which concept, by id — so a
  concept keeps its identity wherever its folder goes, and a concept folder found under the home
  is adopted as it is found.
- **Delete…** names the boards the concept is linked on before it agrees, takes it off them, and
  deletes the folder — or, with the tick cleared, only the sidecar, leaving the files.
- `Esc` closes a dialog, then the concept, then the list.

### On a board
- **Concepts ▾** in a board's header lists every concept: *Link ‹name›* puts it on the board, a
  linked one (⧉) jumps to it. **Boards…** on the concept's page does the same from the other
  side, with a tick per board.
- A linked concept is a **group of its own** on the board, marked ⧉ and outlined in dashes: the
  concept itself, not a copy. Add a picture to the concept and every board that links it has the
  picture; remove one and it is gone everywhere. The group cannot be renamed, recoloured, nested
  or dissolved from the board, and its cards cannot be removed, moved out or captioned there —
  the one thing to do with the group is **Unlink**, which takes it off this board and touches
  nothing in the concept.
- Everything a board does *with* cards works on a concept group: draw from it, view it large,
  put it in the float strip, search it, star and tag its cards. Place, stars and tags are the
  board's own opinions and stay with the board; the picture, caption and text follow the concept.
- **Dropping the board's own card onto a concept group** (or *Move to ⧉ ‹name›*) adds it to the
  concept: the picture is copied into the concept's folder, and the borrowed card takes the
  board's card's place — here and on every other board that links the concept.
- Practice memory of a borrowed card (drawn, never drawn, redo) is kept per board, like
  everything else a board remembers about its cards.

## Keyboard shortcuts

### Session

| Key | Action |
|---|---|
| `Space` | play / pause |
| `←` / `→` | previous / next image |
| `1`–`9` | view mode (None … Notan) |
| `N` | Notan view |
| `,` / `.` / `0` | cooler light · warmer light · neutral |
| `H` | hide the reference · peek at it while drawing from memory |
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
| wheel · `+` / `−` · `0` | in the large view: zoom about the pointer · zoom in / out · fit again (a tablet dial counts as the wheel; once zoomed, drag pans) |
| `←` `→` · `Home` / `End` · `Space` | in the large view: flip · first / last picture · close |
| `Enter` | draw the selection |
| `N` / `L` | new note · new link |
| `G` (or `Ctrl`+`G`) / `Ctrl`+`Shift`+`G` | group the selection · ungroup it |
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

## Going online

ActionDraw is an offline application with exactly one exception: *Fetch preview* on a link card.
Choosing it asks first, then makes a single plain GET to that address, reads the picture the page
advertises for sharing (`og:image`, else `twitter:image`) and saves it into the board's
`_previews/` folder. Only `http` and `https` are followed, at most 8 MB, with short timeouts, no
cookies and no referrer.

The cost is the ordinary one: the site learns that you opened the link. That is why it is never
automatic, never done in the background, and always one card at a time. Afterwards the picture is
a normal file in your board folder and the board works offline again. *Remove preview* takes it
back off the card.

Nothing else in the app makes a network request — no updates, no telemetry, no fonts or icons
loaded from anywhere.

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
