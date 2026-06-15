package de.creaflect.actiondraw.image

import java.io.File

/** Collects the drawable images directly inside a folder (top-level only). */
object ImageScanner {
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")

    fun scan(folder: File): List<File> =
        folder
            .listFiles { f -> f.isFile && f.extension.lowercase() in IMAGE_EXTENSIONS }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
}
