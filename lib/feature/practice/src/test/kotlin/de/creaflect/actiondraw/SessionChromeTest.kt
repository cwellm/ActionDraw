package de.creaflect.actiondraw

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import de.creaflect.actiondraw.practice.PracticePage
import de.creaflect.actiondraw.practice.PracticeState
import de.creaflect.actiondraw.ui.ProvideAtelier
import de.creaflect.actiondraw.ui.SessionScreen
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * A pose as the design has it: Stop and what is running at the top left, the picture's own
 * buttons in a column under the ensō, one tray along the bottom, and the settings of what was
 * just chosen opening right above it - never across the picture.
 */
@OptIn(ExperimentalTestApi::class)
class SessionChromeTest {
    @get:Rule
    val rule = createComposeRule()

    private val pictures: File = Files.createTempDirectory("chrome-pics").toFile()
    private val config: File = Files.createTempDirectory("chrome-cfg").toFile()

    @After
    fun cleanup() {
        pictures.deleteRecursively()
        config.deleteRecursively()
    }

    private fun pose(): PracticeState {
        repeat(3) { i ->
            val image = BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB)
            image.createGraphics().apply { paint = java.awt.Color(200, 160 - i * 20, 120); fillRect(0, 0, 40, 30); dispose() }
            ImageIO.write(image, "png", File(pictures, "pose$i.png"))
        }
        val state = PracticeState(FileLib(config)).also { it.selectFolder(pictures) }
        state.start()
        rule.mainClock.autoAdvance = false // a running pose ticks for ever
        rule.setContent { ProvideAtelier { SessionScreen(state, onToggleFullscreen = {}, isFullscreen = false) } }
        rule.mainClock.advanceTimeBy(300)
        return state
    }

    private fun step() = rule.mainClock.advanceTimeBy(200)

    @Test
    fun stopAndWhatIsRunningSitAtTheTopLeft() {
        val state = pose()
        rule.onNodeWithTag("session-caption").assertTextEquals("2:00 each · ${pictures.name}")
        rule.onNodeWithTag("session-auto").performClick()
        step()
        assertFalse(state.autoAdvance, "auto-advance, switched in words")
        rule.onNodeWithTag("session-stop").performClick()
        step()
        assertEquals(PracticePage.Summary, state.page)
    }

    @Test
    fun theEnsoPausesAndTheSideButtonsActOnThePicture() {
        val state = pose()
        rule.onNodeWithTag("enso").performClick()
        step()
        assertTrue(state.isPaused, "a click on the ensō pauses")
        rule.onNodeWithTag("session-paused").assertExists()
        rule.onNodeWithTag("enso").performClick()
        step()
        assertFalse(state.isPaused)

        val first = state.currentImage
        rule.onNodeWithTag("session-next").performClick()
        step()
        assertNotEquals(first, state.currentImage, "on to the next picture")
        rule.onNodeWithTag("session-prev").performClick()
        step()
        assertEquals(first, state.currentImage, "and back")

        rule.onNodeWithTag("session-redo").performClick()
        step()
        assertTrue(state.isCurrentRedo)
        rule.onNodeWithTag("session-redo").assertIsSelected()
        rule.onNodeWithTag("session-hide").performClick()
        step()
        assertTrue(state.referenceFlipped)
    }

    @Test
    fun aLensSettingsOpenAboveTheTrayAndLeaveThePictureBe() {
        val state = pose()
        rule.onNodeWithTag("settings-lens").assertDoesNotExist()
        rule.onNodeWithTag("lens-notan").performClick()
        step()
        val popover = rule.onNodeWithTag("settings-lens").fetchSemanticsNode().boundsInRoot
        val window = rule.onRoot().fetchSemanticsNode().size
        assertTrue(popover.width < window.width / 2f, "as wide as what it holds: ${popover.width} of ${window.width}")
        val tray = rule.onNodeWithTag("session-chrome").fetchSemanticsNode().boundsInRoot
        assertTrue(popover.bottom <= tray.top, "above the tray")
        val slide = rule.onNodeWithTag("lens-notan").fetchSemanticsNode().boundsInRoot
        assertTrue(slide.center.x in popover.left..popover.right, "over its slide")
        // Clicked again, the chosen slide puts its settings away; the close does too.
        rule.onNodeWithTag("lens-notan").performClick()
        step()
        rule.onNodeWithTag("settings-lens").assertDoesNotExist()
        rule.onNodeWithTag("lens-notan").performClick()
        step()
        rule.onNodeWithTag("settings-close").performClick()
        step()
        rule.onNodeWithTag("settings-lens").assertDoesNotExist()
        assertEquals(ViewMode.NOTAN, state.lens.viewMode, "the lens stays on")
    }

    @Test
    fun theLightBlurAndTheGlassOpenTheirSettingsFromTheirKnobs() {
        val state = pose()
        rule.onNodeWithTag("knob-light").performClick()
        step()
        rule.onNodeWithTag("settings-light").assertExists()
        rule.onNodeWithTag("knob-b").performClick()
        step()
        assertTrue(state.lens.blur)
        rule.onNodeWithTag("settings-blur").assertExists()
        rule.onNodeWithTag("settings-light").assertDoesNotExist()
        rule.onNodeWithTag("glass").performClick()
        step()
        assertTrue(state.lens.glass.inHand)
        rule.onNodeWithTag("settings-glass").assertExists()
        rule.onNodeWithTag("glass-optic-fisheye").performClick()
        step()
        assertEquals(de.creaflect.actiondraw.ui.Optic.FISHEYE, state.lens.glass.optic)
        rule.onNodeWithTag("glass").performClick()
        step()
        assertFalse(state.lens.glass.inHand)
        rule.onNodeWithTag("settings-glass").assertDoesNotExist()
    }
}
