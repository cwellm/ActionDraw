package de.creaflect.actiondraw

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import de.creaflect.actiondraw.lens.LensState
import de.creaflect.actiondraw.ui.Atelier
import de.creaflect.actiondraw.ui.FORM_SAVED
import de.creaflect.actiondraw.ui.Form
import de.creaflect.actiondraw.ui.Optic
import de.creaflect.actiondraw.ui.lensedPng
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A view saved: the picture through the lens, whole, at its own size, looking as it did on screen. */
class LensExportTest {
    private fun picture(w: Int, h: Int, grey: (x: Int, y: Int) -> Int): ImageBitmap {
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until w) for (y in 0 until h) {
            val v = grey(x, y)
            image.setRGB(x, y, (v shl 16) or (v shl 8) or v)
        }
        return image.toComposeImageBitmap()
    }

    /** The saved PNG, decoded, and its grey at a point. */
    private class Saved(png: ByteArray) {
        val image: Image = assertNotNull(Image.makeFromEncoded(png))
        private val bitmap = Bitmap.makeFromImage(image)
        fun grey(x: Int, y: Int): Int = (bitmap.getColor(x, y) shr 16) and 0xFF
    }

    private fun save(bitmap: ImageBitmap, lens: LensState, shownScale: Float = 1f) =
        Saved(assertNotNull(lensedPng(bitmap, lens, shownScale, density = 1f, design = Atelier), "the view drawn"))

    @Test
    fun theViewIsSavedThroughItsLensAtThePicturesOwnSize() {
        val ramp = picture(200, 100) { x, _ -> x * 255 / 199 }
        val saved = save(ramp, LensState().apply { viewMode = ViewMode.SILHOUETTE }, shownScale = 0.5f)
        assertEquals(200 to 100, saved.image.width to saved.image.height, "the picture's own size, not the screen's")
        val row = (5 until 195 step 5).map { saved.grey(it, 50) }
        assertTrue(row.all { it < 10 || it > 245 }, "a silhouette: black and white only, $row")
        assertTrue(row.first() < 10 && row.last() > 245)
    }

    @Test
    fun aPixelatesBlocksGrowWithThePictureAsItWasShownSmaller() {
        val noise = picture(160, 80) { x, y -> (x * 37 + y * 91) % 256 }
        val lens = LensState().apply {
            viewMode = ViewMode.PIXELATE
            pixelateBlock = 8
        }
        // Shown at half its size, an 8-pixel block on screen is 16 of the picture's own. (Along a
        // row and down a column: the noise repeats itself every 8 pixels on the diagonal.)
        val saved = save(noise, lens, shownScale = 0.5f)
        assertEquals(saved.grey(0, 0), saved.grey(15, 0), "one block, 16 across")
        assertEquals(saved.grey(0, 0), saved.grey(0, 15), "and 16 down")
        assertEquals(saved.grey(16, 0), saved.grey(31, 0))
        assertNotEquals(saved.grey(15, 0), saved.grey(16, 0), "and the next one beside it")
    }

    @Test
    fun theGlassOverTheWholePictureIsSavedWithTheView() {
        val ramp = picture(200, 100) { x, _ -> x * 255 / 199 }
        val plain = save(ramp, LensState())
        val laid = save(ramp, LensState().apply { glass.choose(Optic.CONVEX); glass.spreadOver(0.5f, 0.5f) })
        assertTrue(laid.grey(50, 50) > plain.grey(50, 50) + 10, "bent about the middle: ${plain.grey(50, 50)} -> ${laid.grey(50, 50)}")
        assertTrue(kotlin.math.abs(plain.grey(100, 50) - laid.grey(100, 50)) <= 6, "and the middle itself where it was, with no mark on it")
        assertTrue(kotlin.math.abs(plain.grey(91, 50) - laid.grey(91, 50)) <= 12, "nor a ring round it")
    }

    @Test
    fun aPictureOnAFormIsSavedOnItNoLargerThanAFormIsDrawn() {
        val wide = picture(2000, 1000) { x, _ -> 60 + x * 180 / 1999 }
        val saved = save(wide, LensState().apply { form.choose(Form.SPHERE) })
        assertEquals(FORM_SAVED.toInt() to (FORM_SAVED / 2).toInt(), saved.image.width to saved.image.height, "drawn, not resampled: no larger than this")
        assertEquals(0, saved.grey(4, 4), "round the ball, nothing")
        assertTrue(saved.grey(800, 400) > 20, "on it, the picture")
    }

    @Test
    fun theGridAndTheGlassStayBehind() {
        // Black to two thirds across, white after: the grid's lines at a third and at the middle lie on black.
        val half = picture(120, 60) { x, _ -> if (x < 80) 0 else 255 }
        val lens = LensState().apply {
            gridMode = GridMode.THIRDS
            glass.setDown(null, 0.9f, 0.5f, magnification = 4f)
        }
        val saved = save(half, lens)
        assertEquals(0, saved.grey(40, 30), "no grid line at a third")
        assertEquals(0, saved.grey(60, 30), "nor across the middle")
        assertEquals(0, saved.grey(10, 30), "and the picture whole, not the part the glass is down on")
        assertEquals(255, saved.grey(110, 30))
    }
}
