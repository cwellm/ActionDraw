package de.creaflect.actiondraw

import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.Window

fun main() = application {
    val windowState = rememberWindowState()
    val appState = remember { AppState() }
    val isFullscreen = windowState.placement == WindowPlacement.Fullscreen

    Window(
        onCloseRequest = ::exitApplication,
        title = "ActionDraw",
        state = windowState,
        onKeyEvent = { handleKey(it, appState, windowState) },
    ) {
        App(appState, isFullscreen = isFullscreen, onToggleFullscreen = { toggleFullscreen(windowState) })
    }
}

private fun toggleFullscreen(ws: WindowState) {
    ws.placement =
        if (ws.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating
        else WindowPlacement.Fullscreen
}

/** Window-level shortcuts. Keeps hands on the keyboard so the drawing stays in flow. */
private fun handleKey(event: KeyEvent, state: AppState, windowState: WindowState): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when (state.screen) {
        Screen.Summary -> when (event.key) {
            Key.Escape, Key.Enter -> { state.backToMenu(); true }
            else -> false
        }

        Screen.Session -> when (event.key) {
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
            Key.G -> { state.showGrid = !state.showGrid; true }
            Key.M -> { state.mirror = !state.mirror; true }
            Key.B -> { state.blur = !state.blur; true }
            Key.U -> { state.upsideDown = !state.upsideDown; true }
            Key.One -> { state.viewMode = ViewMode.NONE; true }
            Key.Two -> { state.viewMode = ViewMode.GRAYSCALE; true }
            Key.Three -> { state.viewMode = ViewMode.SQUINT; true }
            Key.Four -> { state.viewMode = ViewMode.EDGE; true }
            Key.Five -> { state.viewMode = ViewMode.SILHOUETTE; true }
            else -> false
        }

        else -> false
    }
}
