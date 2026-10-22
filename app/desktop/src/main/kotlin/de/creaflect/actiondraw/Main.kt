package de.creaflect.actiondraw

import androidx.compose.material.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.creaflect.actiondraw.board.BoardHost
import de.creaflect.actiondraw.board.BoardState
import de.creaflect.actiondraw.board.ui.PointerLog
import de.creaflect.actiondraw.board.ui.ReferenceStrip
import de.creaflect.actiondraw.board.ui.handleBoardKey
import de.creaflect.actiondraw.concept.ConceptHost
import de.creaflect.actiondraw.concept.ConceptState
import de.creaflect.actiondraw.lifecycle.LibRun
import de.creaflect.actiondraw.platform.AwtClipboard
import de.creaflect.actiondraw.platform.DesktopBrowser
import de.creaflect.actiondraw.platform.SwingFileDialogs
import de.creaflect.actiondraw.platform.SystemPreferences
import de.creaflect.actiondraw.platform.onUiThread
import de.creaflect.actiondraw.practice.PracticePage
import de.creaflect.actiondraw.practice.PracticeState
import de.creaflect.actiondraw.sketch.SketchHost
import de.creaflect.actiondraw.sketch.SketchState
import de.creaflect.actiondraw.sketch.handleSketchChar
import de.creaflect.actiondraw.sketch.handleSketchShortcut
import de.creaflect.actiondraw.sketch.input.PenSources
import de.creaflect.actiondraw.ui.AtelierTheme
import de.creaflect.actiondraw.ui.SessionScreen
import de.creaflect.actiondraw.ui.SummaryScreen
import de.creaflect.actiondraw.ui.handleSessionShortcut
import de.creaflect.actiondraw.ui.handleSummaryKey
import java.io.File

fun main() = application {
    // The lib's run: the scope and the dispatcher its work goes to, and the writes it must not
    // lose. Closed when the app goes — by its window, or by the JVM shutting down under it.
    val run = remember {
        LibRun().also { run ->
            Runtime.getRuntime().addShutdownHook(Thread { run.close() })
            // A write that failed is not lost silently: there is no one to tell but the console.
            run.writes.onError = { System.err.println("ActionDraw: a write failed: ${it.message}") }
        }
    }
    // The bare lib over the person's files — the only way the app reaches the disk or the network.
    val lib: Lib = remember { FileLib(run).also { PointerLog.diagnostics = it.diagnostics } }
    // Roomy enough for a board, small enough to fit a 1080p screen at 125% scaling.
    val windowState = rememberWindowState(size = DpSize(1120.dp, 800.dp))
    val appState = remember { AppState(lib, SystemPreferences) }
    val practiceState = remember { PracticeState(lib, dialogs = SwingFileDialogs) }
    // Boards and concepts need each other — a concept is linked onto boards, a board reads its
    // concepts — but each only through its host, and the holder lets them be built in turn.
    val boardHolder = remember { BoardStateHolder() }
    // Boards and concepts open a saved sketch again; Live Sketch is built after them.
    val sketchHolder = remember { SketchStateHolder() }
    // The main window, so a sketch started from a session's own window can come to the front.
    val mainWindow = remember { mutableStateOf<java.awt.Window?>(null) }
    val conceptState = remember {
        ConceptState(
            lib,
            object : ConceptHost {
                override fun showConcepts() = appState.showConcepts()
                override fun showConcept() = appState.showConcept()
                override fun leaveConcepts() = appState.leaveConcepts()
                override fun boardsFor(conceptId: String) = boardHolder.state.boardsFor(conceptId)
                override fun setLinked(conceptId: String, board: File, linked: Boolean) {
                    boardHolder.state.setLinked(conceptId, board, linked)
                }
                override fun unlinkEverywhere(conceptId: String) = boardHolder.state.unlinkEverywhere(conceptId)
                override fun openSketch(file: File) {
                    sketchHolder.state.open(file)
                    appState.showSketch(from = Screen.Concept)
                }
            },
            browser = DesktopBrowser,
            dialogs = SwingFileDialogs,
        )
    }
    // The board talks to the rest of the app only through this host (its "plugin" boundary).
    val boardState = remember {
        BoardState(
            lib,
            object : BoardHost {
                override fun startSession(root: File, images: List<File>, setup: SessionSetup?) =
                    practiceState.startBoardSession(root, images, setup)

                override fun showBoard() = appState.showBoard()
                override fun showBoardList() = appState.showBoardList()
                override fun leaveBoard() = appState.leaveBoard()
                override fun currentSetup(): SessionSetup = practiceState.currentSetup()
                override fun showConcept(id: String) {
                    conceptState.openById(id)
                }
                override fun showConcepts() = conceptState.openList()
                override fun openSketch(file: File) {
                    sketchHolder.state.open(file)
                    appState.showSketch(from = Screen.Board)
                }
                override fun sketchOver(picture: File) {
                    sketchHolder.state.openFromSession(picture)
                    appState.showSketch(from = Screen.Board)
                }
            },
            concepts = conceptState,
            // The desktop's own: its clipboard, its browser, its file dialogs.
            clipboard = AwtClipboard,
            browser = DesktopBrowser,
            dialogs = SwingFileDialogs,
        ).also { boardHolder.state = it }
    }
    // Lets a running session file pictures away on a board, without the session knowing what a
    // board is (see PinTargets).
    val pinTargets = remember {
        PinTargets(boards = { boardState.availableBoards() }, pin = boardState::pinTo)
    }
    // Live Sketch saves into boards and concepts through this seam, and never reaches past it.
    val sketchState = remember {
        SketchState(
            lib,
            object : SketchHost {
                override fun boards() = boardState.availableBoards()
                override fun addToBoard(dir: File, pictures: List<File>) = boardState.pinTo(dir, pictures)
                override fun concepts() = conceptState.available()
                override fun conceptDir(id: String) = conceptState.entryById(id)?.dir
                override fun addToConcept(id: String, pictures: List<File>) = conceptState.addToConcept(id, pictures, emptyList()) != null
                override fun leaveSketch() = appState.leaveSketch()
            },
            dialogs = SwingFileDialogs,
        ).also { sketchHolder.state = it }
    }
    val isFullscreen = windowState.placement == WindowPlacement.Fullscreen
    // Everything above is built: the lib is loaded.
    LaunchedEffect(run) { run.loaded() }

    // A session started from a board changes its seen/redo state; refresh the badges when the
    // session window closes.
    LaunchedEffect(practiceState.windowPage) {
        if (practiceState.windowPage == null) boardState.refreshPractice()
    }

    Window(
        // Closing with unsaved strokes asks first; everything else is saved as it happens.
        onCloseRequest = {
            sketchState.guardUnsaved {
                run.close()
                exitApplication()
            }
        },
        title = "ActionDraw",
        state = windowState,
        onKeyEvent = { handleKey(it, appState, practiceState, boardState, conceptState, sketchState, windowState) },
    ) {
        // The pen probe hooks the native window for pressure and tilt while its screen is up, and
        // lets go when it is left — nothing else in the app sees the pen as more than a mouse.
        LaunchedEffect(appState.screen) {
            if (appState.screen == Screen.Sketch) sketchState.onEnter(PenSources.forWindow(window)?.let(::onUiThread)) else sketchState.detach()
        }
        LaunchedEffect(Unit) { mainWindow.value = window }
        App(
            appState,
            practiceState,
            boardState,
            conceptState,
            sketchState,
            lib.pictures,
            pinTargets,
            isFullscreen = isFullscreen,
            onToggleFullscreen = { toggleFullscreen(windowState) },
            setFullscreen = { on ->
                windowState.placement = if (on) WindowPlacement.Fullscreen else WindowPlacement.Floating
            },
        )
    }

    // The reference strip floats above every other application, so a board can sit in the corner
    // of the screen while you paint somewhere else.
    if (boardState.stripOpen) {
        val stripState = rememberWindowState(size = DpSize(360.dp, 520.dp))
        Window(
            onCloseRequest = { boardState.closeStrip() },
            title = "ActionDraw — Reference",
            state = stripState,
            alwaysOnTop = true,
        ) {
            AtelierTheme(reducedMotion = appState.reducedMotion) {
                Surface { ReferenceStrip(boardState, lib.pictures) }
            }
        }
    }

    // Board sessions run in their own window: the board stays visible in the main one, and
    // closing this window aborts the drawing and returns to the board.
    val boardWindow = practiceState.windowPage
    if (boardWindow != null) {
        val sessionWindowState = rememberWindowState(size = DpSize(1120.dp, 800.dp))
        Window(
            onCloseRequest = { practiceState.abortBoardSession() },
            title = "ActionDraw — Session",
            state = sessionWindowState,
            onKeyEvent = { handleSessionWindowKey(it, practiceState, sessionWindowState) },
        ) {
            AtelierTheme(reducedMotion = appState.reducedMotion) {
                Surface {
                    when (boardWindow) {
                        PracticePage.Summary -> SummaryScreen(practiceState, pinTargets, lib.pictures)
                        else -> SessionScreen(
                            practiceState,
                            onToggleFullscreen = { toggleFullscreen(sessionWindowState) },
                            isFullscreen = sessionWindowState.placement == WindowPlacement.Fullscreen,
                            pinTargets = pinTargets,
                            onSketch = { picture ->
                                sketchState.openFromSession(picture)
                                practiceState.pauseForSketch()
                                appState.sketchFromSession()
                                mainWindow.value?.toFront()
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun toggleFullscreen(ws: WindowState) {
    ws.placement =
        if (ws.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating
        else WindowPlacement.Fullscreen
}

/** Main-window shortcuts. Keeps hands on the keyboard so the drawing stays in flow. */
/** A key event in words, for the Pen panel's readout: type, key, character, modifiers. */
private fun describeKey(event: KeyEvent): String {
    val native = event.nativeKeyEvent as? java.awt.event.KeyEvent
    val name = native?.let { java.awt.event.KeyEvent.getKeyText(it.keyCode) } ?: event.key.toString()
    val char = event.utf16CodePoint.takeIf { it > 32 }?.let { " '" + it.toChar() + "'" } ?: ""
    val mods = (if (event.isCtrlPressed) " ctrl" else "") + (if (event.isShiftPressed) " shift" else "") + (if (event.isAltPressed) " alt" else "")
    return "key ${event.type} $name$char$mods"
}

private fun handleKey(
    event: KeyEvent,
    state: AppState,
    practice: PracticeState,
    boardState: BoardState,
    conceptState: ConceptState,
    sketchState: SketchState,
    windowState: WindowState,
): Boolean {
    // A Live Sketch dialog can be open over any screen (the unsaved-strokes question on closing):
    // it owns the keyboard, and Esc puts it away.
    if (sketchState.editor != null && state.screen != Screen.Sketch) {
        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) sketchState.closeEditor()
        return event.key == Key.Escape
    }
    if (state.screen == Screen.Sketch) {
        // What arrived, for the Pen panel: a tablet's dial may send keys, a wheel, or nothing.
        sketchState.lastInput = describeKey(event)
        // Space held pans: the one key whose release matters, so it is read before the rest.
        if (event.key == Key.Spacebar && sketchState.editor == null) {
            sketchState.spaceHeld = event.type == KeyEventType.KeyDown
            return true
        }
        // [ ] + − by the character they type, whatever the layout (see handleSketchChar).
        if (event.type == KeyEventType.Unknown) {
            return handleSketchChar(event.utf16CodePoint.toChar(), sketchState)
        }
        if (event.type != KeyEventType.KeyDown) return false
        return handleSketchShortcut(event.key, event.isCtrlPressed, sketchState)
    }
    if (event.type != KeyEventType.KeyDown) return false
    // A concept dialog owns the keyboard; Esc closes it, and on the concept screens Esc goes up.
    if (conceptState.editor != null) {
        if (event.key == Key.Escape) {
            conceptState.closeEditor()
            return true
        }
        return false
    }
    if (state.screen == Screen.Concept && event.key == Key.Escape) {
        conceptState.closeConcept()
        return true
    }
    if (state.screen == Screen.Concepts && event.key == Key.Escape) {
        conceptState.leaveList()
        return true
    }
    // A board dialog may be open on any screen (the board picker lives on the menu): Esc closes
    // it, everything else stays with the dialog's text fields.
    if (boardState.editor != null) {
        if (event.key == Key.Escape) {
            boardState.closeEditor()
            return true
        }
        return false
    }
    return when (state.screen) {
        Screen.Practice -> when (practice.page) {
            PracticePage.Summary -> handleSummaryKey(event.key, practice)

            PracticePage.Picker -> when (event.key) {
                Key.Escape, Key.Enter -> { practice.closePicker(); true }
                else -> false
            }

            // The top of a room: Esc goes home to the palette.
            PracticePage.Menu -> event.key == Key.Escape && escapeToPalette(state, practice, boardState)

            PracticePage.Session -> sessionKeys(event, practice, windowState)
        }

        Screen.BoardList -> event.key == Key.Escape && escapeToPalette(state, practice, boardState)

        Screen.Board -> handleBoardKey(
            event,
            boardState,
            isFullscreen = windowState.placement == WindowPlacement.Fullscreen,
            setFullscreen = { on ->
                windowState.placement = if (on) WindowPlacement.Fullscreen else WindowPlacement.Floating
            },
        )

        else -> false
    }
}

/** The board-session window: the same session/summary shortcuts, scoped to its own window. */
private fun handleSessionWindowKey(event: KeyEvent, state: PracticeState, windowState: WindowState): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when (state.windowPage) {
        PracticePage.Summary -> handleSummaryKey(event.key, state)
        PracticePage.Session -> sessionKeys(event, state, windowState)
        else -> false
    }
}

private fun sessionKeys(event: KeyEvent, state: PracticeState, windowState: WindowState): Boolean =
    handleSessionShortcut(
        key = event.key,
        state = state,
        isFullscreen = windowState.placement == WindowPlacement.Fullscreen,
        setFullscreen = { on ->
            windowState.placement = if (on) WindowPlacement.Fullscreen else WindowPlacement.Floating
        },
    )

/** Lets the concept host reach the board state that is built after it. */
private class BoardStateHolder {
    lateinit var state: BoardState
}

/** Lets the board's and the concept's hosts reach Live Sketch, which is built after them. */
private class SketchStateHolder {
    lateinit var state: SketchState
}
