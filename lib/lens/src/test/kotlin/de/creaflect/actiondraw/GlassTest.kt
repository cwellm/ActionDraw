package de.creaflect.actiondraw

import androidx.compose.ui.geometry.Offset
import de.creaflect.actiondraw.ui.GlassFocus
import de.creaflect.actiondraw.ui.GlassSpread
import de.creaflect.actiondraw.ui.GlassState
import de.creaflect.actiondraw.ui.Optic
import de.creaflect.actiondraw.ui.PictureView
import de.creaflect.actiondraw.ui.magnificationFor
import de.creaflect.actiondraw.ui.opticShader
import de.creaflect.actiondraw.ui.opticSource
import de.creaflect.actiondraw.ui.pictureView
import de.creaflect.actiondraw.ui.spreadZoom
import de.creaflect.actiondraw.ui.toLayer
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import java.io.File
import kotlin.math.abs
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The glass: its state, where the picture lies under it, and its optics as the shader draws them. */
class GlassTest {
    // ---- The state ----

    @Test
    fun theGlassLaidOverTheWholePictureBendsItAboutAPointThatMoves() {
        val glass = GlassState()
        glass.spreadOver()
        assertEquals(GlassSpread(0.5f, 0.5f), glass.spread, "the middle, unless said")
        assertTrue(glass.inHand, "and the glass is in hand")
        glass.spreadOver(0.2f, 1.4f)
        assertEquals(GlassSpread(0.2f, 1f), glass.spread, "laid there again, it moves — never off the picture")

        glass.setDown(null, 0.3f, 0.3f)
        assertNull(glass.spread, "set down on a part, it no longer lies over the whole")
        glass.spreadOver(0.6f, 0.4f)
        assertNull(glass.focus, "and laid over the whole, it is on no part")

        glass.gather()
        assertNull(glass.spread)
        assertTrue(glass.inHand, "gathered back into the hand, not put away")
        glass.toggleSpread()
        assertEquals(GlassSpread(0.5f, 0.5f), glass.spread, "W: over the whole")
        glass.toggleSpread()
        assertNull(glass.spread, "and back")
        glass.spreadOver()
        glass.putAway()
        assertNull(glass.spread, "put away, the picture is as it is")
    }

    @Test
    fun theGlassIsAsLargeAndBendsAsMuchAsItIsMadeWithinItsLimits() {
        val glass = GlassState()
        glass.resize(180f)
        assertEquals(180f, glass.radius)
        glass.resize(1000f)
        assertEquals(GlassState.MAX_RADIUS, glass.radius)
        glass.resize(0f)
        assertEquals(GlassState.MIN_RADIUS, glass.radius)
        glass.bendTo(0.4f)
        assertEquals(0.4f, glass.bend)
        glass.bendBy(-10f)
        assertEquals(GlassState.MIN_BEND, glass.bend, "never no bend at all")
        glass.bendBy(20f)
        assertEquals(1f, glass.bend, "never more than the glass's own")
    }

    @Test
    fun theBendIsKeptWithTheRestOfTheGlass() {
        val glass = GlassState().apply {
            choose(Optic.FISHEYE)
            bendTo(0.3f)
            resize(200f)
        }
        val next = GlassState().apply { restore(glass.setting()) }
        assertEquals(0.3f, next.bend)
        assertEquals(200f, next.radius)
    }

    @Test
    fun aGlassOverTheWholeKeepsTheFarRimOfItsReachWhereItIs() {
        for (optic in listOf(Optic.CONVEX, Optic.CONCAVE, Optic.FISHEYE)) {
            for (bend in listOf(1f, 0.5f, 0.1f)) {
                val rim = opticSource(optic, 100f, 0f, 100f, zoom = spreadZoom(optic, bend), strength = bend)
                assertEquals(100f, rim.x, 0.01f, "$optic at $bend: the farthest corner stays the corner")
            }
        }
        val convex = spreadZoom(Optic.CONVEX, 1f)
        assertTrue(opticSource(Optic.CONVEX, 10f, 0f, 100f, convex, 1f).x < 10f * 0.5f, "a convex glass over the whole still swells its middle")
        assertEquals(1f, spreadZoom(Optic.CYLINDER, 1f), "a glass rod bends one way only: no further out")
        assertEquals(1f, spreadZoom(Optic.PRISM, 1f))
    }

    @Test
    fun theGlassIsSetDownOnOnePictureAndANewPoseStartsWhole() {
        val glass = GlassState()
        val a = File("a.jpg")
        val b = File("b.jpg")
        assertFalse(glass.inHand)
        glass.setDown(a, 0.25f, 0.75f)
        assertTrue(glass.inHand, "setting it down takes it up")
        assertEquals(GlassFocus(a, 0.25f, 0.75f, GlassState.DEFAULT_MAGNIFICATION), glass.focusOn(a))
        assertNull(glass.focusOn(b), "the next picture is seen whole")
        glass.moveBy(1f, -0.1f)
        assertEquals(1f, glass.focusOn(a)!!.x, "it stays on the picture")
        assertEquals(0.65f, glass.focusOn(a)!!.y, 1e-6f)
        glass.wholePicture()
        assertNull(glass.focusOn(a))
        assertTrue(glass.inHand, "lifted off the picture, still in hand")
        glass.setDown(a, 0.5f, 0.5f)
        glass.putAway()
        assertNull(glass.focus, "put away, it is down nowhere")
    }

    @Test
    fun theMagnificationStaysWithinTheGlassAndFollowsItDown() {
        val glass = GlassState()
        glass.magnifyBy(100f)
        assertEquals(GlassState.MAX_MAGNIFICATION, glass.magnification)
        glass.magnifyBy(-100f)
        assertEquals(GlassState.MIN_MAGNIFICATION, glass.magnification)
        glass.setDown(null, 0.5f, 0.5f, magnification = 3f)
        glass.magnify(5f)
        assertEquals(5f, glass.focus!!.magnification, "down, the view comes nearer too")
        glass.resizeBy(100f)
        assertEquals(GlassState.MAX_RADIUS, glass.radius)
    }

    @Test
    fun choosingAnOpticTakesTheGlassUpAndTheOpticsGoRound() {
        val glass = GlassState()
        glass.choose(Optic.FISHEYE)
        assertTrue(glass.inHand)
        Optic.entries.forEach { _ -> glass.nextOptic() }
        assertEquals(Optic.FISHEYE, glass.optic, "once round, back where it was")
    }

    // ---- Where the picture lies ----

    @Test
    fun setDownThePointChosenComesToTheMiddleAsLargeAsTheGlassShowedIt() {
        val focus = GlassFocus(null, 0.2f, 0.6f, 4f)
        val whole = pictureView(800f, 600f, 400f, 400f, focus, progress = 0f)
        assertEquals(0.5f, whole.scale, "whole: fitted")
        val down = pictureView(800f, 600f, 400f, 400f, focus, progress = 1f)
        assertEquals(2f, down.scale, 1e-5f)
        val middle = down.toView(160f, 360f)
        assertEquals(200f, middle.x, 1e-3f)
        assertEquals(200f, middle.y, 1e-3f)
        val back = down.toPicture(Offset(200f, 200f))
        assertEquals(160f, back.x, 1e-3f)
        // Halfway, the scale is halfway on a log scale: the zoom feels even.
        assertEquals(1f, pictureView(800f, 600f, 400f, 400f, focus, progress = 0.5f).scale, 1e-4f)
    }

    @Test
    fun aPartDraggedOutFillsTheView() {
        // A 200 × 100 part of an 800 × 600 picture in a 400 × 400 view, fitted at 0.5.
        assertEquals(4f, magnificationFor(800f, 600f, 400f, 400f, 200f, 100f), 1e-5f)
    }

    @Test
    fun aMirroredOrTurnedPictureIsPointedAtWhereItIsSeen() {
        val p = Offset(10f, 20f)
        assertEquals(p, toLayer(p, 100f, 50f, mirror = false, upsideDown = false))
        assertEquals(Offset(90f, 20f), toLayer(p, 100f, 50f, mirror = true, upsideDown = false))
        assertEquals(Offset(90f, 30f), toLayer(p, 100f, 50f, mirror = false, upsideDown = true))
        assertEquals(Offset(10f, 30f), toLayer(p, 100f, 50f, mirror = true, upsideDown = true))
        assertEquals(p, toLayer(toLayer(p, 100f, 50f, true, false), 100f, 50f, true, false), "its own inverse")
    }

    // ---- The optics ----

    @Test
    fun theOpticsBendAsTheirNamesSay() {
        val r = 100f
        fun at(optic: Optic, dx: Float, dy: Float = 0f) = opticSource(optic, dx, dy, r, zoom = 1f)
        assertEquals(Offset(30f, 0f), at(Optic.PLAIN, 30f), "flat glass bends nothing")
        assertTrue(at(Optic.CONVEX, 20f).x < 20f * 0.7f, "a magnifier swells the middle")
        assertTrue(at(Optic.CONVEX, 95f).x > 95f, "and draws the rim in")
        assertTrue(at(Optic.CONCAVE, 20f).x > 20f * 1.5f, "a diverging lens shrinks the middle away")
        assertTrue(at(Optic.FISHEYE, 100f).x > 100f * 1.5f, "a fisheye takes in a far wider field at its edge")
        assertTrue(at(Optic.FISHEYE, 10f).x < 10f, "and is near in the middle")
        assertEquals(40f, at(Optic.CYLINDER, 0f, 40f).y, "a rod leaves one way untouched")
        assertTrue(at(Optic.CYLINDER, 40f, 0f).x < 40f * 0.6f, "and stretches the other")
        val half = opticSource(Optic.CONVEX, 20f, 0f, r, zoom = 1f, strength = 0.5f).x
        assertEquals((20f + at(Optic.CONVEX, 20f).x) / 2f, half, 1e-4f, "set down halfway, half bent")
    }

    /**
     * A picture whose colour is its own coordinates (red the column, green the row) seen through
     * each optic: every pixel then says where the shader looked, and that must be where
     * [opticSource] says it looks.
     */
    @Test
    fun theShaderLooksWhereTheArithmeticSays() {
        val coords = coordinatePicture()
        for (optic in Optic.entries.filter { it != Optic.PRISM }) {
            val shot = render(optic, coords, zoom = 0.5f)
            var checked = 0
            for (y in 20 until 236 step 9) for (x in 20 until 236 step 9) {
                val d = Offset(x + 0.5f - 128f, y + 0.5f - 128f)
                val src = opticSource(optic, d.x, d.y, 100f, zoom = 0.5f) + Offset(128f, 128f)
                if (src.x !in 1f..254f || src.y !in 1f..254f) continue
                val c = shot.getColor(x, y)
                assertTrue(abs(Color.getR(c) - floor(src.x)) <= 1f && abs(Color.getG(c) - floor(src.y)) <= 1f, "$optic at $x,$y: shader ${Color.getR(c)},${Color.getG(c)}, arithmetic $src")
                checked++
            }
            assertTrue(checked > 300, "$optic: $checked pixels checked")
        }
    }

    @Test
    fun aPrismPartsTheColoursAtAnEdgeAndFlatGlassDoesNot() {
        // Black on the left half, white on the right: through a prism, the edge comes in colour.
        val edge = picture { x, _ -> if (x < 160) 0 else 255 }
        fun fringe(optic: Optic): Int {
            val shot = render(optic, edge, zoom = 0.6f)
            return (0 until 256).count { x -> shot.getColor(x, 128).let { abs(Color.getR(it) - Color.getB(it)) > 40 } }
        }
        assertEquals(0, fringe(Optic.PLAIN))
        assertTrue(fringe(Optic.PRISM) > 0, "a fringe of colour at the edge")
    }

    private fun picture(grey: (Int, Int) -> Int): Image = bitmapImage { x, y -> grey(x, y).let { Color.makeRGB(it, it, it) } }

    private fun coordinatePicture(): Image = bitmapImage { x, y -> Color.makeRGB(x, y, 0) }

    private fun bitmapImage(color: (Int, Int) -> Int): Image {
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo.makeN32(256, 256, ColorAlphaType.PREMUL))
        val bytes = ByteArray(256 * 256 * 4)
        for (y in 0 until 256) for (x in 0 until 256) {
            val c = color(x, y)
            val i = (y * 256 + x) * 4
            // N32 on this platform is BGRA.
            bytes[i] = Color.getB(c).toByte()
            bytes[i + 1] = Color.getG(c).toByte()
            bytes[i + 2] = Color.getR(c).toByte()
            bytes[i + 3] = 255.toByte()
        }
        bitmap.installPixels(bytes)
        return Image.makeFromBitmap(bitmap)
    }

    private fun render(optic: Optic, image: Image, zoom: Float): Bitmap {
        val surface = Surface.makeRasterN32Premul(256, 256)
        val shader = opticShader(image, Matrix33.IDENTITY, optic, 128f, 128f, 100f, zoom, strength = 1f, sampling = SamplingMode.DEFAULT)
        Paint().use { paint ->
            paint.shader = shader
            surface.canvas.drawRect(Rect.makeWH(256f, 256f), paint)
        }
        val shot = Bitmap()
        shot.allocPixels(ImageInfo.makeN32(256, 256, ColorAlphaType.PREMUL))
        assertNotNull(surface.makeImageSnapshot().readPixels(shot))
        return shot
    }

    @Suppress("unused")
    private val unusedView = PictureView(1f, 1f, 1f, 0f, 0f)
}
