package de.creaflect.actiondraw

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import de.creaflect.actiondraw.ui.SessionScreen
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

/**
 * That the reference is *not drawn* while you work from memory is a rendering fact, and rendering
 * facts have a history in this project of passing every unit test while being wrong on screen.
 * So this one asks the real screen.
 */
class MemoryVeilTest {
    @get:Rule
    val rule = createComposeRule()

    private val dir: File = Files.createTempDirectory("veil-pics").toFile()
    private val config: File = Files.createTempDirectory("veil-cfg").toFile()

    @After
    fun cleanup() {
        dir.deleteRecursively()
        config.deleteRecursively()
    }

    /** A session paused on its first memory pose — paused, so only this test moves its clock. */
    private fun pausedMemorySession(): AppState {
        repeat(2) { i ->
            val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB)
            image.createGraphics().apply { paint = Color.ORANGE; fillRect(0, 0, 16, 16); dispose() }
            ImageIO.write(image, "png", File(dir, "pose$i.png"))
        }
        val state = AppState(Settings(config))
        state.selectFolder(dir)
        state.rampPlan = SessionPlan("Memory", listOf(RampStep(20, 2, studySeconds = 5)))
        state.start()
        state.togglePause()
        return state
    }

    @Test
    fun theReferenceIsOnScreenWhileThereIsStillTimeToStudyIt() {
        val state = pausedMemorySession()
        rule.setContent { SessionScreen(state, onToggleFullscreen = {}, isFullscreen = false) }
        rule.waitForIdle()

        rule.onNodeWithTag("reference").assertIsDisplayed()
        rule.onNodeWithTag("memory-veil").assertDoesNotExist()
    }

    @Test
    fun oncePastTheStudyTimeThePictureIsNotDrawnAtAll() {
        val state = pausedMemorySession()
        rule.setContent { SessionScreen(state, onToggleFullscreen = {}, isFullscreen = false) }
        rule.waitForIdle()

        repeat(5) { state.tick() }
        rule.waitForIdle()

        rule.onNodeWithTag("reference").assertDoesNotExist()
        rule.onNodeWithTag("memory-veil").assertIsDisplayed()
    }

    @Test
    fun hBringsItBackForAPeekAndHidesItAgain() {
        val state = pausedMemorySession()
        rule.setContent { SessionScreen(state, onToggleFullscreen = {}, isFullscreen = false) }
        repeat(5) { state.tick() }
        rule.waitForIdle()
        rule.onNodeWithTag("reference").assertDoesNotExist()

        state.toggleReference()
        rule.waitForIdle()
        rule.onNodeWithTag("reference").assertIsDisplayed()

        state.toggleReference()
        rule.waitForIdle()
        rule.onNodeWithTag("memory-veil").assertIsDisplayed()
    }

    @Test
    fun theReferenceIsBackForTheComparisonAtTheEnd() {
        val state = pausedMemorySession()
        rule.setContent { SessionScreen(state, onToggleFullscreen = {}, isFullscreen = false) }
        repeat(20) { state.tick() }
        rule.waitForIdle()

        rule.onNodeWithTag("reference").assertIsDisplayed()
        rule.onNodeWithTag("memory-veil").assertDoesNotExist()
    }
}
