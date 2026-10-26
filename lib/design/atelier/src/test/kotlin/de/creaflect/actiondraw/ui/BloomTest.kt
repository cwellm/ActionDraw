package de.creaflect.actiondraw.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import de.creaflect.actiondraw.Room
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Surface
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The atelier's bloom (F9.4): a room's pigment spreading from its well and draining back into it.
 * The timeline is pure and tested as such, the watercolour by its pixels.
 */
class BloomTest {
    @Test
    fun aBloomSpreadsCoversThenThinsAndADrainShrinksBackIntoItsWell() {
        val steps = (0..20).map { it / 20f }
        val opening = steps.map { bloomFrame(it, opening = true, reduced = false) }
        assertEquals(0f, opening.first().radius)
        assertTrue(opening.zipWithNext().all { (a, b) -> b.radius >= a.radius }, "it only spreads")
        assertEquals(1f, bloomFrame(Motion.BLOOM_COVERS, opening = true, reduced = false).radius, 1e-4f)
        assertTrue(steps.filter { it <= Motion.BLOOM_COVERS }.all { bloomFrame(it, true, false).alpha == 1f }, "opaque while it spreads")
        assertEquals(0f, opening.last().alpha, 1e-4f)

        val draining = steps.map { bloomFrame(it, opening = false, reduced = false) }
        assertEquals(1f, draining.first().radius)
        assertTrue(draining.zipWithNext().all { (a, b) -> b.radius <= a.radius }, "it only shrinks")
        assertEquals(0f, draining.last().radius, 1e-4f)

        // Reduced: no spreading, no shrinking — a faint wash that fades, the room opened at once.
        assertTrue(steps.all { bloomFrame(it, true, true).radius == 1f && bloomFrame(it, true, true).alpha <= 0.35f })
        assertEquals(0f, bloomCovers(reduced = true))
        assertEquals(Motion.BLOOM_COVERS, bloomCovers(reduced = false))
    }

    /** The watercolour: pigment inside, nothing outside, an edge that wanders, a rim darker than the middle. */
    @Test
    fun theBloomLooksLikeWatercolour() {
        val size = 240
        val c = 120f
        val radius = 80f
        val pigment = Atelier.pigmentOf(Room.BOARDS).mass
        val effect = RuntimeEffect.makeForShader(BLOOM_SKSL)
        val shader = RuntimeShaderBuilder(effect).apply {
            uniform("center", c, c)
            uniform("radius", radius)
            uniform("color", pigment.red, pigment.green, pigment.blue, 1f)
            uniform("seed", 7f)
        }.makeShader(null)
        val surface = Surface.makeRasterN32Premul(size, size)
        surface.canvas.drawRect(Rect.makeWH(size.toFloat(), size.toFloat()), Paint().apply { this.shader = shader })
        val bitmap = org.jetbrains.skia.Bitmap().apply { allocN32Pixels(size, size) }
        surface.readPixels(bitmap, 0, 0)
        fun at(x: Int, y: Int) = Color(bitmap.getColor(x, y))

        assertTrue(at(120, 120).alpha > 0.99f, "pigment in the middle")
        assertTrue(at(120, 120).blue > at(120, 120).red, "and it is the pigment's colour")
        assertEquals(0f, at(2, 2).alpha, "nothing in the corner")

        // Walk out along sixteen directions to where the pigment ends: not one circle.
        val edges = (0 until 16).map { k ->
            val a = 2 * Math.PI * k / 16
            (1..size / 2).first { r ->
                val x = (c + r * cos(a)).toInt().coerceIn(0, size - 1)
                val y = (c + r * sin(a)).toInt().coerceIn(0, size - 1)
                at(x, y).alpha < 0.5f
            }
        }
        assertTrue(edges.max() - edges.min() >= 4, "a cauliflower edge, not a circle: $edges")

        // Just inside the edge, the pigment has gathered. The rim starts six pixels in from where
        // the shader's edge is and darkens toward it; the edge found above sits a pixel or two
        // inside that, so two pixels in from it is well within the rim. Averaged over all sixteen
        // directions against the middle, so the grain (a few per cent) cannot pass for a rim.
        val rim = edges.indices.map { k ->
            val a = 2 * Math.PI * k / 16
            val r = edges[k] - 2
            at((c + r * cos(a)).toInt(), (c + r * sin(a)).toInt()).luminance()
        }.average()
        val middle = (-4..4).flatMap { dx -> (-4..4).map { dy -> at(120 + dx * 3, 120 + dy * 3).luminance() } }.average()
        assertTrue(rim < middle * 0.9, "a darker rim: $rim against $middle in the middle")
    }
}
