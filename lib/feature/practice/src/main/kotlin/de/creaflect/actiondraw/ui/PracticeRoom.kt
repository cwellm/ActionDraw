package de.creaflect.actiondraw.ui

import androidx.compose.runtime.Composable
import de.creaflect.actiondraw.PinTargets
import de.creaflect.actiondraw.image.Pictures
import de.creaflect.actiondraw.practice.PracticePage
import de.creaflect.actiondraw.practice.PracticeState
import java.io.File

/**
 * The Practice room, on whichever of its pages it is: the session setup, the picker, a session, or
 * what the session came to. [onHome] leaves for the palette; [onSketch] takes the picture on screen
 * into Live Sketch, the session waiting.
 */
@Composable
fun PracticeRoom(
    state: PracticeState,
    thumbs: Pictures,
    pinTargets: PinTargets,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onHome: () -> Unit,
    onSketch: (File) -> Unit,
) {
    when (state.page) {
        PracticePage.Menu -> PracticeScreen(state, onHome = onHome)
        PracticePage.Picker -> PickerScreen(state, thumbs)
        PracticePage.Session -> SessionScreen(state, onToggleFullscreen, isFullscreen, pinTargets, onSketch = onSketch)
        PracticePage.Summary -> SummaryScreen(state, pinTargets, thumbs)
    }
}
