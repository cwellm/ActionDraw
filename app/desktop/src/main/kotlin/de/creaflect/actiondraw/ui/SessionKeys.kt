package de.creaflect.actiondraw.ui

import androidx.compose.ui.input.key.Key
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.practice.PracticeState

/** A drawing session's hotkeys, as the hotkey sheet lists them. */
object SessionHotkeys {
    const val TITLE = "Drawing session"
    val ROWS: List<Pair<String, String>> = listOf(
        "Space" to "play / pause",
        "← / →" to "previous / next picture",
        "1 – 9" to "view mode (None … Notan)",
        "N" to "Notan view",
        ", / . / 0" to "cooler light · warmer light · neutral",
        "H" to "hide the reference · peek while drawing from memory",
        "B / I / D / M / U" to "blur / invert / defraction / mirror / upside down",
        "G" to "cycle the proportion grid",
        "R" to "toggle the redo flag",
        "A" to "toggle auto-advance",
        "F" to "toggle fullscreen",
        "L · O · + / −" to "the glass: take up / put away · next optic · nearer / further",
        "click · drag · right click (glass in hand)" to "set it down on a point · on a part · lift it off",
        "wheel · Shift+wheel (glass in hand)" to "magnify · a larger glass (set down: nearer about the pointer)",
        "Esc" to "lift the glass · put it away · leave fullscreen · stop the session",
    )
}

/**
 * The session shortcuts as a plain function of the key pressed, so they can be tested: a
 * [KeyEvent] cannot be constructed outside the Compose runtime, and wiring bugs in this table
 * would otherwise only ever show up under someone's fingers.
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
            // Esc steps back one thing at a time: the glass off the picture, the glass put away,
            // fullscreen left (restoring the decorated window), and only then the session ended.
            when {
                state.glass.focusOn(state.currentImage) != null -> state.glass.wholePicture()
                state.glass.inHand -> state.glass.putAway()
                isFullscreen -> setFullscreen(false)
                else -> state.stop()
            }
            true
        }
        // The glass: L takes it up or puts it away, O turns to the next optic, + − magnify.
        Key.L -> { state.glass.toggle(); true }
        Key.O -> { state.glass.nextOptic(); true }
        Key.Plus, Key.Equals, Key.NumPadAdd -> state.glass.inHand.also { if (it) state.glass.magnifyBy(1f) }
        Key.Minus, Key.NumPadSubtract -> state.glass.inHand.also { if (it) state.glass.magnifyBy(-1f) }
        Key.F -> { setFullscreen(!isFullscreen); true }
        Key.G -> { state.cycleGrid(); true }
        Key.R -> { state.toggleRedoCurrent(); true }
        Key.A -> { state.autoAdvance = !state.autoAdvance; true }
        Key.I -> { state.invert = !state.invert; true }
        Key.D -> { state.toggleDefraction(); true }
        Key.N -> { state.viewMode = ViewMode.NOTAN; true }
        Key.M -> { state.mirror = !state.mirror; true }
        Key.B -> { state.blur = !state.blur; true }
        Key.U -> { state.upsideDown = !state.upsideDown; true }
        // H covers the reference — or peeks at it, while drawing from memory.
        Key.H -> { state.toggleReference(); true }
        // Number row selects the view mode (1..9 -> the nine ViewMode values in order).
        Key.One -> { state.viewMode = ViewMode.NONE; true }
        Key.Two -> { state.viewMode = ViewMode.GRAYSCALE; true }
        Key.Three -> { state.viewMode = ViewMode.SQUINT; true }
        Key.Four -> { state.viewMode = ViewMode.SEPIA; true }
        Key.Five -> { state.viewMode = ViewMode.POSTERIZE; true }
        Key.Six -> { state.viewMode = ViewMode.PIXELATE; true }
        Key.Seven -> { state.viewMode = ViewMode.EDGE; true }
        Key.Eight -> { state.viewMode = ViewMode.SILHOUETTE; true }
        Key.Nine -> { state.viewMode = ViewMode.NOTAN; true }
        // Light: comma cools, period warms, zero puts it back to neutral.
        Key.Comma -> { state.temperature = (state.temperature - 0.1f).coerceAtLeast(-1f); true }
        Key.Period -> { state.temperature = (state.temperature + 0.1f).coerceAtMost(1f); true }
        Key.Zero -> { state.temperature = 0f; true }
        else -> false
    }

/** On the summary, Esc and Enter both leave it: to the menu, or back to the board. */
fun handleSummaryKey(key: Key, state: PracticeState): Boolean = when (key) {
    Key.Escape, Key.Enter -> { state.backToMenu(); true }
    else -> false
}
