package de.creaflect.actiondraw.board

import de.creaflect.actiondraw.board.ui.NoteText
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Phase-3 pieces that are pure enough to test on their own: note markup, palettes, sheets. */
class M3Test {
    private val dir: File = Files.createTempDirectory("actiondraw-m3").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    /** Writes a PNG split into two colour halves, so its palette is predictable. */
    private fun twoColourPng(file: File, left: Int, right: Int, size: Int = 64) {
        val surface = Surface.makeRasterN32Premul(size, size)
        val canvas: Canvas = surface.canvas
        canvas.drawRect(Rect.makeXYWH(0f, 0f, size / 2f, size.toFloat()), Paint().apply { color = left })
        canvas.drawRect(Rect.makeXYWH(size / 2f, 0f, size / 2f, size.toFloat()), Paint().apply { color = right })
        file.writeBytes(surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes)
    }

    // ---- Note markup ----

    @Test
    fun noteMarkupTurnsIntoBoldAndItalicSpans() {
        val formatted = NoteText.format("draw **wings** and *scales*")
        assertEquals("draw wings and scales", formatted.text, "markers are not shown")
        assertEquals(2, formatted.spanStyles.size, "one span per marked run")
    }

    @Test
    fun noteMarkupLeavesPlainTextAlone() {
        val text = "just a plain note"
        assertEquals(text, NoteText.format(text).text)
        assertTrue(NoteText.format(text).spanStyles.isEmpty())
    }

    @Test
    fun plainStripsTheMarkers() {
        assertEquals("wings and scales", NoteText.plain("**wings** and *scales*"))
    }

    // ---- Palette ----

    @Test
    fun paletteFindsThePicturesDominantColours() {
        val file = File(dir, "halves.png")
        twoColourPng(file, left = 0xFFCC2020.toInt(), right = 0xFF2040CC.toInt())

        val palette = Palette.of(file, count = 4)
        assertTrue(palette.size >= 2, "expected at least the two halves, got $palette")
        val top = palette.take(2)
        // Red-dominant and blue-dominant buckets, whichever order they land in.
        assertTrue(top.any { (it shr 16 and 0xFF) > (it and 0xFF) }, "a reddish colour: $top")
        assertTrue(top.any { (it and 0xFF) > (it shr 16 and 0xFF) }, "a blueish colour: $top")
    }

    @Test
    fun paletteOfSomethingUnreadableIsEmpty() {
        val broken = File(dir, "broken.png").apply { writeText("not a picture") }
        assertTrue(Palette.of(broken).isEmpty())
    }

    @Test
    fun hexIsPrintable() {
        assertEquals("#cc2020", Palette.hex(0xCC2020))
    }

    // ---- Contact sheet ----

    @Test
    fun contactSheetWritesAReadablePngWithACellPerPicture() {
        twoColourPng(File(dir, "a.png"), 0xFFCC2020.toInt(), 0xFF992020.toInt())
        twoColourPng(File(dir, "b.png"), 0xFF2040CC.toInt(), 0xFF2030AA.toInt())
        val items = listOf(
            ImageItem(id = "1", path = "a.png", caption = "first"),
            ImageItem(id = "2", path = "b.png"),
        )
        val target = File(dir, "sheet.png")

        assertTrue(ContactSheet.write(dir, items, "Drachenbuch", target, columns = 2))
        assertTrue(target.isFile && target.length() > 0)
        val sheet = Image.makeFromEncoded(target.readBytes())
        assertTrue(sheet.width > 600, "two columns of cells: ${sheet.width}")
        assertTrue(sheet.height > 300, "one row plus the header: ${sheet.height}")
    }

    @Test
    fun contactSheetOfNothingIsRefused() {
        val target = File(dir, "empty.png")
        assertFalse(ContactSheet.write(dir, listOf(NoteItem(id = "n", text = "no pictures")), "x", target))
        assertFalse(target.exists())
    }

    @Test
    fun suggestedNameIsFileSystemSafe() {
        assertEquals("Drachen_ Buch-contact-sheet.png", ContactSheet.suggestedName("Drachen: Buch"))
    }
}
