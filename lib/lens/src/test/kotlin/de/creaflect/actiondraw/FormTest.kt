package de.creaflect.actiondraw

import de.creaflect.actiondraw.lens.LensState
import de.creaflect.actiondraw.ui.Form
import de.creaflect.actiondraw.ui.FormState
import de.creaflect.actiondraw.ui.Optic
import de.creaflect.actiondraw.ui.formDims
import de.creaflect.actiondraw.ui.formRay
import de.creaflect.actiondraw.ui.formRows
import de.creaflect.actiondraw.ui.formShader
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The forms: chosen, turned, taking turns with the glass — and drawn where their Kotlin twin says. */
class FormTest {
    // ---- The state ----

    @Test
    fun pGoesThroughEveryFormAndBackToNone() {
        val shape = FormState()
        assertNull(shape.form)
        val seen = (1..Form.entries.size + 1).map { shape.next(); shape.form }
        assertEquals(Form.entries + listOf(null), seen)
    }

    @Test
    fun aFormIsTurnedTippedNoFurtherThanItsEdgeAndStraightenedAgain() {
        val shape = FormState()
        shape.choose(Form.PLANE)
        val (yaw, pitch) = shape.yaw to shape.pitch
        assertTrue(pitch > 0.5f, "a page is first seen tilted well back")
        shape.turn(0.3f, 10f)
        assertEquals(FormState.MAX_PITCH, shape.pitch, "never past its edge")
        shape.turn(20f, 0f)
        assertTrue(shape.yaw >= -PI.toFloat() && shape.yaw < PI.toFloat(), "turned round and round, the turn stays one: ${shape.yaw}")
        shape.straighten()
        assertEquals(yaw to pitch, shape.yaw to shape.pitch)
        shape.choose(Form.SPHERE)
        assertTrue(shape.pitch < 0f, "a solid is first seen a little from above")
        val copy = FormState().apply { copyFrom(shape) }
        assertEquals(Form.SPHERE, copy.form)
        assertEquals(shape.yaw to shape.pitch, copy.yaw to copy.pitch)
    }

    @Test
    fun theFormAndTheGlassTakeTurns() {
        val lens = LensState()
        lens.glass.takeUp()
        lens.form.choose(Form.SPHERE)
        assertFalse(lens.glass.inHand, "a form chosen: the glass put away")
        lens.glass.choose(Optic.CONVEX)
        assertNull(lens.form.form, "the glass taken up: the picture off its form")
        lens.form.choose(Form.TORUS)
        lens.glass.spreadOver()
        assertNull(lens.form.form, "and so when the glass is laid over the whole")
        lens.form.choose(Form.CONE)
        assertNull(lens.glass.spread)
        lens.glass.setDown(File("pose.png"), 0.5f, 0.5f)
        assertNull(lens.form.form, "or set down on a part")
    }

    @Test
    fun escTakesThePictureOffItsFormAndASavedViewKeepsIt() {
        val lens = LensState()
        lens.form.choose(Form.CHROME)
        lens.form.turn(0.4f, 0.2f)
        val copy = lens.copy()
        assertEquals(Form.CHROME, copy.form.form, "a saved view is drawn on the form")
        assertEquals(lens.form.yaw, copy.form.yaw)
        assertTrue(lens.stepBack(File("pose.png")))
        assertNull(lens.form.form)
        assertFalse(lens.stepBack(File("pose.png")), "and then there is nothing to step back from")
    }

    // ---- Turned ----

    @Test
    fun theTurnIsATurnAndADragToTheRightBringsThePicturesMiddleRight() {
        val rows = formRows(-0.7f, 0.4f)
        for (i in 0 until 3) for (j in 0 until 3) {
            val dot = (0 until 3).sumOf { (rows[i][it] * rows[j][it]).toDouble() }
            assertEquals(if (i == j) 1.0 else 0.0, dot, 1e-5, "rows $i and $j: a turn, nothing stretched or skewed")
        }
        val straight = formRows(0f, 0f)
        assertEquals(0.5f, assertNotNull(formRay(Form.SPHERE, 0f, 0f, straight, 0f to 0f)).u, 0.001f, "unturned, the picture's middle faces you")
        // A drag to the right turns the yaw down: the picture's middle moves to the right of the ball's.
        val turned = formRows(-0.3f, 0f)
        assertTrue(assertNotNull(formRay(Form.SPHERE, 0f, 0f, turned, 0f to 0f)).u < 0.5f, "the middle now shows what was left of it")
        val right = (1..20).map { it / 40f }.minBy { abs(assertNotNull(formRay(Form.SPHERE, it, 0f, turned, 0f to 0f)).u - 0.5f) }
        assertTrue(right > 0.1f, "and the picture's middle is to the right: $right")
        // Tipped down, a drag down: the top comes towards you.
        val tipped = formRows(0f, -0.4f)
        assertTrue(assertNotNull(formRay(Form.SPHERE, 0f, 0f, tipped, 0f to 0f)).v < 0.5f, "the middle now shows what was above it")
    }

    // ---- Drawn ----

    private val sub = listOf(-0.25f to -0.25f, 0.25f to -0.25f, -0.25f to 0.25f, 0.25f to 0.25f)

    @Test
    fun everyFormIsDrawnWhereItsTwinSaysItIs() {
        val picture = bitmapImage { x, y -> Color.makeRGB(x, y, 0) }
        for (form in Form.entries) {
            val yaw = 0.6f
            val pitch = -0.4f
            val shot = render(form, picture, yaw, pitch, lit = false)
            val rows = formRows(yaw, pitch)
            val dims = formDims(form, 1f)
            var compared = 0
            var clear = 0
            for (y in 3 until 197 step 3) for (x in 3 until 197 step 3) {
                val hits = sub.map { (ox, oy) -> formRay(form, (x + 0.5f + ox - 100f) / 80f, (y + 0.5f + oy - 100f) / 80f, rows, dims) }
                val c = shot.getColor(x, y)
                if (hits.all { it == null }) {
                    assertEquals(0, Color.getA(c), "$form: clear where it misses, at $x, $y")
                    clear++
                    continue
                }
                // Its edge, an end against the side, a seam or a steep stretch: four rays that disagree.
                val found = hits.filterNotNull().takeIf { it.size == 4 } ?: continue
                if (found.any { it.plain }) {
                    if (found.all { it.plain }) assertTrue(abs(Color.getR(c) - 219) <= 4, "$form: an end, plain paper, at $x, $y: ${Color.getR(c)}")
                    continue
                }
                if (found.maxOf { it.u } - found.minOf { it.u } > 0.02f || found.maxOf { it.v } - found.minOf { it.v } > 0.02f) continue
                val k = if (form == Form.CHROME) 0.92 else 1.0
                val u = found.map { floor(it.u * 256f).coerceIn(0f, 255f) }.average() * k
                val v = found.map { floor(min(it.v, 0.999f) * 256f).coerceIn(0f, 255f) }.average() * k
                assertEquals(u, Color.getR(c).toDouble(), 3.0, "$form: across the picture at $x, $y")
                assertEquals(v, Color.getG(c).toDouble(), 3.0, "$form: down the picture at $x, $y")
                compared++
            }
            assertTrue(compared > 20, "$form: enough of it compared ($compared)")
            assertTrue(clear > 20, "$form: and enough around it clear ($clear)")
        }
    }

    @Test
    fun theLampLightsAFormFromAboveToTheLeft() {
        val grey = bitmapImage { _, _ -> Color.makeRGB(200, 200, 200) }
        val shot = render(Form.SPHERE, grey, 0f, 0f, lit = true)
        val lit = Color.getR(shot.getColor(70, 70))
        val shaded = Color.getR(shot.getColor(130, 130))
        assertTrue(lit > shaded + 40, "lit up and to the left, in shade down and to the right: $lit, $shaded")
        val flat = render(Form.SPHERE, grey, 0f, 0f, lit = false)
        assertEquals(Color.getR(flat.getColor(70, 70)), Color.getR(flat.getColor(130, 130)), "unlit, only the picture's own values")
    }

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

    /** [image] on [form] in a 200 × 200 view, its middle in the middle, 80 pixels to the sphere's edge. */
    private fun render(form: Form, image: Image, yaw: Float, pitch: Float, lit: Boolean): Bitmap {
        val surface = Surface.makeRasterN32Premul(200, 200)
        val shader = formShader(image, form, yaw, pitch, 100f, 100f, 80f, lit, sampling = SamplingMode.DEFAULT)
        Paint().use { paint ->
            paint.shader = shader
            surface.canvas.drawRect(Rect.makeWH(200f, 200f), paint)
        }
        val shot = Bitmap()
        shot.allocPixels(ImageInfo.makeN32(200, 200, ColorAlphaType.PREMUL))
        assertNotNull(surface.makeImageSnapshot().readPixels(shot))
        return shot
    }
}
