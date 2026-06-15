package de.creaflect.actiondraw.image

import java.io.File

/**
 * Persists which images have already been shown, as a hidden text file inside the image folder
 * (one file name per line). Self-contained per folder and identical on Windows and Linux.
 */
object SeenStore {
    const val FILE_NAME = ".actiondraw_seen.txt"

    private fun file(folder: File) = File(folder, FILE_NAME)

    fun read(folder: File): Set<String> {
        val f = file(folder)
        if (!f.exists()) return emptySet()
        return f.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun write(folder: File, names: Set<String>) {
        file(folder).writeText(names.joinToString(System.lineSeparator()))
    }

    fun clear(folder: File) {
        val f = file(folder)
        if (f.exists()) f.delete()
    }
}
