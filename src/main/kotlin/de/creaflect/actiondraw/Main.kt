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

    val toggleFullscreen = {
        windowState.placement =
            if (windowState.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating
            else WindowPlacement.Fullscreen
    }

    val isFullscreen = windowState.placement == WindowPlacement.Fullscreen

    Window(
        onCloseRequest = ::exitApplication,
        title = "ActionDraw",
        state = windowState,
        onKeyEvent = { handleKey(it, appState, windowState) },
    ) {
        App(appState, isFullscreen = isFullscreen, onToggleFullscreen = toggleFullscreen)
    }
}

/** Window-level shortcuts, active only during a drawing session. */
private fun handleKey(event: KeyEvent, state: AppState, windowState: WindowState): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (state.screen != Screen.Session) return false
    return when (event.key) {
        Key.Spacebar -> { state.togglePause(); true }
        Key.DirectionLeft -> { state.previous(); true }
        Key.DirectionRight -> { state.next(); true }
        Key.Escape -> {
            // Esc leaves fullscreen first (restoring the decorated, resizable window);
            // only when already windowed does it stop the session and return to the menu.
            if (windowState.placement == WindowPlacement.Fullscreen) {
                windowState.placement = WindowPlacement.Floating
            } else {
                state.stop()
            }
            true
        }
        Key.F -> {
            windowState.placement =
                if (windowState.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating
                else WindowPlacement.Fullscreen
            true
        }
        else -> false
    }
}
