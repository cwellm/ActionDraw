package de.creaflect.actiondraw

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.creaflect.actiondraw.platform.HostPreferences
import de.creaflect.actiondraw.ui.Room

/**
 * Where the app is: the palette, its home, or one of the rooms (its [screen]), the well selected
 * on the palette, and how much the app may move. What each room holds is the room's own state;
 * this is only the way between them.
 *
 * [lib] keeps the room and the motion preference from one run to the next; [host] says what the
 * system prefers until the person chooses.
 */
class AppState(private val lib: Lib, host: HostPreferences = HostPreferences { false }) {
    /** The app opens on the palette, its home; every room is entered from there. */
    var screen by mutableStateOf(Screen.Palette)
        private set

    /** The well selected on the palette, kept from one run to the next. */
    var paletteRoom by mutableStateOf(
        lib.preferences.lastRoom()?.let { name -> Room.entries.firstOrNull { it.name == name } } ?: Room.PRACTICE,
    )
        private set

    fun selectRoom(room: Room) {
        if (room == paletteRoom) return
        paletteRoom = room
        lib.preferences.setLastRoom(room.name)
    }

    /** Movement kept to short cross-fades: the setting once made, until then the system's preference. */
    var reducedMotion by mutableStateOf(lib.preferences.reducedMotion() ?: host.prefersReducedMotion())
        private set

    fun setReducedMotionPreference(on: Boolean) {
        reducedMotion = on
        lib.preferences.setReducedMotion(on)
    }

    /** Home: back to the palette. */
    fun showPalette() {
        screen = Screen.Palette
    }

    /** The Practice room, wherever it was left. */
    fun showPractice() {
        screen = Screen.Practice
    }

    // ---- Boards (wired by the app shell; the rooms stay unaware of each other) ----

    fun showBoardList() {
        screen = Screen.BoardList
    }

    fun showBoard() {
        screen = Screen.Board
    }

    fun leaveBoard() {
        screen = Screen.Palette
    }

    // ---- Concept navigation (the same arrangement as for boards) ----

    fun showConcepts() {
        screen = Screen.Concepts
    }

    fun showConcept() {
        screen = Screen.Concept
    }

    fun leaveConcepts() {
        screen = Screen.Palette
    }

    // ---- Live Sketch ----

    /** Where Back from Live Sketch goes: the palette, a paused session, a board, a concept. */
    var sketchOrigin by mutableStateOf(Screen.Palette)
        private set

    fun showSketch(from: Screen = Screen.Palette) {
        if (screen != Screen.Sketch) sketchOrigin = if (from == Screen.Sketch) Screen.Palette else from
        screen = Screen.Sketch
    }

    /**
     * Home from Live Sketch. A session paused for the sketch is not left behind: then home means
     * back to that session, as Back does.
     */
    fun sketchHome() {
        if (sketchOrigin == Screen.Practice) leaveSketch() else {
            sketchOrigin = Screen.Palette
            screen = Screen.Palette
        }
    }

    /**
     * From a session: the sketch takes the main window, and Back returns to where it was — the
     * Practice room, or the board whose session waits, paused, in its own window.
     */
    fun sketchFromSession() {
        showSketch(from = screen)
    }

    fun leaveSketch() {
        screen = sketchOrigin
        sketchOrigin = Screen.Palette
    }

    // ---- The Lens room ----

    /** Where Back from the Lens room goes: the palette, a board, a sketch. */
    var lensOrigin by mutableStateOf(Screen.Palette)
        private set

    fun showLens(from: Screen = Screen.Palette) {
        if (screen != Screen.Lens) lensOrigin = if (from == Screen.Lens) Screen.Palette else from
        screen = Screen.Lens
    }

    /** Home from the Lens room: the palette, wherever it was entered from. */
    fun lensHome() {
        lensOrigin = Screen.Palette
        screen = Screen.Palette
    }

    /** Back from the Lens room, to where it was entered from — a board's card, a sketch, the palette. */
    fun leaveLens() {
        screen = lensOrigin
        lensOrigin = Screen.Palette
    }
}
