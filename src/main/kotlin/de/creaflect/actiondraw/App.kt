package de.creaflect.actiondraw

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import de.creaflect.actiondraw.ui.MenuScreen
import de.creaflect.actiondraw.ui.SessionScreen

enum class Screen { Menu, Session }

@Composable
fun App(state: AppState, isFullscreen: Boolean, onToggleFullscreen: () -> Unit) {
    MaterialTheme(colors = darkColors()) {
        Surface {
            when (state.screen) {
                Screen.Menu -> MenuScreen(state)
                Screen.Session -> SessionScreen(state, onToggleFullscreen, isFullscreen)
            }
        }
    }
}
