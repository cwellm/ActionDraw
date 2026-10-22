package de.creaflect.actiondraw.board

import de.creaflect.actiondraw.isInside
import de.creaflect.actiondraw.samePathAs
import java.io.File

/**
 * One board as the app knows it: its name, and the folder it lives in. A board is found by this
 * record rather than by scanning a home, so it keeps its folder wherever the home points.
 */
data class BoardEntry(
    val id: String,
    val name: String,
    val path: String,
    /**
     * True when ActionDraw made this folder for the board; false when an existing folder was
     * adopted (*Explore…*, or a drop of someone's picture library). It decides what deleting the
     * board means: a folder the app created is the board and goes with it, while a folder that
     * was already yours stays yours and merely stops being a board.
     */
    val ownsFolder: Boolean = false,
) {
    val dir: File get() = File(path)

    /** Windows paths differ only in case, so compare that way rather than by string identity. */
    fun isAt(folder: File): Boolean = dir.samePathAs(folder)

    /** True when [folder] lies inside this board's folder. */
    fun contains(folder: File): Boolean = folder.isInside(dir)

    /** True when this board's folder lies inside [folder]. */
    fun isInside(folder: File): Boolean = dir.isInside(folder)
}
