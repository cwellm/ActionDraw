# ActionDraw's architecture

ActionDraw is built as Gradle modules in three groups. A module can use only what it declares as a
dependency, so the build — not a convention — holds the boundaries below. M10 in the
[roadmap](../ROADMAP.md) built it, step by step, each one broken on purpose to see its guards hold.

- **`lib`** — what an application builds on, in two halves:
  - the **bare lib**: `core` (`model`, `io`, `lifecycle`), `network`, `domain`, `data` and the
    `sketch-engine`. Headless, usable on its own, with no UI at all;
  - the **UI kit**: the design system (its contract and the default *atelier*), the *lens* that
    every room showing a picture sees it through, and the features, one per room — the convenient
    way of putting the bare lib to use, ready for a GUI.
- **`app`** — the applications on top. Today there is one, `desktop`.
- **`device`** — the hardware the apps talk to natively. Today there is one, `pen`.

## The shape

```mermaid
flowchart TB
  subgraph APP["app"]
    desktop[":app:desktop<br/>window · navigation · the desktop's own OS"]
  end
  subgraph DEVICE["device"]
    pen[":device:pen<br/>Windows Ink, through JNA"]
  end
  subgraph LIB["lib"]
    subgraph UI["UI kit"]
      features[":lib:feature:*<br/>practice · board · concept · sketch · lens"]
      lens[":lib:lens<br/>lenses · knobs · grids · the glass"]
      atelier[":lib:design:atelier<br/>the default design system"]
      contract[":lib:design:contract<br/>tokens · materials · chrome"]
    end
    subgraph BARE["bare lib"]
      data[":lib:data<br/>FileLib: the domain over the files"]
      domain[":lib:domain<br/>the API: types · Lib · contracts"]
      network[":lib:network<br/>HTTP, http and https only"]
      engine[":lib:sketch-engine<br/>pen samples to pixels"]
      subgraph CORE["core"]
        model[":lib:core:model<br/>how things are stored"]
        io[":lib:core:io<br/>files · pictures"]
        lifecycle[":lib:core:lifecycle<br/>loading · memory · async"]
      end
    end
  end
  desktop --> features
  desktop --> atelier
  desktop --> pen
  pen --> domain
  features --> lens
  features --> contract
  features --> domain
  lens --> contract
  lens --> domain
  atelier --> contract
  data --> domain
  data --> model
  data --> io
  data --> lifecycle
  data --> network
  data --> engine
```

Simplified: `desktop` also sees `domain`, `lifecycle` and the design contract, `sketch` also uses
the engine, and `atelier` the engine for the paper's grain. A feature does not see `data`: it works
with the domain's `Lib`, which the app makes from `data` and hands in.

## The layers

- **`model`** — how things are stored: the sidecars' and the registries' formats (`BoardJson`,
  `ConceptJson`, …), field for field what the files on disk hold. Only `data` sees it.
- **`io`** — files: the app's own folders, writing a file whole or not at all, moving and deleting
  folders, names Windows accepts, listing and decoding pictures.
- **`lifecycle`** — the lib's run: loading it, the scopes and dispatchers its async work runs in,
  the memory it holds while running (caches, what is open) and giving it back, and closing down
  cleanly — whatever is still to be written is written before the app goes.
- **`network`** — the one way online: a GET for http and https only, with a size cap.
- **`domain`** — the API: the types and contracts callers work with, abstracted from the model —
  just what an application needs. Everything there is to get and keep comes through `Lib`: its
  `boards`, `concepts`, `pictures`, `practice` history, `sketches`, link `previews`, `preferences`
  and `diagnostics`, each answering in types of its own (`BoardMove`, `PreviewResult`, …) and
  leaving the words to the room that shows them. It also says, as contracts, what the lib needs
  from its host: the clipboard, choosing files, opening a link, the pen's input. It depends on
  nothing of ours; a picture comes as a Skia image, what Compose Desktop and the engine draw with.
- **`data`** — `FileLib`, the implementation of `Lib` over `core` and `network`: the
  repositories over the files, mapping what is stored to what callers see. What is written often
  — a board's file after every edit — goes through `lifecycle`'s writes, off the UI thread; a read
  waits for its folder's write, deleting or moving a folder waits for every write on its way.
- **`sketch-engine`** — the drawing engine, a library of its own.
- **`design:contract`** — the design system's contract: tokens (colours, type, lifts, motion),
  materials and room chrome as interfaces, read through the theme the way Material's
  `MaterialTheme` is.
- **`design:atelier`** — the default design system, in Compose: the atelier of
  [CONCEPT.md](../CONCEPT.md). Another design system implements the same contract.
- **`lens`** — how a picture is seen: the lenses and the knobs over them, the proportion grids and
  the glass, with the tray that holds them, the picture they work on and their keys, over one
  `LensState`, and a view drawn off screen to be kept. Part of the UI kit and no room of its own:
  a session in the Practice room sees its picture through it, and so does the Lens room.
- **`feature:*`** — a room as a unit: the contracts it adds in its own domain layer (its host, what
  it asks of other rooms), its state and logic over the domain's `Lib`, and its screens on the
  design contract. The desktop's services reach it as the domain's host contracts, handed in.
- **`app:desktop`** — on top: the windows, navigation between the rooms, the design system chosen,
  the desktop's implementations of the host contracts (AWT clipboard, Swing dialogs, the
  desktop's browser, Windows' animation setting), and the pen wired in from `device:pen`.
- **`device:pen`** — the pen's own input: Windows Ink through JNA.

## The rules

1. **The bare lib has no UI**: no Compose, no Swing, no JNA. It can be tested headless and used
   by any application.
2. **The core modules stand alone**, depending on nothing of ours and not on each other.
3. **`domain` depends on nothing of ours.** It is the contract; everything else implements it,
   uses it or extends it.
4. **Only `data` reaches the files and the network.** It takes `io` and `network` as
   `implementation`, so they are on nobody else's classpath: no screen can read a file or go
   online by itself.
5. **`sketch-engine` depends on nothing of ours** and could be published without the rest.
6. **A `device` module depends on the domain alone**, whose host contract it fills; the app wires
   it in.
7. **Nothing in the lib depends on a feature, and no feature on another.** What one room needs of
   another is a contract, in the domain or in the room's own domain layer, connected by the app.
   What several rooms draw with lives below them in the UI kit — the design contract, the lens.
8. **Features use the design contract, never `atelier` itself**, so another design system
   restyles them.
9. **Compose lives in the UI kit and the app**; the operating system's services — the clipboard,
   file dialogs, the browser, native hooks (Swing, AWT's desktop services, JNA) — only in the app
   and `device`, behind the domain's host contracts.
10. **The files stay the truth.** A board is a folder plus a sidecar; there is no database. An
    index (search across boards, pictures by content) could later sit inside `data` without anyone
    above noticing.
11. **Package names stay when files move**, so a move changes no imports.

## What goes where

| Module | Comes from |
|---|---|
| `:lib:core:model` | `:core:model` (F11.1) |
| `:lib:core:io` | `:core:io` (F11.2) |
| `:lib:core:lifecycle` | new (F11.6): the run, its writes and its caches; before it each state picked its own `Dispatchers.IO`, and nothing was flushed on close |
| `:lib:network` | `:core:network` (F11.3) |
| `:lib:domain` | the types of `:core:model` (F11.7: `Board`, `Concept`, …); `Lib` and its contracts — `Boards`, `Concepts`, `Pictures`, `PracticeHistory`, `Sketches`, `LinkPreviews`, `Preferences`, `Diagnostics` (F11.8); the host contracts `SystemClipboard`, `Browser`, `FileDialogs`, `HostPreferences` and `PenSource` |
| `:lib:data` | `FileLib`, over `BoardStore`, `ConceptStore`, the registries, `Settings`, `SeenStore`, `RedoStore`, `ThumbCache`, `Importer`, `LinkPreview`, `ContactSheet`, `Palette` — and the storage that was inside `BoardState`, `ConceptState`, `SketchState`, `AppState` (F11.8) |
| `:lib:sketch-engine` | `:sketch-engine` |
| `:lib:design:contract` | new: `DesignSystem`, `Materials` and `Chrome` as contracts, read through `DesignTheme`; and the kit's basics, `ui/Motion.kt` (without the bloom) and `Components.kt` (F11.9) |
| `:lib:design:atelier` | `ui/Atelier.kt`, `Materials.kt` (now `AtelierMaterials`), `RoomChrome.kt` (now `AtelierChrome`), the bloom out of `Motion.kt`, the fonts (F11.9) |
| `:lib:lens` | the lens out of the Practice room (F10.1): `Filters.kt`, the glass (`Glass.kt`, `GlassView.kt`), `Grids.kt`; out of `SessionScreen` the tray and its settings (`LensControls`) and the picture (`LensView`), out of `SessionKeys` the lens's keys; the session's lens settings as one `LensState`; new, `lensedPng` |
| `:lib:feature:practice` | the Practice room out of `AppState` (now `PracticeState`, F11.10), `PinTargets`, and its screens (`ui/PracticeScreen`, `SessionScreen`, …) |
| `:lib:feature:board` | `BoardState` and `board/ui`, less the app's settings sheet |
| `:lib:feature:concept` | `ConceptState` and `concept/ui` |
| `:lib:feature:sketch` | `SketchState` and `sketch/ui` |
| `:lib:feature:lens` | new (F10.1): the Lens room — `LensRoomState`, its host `LensHost`, and `lens/ui` |
| `:app:desktop` | `Main` (the windows), `App` (the palette, the room on screen), `Rooms` (the rooms put together, F11.11), `Keys`, `AppState` (the way between the rooms), the settings sheet; the desktop's sides of the host contracts — `AwtClipboard`, `DesktopBrowser`, `SwingFileDialogs`, `SystemPreferences`, the pen delivered on the UI thread |
| `:device:pen` | `PenSource`'s Windows side, `WindowsPointerSource` (F11.5, F11.7) |

## How the app is put together

`app:desktop` makes the lib over the person's files (`FileLib`), puts the rooms together over it
(`Rooms`) with the desktop's own services, and opens the windows. `Rooms` is the one place that
knows which room answers which question — a board's session runs in the Practice room, a concept
is told which boards link it, a sketch is saved onto a board, a card's values are checked in the
Lens room — through the contracts each room owns. The rooms themselves know none of the others,
and a test builds the same `Rooms` over a temp folder with no desktop at all.
