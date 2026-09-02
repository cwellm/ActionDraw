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
     * What an import produced: the new cards, plus whatever was left out — so the board can say
     * why a dropped file did not appear instead of swallowing it.
     */
    data class Outcome(
        val items: List<ImageItem> = emptyList(),
        /** Files of a format this build cannot decode (see [ImageScanner.IMAGE_EXTENSIONS]). */
        val unsupported: List<File> = emptyList(),
        /** Pictures that are already on the board. */
        val duplicates: Int = 0,
    )

    /**
     * Files and/or directories (expanded recursively) → image items. Non-images and paths already
     * on the board ([existingPaths]) are skipped. Each item lands in [groupId] (null = Inbox).
     */
    fun importFiles(
        root: File,
        files: List<File>,
        groupId: String?,
        existingPaths: Set<String>,
    ): Outcome {
        val flat = files.flatMap { if (it.isDirectory) ImageScanner.scanTree(it) else listOf(it) }
        val (images, rest) = flat.partition { ImageScanner.isImage(it) }
        val taken = existingPaths.toMutableSet()
        var duplicates = 0
        val items = images.mapNotNull { file ->
            val path =
                if (isUnder(root, file)) relKey(root, file)
                else copyIn(root, file)?.let { relKey(root, it) } ?: return@mapNotNull null
            if (!taken.add(path)) {
                duplicates++
                return@mapNotNull null
            }
            ImageItem(
                id = newId(),
                path = path,
                groups = listOfNotNull(groupId),
                contentId = ContentId.of(File(root, path)),
            )
        }
        return Outcome(items, rest.filter { it.isFile }, duplicates)
    }

    /** A pasted bitmap (e.g. browser → "Copy image") → PNG in `_imported/` + its item. */
    fun importBitmap(root: File, image: BufferedImage, groupId: String?, timestamp: String): ImageItem? =
        runCatching {
            val dir = File(root, IMPORT_DIR).apply { mkdirs() }
            val target = collisionFree(File(dir, "pasted-$timestamp.png"))
            check(ImageIO.write(image, "png", target))
            val rel = relKey(root, target)
            ImageItem(
                id = newId(),
                path = rel,
                groups = listOfNotNull(groupId),
                contentId = ContentId.of(File(root, rel)),
            )
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
