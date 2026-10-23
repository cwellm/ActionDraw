package de.creaflect.actiondraw

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import de.creaflect.actiondraw.practice.PracticeState
import de.creaflect.actiondraw.ui.Optic
import de.creaflect.actiondraw.ui.ProvideAtelier
import de.creaflect.actiondraw.ui.SessionScreen
import de.creaflect.actiondraw.ui.fitScale
import de.creaflect.actiondraw.ui.magnificationFor
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The glass over a running pose (F10.9): set down where it is clicked — mirrored or not — or on
 * the part dragged out, and lifted off again by the print in the corner or a right click.
 */
@OptIn(ExperimentalTestApi::class)
class GlassSessionTest {
    @get:Rule
    val rule = createComposeRule()

    private val pictures: File = Files.createTempDirectory("glass-pics").toFile()
    private val config: File = Files.createTempDirectory("glass-cfg").toFile()

    @After
    fun cleanup() {
        pictures.deleteRecursively()
        config.deleteRecursively()
    }

    /** A pose of one 400 × 200 picture, on screen. */
    private fun pose(): PracticeState {
        val image = BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply { paint = java.awt.Color.ORANGE; fillRect(0, 0, 400, 200); dispose() }
        ImageIO.write(image, "png", File(pictures, "pose.png"))
        val state = PracticeState(FileLib(config)).also { it.selectFolder(pictures) }
        state.start()
        rule.mainClock.autoAdvance = false // a running pose ticks for ever
        rule.setContent { ProvideAtelier { SessionScreen(state, onToggleFullscreen = {}, isFullscreen = false) } }
        rule.mainClock.advanceTimeBy(500)
        return state
    }

    /** Where on screen a point of the picture is, given as fractions of it, while it is shown whole. */
    private fun onScreen(fx: Float, fy: Float): Offset {
        val area = rule.onNodeWithTag("reference").fetchSemanticsNode().size
        val fit = fitScale(400f, 200f, area.width.toFloat(), area.height.toFloat())
        return Offset((area.width - 400f * fit) / 2f + fx * 400f * fit, (area.height - 200f * fit) / 2f + fy * 200f * fit)
    }

    @Test
    fun withoutTheGlassAClickDoesNothing() {
        val state = pose()
        rule.onNodeWithTag("reference").performMouseInput { click(onScreen(0.25f, 0.5f)) }
        rule.mainClock.advanceTimeBy(100)
        assertNull(state.glass.focus)
    }

    @Test
    fun theGlassInHandLiesWhereThePointerIs() {
        val state = pose()
        val at = onScreen(0.5f, 0.5f)
        rule.onNodeWithTag("reference").performMouseInput { moveTo(at) }
        rule.mainClock.advanceTimeBy(100)
        // Just inside the rim's radius, to the left of the pointer: the picture's orange — then the rim's grey.
        fun rimPixel(): Color {
            val shot = rule.onNodeWithTag("reference").captureToImage().toPixelMap()
            val r = state.glass.radius * rule.density.density
            return shot[(at.x - r - 4f * rule.density.density).toInt(), at.y.toInt()]
        }
        fun orange(c: Color) = c.red > 0.8f && c.green in 0.55f..0.85f && c.blue < 0.3f
        assertTrue(orange(rimPixel()), "no glass yet: the picture")
        state.glass.takeUp()
        rule.mainClock.advanceTimeBy(100)
        val rim = rimPixel()
        assertTrue(!orange(rim) && abs(rim.red - rim.blue) < 0.25f, "the glass's rim, a grey: $rim")
    }

    @Test
    fun aClickSetsTheGlassDownThereAndThePrintTakesItBack() {
        val state = pose()
        rule.onNodeWithTag("glass").performClick()
        rule.mainClock.advanceTimeBy(100)
        assertTrue(state.glass.inHand, "the glass taken up from the tray")
        rule.onNodeWithTag("reference").performMouseInput { click(onScreen(0.25f, 0.75f)) }
        rule.mainClock.advanceTimeBy(600)
        val focus = assertNotNull(state.glass.focusOn(state.currentImage))
        assertEquals(0.25f, focus.x, 0.01f)
        assertEquals(0.75f, focus.y, 0.01f)
        rule.onNodeWithTag("glass-whole").assertIsDisplayed().performClick()
        rule.mainClock.advanceTimeBy(600)
        assertNull(state.glass.focus, "the print in the corner: the whole picture again")
        assertTrue(state.glass.inHand, "with the glass still in hand")
    }

    @Test
    fun aMirroredPictureIsSetDownWhereItIsSeen() {
        val state = pose()
        state.mirror = true
        state.glass.takeUp()
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("reference").performMouseInput { click(onScreen(0.25f, 0.5f)) }
        rule.mainClock.advanceTimeBy(600)
        assertEquals(0.75f, assertNotNull(state.glass.focus).x, 0.01f, "the left of the screen is the picture's right")
    }

    @Test
    fun aPartDraggedOutFillsTheViewAndARightClickLiftsTheGlass() {
        val state = pose()
        state.glass.choose(Optic.CONVEX)
        rule.mainClock.advanceTimeBy(100)
        val from = onScreen(0.1f, 0.2f)
        val to = onScreen(0.3f, 0.4f)
        rule.onNodeWithTag("reference").performMouseInput {
            moveTo(from)
            press()
            // A mouse moves a few pixels at a time.
            val steps = 40
            repeat(steps) { moveBy((to - from) / steps.toFloat()) }
            release()
        }
        rule.mainClock.advanceTimeBy(600)
        val focus = assertNotNull(state.glass.focus)
        assertEquals(0.2f, focus.x, 0.01f)
        assertEquals(0.3f, focus.y, 0.01f)
        val area = rule.onNodeWithTag("reference").fetchSemanticsNode().size
        val expected = magnificationFor(400f, 200f, area.width.toFloat(), area.height.toFloat(), 80f, 40f)
        assertEquals(expected, focus.magnification, expected * 0.03f)
        rule.onNodeWithTag("reference").performMouseInput { rightClick(Offset(10f, 10f)) }
        rule.mainClock.advanceTimeBy(600)
        assertNull(state.glass.focus)
    }
}
