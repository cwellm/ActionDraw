package de.creaflect.actiondraw.board

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.SessionSetup
import de.creaflect.actiondraw.Settings
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.board.ui.BoardDialogs
import de.creaflect.actiondraw.board.ui.BoardScreen
import de.creaflect.actiondraw.image.ThumbCache
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The board's chrome after the redesign: one header line, an action bar that exists only for a
 * selection, and an overflow menu whose entries actually do things. All wiring — so all on the
 * real screen.
 */
@OptIn(ExperimentalTestApi::class)
class BoardChromeTest {
    @get:Rule
    val rule = createComposeRule()

    private val home: File = Files.createTempDirectory("chrome-home").toFile()
    private val config: File = Files.createTempDirectory("chrome-cfg").toFile()
    private var left = 0
    private val host = object : BoardHost {
        override fun startSession(root: File, images: List<File>, setup: SessionSetup?) = Unit
        override fun showBoard() = Unit
        override fun showBoardList() = Unit
        override fun leaveBoard() { left++ }
        override fun currentSetup() = SessionSetup(null, 120, true, ViewMode.NONE, GridMode.OFF)
    }

    @After
    fun cleanup() {
        home.deleteRecursively()
        config.deleteRecursively()
    }

    private fun shownBoard(): Pair<BoardState, List<String>> {
        val state = BoardState(Settings(config), host)
        state.createBoard(home, "Chrome")
        val root = state.root!!
        state.importExternal(listOf("a.jpg", "b.jpg").map { File(root, it).apply { createNewFile() } })
        state.clearSelection()
        // The dialogs float above every screen in the app shell, so the test composes them too.
        rule.setContent {
            BoardScreen(state, ThumbCache(config), isFullscreen = false, setFullscreen = {})
            BoardDialogs(state)
        }
        rule.waitForIdle()
        return state to state.board!!.items.map { it.id }
    }

    @Test
    fun theActionBarExistsOnlyForASelection() {
        val (state, ids) = shownBoard()
        rule.onNodeWithTag("action-bar").assertDoesNotExist()

        state.clickItem(ids[0], ctrl = false, shift = false)
        rule.waitForIdle()
        rule.onNodeWithTag("action-bar").assertIsDisplayed()

        state.clearSelection()
        rule.waitForIdle()
        rule.onNodeWithTag("action-bar").assertDoesNotExist()
    }

    @Test
    fun theHeaderIsOneCompactStripe() {
        shownBoard()
        val header = rule.onNodeWithTag("board-header").fetchSemanticsNode().boundsInRoot
        assertTrue(header.height in 24f..64f, "one line of controls on a stripe, not a settings page: ${header.height} px")
        assertTrue(header.width >= 1000f, "spanning the window")
    }

    @Test
    fun theSegmentedControlSwitchesTheLayout() {
        val (state, _) = shownBoard()
        assertEquals(BoardLayouts.GRID, state.layout)

        rule.onNodeWithTag("layout-Free", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        assertEquals(BoardLayouts.FREE, state.layout)

        rule.onNodeWithTag("layout-Grid", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        assertEquals(BoardLayouts.GRID, state.layout)
    }

    @Test
    fun searchLivesInTheHeader() {
        val (state, _) = shownBoard()
        rule.onNodeWithTag("board-search").performTextInput("wing")
        rule.waitForIdle()
        assertEquals("wing", state.query)
    }

    @Test
    fun theOverflowMenuClosesTheBoard() {
        val (state, _) = shownBoard()
        rule.onNodeWithTag("board-more").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("board-close").performClick()
        rule.waitForIdle()

        assertFalse(state.isOpen, "the board is closed")
        assertEquals(1, left, "and the host was told to leave it")
    }

    @Test
    fun theAddMenuOffersANewNote() {
        val (state, _) = shownBoard()
        rule.onNodeWithTag("board-add").performClick()
        rule.waitForIdle()
        rule.onNode(androidx.compose.ui.test.hasText("New document note")).performClick()
        rule.waitForIdle()
        assertTrue(state.editor is BoardEditor.EditNote, "the note dialog is what opens: ${state.editor}")
    }

    @Test
    fun enterInTheGroupNameConfirmsTheGroup() {
        val (state, ids) = shownBoard()
        state.clickItem(ids[0], ctrl = false, shift = false)
        state.startGrouping()
        rule.waitForIdle()

        rule.onNodeWithTag("group-name").performTextInput("Flügel")
        rule.onNodeWithTag("group-name").performKeyInput { pressKey(Key.Enter) }
        rule.waitForIdle()

        assertEquals(listOf("Flügel"), state.sortedGroups.map { it.name }, "Enter is the Group button")
        assertTrue(state.editor == null, "and the dialog closed")
    }

    @Test
    fun enterInTheBoardNameCreatesTheBoard() {
        val (state, _) = shownBoard()
        state.openEditor(BoardEditor.NewBoard(null))
        rule.waitForIdle()

        rule.onNodeWithTag("board-name").performTextInput("Neu")
        rule.onNodeWithTag("board-name").performKeyInput { pressKey(Key.Enter) }
        rule.waitForIdle()

        assertEquals("Neu", state.board?.name, "Enter is the Create button, and the new board opens")
        assertTrue(state.editor == null, "and the dialog closed")
    }

    @Test
    fun theOverflowsSnapItemTogglesThePreference() {
        val (state, _) = shownBoard()
        state.setLayout(BoardLayouts.FREE)
        assertFalse(state.snapping, "off to begin with")

        rule.onNodeWithTag("board-more").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("snap-toggle").performClick()
        rule.waitForIdle()

        assertTrue(state.snapping)
        assertTrue(BoardState(Settings(config), host).snapping, "and it is remembered")
    }
}
