package de.creaflect.actiondraw

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import de.creaflect.actiondraw.ui.Materials
import de.creaflect.actiondraw.ui.Room
import de.creaflect.actiondraw.ui.dabPath
import de.creaflect.actiondraw.ui.drawDab
import de.creaflect.actiondraw.ui.drawLampShadow
import de.creaflect.actiondraw.ui.drawPin
import de.creaflect.actiondraw.ui.drawTape
import de.creaflect.actiondraw.ui.drawWell
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The atelier's materials (F9.2), drawn into bitmaps and read back: each has to look like what it
 * is in the way that matters — the lamp at the top left, wet paint shining, tape you can see
 * through.
 */
class MaterialsTest {
    private fun draw(w: Int, h: Int, block: DrawScope.() -> Unit): PixelMap {
        val bitmap = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(w.toFloat(), h.toFloat()), block)
        return bitmap.toPixelMap()
    }

    @Test
    fun theGrainIsAFaintToothOfLightAndDark() {
        val tile = Materials.renderGrain(64, Materials.STRENGTH)
        val grey = Color(0xFF808080)
        val pixels = draw(64, 64) {
            drawRect(grey)
            drawRect(ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated)))
        }
        val shifts = buildList { for (x in 0 until 64) for (y in 0 until 64) add(pixels[x, y].red - grey.red) }
        assertTrue(shifts.any { it > 0.01f }, "some grains are lighter")
        assertTrue(shifts.any { it < -0.01f }, "some grains are darker")
        assertTrue(shifts.all { kotlin.math.abs(it) <= Materials.STRENGTH * 0.6f }, "felt more than seen: ${shifts.maxOf { kotlin.math.abs(it) }}")
    }

    @Test
    fun aDabKeepsItsShapeAndStaysInside() {
        val size = Size(100f, 88f)
        val a = dabPath(7, size).getBounds()
        assertEquals(a, dabPath(7, size).getBounds(), "the same seed, the same dab")
        assertNotEquals(a, dabPath(8, size).getBounds(), "another seed, another dab")
        assertTrue(a.left >= -2f && a.top >= -2f && a.right <= size.width + 2f && a.bottom <= size.height + 2f, "inside its box: $a")
        assertTrue(a.width > size.width * 0.8f && a.height > size.height * 0.8f, "and filling it: $a")
    }

    @Test
    fun wetPaintShinesAndDryPaintHasGonePale() {
        val pigment = Room.BOARDS.pigment
        val size = Size(100f, 88f)
        val wet = draw(100, 88) { drawDab(Offset.Zero, size, pigment, seed = 3, wet = true) }
        val dry = draw(100, 88) { drawDab(Offset.Zero, size, pigment, seed = 3, wet = false) }
        val centre = 50 to 52
        val gloss = 36 to 23
        assertTrue(wet[gloss.first, gloss.second].luminance() > wet[centre.first, centre.second].luminance() + 0.2f, "the lamp shines on wet paint")
        assertTrue(dry[centre.first, centre.second].luminance() > wet[centre.first, centre.second].luminance() + 0.15f, "dry paint is paler")
        assertTrue(dry[gloss.first, gloss.second].luminance() < dry[centre.first, centre.second].luminance() + 0.1f, "and has no shine")
        assertEquals(0f, wet[1, 1].alpha, "the corner is not paint")
    }

    @Test
    fun aWellIsInShadowAlongItsTop() {
        val pixels = draw(100, 100) { drawWell(Offset(50f, 50f), 45f) }
        assertTrue(pixels[50, 12].luminance() < pixels[50, 86].luminance(), "lit from above, the inner wall at the top is in shadow")
    }

    @Test
    fun theLampCastsShadowsDownAndToTheRight() {
        val square = Path().apply { addRect(Rect(30f, 30f, 70f, 70f)) }
        val pixels = draw(100, 100) { drawLampShadow(square, depth = 10f) }
        // Just past the square's own corner, where only the cast shadow can be — and its mirror.
        assertTrue(pixels[72, 74].alpha > pixels[28, 26].alpha + 0.1f, "below right: ${pixels[72, 74].alpha} · above left: ${pixels[28, 26].alpha}")
    }

    @Test
    fun tapeIsPaperYouCanSeeThrough() {
        val pixels = draw(100, 100) { drawTape(Offset(50f, 50f), Size(80f, 20f), degrees = 0f, seed = 1) }
        assertTrue(pixels[50, 50].alpha in 0.6f..0.95f, "alpha ${pixels[50, 50].alpha}")
    }

    @Test
    fun aPinsShadowFallsToTheBottomRight() {
        val pixels = draw(100, 100) { drawPin(Offset(50f, 50f), 10f, Room.BOARDS.pigment) }
        assertTrue(pixels[60, 62].alpha > 0.2f, "shadow below right")
        assertEquals(0f, pixels[40, 38].alpha, "nothing above left")
    }
}
