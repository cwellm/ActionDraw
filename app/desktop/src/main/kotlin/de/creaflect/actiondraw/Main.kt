package de.creaflect.actiondraw

import androidx.compose.material.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.creaflect.actiondraw.board.ui.PointerLog
import de.creaflect.actiondraw.board.ui.ReferenceStrip
import de.creaflect.actiondraw.lifecycle.LibRun
import de.creaflect.actiondraw.platform.AwtClipboard
import de.creaflect.actiondraw.platform.DesktopBrowser
import de.creaflect.actiondraw.platform.SwingFileDialogs
import de.creaflect.actiondraw.platform.SystemPreferences
import de.creaflect.actiondraw.platform.onUiThread
import de.creaflect.actiondraw.practice.PracticePage
import de.creaflect.actiondraw.sketch.input.PenSources
import de.creaflect.actiondraw.ui.AtelierTheme
import de.creaflect.actiondraw.ui.SessionScreen
import de.creaflect.actiondraw.ui.SummaryScreen

/**
 * The desktop application: the lib over the person's files, the rooms put together over it with
 * the desktop's own services, and the windows — the main one, the reference strip that floats
 * above everything, and the window a board's session runs in.
 */
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
    val rooms = remember {
        // The bare lib over the person's files — the only way the app reaches the disk or the network.
        val lib = FileLib(run).also { PointerLog.diagnostics = it.diagnostics }
        // The desktop's own: its clipboard, its browser, its file dialogs, Windows' animation setting.
        Rooms(lib, HostServices(AwtClipboard, DesktopBrowser, SwingFileDialogs, SystemPreferences))
    }
    val app = rooms.app
    // Roomy enough for a board, small enough to fit a 1080p screen at 125% scaling.
    val windowState = rememberWindowState(size = DpSize(1120.dp, 800.dp))
    // The main window, so a sketch started from a session's own window can come to the front.
    val mainWindow = remember { mutableStateOf<java.awt.Window?>(null) }
    // Everything above is built: the lib is loaded.
    LaunchedEffect(run) { run.loaded() }

    // A session started from a board changes its seen/redo state; refresh the badges when the
    // session window closes.
    LaunchedEffect(rooms.practice.windowPage) {
        if (rooms.practice.windowPage == null) rooms.boards.refreshPractice()
    }

    Window(
        // Closing with unsaved strokes asks first; everything else is saved as it happens.
        onCloseRequest = {
            rooms.sketch.guardUnsaved {
                run.close()
                exitApplication()
            }
        },
        title = "ActionDraw",
        state = windowState,
        onKeyEvent = { handleKey(it, rooms, windowState) },
    ) {
        // The pen is hooked to the native window for pressure and tilt while Live Sketch is up, and
        // let go when it is left — nothing else in the app sees the pen as more than a mouse.
        LaunchedEffect(app.screen) {
            if (app.screen == Screen.Sketch) rooms.sketch.onEnter(PenSources.forWindow(window)?.let(::onUiThread)) else rooms.sketch.detach()
        }
        LaunchedEffect(Unit) { mainWindow.value = window }
        App(
            rooms,
            isFullscreen = windowState.placement == WindowPlacement.Fullscreen,
            onToggleFullscreen = { toggleFullscreen(windowState) },
            setFullscreen = { on ->
                windowState.placement = if (on) WindowPlacement.Fullscreen else WindowPlacement.Floating
            },
        )
    }

    // The reference strip floats above every other application, so a board can sit in the corner
    // of the screen while you paint somewhere else.
    if (rooms.boards.stripOpen) {
        val stripState = rememberWindowState(size = DpSize(360.dp, 520.dp))
        Window(
            onCloseRequest = { rooms.boards.closeStrip() },
            title = "ActionDraw — Reference",
            state = stripState,
            alwaysOnTop = true,
        ) {
            AtelierTheme(reducedMotion = app.reducedMotion) {
                Surface { ReferenceStrip(rooms.boards, rooms.lib.pictures) }
            }
        }
    }

    // Board sessions run in their own window: the board stays visible in the main one, and
    // closing this window aborts the drawing and returns to the board.
    val boardWindow = rooms.practice.windowPage
    if (boardWindow != null) {
        val sessionWindowState = rememberWindowState(size = DpSize(1120.dp, 800.dp))
        Window(
            onCloseRequest = { rooms.practice.abortBoardSession() },
            title = "ActionDraw — Session",
            state = sessionWindowState,
            onKeyEvent = { handleSessionWindowKey(it, rooms.practice, sessionWindowState) },
        ) {
            AtelierTheme(reducedMotion = app.reducedMotion) {
                Surface {
                    when (boardWindow) {
                        PracticePage.Summary -> SummaryScreen(rooms.practice, rooms.pinTargets, rooms.lib.pictures)
                        else -> SessionScreen(
                            rooms.practice,
                            onToggleFullscreen = { toggleFullscreen(sessionWindowState) },
                            isFullscreen = sessionWindowState.placement == WindowPlacement.Fullscreen,
                            pinTargets = rooms.pinTargets,
                            onSketch = { picture ->
                                rooms.sketchFromSession(picture)
                                mainWindow.value?.toFront()
                            },
                        )
                    }
                }
            }
        }
    }
}
