package de.creaflect.actiondraw

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import de.creaflect.actiondraw.practice.PracticeState
import de.creaflect.actiondraw.ui.ChromeAlpha
import de.creaflect.actiondraw.ui.Motion
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
import kotlin.test.assertTrue

/**
 * Motion in the app (F9.4): reduced motion as the person chose it, and a session's controls
 * stepping back on the real screen. The timelines themselves are tested where they live: the
 * receding chrome's in the design contract, the bloom's in the atelier.
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
    fun reducedMotionIsWhatWasChosenOnceItIsChosen() {
        val lib = FileLib(config)
        lib.preferences.setReducedMotion(true)
        assertTrue(AppState(lib).reducedMotion)
        val app = AppState(lib)
        app.setReducedMotionPreference(false)
        assertEquals(false, AppState(FileLib(config)).reducedMotion, "and it is remembered")
    }

    @Test
    fun aRunningPoseLetsItsControlsStepBackAndPausingBringsThemBack() {
        repeat(2) { i ->
            val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB)
            image.createGraphics().apply { paint = java.awt.Color.ORANGE; fillRect(0, 0, 16, 16); dispose() }
            ImageIO.write(image, "png", File(pictures, "pose$i.png"))
        }
        val state = PracticeState(FileLib(config))
        state.selectFolder(pictures)
        state.start()
        // A running pose ticks every second for ever: the clock moves only when this test says so,
        // or waiting for the screen to be idle would never end.
        rule.mainClock.autoAdvance = false
        rule.setContent { ProvideAtelier { SessionScreen(state, onToggleFullscreen = {}, isFullscreen = false) } }
        rule.waitForIdle()
        val chrome = rule.onNodeWithTag("session-chrome")
        chrome.assert(SemanticsMatcher.expectValue(ChromeAlpha, 1f))
        fun pictureHeight() = rule.onNodeWithTag("reference").fetchSemanticsNode().size.height
        val window = rule.onRoot().fetchSemanticsNode().size.height
        assertEquals(window, pictureHeight() + chrome.fetchSemanticsNode().size.height, "the controls take their room")

        // The mouse rests: the drawing is on paper, and the picture takes the controls' room.
        rule.mainClock.advanceTimeBy(Motion.RECEDE_AFTER_MS + 200L)
        rule.waitForIdle()
        chrome.assert(SemanticsMatcher.expectValue(ChromeAlpha, 0f))
        rule.mainClock.advanceTimeBy(Motion.RECEDE_MS + 100L)
        rule.waitForIdle()
        assertEquals(window, pictureHeight(), "the whole height is the picture's")

        // Down to the bar: there it is.
        chrome.performMouseInput { moveTo(center) }
        rule.waitForIdle()
        chrome.assert(SemanticsMatcher.expectValue(ChromeAlpha, 1f))

        // Away again, resting over the picture: back it steps; a pause brings it back.
        rule.onNodeWithTag("reference").performMouseInput { moveTo(center) }
        rule.mainClock.advanceTimeBy(Motion.RECEDE_AFTER_MS + 200L)
        rule.waitForIdle()
        chrome.assert(SemanticsMatcher.expectValue(ChromeAlpha, 0f))
        state.togglePause()
        rule.waitForIdle()
        chrome.assert(SemanticsMatcher.expectValue(ChromeAlpha, 1f))
        rule.mainClock.advanceTimeBy(Motion.RETURN_MS + 100L)
        rule.waitForIdle()
        // Paused, the bar is taller (the time can be set): the picture gives back just what it takes.
        assertEquals(window, pictureHeight() + chrome.fetchSemanticsNode().size.height, "and gives the room back as they return")
    }
}
