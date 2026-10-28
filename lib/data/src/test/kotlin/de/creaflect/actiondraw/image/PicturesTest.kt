package de.creaflect.actiondraw.image

import de.creaflect.actiondraw.FileLib
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The lib's pictures as a caller sees them: decoding, thumbnails, colours, contact sheets. */
class PicturesTest {
    private val dir: File = Files.createTempDirectory("actiondraw-pictures").toFile()
    private val home: File = Files.createTempDirectory("actiondraw-pictures-home").toFile()
    private val pictures = FileLib(home).pictures

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
        home.deleteRecursively()
    }

    /** Writes a PNG split into two colour halves, so its palette is predictable. */
    private fun twoColourPng(file: File, left: Int, right: Int, size: Int = 64) {
        val surface = Surface.makeRasterN32Premul(size, size)
        val canvas: Canvas = surface.canvas
        canvas.drawRect(Rect.makeXYWH(0f, 0f, size / 2f, size.toFloat()), Paint().apply { color = left })
        canvas.drawRect(Rect.makeXYWH(size / 2f, 0f, size / 2f, size.toFloat()), Paint().apply { color = right })
        file.writeBytes(surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes)
    }

    // ---- Writing ----

    @Test
    fun aPngIsWrittenWholeOrBesideAnotherUnderAFreeName() {
        val png = File(dir, "source.png").also { twoColourPng(it, 0xFF202020.toInt(), 0xFFE0E0E0.toInt()) }.readBytes()
        val target = File(dir, "views/pose - notan.png")
        assertEquals(target, pictures.writePng(png, target), "into a folder made for it")
        assertTrue(png.contentEquals(target.readBytes()))
        assertEquals(File(dir, "views/pose - notan (2).png"), pictures.writePng(png, target, beside = true), "beside the one already there")
        assertEquals(target, pictures.writePng(byteArrayOf(1, 2, 3), target), "or over it, where asked")
        assertEquals(3L, target.length())
        assertFalse(File(dir, "views").listFiles()!!.any { it.name.endsWith(".tmp") }, "and nothing half-written left behind")
    }

    // ---- Decoding ----

    @Test
    fun aPictureDecodesWholeAndAsAThumbnailKeptInTheLibsHome() {
        val file = File(dir, "big.png").also { twoColourPng(it, 0xFFCC2020.toInt(), 0xFF2040CC.toInt(), size = 400) }

        assertEquals(400, assertNotNull(pictures.decode(file)).width)
        assertEquals(100, assertNotNull(pictures.thumbnail(file, maxSize = 100)).width, "downscaled on its long edge")
        assertTrue(File(home, ".actiondraw/thumbs").listFiles().orEmpty().isNotEmpty(), "and kept for next time, in the lib's own home")
    }

    @Test
    fun somethingThatIsNotAPictureDecodesToNothing() {
        val broken = File(dir, "broken.png").apply { writeText("not a picture") }
        assertNull(pictures.decode(broken))
        assertNull(pictures.thumbnail(broken))
        assertFalse(pictures.isPicture(File(dir, "notes.txt").apply { writeText("x") }))
        assertTrue("png" in pictures.extensions)
    }

    // ---- Palette ----

    @Test
    fun paletteFindsThePicturesDominantColours() {
        val file = File(dir, "halves.png")
        twoColourPng(file, left = 0xFFCC2020.toInt(), right = 0xFF2040CC.toInt())

        val palette = pictures.palette(file, count = 4)
        assertTrue(palette.size >= 2, "expected at least the two halves, got $palette")
        val top = palette.take(2)
        // Red-dominant and blue-dominant buckets, whichever order they land in.
        assertTrue(top.any { (it shr 16 and 0xFF) > (it and 0xFF) }, "a reddish colour: $top")
        assertTrue(top.any { (it and 0xFF) > (it shr 16 and 0xFF) }, "a blueish colour: $top")
    }

    @Test
    fun paletteOfSomethingUnreadableIsEmpty() {
        val broken = File(dir, "broken.png").apply { writeText("not a picture") }
        assertTrue(pictures.palette(broken).isEmpty())
    }

    // ---- Contact sheet ----

    @Test
    fun contactSheetWritesAReadablePngWithACellPerPicture() {
        twoColourPng(File(dir, "a.png"), 0xFFCC2020.toInt(), 0xFF992020.toInt())
        twoColourPng(File(dir, "b.png"), 0xFF2040CC.toInt(), 0xFF2030AA.toInt())
        val cells = listOf(SheetCell(File(dir, "a.png"), "first"), SheetCell(File(dir, "b.png"), "b.png"))
        val target = File(dir, "sheet.png")

        assertTrue(pictures.contactSheet("Drachenbuch", cells, target, columns = 2))
        assertTrue(target.isFile && target.length() > 0)
        val sheet = Image.makeFromEncoded(target.readBytes())
        assertTrue(sheet.width > 600, "two columns of cells: ${sheet.width}")
        assertTrue(sheet.height > 300, "one row plus the header: ${sheet.height}")
    }

    @Test
    fun contactSheetOfNothingIsRefused() {
        val target = File(dir, "empty.png")
        assertFalse(pictures.contactSheet("x", emptyList(), target))
        assertFalse(target.exists())
    }

    @Test
    fun aPictureThatCannotBeFoundLeavesItsCellEmptyButKeepsItsCaption() {
        twoColourPng(File(dir, "a.png"), 0xFFCC2020.toInt(), 0xFF992020.toInt())
        val target = File(dir, "sheet.png")
        assertTrue(pictures.contactSheet("x", listOf(SheetCell(File(dir, "a.png"), "a"), SheetCell(null, "gone")), target))
        assertTrue(target.isFile)
    }
}
