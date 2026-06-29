package de.creaflect.actiondraw

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import de.creaflect.actiondraw.ui.MenuScreen
import de.creaflect.actiondraw.ui.SessionScreen
import de.creaflect.actiondraw.ui.SummaryScreen

enum class Screen { Menu, Session, Summary }

/** Calm, warm dark palette — easy on the eyes for long drawing sessions. */
private val ActionDrawColors = darkColors(
    primary = Color(0xFFFFB74D),
    primaryVariant = Color(0xFFFFA726),
    secondary = Color(0xFF80CBC4),
    background = Color(0xFF121212),
    surface = Color(0xFF1C1C1E),
    onPrimary = Color(0xFF1A1A1A),
    onSecondary = Color(0xFF0E1413),
    onBackground = Color(0xFFEDEDED),
    onSurface = Color(0xFFEDEDED),
)

@Composable
fun App(state: AppState, isFullscreen: Boolean, onToggleFullscreen: () -> Unit) {
    MaterialTheme(colors = ActionDrawColors) {
        Surface {
            when (state.screen) {
                Screen.Menu -> MenuScreen(state)
                Screen.Session -> SessionScreen(state, onToggleFullscreen, isFullscreen)
                Screen.Summary -> SummaryScreen(state)
            }
        }
    }
}
