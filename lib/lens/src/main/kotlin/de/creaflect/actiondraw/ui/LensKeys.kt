package de.creaflect.actiondraw.ui

import androidx.compose.ui.input.key.Key
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.lens.LensState

/** The lens's hotkeys, as the hotkey sheet lists them — wherever a picture is seen through it. */
object LensHotkeys {
    val ROWS: List<Pair<String, String>> = listOf(
        "1 – 9" to "view mode (None … Notan)",
        "N" to "Notan view",
        ", / . / 0" to "cooler light · warmer light · neutral",
        "B / I / D / M / U" to "blur / invert / defraction / mirror / upside down",
        "G" to "cycle the proportion grid",
        "L · O · + / −" to "the glass: take up / put away · next optic · nearer / further",
        "W" to "the glass over the whole picture, or back in hand",
        "click · drag · right click (glass in hand)" to "set it down on a point · on a part · lift it off",
        "click · drag · right click (over the whole)" to "move its centre · move it along · back in hand",
        "wheel · Shift+wheel (glass in hand)" to "magnify · a larger glass (set down: nearer about the pointer; over the whole: bend more or less)",
    )
}

/**
 * The lens's keys as a plain function of the key pressed, so they can be tested: a [KeyEvent]
 * cannot be constructed outside the Compose runtime, and wiring bugs in this table would
 * otherwise only ever show up under someone's fingers. Esc is not here — what it steps back from
 * goes beyond the lens ([LensState.stepBack] is the lens's part of it).
 */
fun handleLensKey(key: Key, lens: LensState): Boolean =
    when (key) {
        // The glass: L takes it up or puts it away, O turns to the next optic, + − magnify.
        Key.L -> { lens.glass.toggle(); true }
        Key.O -> { lens.glass.nextOptic(); true }
        Key.W -> { lens.glass.toggleSpread(); true }
        Key.Plus, Key.Equals, Key.NumPadAdd -> lens.glass.inHand.also { if (it) lens.glass.magnifyBy(1f) }
        Key.Minus, Key.NumPadSubtract -> lens.glass.inHand.also { if (it) lens.glass.magnifyBy(-1f) }
        Key.G -> { lens.cycleGrid(); true }
        Key.I -> { lens.invert = !lens.invert; true }
        Key.D -> { lens.toggleDefraction(); true }
        Key.N -> { lens.viewMode = ViewMode.NOTAN; true }
        Key.M -> { lens.mirror = !lens.mirror; true }
        Key.B -> { lens.blur = !lens.blur; true }
        Key.U -> { lens.upsideDown = !lens.upsideDown; true }
        // Number row selects the view mode (1..9 -> the nine ViewMode values in order).
        Key.One -> { lens.viewMode = ViewMode.NONE; true }
        Key.Two -> { lens.viewMode = ViewMode.GRAYSCALE; true }
        Key.Three -> { lens.viewMode = ViewMode.SQUINT; true }
        Key.Four -> { lens.viewMode = ViewMode.SEPIA; true }
        Key.Five -> { lens.viewMode = ViewMode.POSTERIZE; true }
        Key.Six -> { lens.viewMode = ViewMode.PIXELATE; true }
        Key.Seven -> { lens.viewMode = ViewMode.EDGE; true }
        Key.Eight -> { lens.viewMode = ViewMode.SILHOUETTE; true }
        Key.Nine -> { lens.viewMode = ViewMode.NOTAN; true }
        // Light: comma cools, period warms, zero puts it back to neutral.
        Key.Comma -> { lens.temperature = (lens.temperature - 0.1f).coerceAtLeast(-1f); true }
        Key.Period -> { lens.temperature = (lens.temperature + 0.1f).coerceAtMost(1f); true }
        Key.Zero -> { lens.temperature = 0f; true }
        else -> false
    }
