package de.creaflect.actiondraw.image

import de.creaflect.actiondraw.board.ImageItem
import de.creaflect.actiondraw.board.ImportOutcome
import org.jetbrains.skia.Image
import java.io.File

/**
 * Pictures on disk: which files are pictures, decoding them whole or small, the colours they are
 * made of, and bringing them into a board's or a concept's folder. A picture comes as a Skia
 * image — what Compose Desktop and the sketch engine both draw with — so a screen shows it
 * without decoding it again.
 */
interface Pictures {
    /** The extensions this build reads, lower case: what a file chooser offers. */
    val extensions: Set<String>

    /** Whether [file] is a picture this build reads. */
    fun isPicture(file: File): Boolean

    /** The pictures directly in [folder], by name. */
    fun scan(folder: File): List<File>

    /** [file] decoded whole, or null when it cannot be. Call it off the UI thread. */
    fun decode(file: File): Image?

    /**
     * [file] at most [maxSize] on its long edge. Kept on disk, so a board of large photos opens
     * fast the second time; a picture changed since is decoded again. Call it off the UI thread.
     */
    fun thumbnail(file: File, maxSize: Int = 192): Image?

    /** Up to [count] colours [file] is mostly made of, most common first, as `0xRRGGBB`. */
    fun palette(file: File, count: Int = 6): List<Int>

    /**
     * [files] — folders taken whole — as cards in the folder [root]: a picture inside it is
     * referred to where it is, one from outside is copied in. Each card lands in [groupId] (the
     * inbox when null); pictures whose path is in [existingPaths] are counted, not added again.
     */
    fun importInto(root: File, files: List<File>, groupId: String? = null, existingPaths: Set<String> = emptySet()): ImportOutcome

    /** A pasted picture, as PNG bytes, written into [root] as `pasted-<timestamp>.png`, and its card. */
    fun importPng(root: File, png: ByteArray, groupId: String?, timestamp: String): ImageItem?

    /**
     * Draws [cells] as one printable sheet, [columns] wide, under [title], and writes it as a PNG
     * to [target]. False when there was nothing to draw or it could not be written.
     */
    fun contactSheet(title: String, cells: List<SheetCell>, target: File, columns: Int = 4): Boolean
}

/** One picture on a contact sheet: its file (null when it cannot be found) and its caption. */
data class SheetCell(val file: File?, val caption: String)
