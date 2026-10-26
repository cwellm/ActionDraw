package de.creaflect.actiondraw

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.creaflect.actiondraw.platform.HostPreferences

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

    // ---- Into a room and back (the hand-off seam, the palette) ----

    /** Where Back from each room entered so goes: the palette, a board, a sketch, a paused session. */
    private val origins = mutableMapOf<Screen, Screen>()

    /** Where Back from [screen] goes. */
    fun originOf(screen: Screen): Screen = origins[screen] ?: Screen.Palette

    /**
     * Into [to], remembering where Back there leads: [from] — where the app is, unless said
     * otherwise. Already there, it keeps the way back it had.
     */
    fun enter(to: Screen, from: Screen = screen) {
        if (screen != to) origins[to] = if (from == to) Screen.Palette else from
        screen = to
    }

    /** Back from [from]: to where it was entered from. */
    fun back(from: Screen) {
        screen = originOf(from)
        origins.remove(from)
    }

    /**
     * Home from [from]: the palette. A session paused for this room is not left behind: then home
     * means back to that session, as Back does.
     */
    fun home(from: Screen) {
        if (originOf(from) == Screen.Practice) {
            back(from)
        } else {
            origins.remove(from)
            screen = Screen.Palette
        }
    }
}
