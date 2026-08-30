package de.creaflect.actiondraw.board

import de.creaflect.actiondraw.image.ImageScanner
import de.creaflect.actiondraw.image.relKey
import java.awt.image.BufferedImage
import java.io.File
import java.util.UUID
import javax.imageio.ImageIO

/**
 * Turns external material into board items. Files already inside the board folder are referenced
 * in place; anything from outside is copied into `_imported/` (shaping A1/A2) — the board never
 * moves or renames what it didn't create.
 */
object Importer {
    const val IMPORT_DIR = "_imported"

    /**
     * Files and/or directories (expanded recursively) → image items. Non-images and paths already
     * on the board ([existingPaths]) are skipped. Each item lands in [groupId] (null = Inbox).
     */
    fun importFiles(
        root: File,
        files: List<File>,
        groupId: String?,
        existingPaths: Set<String>,
    ): List<ImageItem> {
        val flat = files.flatMap { if (it.isDirectory) ImageScanner.scanTree(it) else listOf(it) }
            .filter { ImageScanner.isImage(it) }
        val taken = existingPaths.toMutableSet()
        return flat.mapNotNull { file ->
            val path =
                if (isUnder(root, file)) relKey(root, file)
                else copyIn(root, file)?.let { relKey(root, it) } ?: return@mapNotNull null
            if (!taken.add(path)) return@mapNotNull null
            ImageItem(id = newId(), path = path, groups = listOfNotNull(groupId))
        }
    }

    /** A pasted bitmap (e.g. browser → "Copy image") → PNG in `_imported/` + its item. */
    fun importBitmap(root: File, image: BufferedImage, groupId: String?, timestamp: String): ImageItem? =
        runCatching {
            val dir = File(root, IMPORT_DIR).apply { mkdirs() }
            val target = collisionFree(File(dir, "pasted-$timestamp.png"))
            check(ImageIO.write(image, "png", target))
            ImageItem(id = newId(), path = relKey(root, target), groups = listOfNotNull(groupId))
        }.getOrNull()

    private fun copyIn(root: File, file: File): File? = runCatching {
        val dir = File(root, IMPORT_DIR).apply { mkdirs() }
        val target = collisionFree(File(dir, file.name))
        file.copyTo(target)
        target
    }.getOrNull()

    /** `foo.jpg` → `foo (2).jpg` → `foo (3).jpg` … the first name that doesn't exist yet. */
    fun collisionFree(target: File): File {
        if (!target.exists()) return target
        val base = target.nameWithoutExtension
        val ext = target.extension
        var n = 2
        while (true) {
            val name = if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext"
            val candidate = File(target.parentFile, name)
            if (!candidate.exists()) return candidate
            n++
        }
    }

    fun isUnder(root: File, file: File): Boolean = runCatching {
        file.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())
    }.getOrDefault(false)

    fun newId(): String = UUID.randomUUID().toString()
}
