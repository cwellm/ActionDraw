package de.creaflect.actiondraw

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import de.creaflect.actiondraw.board.BoardRef
import de.creaflect.actiondraw.board.BoardSource
import de.creaflect.actiondraw.platform.FileDialogs
import de.creaflect.actiondraw.practice.PracticePage
import de.creaflect.actiondraw.practice.PracticeState
import de.creaflect.actiondraw.ui.Exercise
import de.creaflect.actiondraw.ui.PracticeScreen
import de.creaflect.actiondraw.ui.ProvideAtelier
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

    private fun stateWithPictures(n: Int = 3, boards: BoardSource = BoardSource.None): PracticeState {
        repeat(n) { i ->
            val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB)
            image.createGraphics().apply { paint = java.awt.Color.ORANGE; fillRect(0, 0, 16, 16); dispose() }
            ImageIO.write(image, "png", File(pictures, "pose$i.png"))
        }
        return PracticeState(FileLib(config), boards = boards).also { it.selectFolder(pictures) }
    }

    /** The boards as the board room would answer: one, called Buch, with [onBoard] on it. */
    private fun boardsOf(boardDir: File, onBoard: List<File>) = object : BoardSource {
        override fun available() = listOf(BoardRef("Buch", boardDir))
        override fun pictures(dir: File) = if (dir == boardDir) onBoard else emptyList()
        override fun pin(dir: File, pictures: List<File>) = ""
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
        val state = PracticeState(FileLib(config))
        state.chooseExercise(Exercise.LENS)
        assertEquals(ViewMode.NOTAN, state.lens.viewMode)
        assertEquals(Exercise.LENS, state.exercise)

        state.chooseExercise(Exercise.MEMORY)
        assertEquals(SessionPlans.FROM_MEMORY, state.rampPlan)
        assertEquals(Exercise.MEMORY, state.exercise)

        state.chooseExercise(Exercise.ACTION)
        assertEquals(ViewMode.NONE, state.lens.viewMode)
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
        rule.setContent { ProvideAtelier { PracticeScreen(state) } }
        rule.waitForIdle()
        rule.onNodeWithTag("exercise-action").assertIsSelected()
        rule.onNodeWithTag("exercise-flicker").assertIsNotEnabled()
        rule.onNodeWithTag("exercise-staged").assertIsNotEnabled()
        rule.onNodeWithTag("exercise-compare").assertIsNotEnabled()

        rule.onNodeWithTag("exercise-lens").performClick()
        rule.waitForIdle()
        assertEquals(ViewMode.NOTAN, state.lens.viewMode)
        rule.onNodeWithTag("start-lens-edge").performClick()
        rule.waitForIdle()
        assertEquals(ViewMode.EDGE, state.lens.viewMode, "the starting lens")

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
        rule.setContent { ProvideAtelier { SessionScreen(state, onToggleFullscreen = {}, isFullscreen = false) } }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("enso").assertIsDisplayed()

        rule.onNodeWithTag("lens-edge").performClick()
        rule.mainClock.advanceTimeBy(100)
        assertEquals(ViewMode.EDGE, state.lens.viewMode)
        rule.onNodeWithTag("lens-edge").assertIsSelected()

        rule.onNodeWithTag("knob-b").performClick()
        rule.mainClock.advanceTimeBy(100)
        assertTrue(state.lens.blur)
        rule.onNodeWithTag("knob-g").performClick()
        rule.mainClock.advanceTimeBy(100)
        assertEquals(GridMode.THIRDS, state.lens.gridMode, "the grid knob turns the grids round, as G does")
    }

    /** The ensō's brush is on screen, a tenth of the way round a tenth of the way into the pose. */
    @Test
    fun theEnsoIsDrawnAsThePoseGoesBy() {
        val state = stateWithPictures()
        state.intervalSeconds = 10
        state.start()
        rule.mainClock.autoAdvance = false
        rule.setContent { ProvideAtelier { SessionScreen(state, onToggleFullscreen = {}, isFullscreen = false) } }
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
        rule.setContent { ProvideAtelier { SummaryScreen(state) } }
        rule.waitForIdle()
        rule.onNodeWithTag("summary-flagged").assertIsDisplayed()
        assertNull(state.pinNotice)
    }

    /** A board as the source: its pictures - wherever they are - and its folder for seen and redo. */
    @Test
    fun aBoardCanBeWhatThePracticeRoomDrawsFrom() {
        val boardDir = Files.createTempDirectory("practice-board").toFile()
        val elsewhere = Files.createTempDirectory("practice-concept").toFile()
        val onBoard = listOf(File(boardDir, "own.png"), File(elsewhere, "borrowed.png")).onEach { f ->
            ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", f)
        }
        val boards = boardsOf(boardDir, onBoard)
        try {
            val state = stateWithPictures(boards = boards)
            state.selectBoard(BoardRef("Buch", boardDir))
            assertEquals(BoardRef("Buch", boardDir), state.practiceBoard)
            assertEquals(2, state.totalCount, "the board's own picture and its concept's")
            state.start()
            assertEquals(PracticePage.Session, state.page)
            assertTrue(state.currentImage in onBoard, "drawing from the board: ${state.currentImage}")
            assertEquals(2, state.totalCount, "the session holds the concept's picture too, not just the folder's")

            assertNull(PracticeState(FileLib(config)).practiceBoard, "with no boards to draw from, the folder")
            val next = PracticeState(FileLib(config), boards = boards)
            assertEquals(BoardRef("Buch", boardDir), next.practiceBoard, "remembered for the next start")
            next.selectFolder(pictures)
            assertNull(next.practiceBoard, "a folder chosen is a folder again")
            assertNull(FileLib(config).preferences.practiceBoard())
        } finally {
            boardDir.deleteRecursively()
            elsewhere.deleteRecursively()
        }
    }

    @Test
    fun theSourceIsChosenFromOneMenuWithTheBoardsInIt() {
        val boardDir = Files.createTempDirectory("practice-board").toFile()
        try {
            ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", File(boardDir, "a.png"))
            val state = stateWithPictures(boards = boardsOf(boardDir, listOf(File(boardDir, "a.png"))))
            rule.setContent { ProvideAtelier { PracticeScreen(state, onHome = {}) } }
            rule.waitForIdle()
            rule.onNodeWithTag("drawing-from").performClick()
            rule.waitForIdle()
            rule.onNodeWithTag("source-board-Buch").performClick()
            rule.waitForIdle()
            assertEquals(BoardRef("Buch", boardDir), state.practiceBoard)
            rule.onNodeWithTag("drawing-from").assertTextContains("Buch", substring = true)
        } finally {
            boardDir.deleteRecursively()
        }
    }

    @Test
    fun everyExerciseCanStartWithALens() {
        val state = stateWithPictures()
        rule.setContent { ProvideAtelier { PracticeScreen(state, onHome = {}) } }
        rule.waitForIdle()
        assertEquals(Exercise.ACTION, state.exercise)
        rule.onNodeWithTag("start-lens-notan").performClick()
        rule.waitForIdle()
        assertEquals(ViewMode.NOTAN, state.lens.viewMode)
        assertEquals(Exercise.LENS, state.exercise, "a lens to start with makes it a lens study")
        rule.onNodeWithTag("start-lens-none").performClick()
        rule.waitForIdle()
        assertEquals(Exercise.ACTION, state.exercise)
    }

    // ---- The host's services ----

    @Test
    fun theFolderIsChosenWithTheHostsDialogs() {
        val chosen = Files.createTempDirectory("practice-chosen").toFile()
        try {
            File(chosen, "a.png").createNewFile()
            val dialogs = object : FileDialogs by FileDialogs.None {
                override fun chooseFolder(start: File?, title: String): File = chosen
            }
            val state = PracticeState(FileLib(config), dialogs = dialogs)
            rule.setContent { ProvideAtelier { PracticeScreen(state) } }
            rule.waitForIdle()
            rule.onNodeWithTag("source-folder").assertDoesNotExist()
            rule.onNode(androidx.compose.ui.test.hasText("folder ▾")).performClick()
            rule.waitForIdle()
            rule.onNodeWithTag("source-folder").performClick()
            rule.waitForIdle()
            assertEquals(chosen, state.folder, "the room draws from the folder the dialog gave")
        } finally {
            chosen.deleteRecursively()
        }
    }
}
