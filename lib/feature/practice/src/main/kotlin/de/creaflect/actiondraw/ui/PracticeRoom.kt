package de.creaflect.actiondraw.ui

import androidx.compose.runtime.Composable
import de.creaflect.actiondraw.image.Pictures
import de.creaflect.actiondraw.practice.PracticePage
import de.creaflect.actiondraw.practice.PracticeState

/**
 * The Practice room, on whichever of its pages it is: the session setup, the picker, a session, or
 * what the session came to. [onHome] leaves for the palette; where a pose's picture goes from here
 * is the hand-off seam's, which the room's state holds.
 */
@Composable
fun PracticeRoom(
    state: PracticeState,
    thumbs: Pictures,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onHome: () -> Unit,
) {
    when (state.page) {
        PracticePage.Menu -> PracticeScreen(state, onHome = onHome)
        PracticePage.Picker -> PickerScreen(state, thumbs)
        PracticePage.Session -> SessionScreen(state, onToggleFullscreen, isFullscreen)
        PracticePage.Summary -> SummaryScreen(state, thumbs)
    }
}
