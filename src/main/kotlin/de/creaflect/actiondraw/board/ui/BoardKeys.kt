package de.creaflect.actiondraw.board.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import de.creaflect.actiondraw.board.BoardEditor
import de.creaflect.actiondraw.board.BoardLayouts
import de.creaflect.actiondraw.board.BoardState
import de.creaflect.actiondraw.board.ImageItem
import de.creaflect.actiondraw.board.NoteItem

/** Board-screen shortcuts, wired from the window-level key handler in Main. */
fun handleBoardKey(
    event: KeyEvent,
    state: BoardState,
    isFullscreen: Boolean,
    setFullscreen: (Boolean) -> Unit,
): Boolean {
    if (state.editor != null) {
        // Open dialogs own the keyboard; the window handler only closes them on Esc.
        if (event.key == Key.Escape) {
            state.closeEditor()
            return true
        }
        return false
    }
    if (state.quickLookId != null) {
        return when (event.key) {
            Key.Escape, Key.Spacebar -> { state.closeQuickLook(); true }
            Key.DirectionLeft -> { state.quickLookStep(-1); true }
            Key.DirectionRight -> { state.quickLookStep(1); true }
            else -> false
        }
    }
    if (event.isCtrlPressed) {
        return when (event.key) {
            Key.A -> { state.selectAll(); true }
            Key.C -> { state.copySelection(); true }
            Key.V -> { state.importPasted(); true }
            else -> false
        }
    }
    val free = state.layout == BoardLayouts.FREE
    return when (event.key) {
        Key.Escape -> {
            // Immersive first; a windowed board closes back to the menu.
            if (state.immersive || isFullscreen) {
                state.immersive = false
                setFullscreen(false)
            } else {
                state.closeBoard()
            }
            true
        }
        Key.F -> {
            // F = immersive (fullscreen + hidden chrome). Menus always show when windowed.
            val on = !(state.immersive && isFullscreen)
            state.immersive = on
            setFullscreen(on)
            true
        }
        Key.Enter -> { state.drawSelection(); true }
        Key.Spacebar -> { state.toggleQuickLook(); true }
        Key.DirectionLeft -> { if (free) state.nudgeSelection(-10f, 0f) else state.moveFocus(-1); true }
        Key.DirectionRight -> { if (free) state.nudgeSelection(10f, 0f) else state.moveFocus(1); true }
        Key.DirectionUp -> { if (free) state.nudgeSelection(0f, -10f) else state.moveFocus(-1); true }
        Key.DirectionDown -> { if (free) state.nudgeSelection(0f, 10f) else state.moveFocus(1); true }
        Key.N -> { state.openEditor(BoardEditor.EditNote(null)); true }
        Key.G -> { state.openEditor(BoardEditor.NewGroup); true }
        Key.S -> { state.toggleStar(state.selection); true }
        Key.T -> {
            val ids = state.selection.ifEmpty { setOfNotNull(state.focusId) }
            if (ids.isNotEmpty()) state.openEditor(BoardEditor.EditTags(ids))
            true
        }
        Key.F2 -> {
            when (val focused = state.focusId?.let(state::item)) {
                is ImageItem -> state.openEditor(BoardEditor.EditCaption(focused.id))
                is NoteItem -> state.openEditor(BoardEditor.EditNote(focused.id))
                null -> {}
            }
            true
        }
        Key.Delete -> { state.removeItems(state.selection); true }
        else -> false
    }
}
