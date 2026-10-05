package de.creaflect.actiondraw

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import de.creaflect.actiondraw.board.BoardHost
import de.creaflect.actiondraw.board.BoardState
import de.creaflect.actiondraw.concept.ConceptHost
import de.creaflect.actiondraw.concept.ConceptState
import de.creaflect.actiondraw.ui.AtelierTheme
import de.creaflect.actiondraw.ui.PALETTE_ORDER
import de.creaflect.actiondraw.ui.PaletteScreen
import de.creaflect.actiondraw.ui.Room
import de.creaflect.actiondraw.ui.paletteStep
import de.creaflect.actiondraw.ui.stepRoom
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The palette is home (F9.3): the app starts there, every room opens from it at its next step,
 * and the way back from the top of every room ends there. The dry wells cannot be chosen.
 */
@OptIn(ExperimentalTestApi::class)
class PaletteTest {
    @get:Rule
    val rule = createComposeRule()

    private val home: File = Files.createTempDirectory("palette-home").toFile()
    private val config: File = Files.createTempDirectory("palette-cfg").toFile()

    @After
    fun cleanup() {
        home.deleteRecursively()
        config.deleteRecursively()
    }

    /** The app's states wired to each other as Main wires them, without the windows. */
    private inner class Shell {
        val settings = Settings(config)
        val app = AppState(settings)
        val boards = BoardState(settings, object : BoardHost {
            override fun startSession(root: File, images: List<File>, setup: SessionSetup?) = Unit
            override fun showBoard() = app.showBoard()
            override fun showBoardList() = app.showBoardList()
            override fun leaveBoard() = app.leaveBoard()
            override fun currentSetup() = app.currentSetup()
        })
        val concepts = ConceptState(settings, object : ConceptHost {
            override fun showConcepts() = app.showConcepts()
            override fun showConcept() = app.showConcept()
            override fun leaveConcepts() = app.leaveConcepts()
        })

        fun open(room: Room) = openRoom(room, app, boards, concepts)
    }

    @Test
    fun theAppStartsOnThePaletteWithTheWellChosenLastTime() {
        val shell = Shell()
        assertEquals(Screen.Palette, shell.app.screen)
        assertEquals(Room.PRACTICE, shell.app.paletteRoom)

        shell.app.selectRoom(Room.SKETCH)
        assertEquals(Room.SKETCH, AppState(Settings(config)).paletteRoom, "the next start remembers the well")
    }

    @Test
    fun everyRoomOpensAtItsNextStep() {
        val shell = Shell()
        val app = shell.app

        shell.open(Room.PRACTICE)
        assertEquals(Screen.Menu, app.screen, "Practice opens the session setup")
        app.showPalette()

        shell.open(Room.SKETCH)
        assertEquals(Screen.Sketch, app.screen)
        assertEquals(Screen.Palette, app.sketchOrigin, "Back from the sketch leads home")
        app.leaveSketch()

        shell.open(Room.CONCEPTS)
        assertEquals(Screen.Concepts, app.screen)
        shell.concepts.leaveList()

        // No board opened yet: the list.
        shell.open(Room.BOARDS)
        assertEquals(Screen.BoardList, app.screen)
        assertEquals("All boards", paletteWells(shell.boards.lastBoard(), false).single { it.room == Room.BOARDS }.action)

        // Once there is a last board, the well opens it by name.
        shell.boards.createBoard(home, "Drachenbuch")
        shell.boards.closeBoard()
        assertEquals(Screen.Palette, app.screen)
        assertEquals("Open Drachenbuch", paletteWells(shell.boards.lastBoard(), false).single { it.room == Room.BOARDS }.action)
        shell.open(Room.BOARDS)
        assertEquals(Screen.Board, app.screen)
        assertEquals("Drachenbuch", shell.boards.board?.name)
        assertEquals(Room.BOARDS, app.paletteRoom, "the well opened stays chosen for the way back")

        // A dry well does nothing.
        shell.boards.closeBoard()
        shell.open(Room.LENS)
        shell.open(Room.COLLAGE)
        assertEquals(Screen.Palette, app.screen)
    }

    @Test
    fun escFromTheTopOfEveryRoomEndsOnThePalette() {
        val shell = Shell()
        val app = shell.app

        app.showPractice()
        assertTrue(escapeToPalette(app.screen, app, shell.boards))
        assertEquals(Screen.Palette, app.screen, "Practice")

        shell.boards.openBoardList()
        assertTrue(escapeToPalette(app.screen, app, shell.boards))
        assertEquals(Screen.Palette, app.screen, "the board list")

        // A board's own Esc closes it (handleBoardKey) — and closing a board leads home.
        shell.boards.createBoard(home, "Wings")
        assertEquals(Screen.Board, app.screen)
        assertFalse(escapeToPalette(app.screen, app, shell.boards), "a board keeps its own Esc")
        shell.boards.closeBoard()
        assertEquals(Screen.Palette, app.screen, "a board")

        // The concept list's Esc leaves the list.
        shell.concepts.openList()
        shell.concepts.leaveList()
        assertEquals(Screen.Palette, app.screen, "the concept list")

        // Live Sketch's Esc goes back where it came from — home, when it came from home.
        app.showSketch(from = Screen.Palette)
        app.leaveSketch()
        assertEquals(Screen.Palette, app.screen, "a sketch begun on the palette")
    }

    @Test
    fun homeFromASketchDoesNotLeaveAPausedSessionBehind() {
        val app = Shell().app
        app.showSketch(from = Screen.Board)
        app.sketchHome()
        assertEquals(Screen.Palette, app.screen)

        app.showSketch(from = Screen.Session)
        app.sketchHome()
        assertEquals(Screen.Session, app.screen, "a sketch made from a pose returns to the pose")
    }

    @Test
    fun theKeysTurnThePalettePastTheDryWells() {
        val wells = paletteWells(lastBoard = null, sketchOpen = false)
        assertEquals(PALETTE_ORDER, wells.map { it.room }, "the wells are listed in their order round the rim")
        assertEquals(Room.SKETCH, stepRoom(wells, Room.PRACTICE, 1), "Lens is dry")
        assertEquals(Room.CONCEPTS, stepRoom(wells, Room.SKETCH, 1), "Collage is dry")
        assertEquals(Room.PRACTICE, stepRoom(wells, Room.BOARDS, 1), "round the rim")
        assertEquals(Room.BOARDS, stepRoom(wells, Room.PRACTICE, -1))
        assertEquals(1, paletteStep(Key.DirectionRight))
        assertEquals(1, paletteStep(Key.DirectionDown))
        assertEquals(-1, paletteStep(Key.DirectionLeft))
        assertEquals(-1, paletteStep(Key.DirectionUp))
        assertEquals(null, paletteStep(Key.A))
    }

    @Test
    fun onScreenAClickChoosesASecondOpensAndTheKeysDoTheSame() {
        val opened = mutableListOf<Room>()
        rule.setContent {
            var selected by remember { mutableStateOf(Room.PRACTICE) }
            AtelierTheme {
                PaletteScreen(paletteWells(null, false), selected, onSelect = { selected = it }, onOpen = { opened += it })
            }
        }
        rule.waitForIdle()
        for (room in PALETTE_ORDER) rule.onNodeWithTag("well-${room.name.lowercase()}").assertIsDisplayed()
        rule.onNodeWithTag("well-lens").assertIsNotEnabled()
        rule.onNodeWithTag("well-collage").assertIsNotEnabled()
        rule.onNodeWithTag("well-sketch").assertIsEnabled()

        // A click on another well chooses it; the centre well says so.
        rule.onNodeWithTag("well-sketch").performClick()
        rule.waitForIdle()
        rule.onNode(hasText("Seven tools", substring = true)).assertIsDisplayed()
        rule.onNodeWithTag("palette-open").assert(hasText("New sketch"))
        assertEquals(emptyList(), opened)

        // A dry well cannot be chosen.
        rule.onNodeWithTag("well-lens").performClick()
        rule.waitForIdle()
        assertEquals(emptyList(), opened)

        // A second click on the chosen well opens it, and so does the centre's button.
        rule.onNodeWithTag("well-sketch").performClick()
        rule.onNodeWithTag("palette-open").performClick()
        rule.waitForIdle()
        assertEquals(listOf(Room.SKETCH, Room.SKETCH), opened)

        // The keys: focus sits on the chosen well, the arrows turn it, Enter opens.
        rule.onNodeWithTag("well-sketch").requestFocus()
        rule.onNodeWithTag("well-sketch").performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        rule.onNodeWithTag("well-concepts").assertIsFocused()
        rule.onNodeWithTag("well-concepts").performKeyInput { pressKey(Key.Enter) }
        rule.waitForIdle()
        assertEquals(listOf(Room.SKETCH, Room.SKETCH, Room.CONCEPTS), opened)
    }
}
