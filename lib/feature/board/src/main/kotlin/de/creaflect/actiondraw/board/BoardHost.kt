package de.creaflect.actiondraw.board

import de.creaflect.actiondraw.SessionSetup
import java.io.File

/**
 * What the board asks of the rest of the app — a session drawn from it, the way between its own
 * screens, a concept opened — through one boundary (shaping §4) the app fills; the practice side
 * knows nothing about boards. A picture taken into another room goes through the hand-off seam
 * (the domain's `Handoff`), and the other rooms reach the boards through `BoardSource`.
 */
interface BoardHost {
    /**
     * Start a practice session whose pool is exactly [images]; seen/redo state lives in [root].
     * [setup] is the board's remembered session settings, or null to keep the menu's.
     */
    fun startSession(root: File, images: List<File>, setup: SessionSetup?)

    /** Navigate to the board screen (after a board was opened or created). */
    fun showBoard()

    /** Navigate to the list of boards. */
    fun showBoardList()

    /** Navigate back to the menu (the board was closed). */
    fun leaveBoard()

    /** The practice side's current settings — what "remember what is on screen" saves. */
    fun currentSetup(): SessionSetup

    /** Show the concept with [id] — a linked group's "Open concept". Ignored where there are none. */
    fun showConcept(id: String) {}

    /** Show the list of concepts. */
    fun showConcepts() {}
}
