package de.creaflect.actiondraw.sketch

import androidx.compose.ui.input.key.Key
import de.creaflect.sketch.Lead

/**
 * Live Sketch's keys, as pure functions — the window's key handler calls them, and a test can
 * too, since a `KeyEvent` cannot be built in one.
 *
 * Keys by their key: `1` to `7` the tools, in [Lead]'s order · `E` eraser · `+` `−` zoom in / out,
 * of either kind and with or without Ctrl (a tablet's dial in its zoom setting sends what it
 * sends) · `Ctrl+Z` / `Ctrl+Y` undo / redo · `Ctrl+S` save · `Ctrl+N` new · `Ctrl+O` open ·
 * `Ctrl+0` fit · `Esc` leave (a dialog first).
 */
fun handleSketchShortcut(key: Key, ctrl: Boolean, state: SketchState): Boolean {
    if (state.editor != null) {
        if (key == Key.Escape) {
            state.closeEditor()
            return true
        }
        return false
    }
    return when {
        key == Key.Escape -> { state.leave(); true }
        ctrl && key == Key.Z -> { state.undo(); true }
        ctrl && key == Key.Y -> { state.redo(); true }
        ctrl && key == Key.S -> { state.save(); true }
        ctrl && key == Key.N -> { state.openEditor(SketchEditor.NewSketch); true }
        ctrl && key == Key.O -> { state.openEditor(SketchEditor.Open); true }
        ctrl && (key == Key.Zero || key == Key.NumPad0) -> { state.fit(); true }
        key == Key.Plus || key == Key.Equals || key == Key.NumPadAdd -> { state.zoomStep(zoomIn = true); true }
        key == Key.Minus || key == Key.NumPadSubtract -> { state.zoomStep(zoomIn = false); true }
        ctrl -> false
        key == Key.E -> { state.toggleEraser(); true }
        else -> toolKey(key)?.let { state.setLead(it); true } ?: false
    }
}

/** `1` to `7`, on the row or the pad: the tools in [Lead]'s order. */
private fun toolKey(key: Key): Lead? {
    val n = when (key) {
        Key.One, Key.NumPad1 -> 1
        Key.Two, Key.NumPad2 -> 2
        Key.Three, Key.NumPad3 -> 3
        Key.Four, Key.NumPad4 -> 4
        Key.Five, Key.NumPad5 -> 5
        Key.Six, Key.NumPad6 -> 6
        Key.Seven, Key.NumPad7 -> 7
        else -> return null
    }
    return Lead.entries.getOrNull(n - 1)
}

/**
 * Keys by the character they type: `[` `]` thinner / thicker. Read from the typed character,
 * not the key, because the keys differ by layout — on a German keyboard `[` is AltGr+8, which
 * also reads as Ctrl. (Zoom went to the keys: `+` has a key of its own, and a dial sends keys.)
 */
fun handleSketchChar(char: Char, state: SketchState): Boolean {
    if (state.editor != null) return false
    return when (char) {
        '[' -> { state.setSize(state.brush.size - 1f); true }
        ']' -> { state.setSize(state.brush.size + 1f); true }
        else -> false
    }
}
