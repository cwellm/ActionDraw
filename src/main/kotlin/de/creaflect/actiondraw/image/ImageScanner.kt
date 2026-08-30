package de.creaflect.actiondraw.image

import java.io.File

/** Collects the drawable images of a folder — flat for practice, recursive for Idea Boards. */
object ImageScanner {
    val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")

    fun isImage(file: File): Boolean = file.isFile && file.extension.lowercase() in IMAGE_EXTENSIONS

    /** The drawable images directly inside [folder] (top-level only). */
    fun scan(folder: File): List<File> =
        folder
            .listFiles { f -> isImage(f) }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    /** Every drawable image in the tree under [root]; dot-directories are skipped. */
    fun scanTree(root: File): List<File> =
        root.walkTopDown()
            .onEnter { it == root || !it.name.startsWith(".") }
            .filter { isImage(it) }
            .sortedBy { relKey(root, it).lowercase() }
            .toList()
}

/**
 * Path of [file] relative to [root], `/`-separated on every OS — the stable key used by the
 * seen/redo stores and by board sidecars. For a file directly inside [root] this equals the file
 * name, which keeps existing flat-folder store files valid. Falls back to the plain name when
 * [file] cannot be relativized against [root].
 */
fun relKey(root: File, file: File): String = runCatching {
    root.absoluteFile.toPath().normalize()
        .relativize(file.absoluteFile.toPath().normalize())
        .joinToString("/")
}.getOrDefault(file.name)
