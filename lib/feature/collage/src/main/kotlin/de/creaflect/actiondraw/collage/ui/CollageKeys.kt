package de.creaflect.actiondraw.collage.ui

import androidx.compose.ui.input.key.Key
import de.creaflect.actiondraw.collage.CollageState
import de.creaflect.actiondraw.collage.Tool

/** The Collage room's hotkeys, as the hotkey sheet lists them. */
object CollageHotkeys {
    const val TITLE = "Collage"
    val ROWS: List<Pair<String, String>> = listOf(
        "Ctrl+N" to "a new collage",
        "Ctrl+O" to "add a picture",
        "Ctrl+V" to "add the picture on the clipboard",
        "Ctrl+S" to "save the collage as a picture beside it",
        "Ctrl+Z / Ctrl+Y" to "undo · redo",
        "M / S / R / T" to "Move · the scissors · tear · tape",
        "click, drag" to "with the scissors: a corner, a freehand cut (Shift: straight); a click on the first corner cuts round",
        "Enter" to "cut along the corners so far",
        "L" to "cut and tear through every layer, or one alone",
        "Arrows" to "move the chosen piece (Shift: further)",
        "Q / E" to "turn it (Shift: by 15°)",
        "F" to "flip it over",
        "PgUp / PgDn  or  ] / [" to "bring it forward · send it backward",
        "Del" to "take it away",
        "drag" to "a piece to move it — and what is taped to it; its round handle to turn it, its corner to grow it (Shift: in 15° steps)",
        "Esc" to "drop the cut · let go of the piece · Move again · back where you came from",
    )
}

/**
 * Keys by the character they type: `]` forward, `[` backward. Read from the typed character, not the
 * key, because the keys differ by layout — on a German keyboard `[` is AltGr+8, which also reads as
 * Ctrl — as Live Sketch's `[` `]` are.
 */
fun handleCollageChar(char: Char, state: CollageState): Boolean = when (char) {
    ']' -> { state.forward(); true }
    '[' -> { state.backward(); true }
    else -> false
}

/** How far the arrow keys move a piece, in mat units: a little, or with Shift a quarter of a mark. */
private const val NUDGE = 2f
private const val NUDGE_FAR = 25f

/**
 * The Collage room's keys as a plain function of the key pressed, so they can be tested: the
 * collage's own — new, add, paste, undo, redo — the tools, the cut being made, and the chosen piece's.
 */
fun handleCollageKey(key: Key, ctrl: Boolean, shift: Boolean, state: CollageState): Boolean {
    if (ctrl) {
        return when (key) {
            Key.N -> { state.newCollage(); true }
            Key.O -> { state.chooseFile(); true }
            Key.V -> { state.paste(); true }
            Key.S -> { state.saveAsPicture(); true }
            Key.Z -> { if (shift) state.redo() else state.undo(); true }
            Key.Y -> { state.redo(); true }
            else -> false
        }
    }
    val step = if (shift) NUDGE_FAR else NUDGE
    val turn = if (shift) 15f else 1f
    return when (key) {
        Key.Escape -> { state.stepBack(); true }
        Key.M -> { state.choose(Tool.MOVE); true }
        Key.S -> { state.choose(Tool.SCISSORS); true }
        Key.R -> { state.choose(Tool.TEAR); true }
        Key.T -> { state.choose(Tool.TAPE); true }
        Key.L -> { state.cutThroughAll(!state.throughAll); true }
        Key.Enter, Key.NumPadEnter -> if (state.path.size < 2) false else { state.cutAlongPath(closed = false); true }
        Key.DirectionLeft -> { state.nudge(-step, 0f); true }
        Key.DirectionRight -> { state.nudge(step, 0f); true }
        Key.DirectionUp -> { state.nudge(0f, -step); true }
        Key.DirectionDown -> { state.nudge(0f, step); true }
        Key.Q -> { state.turnBy(-turn); true }
        Key.E -> { state.turnBy(turn); true }
        Key.F -> { state.flip(); true }
        Key.PageUp -> { state.forward(); true }
        Key.PageDown -> { state.backward(); true }
        Key.Delete, Key.Backspace -> { state.remove(); true }
        else -> false
    }
}
