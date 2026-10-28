package de.creaflect.actiondraw

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import de.creaflect.actiondraw.board.ui.handleBoardKey
import de.creaflect.actiondraw.lens.ui.handleLensRoomKey
import de.creaflect.actiondraw.practice.PracticePage
import de.creaflect.actiondraw.practice.PracticeState
import de.creaflect.actiondraw.sketch.handleSketchChar
import de.creaflect.actiondraw.sketch.handleSketchShortcut
import de.creaflect.actiondraw.ui.handleSessionShortcut
import de.creaflect.actiondraw.ui.handleSummaryKey

// The windows' keys, handed to whichever room is on screen — each room handles its own; what is
// here is only which room that is, and what a window does with fullscreen.

internal fun toggleFullscreen(ws: WindowState) {
    ws.placement =
        if (ws.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating
        else WindowPlacement.Fullscreen
}

/** A key event in words, for the Pen panel's readout: type, key, character, modifiers. */
private fun describeKey(event: KeyEvent): String {
    val native = event.nativeKeyEvent as? java.awt.event.KeyEvent
    val name = native?.let { java.awt.event.KeyEvent.getKeyText(it.keyCode) } ?: event.key.toString()
    val char = event.utf16CodePoint.takeIf { it > 32 }?.let { " '" + it.toChar() + "'" } ?: ""
    val mods = (if (event.isCtrlPressed) " ctrl" else "") + (if (event.isShiftPressed) " shift" else "") + (if (event.isAltPressed) " alt" else "")
    return "key ${event.type} $name$char$mods"
}

/** The main window's keys. Keeps hands on the keyboard so the drawing stays in flow. */
internal fun handleKey(event: KeyEvent, rooms: Rooms, windowState: WindowState): Boolean {
    val state = rooms.app
    val sketchState = rooms.sketch
    val conceptState = rooms.concepts
    val boardState = rooms.boards
    val practice = rooms.practice
    // A Live Sketch dialog can be open over any screen (the unsaved-strokes question on closing):
    // it owns the keyboard, and Esc puts it away.
    if (sketchState.editor != null && state.screen != Screen.Sketch) {
        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) sketchState.closeEditor()
        return event.key == Key.Escape
    }
    if (state.screen == Screen.Sketch) {
        // What arrived, for the Pen panel: a tablet's dial may send keys, a wheel, or nothing.
        sketchState.lastInput = describeKey(event)
        // Space held pans: the one key whose release matters, so it is read before the rest.
        if (event.key == Key.Spacebar && sketchState.editor == null) {
            sketchState.spaceHeld = event.type == KeyEventType.KeyDown
            return true
        }
        // [ ] + − by the character they type, whatever the layout (see handleSketchChar).
        if (event.type == KeyEventType.Unknown) {
            return handleSketchChar(event.utf16CodePoint.toChar(), sketchState)
        }
        if (event.type != KeyEventType.KeyDown) return false
        return handleSketchShortcut(event.key, event.isCtrlPressed, sketchState)
    }
    if (event.type != KeyEventType.KeyDown) return false
    // A concept dialog owns the keyboard; Esc closes it, and on the concept screens Esc goes up.
    if (conceptState.editor != null) {
        if (event.key == Key.Escape) {
            conceptState.closeEditor()
            return true
        }
        return false
    }
    if (state.screen == Screen.Concept && event.key == Key.Escape) {
        conceptState.closeConcept()
        return true
    }
    if (state.screen == Screen.Concepts && event.key == Key.Escape) {
        conceptState.leaveList()
        return true
    }
    // A board dialog may be open on any screen (the board picker lives on the menu): Esc closes
    // it, everything else stays with the dialog's text fields.
    if (boardState.editor != null) {
        if (event.key == Key.Escape) {
            boardState.closeEditor()
            return true
        }
        return false
    }
    return when (state.screen) {
        Screen.Practice -> when (practice.page) {
            PracticePage.Summary -> handleSummaryKey(event.key, practice)

            PracticePage.Picker -> when (event.key) {
                Key.Escape, Key.Enter -> { practice.closePicker(); true }
                else -> false
            }

            // The top of a room: Esc goes home to the palette.
            PracticePage.Menu -> event.key == Key.Escape && escapeToPalette(state, practice, boardState)

            PracticePage.Session -> sessionKeys(event, practice, windowState)
        }

        Screen.BoardList -> event.key == Key.Escape && escapeToPalette(state, practice, boardState)

        Screen.Lens -> handleLensRoomKey(event.key, event.isCtrlPressed, rooms.lens)

        Screen.Board -> handleBoardKey(
            event,
            boardState,
            isFullscreen = windowState.placement == WindowPlacement.Fullscreen,
            setFullscreen = { on ->
                windowState.placement = if (on) WindowPlacement.Fullscreen else WindowPlacement.Floating
            },
        )

        else -> false
    }
}

/** The board-session window: the same session/summary shortcuts, scoped to its own window. */
internal fun handleSessionWindowKey(event: KeyEvent, state: PracticeState, windowState: WindowState): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when (state.windowPage) {
        PracticePage.Summary -> handleSummaryKey(event.key, state)
        PracticePage.Session -> sessionKeys(event, state, windowState)
        else -> false
    }
}

private fun sessionKeys(event: KeyEvent, state: PracticeState, windowState: WindowState): Boolean =
    handleSessionShortcut(
        key = event.key,
        state = state,
        isFullscreen = windowState.placement == WindowPlacement.Fullscreen,
        setFullscreen = { on ->
            windowState.placement = if (on) WindowPlacement.Fullscreen else WindowPlacement.Floating
        },
    )
