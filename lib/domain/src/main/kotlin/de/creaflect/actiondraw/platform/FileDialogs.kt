package de.creaflect.actiondraw.platform

import java.io.File

/**
 * The host's file and folder dialogs. Each waits until the person has chosen, and says "nothing" —
 * null, or an empty list — when they cancel. What a dialog shows is the caller's to say.
 */
interface FileDialogs {
    /** A folder, starting from [start] when it is one. */
    fun chooseFolder(start: File?, title: String): File?

    /** Several files with one of [extensions], shown as [description]. */
    fun chooseFiles(start: File?, title: String, description: String, extensions: Collection<String>): List<File>

    /** One file that [accepts] lets through, shown as [description]. */
    fun chooseFile(start: File?, title: String, description: String, accepts: (File) -> Boolean): File?

    /** Where to save [suggested]; the suggested extension is added when the person drops it. */
    fun chooseSaveFile(suggested: String, start: File?): File?

    /** No dialogs: every one comes back as cancelled. Where a host has none, or a test wants none. */
    object None : FileDialogs {
        override fun chooseFolder(start: File?, title: String): File? = null
        override fun chooseFiles(start: File?, title: String, description: String, extensions: Collection<String>): List<File> = emptyList()
        override fun chooseFile(start: File?, title: String, description: String, accepts: (File) -> Boolean): File? = null
        override fun chooseSaveFile(suggested: String, start: File?): File? = null
    }
}

/** Pictures to bring in: those with one of [extensions] (the lib's `Pictures.extensions`). */
fun FileDialogs.chooseImages(start: File?, extensions: Collection<String>): List<File> =
    chooseFiles(start, "Import images", "Images", extensions)
