package de.creaflect.actiondraw.board.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import de.creaflect.actiondraw.board.BoardEditor
import de.creaflect.actiondraw.board.BoardLayouts
import de.creaflect.actiondraw.board.BoardState
import de.creaflect.actiondraw.board.ImageItem
import de.creaflect.actiondraw.board.LinkItem
import de.creaflect.actiondraw.board.NoteItem

/** Board-screen shortcuts, wired from the window-level key handler in Main. */
fun handleBoardKey(
    event: KeyEvent,
    state: BoardState,
    isFullscreen: Boolean,
    setFullscreen: (Boolean) -> Unit,
): Boolean = handleBoardShortcut(
    key = event.key,
    ctrl = event.isCtrlPressed,
    shift = event.isShiftPressed,
    state = state,
    isFullscreen = isFullscreen,
    setFullscreen = setFullscreen,
)

/**
 * Which command a shortcut stands for. Split out from [handleBoardKey] so the mapping can be
 * tested without building a Compose key event — the bugs here are about *which* command a key
 * runs (plain `G` used to make an empty group even with cards selected), which no test of the
 * commands themselves can catch.
 */
internal fun handleBoardShortcut(
    key: Key,
    ctrl: Boolean,
    shift: Boolean,
    state: BoardState,
    isFullscreen: Boolean,
    setFullscreen: (Boolean) -> Unit,
): Boolean {
    if (state.editor != null) {
        // Open dialogs own the keyboard; the window handler only closes them on Esc.
        if (key == Key.Escape) {
            state.closeEditor()
            return true
        }
        return false
    }
    if (state.viewerOpen) {
        return when (key) {
            Key.Escape, Key.Spacebar -> { state.closeViewer(); true }
            Key.DirectionLeft, Key.DirectionUp -> { state.viewerStep(-1); true }
            Key.DirectionRight, Key.DirectionDown -> { state.viewerStep(1); true }
            Key.Home -> { state.viewerGoTo(0); true }
            Key.MoveEnd -> { state.viewerGoTo(state.viewerIds.lastIndex); true }
            else -> false
        }
    }
    val free = state.layout == BoardLayouts.FREE
    if (ctrl) {
        // Ctrl+↑/↓ reorders the focused card: grid = earlier/later in its group,
        // free = one z-level; with Shift it goes all the way.
        val focused = state.focusId
        val all = shift
        return when (key) {
            Key.A -> { state.selectAll(); true }
            Key.G -> {
                if (shift) state.ungroupItems(state.selection) else state.startGrouping()
                true
            }
            Key.D -> { state.drawerOpen = !state.drawerOpen; true }
            Key.C -> { state.copySelection(); true }
            Key.V -> { state.importPasted(); true }
            Key.DirectionUp -> {
                if (focused != null) reorder(state, focused, free, raise = free, all = all)
                true
            }
            Key.DirectionDown -> {
                if (focused != null) reorder(state, focused, free, raise = !free, all = all)
                true
            }
            else -> false
        }
    }
    return when (key) {
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
        Key.Spacebar -> { state.toggleViewer(); true }
        Key.DirectionLeft -> { if (free) state.nudgeSelection(-10f, 0f) else state.moveFocus(-1); true }
        Key.DirectionRight -> { if (free) state.nudgeSelection(10f, 0f) else state.moveFocus(1); true }
        Key.DirectionUp -> { if (free) state.nudgeSelection(0f, -10f) else state.moveFocus(-1); true }
        Key.DirectionDown -> { if (free) state.nudgeSelection(0f, 10f) else state.moveFocus(1); true }
        Key.N -> { state.openEditor(BoardEditor.EditNote(null)); true }
        Key.L -> { state.openEditor(BoardEditor.EditLink(null)); true }
        Key.P -> {
            val ids = state.selection.ifEmpty { setOfNotNull(state.focusId) }
            if (ids.isNotEmpty()) state.openEditor(BoardEditor.ShowPalette(ids))
            true
        }
        Key.G -> { state.startGrouping(); true }
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
                is LinkItem -> state.openEditor(BoardEditor.EditLink(focused.id))
                null -> {}
            }
            true
        }
        Key.Delete -> { state.removeItems(state.selection); true }
        else -> false
    }
}

/**
 * Shared Ctrl+↑/↓ reorder. [raise] means "later in the items array": that is *up* in the
 * freeform z-order but *later* (Ctrl+↓) in the grid's reading order — the caller maps the
 * arrow direction per mode so both feel natural.
 */
private fun reorder(state: BoardState, id: String, free: Boolean, raise: Boolean, all: Boolean) {
    when {
        all && raise -> state.bringToFront(id)
        all -> state.sendToBack(id)
        free -> state.stepZ(id, forward = raise)
        else -> state.stepInGroup(id, state.item(id)?.groups?.firstOrNull(), forward = raise)
    }
}
