package de.creaflect.actiondraw.board

import de.creaflect.actiondraw.image.ImageScanner
import de.creaflect.actiondraw.image.relKey
import de.creaflect.actiondraw.io.Folders
import de.creaflect.actiondraw.newId
import java.io.File

/**
 * Turns external material into board items. Files already inside the board folder are referenced
 * in place; anything from outside is copied into `_imported/` (shaping A1/A2) — the board never
 * moves or renames what it didn't create.
 */
internal object Importer {
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
    ): ImportOutcome {
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
        return ImportOutcome(items, rest.filter { it.isFile }, duplicates)
    }

    /** A pasted picture, as PNG ([png]), into `_imported/` + its item. */
    fun importBitmap(root: File, png: ByteArray, groupId: String?, timestamp: String): ImageItem? =
        runCatching {
            val dir = File(root, IMPORT_DIR).apply { mkdirs() }
            val target = Folders.freeFile(File(dir, "pasted-$timestamp.png"))
            target.writeBytes(png)
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
        val target = Folders.freeFile(File(dir, file.name))
        file.copyTo(target)
        target
    }.getOrNull()

    fun isUnder(root: File, file: File): Boolean = runCatching {
        file.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())
    }.getOrDefault(false)
}
