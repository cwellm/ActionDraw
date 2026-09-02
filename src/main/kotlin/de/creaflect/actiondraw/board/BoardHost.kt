package de.creaflect.actiondraw.board

import de.creaflect.actiondraw.SessionSetup
import java.io.File

/**
 * Everything the board is allowed to ask of the rest of the app — the whole "plugin" boundary
 * (shaping §4). The practice side knows nothing about boards; the app shell implements this.
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
}
