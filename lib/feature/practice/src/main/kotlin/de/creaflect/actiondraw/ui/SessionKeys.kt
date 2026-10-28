package de.creaflect.actiondraw.ui

import androidx.compose.ui.input.key.Key
import de.creaflect.actiondraw.practice.PracticeState

/** A drawing session's hotkeys, as the hotkey sheet lists them: the session's own, and the lens's. */
object SessionHotkeys {
    const val TITLE = "Drawing session"
    val ROWS: List<Pair<String, String>> = listOf(
        "Space" to "play / pause",
        "← / →" to "previous / next picture",
    ) + LensHotkeys.ROWS + listOf(
        "H" to "hide the reference · peek while drawing from memory",
        "R" to "toggle the redo flag",
        "A" to "toggle auto-advance",
        "F" to "toggle fullscreen",
        "Esc" to "lift the glass · put it away · leave fullscreen · stop the session",
    )
}

/**
 * The session shortcuts as a plain function of the key pressed, so they can be tested: a
 * [KeyEvent] cannot be constructed outside the Compose runtime, and wiring bugs in this table
 * would otherwise only ever show up under someone's fingers. What the session does not take is
 * the lens's ([handleLensKey]).
 */
fun handleSessionShortcut(
    key: Key,
    state: PracticeState,
    isFullscreen: Boolean,
    setFullscreen: (Boolean) -> Unit,
): Boolean =
    when (key) {
        Key.Spacebar -> { state.togglePause(); true }
        Key.DirectionLeft -> { state.previous(); true }
        Key.DirectionRight -> { state.next(); true }
        Key.Escape -> {
            // Esc steps back one thing at a time: the lens first (the glass off the picture, the
            // glass put away), fullscreen left (restoring the decorated window), and only then the
            // session ended.
            when {
                state.lens.stepBack(state.currentImage) -> Unit
                isFullscreen -> setFullscreen(false)
                else -> state.stop()
            }
            true
        }
        Key.F -> { setFullscreen(!isFullscreen); true }
        Key.R -> { state.toggleRedoCurrent(); true }
        Key.A -> { state.autoAdvance = !state.autoAdvance; true }
        // H covers the reference — or peeks at it, while drawing from memory.
        Key.H -> { state.toggleReference(); true }
        else -> handleLensKey(key, state.lens)
    }

/** On the summary, Esc and Enter both leave it: to the menu, or back to the board. */
fun handleSummaryKey(key: Key, state: PracticeState): Boolean = when (key) {
    Key.Escape, Key.Enter -> { state.backToMenu(); true }
    else -> false
}
