package de.creaflect.actiondraw.image

import java.io.File

/**
 * What has been drawn from a folder, and what is to be drawn again: kept in the folder itself, so
 * it travels with it. A picture is named by its path in the folder ([relKey]). Remembering is
 * best-effort: a folder that cannot be written to simply remembers nothing.
 */
interface PracticeHistory {
    /** The pictures of [folder] already shown in a session. */
    fun seen(folder: File): Set<String>

    fun setSeen(folder: File, names: Set<String>)

    /** The pictures of [folder] flagged to be drawn again, first thing next time. */
    fun redo(folder: File): Set<String>

    fun setRedo(folder: File, names: Set<String>)
}
