package de.creaflect.actiondraw.image

import java.io.File

/**
 * Persists images flagged for "redo" as a hidden text file inside the image folder (one name per
 * line). Mirrors [SeenStore]. All IO is best-effort: a read-only or unwritable folder degrades to
 * "no redo flags" rather than crashing a practice session.
 */
object RedoStore {
    const val FILE_NAME = ".actiondraw_redo.txt"

    private fun file(folder: File) = File(folder, FILE_NAME)

    fun read(folder: File): Set<String> = runCatching {
        val f = file(folder)
        if (!f.exists()) emptySet()
        else f.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }.getOrDefault(emptySet())

    fun write(folder: File, names: Set<String>) {
        runCatching {
            val f = file(folder)
            if (names.isEmpty()) f.delete() else f.writeText(names.joinToString(System.lineSeparator()))
        }
    }

    fun clear(folder: File) {
        runCatching { file(folder).takeIf { it.exists() }?.delete() }
    }
}
