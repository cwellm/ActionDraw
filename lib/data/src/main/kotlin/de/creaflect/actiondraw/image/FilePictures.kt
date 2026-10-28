package de.creaflect.actiondraw.image

import de.creaflect.actiondraw.board.ContactSheet
import de.creaflect.actiondraw.board.ImageItem
import de.creaflect.actiondraw.board.ImportOutcome
import de.creaflect.actiondraw.board.Importer
import de.creaflect.actiondraw.board.Palette
import de.creaflect.actiondraw.io.Folders
import org.jetbrains.skia.Image
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** The [Pictures] over the files: `lib:core:io`'s decoding and scanning, with a thumbnail cache on disk. */
internal class FilePictures(private val thumbs: ThumbCache) : Pictures {
    override val extensions: Set<String> get() = ImageScanner.IMAGE_EXTENSIONS

    override fun isPicture(file: File): Boolean = ImageScanner.isImage(file)

    override fun scan(folder: File): List<File> = ImageScanner.scan(folder)

    override fun decode(file: File): Image? = runCatching { ImageDecoder.decode(file) }.getOrNull()

    override fun thumbnail(file: File, maxSize: Int): Image? = thumbs.load(file, maxSize)

    override fun palette(file: File, count: Int): List<Int> = Palette.of(file, count)

    override fun importInto(root: File, files: List<File>, groupId: String?, existingPaths: Set<String>): ImportOutcome =
        Importer.importFiles(root, files, groupId, existingPaths)

    override fun importPng(root: File, png: ByteArray, groupId: String?, timestamp: String): ImageItem? =
        Importer.importBitmap(root, png, groupId, timestamp)

    override fun writePng(png: ByteArray, target: File, beside: Boolean): File? = runCatching {
        target.parentFile?.mkdirs()
        val file = if (beside) Folders.freeFile(target) else target
        // Whole or not at all: the bytes go to a file of their own first, then take the name.
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeBytes(png)
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        file
    }.getOrNull()

    override fun contactSheet(title: String, cells: List<SheetCell>, target: File, columns: Int): Boolean =
        ContactSheet.write(title, cells, target, columns)
}

/** The [PracticeHistory] as two hidden text files in each folder ([SeenStore], [RedoStore]). */
internal object FilePracticeHistory : PracticeHistory {
    override fun seen(folder: File): Set<String> = SeenStore.read(folder)

    override fun setSeen(folder: File, names: Set<String>) = SeenStore.write(folder, names)

    override fun redo(folder: File): Set<String> = RedoStore.read(folder)

    override fun setRedo(folder: File, names: Set<String>) = RedoStore.write(folder, names)
}
