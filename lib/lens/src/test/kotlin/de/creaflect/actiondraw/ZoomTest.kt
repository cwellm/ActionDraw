package de.creaflect.actiondraw

import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import de.creaflect.actiondraw.lens.LensState
import de.creaflect.actiondraw.ui.MOST_ZOOM
import de.creaflect.actiondraw.ui.ZOOM_STEP
import de.creaflect.actiondraw.ui.handleLensKey
import de.creaflect.actiondraw.ui.zoomedPart
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A part of the picture zoomed into, taken as the picture: how it is zoomed, moved and let go of, and its pixels. */
class ZoomTest {
    private val pose = File("pose.png")

    @Test
    fun theWheelZoomsAboutThePointerWhichStaysWhereItIs() {
        val lens = LensState()
        lens.zoomBy(pose, 2f, 0.75f, 0.25f)
        val zoom = assertNotNull(lens.zoomOn(pose))
        assertEquals(ZOOM_STEP * ZOOM_STEP, zoom.factor, 0.001f)
        val (x, y) = zoom.onWhole(0.75f, 0.25f)
        assertEquals(0.75f, x, 0.001f, "the place under the pointer stays under it")
        assertEquals(0.25f, y, 0.001f)
        lens.zoomBy(pose, -2f, 0.3f, 0.3f)
        assertNull(lens.zoomOn(pose), "out as far as it went in: the whole picture again")
        lens.zoomBy(pose, -3f)
        assertNull(lens.zoomOn(pose), "and no further out than that")
    }

    @Test
    fun zoomedNearTheEdgeThePartStaysOnThePictureAndNoNearerThanTheMost() {
        val lens = LensState()
        lens.zoomBy(pose, 4f, 1f, 1f)
        val corner = assertNotNull(lens.zoomOn(pose))
        assertEquals(1f, corner.left + corner.width, 0.001f, "kept on the picture at its right")
        assertEquals(1f, corner.top + corner.height, 0.001f)
        lens.zoomBy(pose, 100f)
        assertEquals(MOST_ZOOM, assertNotNull(lens.zoomOn(pose)).factor, 0.01f, "no nearer than the most")
    }

    @Test
    fun aPartDraggedOutIsZoomedIntoAndFurtherInFromThere() {
        val lens = LensState()
        lens.zoomInto(pose, 0.9f, 0.6f, 0.5f, 0f)
        val right = assertNotNull(lens.zoomOn(pose))
        listOf(0.5f to right.left, 0f to right.top, 0.4f to right.width, 0.6f to right.height).forEach { (want, got) ->
            assertEquals(want, got, 0.001f, "either way it was dragged")
        }
        lens.zoomInto(pose, 0.5f, 0.5f, 1f, 1f)
        val further = assertNotNull(lens.zoomOn(pose))
        assertEquals(0.7f, further.left, 0.001f, "a part of the part, in the whole picture's measure")
        assertEquals(0.3f, further.top, 0.001f)
        assertEquals(0.2f, further.width, 0.001f)
    }

    @Test
    fun thePartZoomedIntoMovesButStaysOnThePicture() {
        val lens = LensState()
        lens.panBy(pose, 0.5f, 0f)
        assertNull(lens.zoomOn(pose), "the whole picture has nowhere to move")
        lens.zoomInto(pose, 0.25f, 0.25f, 0.75f, 0.75f)
        lens.panBy(pose, 0.2f, -0.1f)
        val moved = assertNotNull(lens.zoomOn(pose))
        assertEquals(0.35f, moved.left, 0.001f, "by a part of itself")
        assertEquals(0.2f, moved.top, 0.001f)
        lens.panBy(pose, 5f, 5f)
        val edge = assertNotNull(lens.zoomOn(pose))
        assertEquals(1f, edge.left + edge.width, 0.001f, "kept on the picture")
        assertEquals(0.5f, edge.width, 0.001f, "the same size")
    }

    @Test
    fun anotherPictureStartsWholeAndEscLetsGoAfterTheGlass() {
        val lens = LensState()
        lens.zoomBy(pose, 1f)
        assertNull(lens.zoomOn(File("next.png")), "a new picture starts whole")
        lens.glass.takeUp()
        assertTrue(lens.stepBack(pose))
        assertFalse(lens.glass.inHand, "the glass put away first")
        assertNotNull(lens.zoomOn(pose))
        assertTrue(lens.stepBack(pose))
        assertNull(lens.zoomOn(pose), "then the whole picture")
        assertFalse(lens.stepBack(pose), "and then nothing of the lens's")
    }

    @Test
    fun aGlassDownOnThePartIsLiftedAsTheZoomChanges() {
        val lens = LensState()
        lens.zoomBy(pose, 1f)
        lens.glass.setDown(pose, 0.5f, 0.5f)
        lens.zoomBy(pose, 1f)
        assertNull(lens.glass.focusOn(pose), "what it was down on is gone")
    }

    @Test
    fun viewsSideBySideShowThePartTheirPartnerIsZoomedInto() {
        val first = LensState()
        val second = LensState().also { it.zoomsWith = first }
        second.zoomBy(pose, 1f)
        assertEquals(first.zoomOn(pose), second.zoomOn(pose), "zoomed on either, both show it")
        first.showWhole()
        assertNull(second.zoomOn(pose))
    }

    @Test
    fun theKeysZoomWhereTheGlassIsNotInHand() {
        val lens = LensState()
        assertTrue(handleLensKey(Key.Plus, lens, pose))
        assertEquals(ZOOM_STEP, assertNotNull(lens.zoomOn(pose)).factor, 0.001f, "+ zooms in")
        assertTrue(handleLensKey(Key.Minus, lens, pose))
        assertNull(lens.zoomOn(pose), "− out")
        assertFalse(handleLensKey(Key.Minus, lens, pose), "and from the whole picture, − is nobody's")
        handleLensKey(Key.Plus, lens, pose)
        assertTrue(handleLensKey(Key.Z, lens, pose))
        assertNull(lens.zoomOn(pose), "Z: the whole picture again")
        assertFalse(handleLensKey(Key.Z, lens, pose))
        lens.glass.takeUp()
        val m = lens.glass.magnification
        handleLensKey(Key.Plus, lens, pose)
        assertTrue(lens.glass.magnification > m, "with the glass in hand + is the glass's")
        assertNull(lens.zoomOn(pose))
    }

    @Test
    fun thePartIsAPictureOfItsOwnItsPixelsThoseOfThePart() {
        // 40 × 20: red on its left half, blue on its right.
        val image = BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until 40) for (y in 0 until 20) image.setRGB(x, y, if (x < 20) 0xFF0000 else 0x0000FF)
        val whole = image.toComposeImageBitmap()
        val lens = LensState()
        assertEquals(whole, zoomedPart(whole, null), "not zoomed: the picture itself")
        lens.zoomInto(pose, 0.5f, 0f, 1f, 1f)
        val part = zoomedPart(whole, lens.zoomOn(pose))
        assertEquals(20 to 20, part.width to part.height)
        val pixels = part.toPixelMap()
        assertTrue(pixels[2, 10].blue > 0.9f && pixels[17, 10].blue > 0.9f, "all of it the right half's blue")
    }
}
