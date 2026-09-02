package de.creaflect.actiondraw.board

import java.io.File

/**
 * Everything the board is allowed to ask of the rest of the app — the whole "plugin" boundary
 * (shaping §4). The practice side knows nothing about boards; the app shell implements this.
 */
interface BoardHost {
    /** Start a practice session whose pool is exactly [images]; seen/redo state lives in [root]. */
    fun startSession(root: File, images: List<File>)

    /** Navigate to the board screen (after a board was opened or created). */
    fun showBoard()

    /** Navigate back to the menu (the board was closed). */
    fun leaveBoard()
}
