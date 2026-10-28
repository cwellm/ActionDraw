package de.creaflect.actiondraw.lens.ui

import androidx.compose.ui.input.key.Key
import de.creaflect.actiondraw.lens.LensRoomState
import de.creaflect.actiondraw.ui.LensHotkeys
import de.creaflect.actiondraw.ui.handleLensKey

/** The Lens room's hotkeys, as the hotkey sheet lists them: the room's own, and the lens's. */
object LensRoomHotkeys {
    const val TITLE = "Lens"
    val ROWS: List<Pair<String, String>> = listOf(
        "Ctrl+O" to "open a picture",
        "Ctrl+V" to "look at the picture on the clipboard",
        "Ctrl+S" to "save the view as a picture",
        "Tab" to "the other lens, while two are compared",
    ) + LensHotkeys.ROWS + listOf(
        "Esc" to "lift the glass · put it away · back where you came from",
    )
}

/**
 * The Lens room's keys as a plain function of the key pressed, so they can be tested: the room's
 * own — open, paste, save, the other lens, Esc — and, while there is a picture, the lens's.
 */
fun handleLensRoomKey(key: Key, ctrl: Boolean, state: LensRoomState): Boolean {
    if (ctrl) {
        return when (key) {
            Key.O -> { state.chooseFile(); true }
            Key.V -> { state.paste(); true }
            Key.S -> { state.save(); true }
            else -> false
        }
    }
    return when (key) {
        // Esc steps back one thing at a time: the glass off the picture, the glass put away, and
        // only then out of the room.
        Key.Escape -> {
            if (!state.lens.stepBack(state.file)) state.leave()
            true
        }
        Key.Tab -> state.otherLens()
        else -> state.picture != null && handleLensKey(key, state.lens)
    }
}
