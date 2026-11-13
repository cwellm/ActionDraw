package de.creaflect.actiondraw

import de.creaflect.actiondraw.image.ImageDecoder
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import java.awt.Transparency
import java.awt.color.ColorSpace
import java.awt.image.BufferedImage
import java.awt.image.ComponentColorModel
import java.awt.image.DataBuffer
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Pictures Skia cannot read itself, decoded through ImageIO: an AVIF, and one deeper than eight bits a channel. */
class ImageDecoderTest {
    private val dir: File = Files.createTempDirectory("decoder").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun Image.colourAt(x: Int, y: Int): Int = Bitmap.makeFromImage(this).use { it.getColor(x, y) }

    /** Within a step of eight bits of [want], channel by channel: the decoders round a little differently. */
    private fun assertNear(want: Int, got: Int, what: String) {
        for (shift in listOf(16, 8, 0)) {
            val a = (want shr shift) and 0xFF
            val b = (got shr shift) and 0xFF
            assertTrue(kotlin.math.abs(a - b) <= 6, "$what: #${Integer.toHexString(got)}, not #${Integer.toHexString(want)}")
        }
    }

    @Test
    fun anAvifIsDecodedAsItLooks() {
        // 64 × 32, orange on its left half and blue on its right, as Pillow wrote it.
        val bytes = assertNotNull(ImageDecoderTest::class.java.getResourceAsStream("/halves.avif")).use { it.readBytes() }
        val avif = File(dir, "halves.avif").apply { writeBytes(bytes) }
        val image = assertNotNull(ImageDecoder.decode(avif))
        assertEquals(64 to 32, image.width to image.height)
        assertNear(0xE68C28, image.colourAt(10, 16), "its left half orange")
        assertNear(0x2846C8, image.colourAt(54, 16), "its right half blue")
    }

    @Test
    fun aPictureTenBitsAChannelDeepIsAsBrightAsItIs() {
        // As the AVIF reader gives a 10-bit AVIF: three channels of 10 bits, each held in 16.
        val model = ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), intArrayOf(10, 10, 10), false, false, Transparency.OPAQUE, DataBuffer.TYPE_USHORT)
        val deep = BufferedImage(model, model.createCompatibleWritableRaster(4, 2), false, null)
        // A warm mid grey, (90, 78, 66) in eight bits: 361, 313, 265 of 1023.
        for (x in 0 until 4) for (y in 0 until 2) deep.raster.setPixel(x, y, intArrayOf(361, 313, 265))
        assertNear(0x5A4E42, deep.getRGB(1, 1), "as the picture's own colour model sees it")
        val image = ImageDecoder.fromImageIo(deep)
        assertNear(0x5A4E42, image.colourAt(1, 1), "handed to Skia, not all but black")
    }
}
