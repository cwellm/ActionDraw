package de.creaflect.actiondraw.sketch

import androidx.compose.ui.input.key.Key
import de.creaflect.sketch.Lead

/**
 * Live Sketch's keys, as pure functions — the window's key handler calls them, and a test can
 * too, since a `KeyEvent` cannot be built in one.
 *
 * Keys by their key: `1` `2` `3` the leads · `E` eraser · `Ctrl+Z` / `Ctrl+Y` undo / redo ·
 * `Ctrl+S` save · `Ctrl+N` new · `Ctrl+O` open · `Ctrl+0` fit · `Esc` leave (a dialog first).
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
        ctrl -> false
        key == Key.One || key == Key.NumPad1 -> { state.setLead(Lead.HARD); true }
        key == Key.Two || key == Key.NumPad2 -> { state.setLead(Lead.MEDIUM); true }
        key == Key.Three || key == Key.NumPad3 -> { state.setLead(Lead.SOFT); true }
        key == Key.E -> { state.toggleEraser(); true }
        else -> false
    }
}

/**
 * Keys by the character they type: `[` `]` thinner / thicker, `+` `−` zoom in / out. Read from
 * the typed character, not the key, because the keys differ by layout — on a German keyboard
 * `[` is AltGr+8, which also reads as Ctrl, and `+` has a key of its own.
 */
fun handleSketchChar(char: Char, state: SketchState): Boolean {
    if (state.editor != null) return false
    return when (char) {
        '[' -> { state.setSize(state.brush.size - 1f); true }
        ']' -> { state.setSize(state.brush.size + 1f); true }
        '+' -> { state.zoomStep(zoomIn = true); true }
        '-' -> { state.zoomStep(zoomIn = false); true }
        else -> false
    }
}
