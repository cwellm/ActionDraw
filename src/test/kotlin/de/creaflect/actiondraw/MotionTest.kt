package de.creaflect.actiondraw

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import de.creaflect.actiondraw.ui.BLOOM_SKSL
import de.creaflect.actiondraw.ui.ChromeAlpha
import de.creaflect.actiondraw.ui.Motion
import de.creaflect.actiondraw.ui.Room
import de.creaflect.actiondraw.ui.SessionScreen
import de.creaflect.actiondraw.ui.bloomCovers
import de.creaflect.actiondraw.ui.bloomFrame
import de.creaflect.actiondraw.ui.chromeAlpha
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Surface
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The atelier's motion (F9.4): the bloom between the palette and a room, chrome that steps back
 * while you work, and reduced motion. The timelines are pure and tested as such; that a session's
 * controls really step back is tested on the real screen.
 */
@OptIn(ExperimentalTestApi::class)
class MotionTest {
    @get:Rule
    val rule = createComposeRule()

    private val pictures: File = Files.createTempDirectory("motion-pics").toFile()
    private val config: File = Files.createTempDirectory("motion-cfg").toFile()

    @After
    fun cleanup() {
        pictures.deleteRecursively()
        config.deleteRecursively()
    }

    @Test
    fun chromeIsThereUntilItRecedesAndComesBackAsThePointerNears() {
        assertEquals(1f, chromeAlpha(receded = false, near = false, over = false))
        assertEquals(0f, chromeAlpha(receded = true, near = false, over = false))
        assertEquals(Motion.APPROACH_ALPHA, chromeAlpha(receded = true, near = true, over = false))
        assertEquals(1f, chromeAlpha(receded = true, near = true, over = true))
    }

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
        val pigment = Room.BOARDS.pigment.mass
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

        // Just inside the edge, the pigment has gathered.
        val inner = edges[0] - 6
        assertTrue(at((c + inner).toInt(), 120).luminance() < at(120, 120).luminance(), "a darker rim")
    }

    @Test
    fun reducedMotionIsWhatWasChosenOnceItIsChosen() {
        val settings = Settings(config)
        settings.setReducedMotion(true)
        assertTrue(AppState(settings).reducedMotion)
        val app = AppState(settings)
        app.setReducedMotionPreference(false)
        assertEquals(false, AppState(Settings(config)).reducedMotion, "and it is remembered")
    }

    @Test
    fun aRunningPoseLetsItsControlsStepBackAndPausingBringsThemBack() {
        repeat(2) { i ->
            val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB)
            image.createGraphics().apply { paint = java.awt.Color.ORANGE; fillRect(0, 0, 16, 16); dispose() }
            ImageIO.write(image, "png", File(pictures, "pose$i.png"))
        }
        val state = AppState(Settings(config))
        state.selectFolder(pictures)
        state.start()
        // A running pose ticks every second for ever: the clock moves only when this test says so,
        // or waiting for the screen to be idle would never end.
        rule.mainClock.autoAdvance = false
        rule.setContent { SessionScreen(state, onToggleFullscreen = {}, isFullscreen = false) }
        rule.waitForIdle()
        val chrome = rule.onNodeWithTag("session-chrome")
        chrome.assert(SemanticsMatcher.expectValue(ChromeAlpha, 1f))

        // The mouse rests: the drawing is on paper.
        rule.mainClock.advanceTimeBy(Motion.RECEDE_AFTER_MS + 200L)
        rule.waitForIdle()
        chrome.assert(SemanticsMatcher.expectValue(ChromeAlpha, 0f))

        // Down to the bar: there it is.
        chrome.performMouseInput { moveTo(center) }
        rule.waitForIdle()
        chrome.assert(SemanticsMatcher.expectValue(ChromeAlpha, 1f))

        // Away again and resting: back it steps; a pause brings it back.
        rule.onRoot().performMouseInput { moveTo(androidx.compose.ui.geometry.Offset(10f, 10f)) }
        rule.mainClock.advanceTimeBy(Motion.RECEDE_AFTER_MS + 200L)
        rule.waitForIdle()
        chrome.assert(SemanticsMatcher.expectValue(ChromeAlpha, 0f))
        state.togglePause()
        rule.waitForIdle()
        chrome.assert(SemanticsMatcher.expectValue(ChromeAlpha, 1f))
    }
}
