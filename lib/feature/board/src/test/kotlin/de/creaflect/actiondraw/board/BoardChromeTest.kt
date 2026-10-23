package de.creaflect.actiondraw.board

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import de.creaflect.actiondraw.FileLib
import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.SessionSetup
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.board.ui.BoardDialogs
import de.creaflect.actiondraw.board.ui.BoardScreen
import de.creaflect.actiondraw.platform.FileDialogs
import de.creaflect.actiondraw.ui.ProvideAtelier
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
    private var started: List<File> = emptyList()
    private val host = object : BoardHost {
        override fun startSession(root: File, images: List<File>, setup: SessionSetup?) { started = images }
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
        val state = BoardState(FileLib(config), host)
        state.createBoard(home, "Chrome")
        val root = state.root!!
        state.importExternal(listOf("a.jpg", "b.jpg").map { File(root, it).apply { createNewFile() } })
        state.clearSelection()
        // The dialogs float above every screen in the app shell, so the test composes them too.
        rule.setContent {
            ProvideAtelier {
                BoardScreen(state, FileLib(config).pictures, isFullscreen = false, setFullscreen = {})
                BoardDialogs(state)
            }
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
        assertTrue(BoardState(FileLib(config), host).snapping, "and it is remembered")
    }

    @Test
    fun theHeaderSaysWhereYouAreAndDrawsTheWholeBoard() {
        val (state, _) = shownBoard()
        rule.onNodeWithTag("board-switcher").assertIsDisplayed()
        rule.onNodeWithText("Chrome").assertIsDisplayed()
        rule.onNodeWithTag("board-draw-all").performClick()
        rule.waitForIdle()
        assertEquals(listOf("a.jpg", "b.jpg"), started.map { it.name }.sorted(), "every picture on the board")
        // A board starts on cork, so the swatch that proves anything is another one.
        assertEquals(BoardThemes.CORK, state.theme)
        rule.onNodeWithTag("board-surface-papyrus").performClick()
        rule.waitForIdle()
        assertEquals(BoardThemes.PAPYRUS, state.theme)
    }

    @Test
    fun theCornersHoldTheLayoutTheZoomAndTheContents() {
        val (state, _) = shownBoard()
        rule.onNodeWithTag("zoom-in").assertDoesNotExist()
        rule.onNodeWithTag("layout-Free", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        val zoom = state.zoom
        rule.onNodeWithTag("zoom-in").performClick()
        rule.waitForIdle()
        assertTrue(state.zoom > zoom, "plus comes nearer")
        rule.onNodeWithTag("zoom-out").performClick()
        rule.onNodeWithTag("zoom-out").performClick()
        rule.waitForIdle()
        assertTrue(state.zoom < zoom, "minus goes further")
        val corner = rule.onNodeWithTag("board-view-corner").fetchSemanticsNode().boundsInRoot
        val canvas = rule.onNodeWithTag("canvas").fetchSemanticsNode().boundsInRoot
        assertTrue(corner.left < canvas.left + 40f && corner.bottom > canvas.bottom - 40f, "bottom left: $corner in $canvas")
        assertFalse(state.drawerOpen)
        rule.onNodeWithTag("board-contents").performClick()
        rule.waitForIdle()
        assertTrue(state.drawerOpen, "Contents, from the bottom right")
    }

    // ---- The host's services, as the board asks for them ----

    @Test
    fun importingAsksTheHostsDialogsForThePictures() {
        val outside = Files.createTempDirectory("chrome-outside").toFile()
        val picture = File(outside, "wing.jpg").apply { createNewFile() }
        var asked: Collection<String>? = null
        val dialogs = object : FileDialogs by FileDialogs.None {
            override fun chooseFiles(start: File?, title: String, description: String, extensions: Collection<String>): List<File> {
                asked = extensions
                return listOf(picture)
            }
        }
        val state = BoardState(FileLib(config), host, dialogs = dialogs)
        state.createBoard(home, "Import")
        rule.setContent { ProvideAtelier { BoardScreen(state, FileLib(config).pictures, isFullscreen = false, setFullscreen = {}) } }
        rule.waitForIdle()

        rule.onNodeWithTag("board-add").performClick()
        rule.waitForIdle()
        rule.onNode(androidx.compose.ui.test.hasText("Import pictures…")).performClick()
        rule.waitForIdle()

        assertTrue("jpg" in asked.orEmpty(), "the dialog offers the pictures the lib reads: $asked")
        assertEquals(listOf("_imported/wing.jpg"), state.board!!.items.filterIsInstance<ImageItem>().map { it.path }, "and what was chosen is on the board")
        outside.deleteRecursively()
    }

    @Test
    fun aLinkLeavesThroughTheHostsBrowser() {
        val opened = mutableListOf<String>()
        val state = BoardState(FileLib(config), host, browser = { url -> opened += url; true })
        state.createBoard(home, "Links")
        state.saveLink(null, "https://example.com/wings", "Wings")
        state.openLink(state.board!!.items.filterIsInstance<LinkItem>().single())
        assertEquals(listOf("https://example.com/wings"), opened)
    }
}
