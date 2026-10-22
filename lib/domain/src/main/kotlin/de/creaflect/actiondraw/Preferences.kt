package de.creaflect.actiondraw

import java.io.File

/**
 * What the person chose, kept for the next run: the folders things go in, the room and the
 * source they used last, how the app should behave. It belongs to the installation, not to a
 * board. Remembering is best-effort: what cannot be kept is simply not remembered.
 */
interface Preferences {
    /** The folder Practice drew from last time, or null if unknown or no longer a folder. */
    fun lastFolder(): File?

    fun setLastFolder(folder: File)

    /** Reduced motion as chosen in ActionDraw; null until chosen, when the system's preference counts. */
    fun reducedMotion(): Boolean?

    fun setReducedMotion(on: Boolean)

    /** The board the Practice room draws from, or null while a folder is the source. */
    fun practiceBoard(): File?

    fun setPracticeBoard(dir: File?)

    /** Whether a tilted pen shades with the side of the lead; on unless it was turned off. */
    fun tiltShading(): Boolean

    fun setTiltShading(on: Boolean)

    /** The room the app was in when it was last used, by name. */
    fun lastRoom(): String?

    fun setLastRoom(room: String)

    /** Where new concepts go. Need not exist yet. */
    fun conceptsHome(): File

    fun setConceptsHome(dir: File)

    /** Where sketches are saved unless another folder is chosen. Need not exist yet. */
    fun sketchesHome(): File

    fun setSketchesHome(dir: File)

    /** Align dragged cards to their neighbours' centre lines. Off unless switched on. */
    fun snapByDefault(): Boolean

    fun setSnapByDefault(on: Boolean)

    /** Where new top-level boards go. Need not exist yet. */
    fun boardsHome(): File

    fun setBoardsHome(dir: File)

    /** Board folders opened lately, most recent first; vanished ones are left out. */
    fun recentBoards(): List<File>

    fun addRecentBoard(dir: File)

    /** Forgets a board — when it is deleted or has moved, so it stops showing up as recent. */
    fun removeRecentBoard(dir: File)

    /** Sketch documents saved or opened lately, anywhere; vanished ones are left out. */
    fun recentSketches(): List<File>

    fun addRecentSketch(file: File)

    /** The Tune panel's leads, as the sketch side encodes them; null when there are none. */
    fun leadPresetsJson(): String?

    fun setLeadPresetsJson(json: String)
}
