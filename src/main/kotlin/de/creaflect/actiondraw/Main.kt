package de.creaflect.actiondraw

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.creaflect.actiondraw.board.BoardHost
import de.creaflect.actiondraw.board.BoardState
import de.creaflect.actiondraw.board.ui.handleBoardKey
import de.creaflect.actiondraw.image.ThumbCache
import de.creaflect.actiondraw.ui.SessionScreen
import de.creaflect.actiondraw.ui.SummaryScreen
import java.io.File

fun main() = application {
    // Roomy enough for a board, small enough to fit a 1080p screen at 125% scaling.
    val windowState = rememberWindowState(size = DpSize(1120.dp, 800.dp))
    val settings = remember { Settings() }
    val appState = remember { AppState(settings) }
    // The board talks to the rest of the app only through this host (its "plugin" boundary).
    val boardState = remember {
        BoardState(settings, object : BoardHost {
            override fun startSession(root: File, images: List<File>, setup: SessionSetup?) =
                appState.startBoardSession(root, images, setup)

            override fun showBoard() = appState.showBoard()
            override fun showBoardList() = appState.showBoardList()
            override fun leaveBoard() = appState.leaveBoard()
            override fun currentSetup(): SessionSetup = appState.currentSetup()
        })
    }
    // Lets a running session file pictures away on a board, without the session knowing what a
    // board is (see PinTargets).
    val pinTargets = remember {
        PinTargets(boards = { boardState.availableBoards() }, pin = boardState::pinTo)
    }
    val thumbs = remember { ThumbCache() }
    val isFullscreen = windowState.placement == WindowPlacement.Fullscreen

    // A session started from a board changes its seen/redo state; refresh the badges when the
    // session window closes.
    LaunchedEffect(appState.boardWindowScreen) {
        if (appState.boardWindowScreen == null) boardState.refreshPractice()
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "ActionDraw",
        state = windowState,
        onKeyEvent = { handleKey(it, appState, boardState, windowState) },
    ) {
        App(
            appState,
            boardState,
            thumbs,
            pinTargets,
            isFullscreen = isFullscreen,
            onToggleFullscreen = { toggleFullscreen(windowState) },
            setFullscreen = { on ->
                windowState.placement = if (on) WindowPlacement.Fullscreen else WindowPlacement.Floating
            },
        )
    }

    // Board sessions run in their own window: the board stays visible in the main one, and
    // closing this window aborts the drawing and returns to the board.
    val boardWindow = appState.boardWindowScreen
    if (boardWindow != null) {
        val sessionWindowState = rememberWindowState(size = DpSize(1120.dp, 800.dp))
        Window(
            onCloseRequest = { appState.abortBoardSession() },
            title = "ActionDraw — Session",
            state = sessionWindowState,
            onKeyEvent = { handleSessionWindowKey(it, appState, sessionWindowState) },
        ) {
            MaterialTheme(colors = ActionDrawColors) {
                Surface {
                    when (boardWindow) {
                        Screen.Summary -> SummaryScreen(appState, pinTargets)
                        else -> SessionScreen(
                            appState,
                            onToggleFullscreen = { toggleFullscreen(sessionWindowState) },
                            isFullscreen = sessionWindowState.placement == WindowPlacement.Fullscreen,
                            pinTargets = pinTargets,
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
private fun handleKey(
    event: KeyEvent,
    state: AppState,
    boardState: BoardState,
    windowState: WindowState,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
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
        Screen.Summary -> summaryKeys(event, state)

        Screen.Picker -> when (event.key) {
            Key.Escape, Key.Enter -> { state.closePicker(); true }
            else -> false
        }

        Screen.Board -> handleBoardKey(
            event,
            boardState,
            isFullscreen = windowState.placement == WindowPlacement.Fullscreen,
            setFullscreen = { on ->
                windowState.placement = if (on) WindowPlacement.Fullscreen else WindowPlacement.Floating
            },
        )

        Screen.Session -> sessionKeys(event, state, windowState)

        else -> false
    }
}

/** The board-session window: the same session/summary shortcuts, scoped to its own window. */
private fun handleSessionWindowKey(event: KeyEvent, state: AppState, windowState: WindowState): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when (state.boardWindowScreen) {
        Screen.Summary -> summaryKeys(event, state)
        Screen.Session -> sessionKeys(event, state, windowState)
        else -> false
    }
}

private fun summaryKeys(event: KeyEvent, state: AppState): Boolean = when (event.key) {
    Key.Escape, Key.Enter -> { state.backToMenu(); true }
    else -> false
}

private fun sessionKeys(event: KeyEvent, state: AppState, windowState: WindowState): Boolean =
    when (event.key) {
        Key.Spacebar -> { state.togglePause(); true }
        Key.DirectionLeft -> { state.previous(); true }
        Key.DirectionRight -> { state.next(); true }
        Key.Escape -> {
            // Esc leaves fullscreen first (restoring the decorated window);
            // when already windowed, it ends the session.
            if (windowState.placement == WindowPlacement.Fullscreen) {
                windowState.placement = WindowPlacement.Floating
            } else {
                state.stop()
            }
            true
        }
        Key.F -> { toggleFullscreen(windowState); true }
        Key.G -> { state.cycleGrid(); true }
        Key.R -> { state.toggleRedoCurrent(); true }
        Key.A -> { state.autoAdvance = !state.autoAdvance; true }
        Key.I -> { state.invert = !state.invert; true }
        Key.D -> { state.toggleDefraction(); true }
        Key.N -> { state.viewMode = ViewMode.NOTAN; true }
        Key.M -> { state.mirror = !state.mirror; true }
        Key.B -> { state.blur = !state.blur; true }
        Key.U -> { state.upsideDown = !state.upsideDown; true }
        // Number row selects the view mode (1..0 -> the ten ViewMode values in order).
        Key.One -> { state.viewMode = ViewMode.NONE; true }
        Key.Two -> { state.viewMode = ViewMode.GRAYSCALE; true }
        Key.Three -> { state.viewMode = ViewMode.SQUINT; true }
        Key.Four -> { state.viewMode = ViewMode.SEPIA; true }
        Key.Five -> { state.viewMode = ViewMode.POSTERIZE; true }
        Key.Six -> { state.viewMode = ViewMode.PIXELATE; true }
        Key.Seven -> { state.viewMode = ViewMode.WARM; true }
        Key.Eight -> { state.viewMode = ViewMode.COOL; true }
        Key.Nine -> { state.viewMode = ViewMode.EDGE; true }
        Key.Zero -> { state.viewMode = ViewMode.SILHOUETTE; true }
        else -> false
    }
