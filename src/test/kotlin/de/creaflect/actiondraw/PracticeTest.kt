package de.creaflect.actiondraw

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import de.creaflect.actiondraw.ui.Exercise
import de.creaflect.actiondraw.ui.PracticeScreen
import de.creaflect.actiondraw.ui.SessionScreen
import de.creaflect.actiondraw.ui.SummaryScreen
import de.creaflect.actiondraw.ui.chooseExercise
import de.creaflect.actiondraw.ui.ensoSweep
import de.creaflect.actiondraw.ui.exercise
import de.creaflect.actiondraw.ui.exerciseOf
import de.creaflect.actiondraw.ui.rampBars
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Practice room re-dressed (F9.6): exercises as cards, the ramp as bars, the ensō, the lens
 * tray and the knobs, and the summary's flagged prints — what each one does, on the real screen.
 */
@OptIn(ExperimentalTestApi::class)
class PracticeTest {
    @get:Rule
    val rule = createComposeRule()

    private val pictures: File = Files.createTempDirectory("practice-pics").toFile()
    private val config: File = Files.createTempDirectory("practice-cfg").toFile()

    @After
    fun cleanup() {
        pictures.deleteRecursively()
        config.deleteRecursively()
    }

    private fun stateWithPictures(n: Int = 3): AppState {
        repeat(n) { i ->
            val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB)
            image.createGraphics().apply { paint = java.awt.Color.ORANGE; fillRect(0, 0, 16, 16); dispose() }
            ImageIO.write(image, "png", File(pictures, "pose$i.png"))
        }
        return AppState(Settings(config)).also { it.selectFolder(pictures) }
    }

    @Test
    fun theExerciseIsWhatTheSettingsAmountTo() {
        assertEquals(Exercise.ACTION, exerciseOf(null, ViewMode.NONE))
        assertEquals(Exercise.ACTION, exerciseOf(SessionPlans.CLASSIC_GESTURE, ViewMode.NONE))
        assertEquals(Exercise.LENS, exerciseOf(null, ViewMode.NOTAN))
        assertEquals(Exercise.MEMORY, exerciseOf(SessionPlans.FROM_MEMORY, ViewMode.NONE))
        assertEquals(Exercise.MEMORY, exerciseOf(SessionPlans.FROM_MEMORY, ViewMode.EDGE), "memory wins: the picture hides whatever the lens")
    }

    @Test
    fun choosingAnExerciseSetsWhatMakesItThatExercise() {
        val state = AppState(Settings(config))
        state.chooseExercise(Exercise.LENS)
        assertEquals(ViewMode.NOTAN, state.viewMode)
        assertEquals(Exercise.LENS, state.exercise)

        state.chooseExercise(Exercise.MEMORY)
        assertEquals(SessionPlans.FROM_MEMORY, state.rampPlan)
        assertEquals(Exercise.MEMORY, state.exercise)

        state.chooseExercise(Exercise.ACTION)
        assertEquals(ViewMode.NONE, state.viewMode)
        assertEquals(SessionPlans.CLASSIC_GESTURE, state.rampPlan, "no longer hiding the picture")
        assertEquals(Exercise.ACTION, state.exercise)

        state.chooseExercise(Exercise.FLICKER)
        assertEquals(Exercise.ACTION, state.exercise, "an exercise still to come cannot be chosen")
    }

    @Test
    fun theRampHasABarPerPoseAsTallAsThePoseIsLong() {
        val plan = SessionPlans.CLASSIC_GESTURE
        val bars = rampBars(plan)
        assertEquals(plan.totalPoses, bars.size)
        assertEquals(0f, bars.first())
        assertEquals(1f, bars.last())
        assertTrue(bars.zipWithNext().all { (a, b) -> b >= a }, "longer poses, taller bars")
        assertEquals(0f, ensoSweep(0, 60))
        assertEquals(0.5f, ensoSweep(30, 60))
        assertEquals(1f, ensoSweep(90, 60), "full once the time is up")
    }

    @Test
    fun theCardsChooseTheExerciseAndOnlyTheBuiltOnesCanBeChosen() {
        val state = stateWithPictures()
        rule.setContent { PracticeScreen(state) }
        rule.waitForIdle()
        rule.onNodeWithTag("exercise-action").assertIsSelected()
        rule.onNodeWithTag("exercise-flicker").assertIsNotEnabled()
        rule.onNodeWithTag("exercise-staged").assertIsNotEnabled()
        rule.onNodeWithTag("exercise-compare").assertIsNotEnabled()

        rule.onNodeWithTag("exercise-lens").performClick()
        rule.waitForIdle()
        assertEquals(ViewMode.NOTAN, state.viewMode)
        rule.onNodeWithTag("start-lens-edge").performClick()
        rule.waitForIdle()
        assertEquals(ViewMode.EDGE, state.viewMode, "the starting lens")

        rule.onNodeWithTag("exercise-memory").performClick()
        rule.waitForIdle()
        assertEquals(SessionPlans.FROM_MEMORY, state.rampPlan)
        rule.onNodeWithTag("ramp").assertIsDisplayed()
        rule.onNodeWithTag("practice-start").assertIsEnabled()
    }

    @Test
    fun aPoseShowsTheEnsoAndTheTrayAndTheKnobsDoWhatTheKeysDo() {
        val state = stateWithPictures()
        state.start()
        rule.mainClock.autoAdvance = false // a running pose ticks for ever
        rule.setContent { SessionScreen(state, onToggleFullscreen = {}, isFullscreen = false) }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("enso").assertIsDisplayed()

        rule.onNodeWithTag("lens-edge").performClick()
        rule.mainClock.advanceTimeBy(100)
        assertEquals(ViewMode.EDGE, state.viewMode)
        rule.onNodeWithTag("lens-edge").assertIsSelected()

        rule.onNodeWithTag("knob-b").performClick()
        rule.mainClock.advanceTimeBy(100)
        assertTrue(state.blur)
        rule.onNodeWithTag("knob-g").performClick()
        rule.mainClock.advanceTimeBy(100)
        assertEquals(GridMode.THIRDS, state.gridMode, "the grid knob turns the grids round, as G does")
    }

    /** The ensō's brush is on screen, a tenth of the way round a tenth of the way into the pose. */
    @Test
    fun theEnsoIsDrawnAsThePoseGoesBy() {
        val state = stateWithPictures()
        state.intervalSeconds = 10
        state.start()
        rule.mainClock.autoAdvance = false
        rule.setContent { SessionScreen(state, onToggleFullscreen = {}, isFullscreen = false) }
        fun brush(): Int {
            val map = rule.onNodeWithTag("enso").captureToImage().toPixelMap()
            var n = 0
            for (x in 0 until map.width) for (y in 0 until map.height) {
                val c = map[x, y]
                if (c.red > 0.8f && c.green in 0.45f..0.75f && c.blue < 0.5f) n++
            }
            return n
        }
        rule.mainClock.advanceTimeBy(1100) // one second in, and the controls have not stepped back yet
        assertEquals(1, state.elapsedSeconds)
        assertTrue(brush() > 100, "the brush has gone a tenth of the way round: ${brush()} pixels")
    }

    @Test
    fun theSummaryLaysOutTheFlaggedPicturesAsPrints() {
        val state = stateWithPictures()
        state.start()
        state.toggleRedoCurrent()
        state.stop()
        rule.setContent { SummaryScreen(state) }
        rule.waitForIdle()
        rule.onNodeWithTag("summary-flagged").assertIsDisplayed()
        assertNull(state.pinNotice)
    }
}
