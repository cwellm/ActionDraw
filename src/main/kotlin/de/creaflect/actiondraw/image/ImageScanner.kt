package de.creaflect.actiondraw.image

import java.io.File

/** Collects the drawable images of a folder — flat for practice, recursive for Idea Boards. */
object ImageScanner {
    /** Formats the bundled Skia decodes on its own. */
    private val BUILT_IN = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")

    /**
     * Formats that need an ImageIO plugin (neither the JDK nor Skia decodes them). They are only
     * advertised when a reader is actually installed, so a board never shows a card whose picture
     * can't be drawn. Add `io.github.nemanjastokuca:avif-imageio-native-reader` (or any other AVIF
     * ImageIO plugin) to the runtime classpath and `.avif` files start working with no code change.
     */
    private val PLUGIN_ONLY = setOf("avif", "heic", "heif")

    val IMAGE_EXTENSIONS: Set<String> = BUILT_IN + PLUGIN_ONLY.filter(ImageDecoder::imageIoCanRead)

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
