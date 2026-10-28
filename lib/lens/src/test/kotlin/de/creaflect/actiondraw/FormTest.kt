package de.creaflect.actiondraw

import de.creaflect.actiondraw.lens.LensState
import de.creaflect.actiondraw.ui.Block
import de.creaflect.actiondraw.ui.Form
import de.creaflect.actiondraw.ui.FormState
import de.creaflect.actiondraw.ui.Heights
import de.creaflect.actiondraw.ui.Optic
import de.creaflect.actiondraw.ui.ReliefHeights
import de.creaflect.actiondraw.ui.drawnInBands
import de.creaflect.actiondraw.ui.formDims
import de.creaflect.actiondraw.ui.formRay
import de.creaflect.actiondraw.ui.formRows
import de.creaflect.actiondraw.ui.formShader
import de.creaflect.actiondraw.ui.softHeights
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
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
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
    fun aBlocksShapeAndAReliefsHeightsAreCopiedWithIt() {
        val shape = FormState()
        shape.choose(Form.RELIEF)
        assertTrue(shape.pitch in 0.3f..0.9f, "a relief is first seen tilted less than a page, so it stands out")
        shape.block = Block(ends = -1f, around = 0.4f, height = 2f)
        shape.depth = 0.5f
        val heights = Heights("knee", bitmapImage { _, _ -> Color.WHITE })
        shape.useHeights(heights)
        val copy = FormState().apply { copyFrom(shape) }
        assertEquals(shape.block, copy.block)
        assertEquals(0.5f, copy.depth)
        assertTrue(copy.heights === heights, "a saved view is raised by the same heights")
        shape.useHeights(null)
        assertNull(shape.heights, "and back to the picture's own")
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
    private val near = listOf(-1.25f to -1.25f, 1.25f to -1.25f, -1.25f to 1.25f, 1.25f to 1.25f)

    @Test
    fun everyFormIsDrawnWhereItsTwinSaysItIs() {
        val picture = bitmapImage { x, y -> Color.makeRGB(x, y, 0) }
        // A relief raised by the picture's own values: here, the coordinates' greys; a block of
        // uneven measures, so its round and its ends are not the ball's.
        val own = ReliefHeights(256, 256) { u, v -> (0.299f * floor(u * 256f) + 0.587f * floor(v * 256f)) / 255f }
        val shaped = Block(ends = -0.6f, around = -0.3f, height = 1.4f, depth = 0.8f)
        for (form in Form.entries) {
            val yaw = 0.6f
            val pitch = if (form == Form.RELIEF) 0.5f else -0.4f
            val shot = render(form, picture, yaw, pitch, lit = false, block = shaped)
            val rows = formRows(yaw, pitch)
            val dims = formDims(form, 1f)
            var compared = 0
            var clear = 0
            for (y in 3 until 197 step 3) for (x in 3 until 197 step 3) {
                val hits = sub.map { (ox, oy) -> formRay(form, (x + 0.5f + ox - 100f) / 80f, (y + 0.5f + oy - 100f) / 80f, rows, dims, shaped, own) }
                val c = shot.getColor(x, y)
                if (hits.all { it == null }) {
                    // Within a pixel of its edge a grazing ray may go either way: the processor's
                    // powers are near enough, not exact.
                    val edge = near.any { (ox, oy) -> formRay(form, (x + 0.5f + ox - 100f) / 80f, (y + 0.5f + oy - 100f) / 80f, rows, dims, shaped, own) != null }
                    if (edge) continue
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
                val u = found.map { floor(min(it.u, 0.999f) * 256f).coerceIn(0f, 255f) }.average() * k
                val v = found.map { floor(min(it.v, 0.999f) * 256f).coerceIn(0f, 255f) }.average() * k
                assertEquals(u, Color.getR(c).toDouble(), 3.0, "$form: across the picture at $x, $y")
                assertEquals(v, Color.getG(c).toDouble(), 3.0, "$form: down the picture at $x, $y")
                compared++
            }
            assertTrue(compared > 20, "$form: enough of it compared ($compared)")
            assertTrue(clear > 20, "$form: and enough around it clear ($clear)")
        }
    }

    // ---- Shaped ----

    @Test
    fun aRoundBlockOfEvenMeasuresIsTheSphere() {
        val rows = formRows(0.6f, -0.4f)
        val round = Block(ends = 0f, around = 0f)
        var compared = 0
        for (i in -12..12) for (j in -12..12) {
            val q = i / 12f to j / 12f
            if (hypot(q.first, q.second) in 0.85f..1.05f) continue // the edge, where a grazing ray may go either way
            val ball = formRay(Form.SPHERE, q.first, q.second, rows, 0f to 0f)
            val block = formRay(Form.BLOCK, q.first, q.second, rows, 0f to 0f, block = round)
            assertEquals(ball == null, block == null, "met at the same places: $q")
            if (ball == null || block == null || ball.u < 0.02f || ball.u > 0.98f) continue
            assertEquals(ball.u, block.u, 0.002f, "across, at $q")
            assertEquals(ball.v, block.v, 0.002f, "down, at $q")
            compared++
        }
        assertTrue(compared > 200, "enough of it compared: $compared")
    }

    @Test
    fun theSlidersShapeTheBlock() {
        val front = formRows(0f, 0f)
        val above = formRows(0f, -1.5f)
        val can = Block(ends = -1f, around = 0f)
        val cushion = Block(ends = 0f, around = -1f)
        fun meets(block: Block, rows: Array<FloatArray>) = formRay(Form.BLOCK, 0.58f, 0.58f, rows, 0f to 0f, block = block) != null
        // A corner of the view, off the round: a can's square ends reach it from the front, and a
        // cushion's square round from above — not the other way about.
        assertTrue(meets(can, front), "a can, from the front: a rectangle")
        assertFalse(meets(can, above), "a can, from above: round")
        assertFalse(meets(cushion, front), "a cushion, from the front: round")
        assertTrue(meets(cushion, above), "a cushion, from above: square")
        val tall = Block(ends = 0f, around = 0f, height = 2f)
        assertNotNull(formRay(Form.BLOCK, 0f, -0.85f, front, 0f to 0f, block = tall), "twice as tall: up to the view's top")
        assertNull(formRay(Form.BLOCK, 0.6f, 0f, front, 0f to 0f, block = tall), "and narrower for it")
        assertNotNull(formRay(Form.BLOCK, 0.6f, 0f, front, 0f to 0f, block = Block(0f, 0f)), "where a round one is")
    }

    @Test
    fun aReliefStandsOutOfItsPageWhereItsHeightsAreLight() {
        val rows = formRows(0.3f, 0.5f)
        val dims = formDims(Form.RELIEF, 1f)
        val flat = ReliefHeights(64, 64) { _, _ -> 0f }
        for (i in -10..10) for (j in -10..10) {
            val q = i / 10f to j / 10f
            val page = formRay(Form.PLANE, q.first, q.second, rows, dims)
            val relief = formRay(Form.RELIEF, q.first, q.second, rows, dims, heights = flat)
            assertEquals(page == null, relief == null, "flat, it is the page: $q")
            if (page != null && relief != null) {
                assertEquals(page.u, relief.u, 0.003f)
                assertEquals(page.v, relief.v, 0.003f)
            }
        }
        // Raised all over, it is the page brought nearer: seen straight on, larger, so a point of the
        // view off its middle reads nearer the picture's middle.
        val straight = formRows(0f, 0f)
        val raised = ReliefHeights(64, 64) { _, _ -> 1f }
        val near = assertNotNull(formRay(Form.RELIEF, 0.5f, 0f, straight, dims, heights = raised, depth = 0.5f))
        val far = assertNotNull(formRay(Form.PLANE, 0.5f, 0f, straight, dims))
        assertTrue(near.u - 0.5f < (far.u - 0.5f) * 0.95f, "raised, nearer: ${near.u} against ${far.u}")
        // From behind: the plain back of its page — so the shader draws it too.
        assertTrue(assertNotNull(formRay(Form.RELIEF, 0f, 0f, formRows(PI.toFloat(), 0f), dims, heights = raised)).plain)
        val back = render(Form.RELIEF, bitmapImage { _, _ -> Color.makeRGB(40, 90, 200) }, PI.toFloat(), 0f, lit = false)
        assertTrue(abs(Color.getR(back.getColor(100, 100)) - 219) <= 4, "the back, plain paper: ${Color.getR(back.getColor(100, 100))}")
    }

    @Test
    fun aReliefsOwnHeightsAreMadeSmallAndSoft() {
        val big = bitmapImage(1000, 500) { _, _ -> Color.WHITE }
        val small = softHeights(big)
        assertEquals(384 to 192, small.width to small.height, "no more than 384 pixels on its long side")
        // A hard edge between black and white comes out graded, so a photograph's grain is no needle.
        val edge = softHeights(bitmapImage { x, _ -> if (x < 128) Color.BLACK else Color.WHITE })
        val row = Bitmap().apply { allocPixels(ImageInfo.makeN32(256, 256, ColorAlphaType.PREMUL)) }
        assertTrue(edge.readPixels(row))
        val greys = (124..131).map { Color.getR(row.getColor(it, 128)) }
        assertTrue(greys.count { it in 20..235 } >= 3, "graded across the edge: $greys")
    }

    @Test
    fun aReliefIsLitWhereItTurnsToTheLamp() {
        // A round hill in the middle of a page seen straight on: its upper left flank faces the lamp.
        val hill = bitmapImage { x, y -> exp(-(((x - 128) * (x - 128) + (y - 128) * (y - 128)) / 1800.0)).times(255).toInt().let { Color.makeRGB(it, it, it) } }
        val grey = bitmapImage { _, _ -> Color.makeRGB(200, 200, 200) }
        val shot = render(Form.RELIEF, grey, 0f, 0f, lit = true, heights = hill, depth = 0.5f)
        val toward = Color.getR(shot.getColor(84, 84))
        val away = Color.getR(shot.getColor(116, 116))
        val level = Color.getR(shot.getColor(40, 160))
        assertTrue(toward > level + 10, "the flank turned to the lamp, lighter than the level page: $toward, $level")
        assertTrue(away < level - 10, "and the one turned away, darker: $away, $level")
    }

    @Test
    fun aSavedFormIsDrawnInBandsJustAsOneDrawWouldDrawIt() {
        val picture = bitmapImage { x, y -> Color.makeRGB(x, y, 0) }
        for (form in listOf(Form.SPHERE, Form.RELIEF)) {
            val whole = render(form, picture, 0.6f, 0.3f, lit = true, rays = 1)
            val banded = Bitmap().apply { allocPixels(ImageInfo.makeN32(200, 200, ColorAlphaType.PREMUL)) }
            drawnInBands(200, 200) { formShader(picture, form, 0.6f, 0.3f, 100f, 100f, 80f, true, sampling = SamplingMode.DEFAULT, rays = 1) }.use { assertTrue(it.readPixels(banded)) }
            var differ = 0
            for (y in 0 until 200) for (x in 0 until 200) if (whole.getColor(x, y) != banded.getColor(x, y)) differ++
            assertEquals(0, differ, "$form: every pixel as one draw has it")
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

    private fun bitmapImage(w: Int = 256, h: Int = 256, color: (Int, Int) -> Int): Image {
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo.makeN32(w, h, ColorAlphaType.PREMUL))
        val bytes = ByteArray(w * h * 4)
        for (y in 0 until h) for (x in 0 until w) {
            val c = color(x, y)
            val i = (y * w + x) * 4
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
    private fun render(
        form: Form,
        image: Image,
        yaw: Float,
        pitch: Float,
        lit: Boolean,
        heights: Image? = null,
        depth: Float = FormState.DEPTH,
        rays: Int = 4,
        block: Block = Block(),
    ): Bitmap {
        val surface = Surface.makeRasterN32Premul(200, 200)
        val shader = formShader(image, form, yaw, pitch, 100f, 100f, 80f, lit, SamplingMode.DEFAULT, block, heights, depth, rays)
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
