package de.creaflect.actiondraw.board

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import de.creaflect.actiondraw.AppState
import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.SessionSetup
import de.creaflect.actiondraw.Settings
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.board.ui.BoardCanvas
import de.creaflect.actiondraw.board.ui.BoardDialogs
import de.creaflect.actiondraw.board.ui.BoardMenuButton
import de.creaflect.actiondraw.board.ui.BoardScreen
import de.creaflect.actiondraw.board.ui.MenuExtras
import de.creaflect.actiondraw.image.ThumbCache
import de.creaflect.actiondraw.ui.MenuScreen
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Adding a picture to a group later: drop it on the group's frame. Which group a point belongs
 * to is pure geometry and tested as such; that a real drag ends in the group is tested on the
 * real canvas. Plus: Settings and Hotkeys are menu points, on the start menu and on the board.
 */
@OptIn(ExperimentalTestApi::class)
class DropIntoGroupTest {
    @get:Rule
    val rule = createComposeRule()

    private val home: File = Files.createTempDirectory("drop-home").toFile()
    private val config: File = Files.createTempDirectory("drop-cfg").toFile()
    private val host = object : BoardHost {
        override fun startSession(root: File, images: List<File>, setup: SessionSetup?) = Unit
        override fun showBoard() = Unit
        override fun showBoardList() = Unit
        override fun leaveBoard() = Unit
        override fun currentSetup() = SessionSetup(null, 120, true, ViewMode.NONE, GridMode.OFF)
    }

    @After
    fun cleanup() {
        home.deleteRecursively()
        config.deleteRecursively()
    }

    /** Four cards laid out freely; the first two grouped, the rest loose. */
    private fun boardWithAGroup(): Pair<BoardState, List<String>> {
        val state = BoardState(Settings(config), host)
        state.createBoard(home, "Drop")
        val root = state.root!!
        state.importExternal(listOf("a.jpg", "b.jpg", "c.jpg", "d.jpg").map { File(root, it).apply { createNewFile() } })
        state.setLayout(BoardLayouts.FREE)
        val ids = state.board!!.items.map { it.id }
        state.clearSelection(); ids.take(2).forEach { state.clickItem(it, ctrl = true, shift = false) }
        state.groupSelection("Flügel")
        state.clearSelection()
        return state to ids
    }

    // ---- Which group a point belongs to ----

    @Test
    fun aPointOnACardIsItsGroups() {
        val (state, ids) = boardWithAGroup()
        val pos = state.item(ids[0])!!.pos!!
        assertEquals("Flügel", state.groupAt(pos.x, pos.y)?.name)
    }

    @Test
    fun aPointFarFromEveryFrameIsNobodys() {
        val (state, _) = boardWithAGroup()
        assertNull(state.groupAt(-9000f, -9000f))
    }

    @Test
    fun thePointBetweenTwoSeparatedCardsIsStillTheGroups() {
        val (state, ids) = boardWithAGroup()
        state.clearSelection(); state.clickItem(ids[1], ctrl = false, shift = false)
        state.nudgeSelection(BoardState.BASE_SIZE * 2.5f, 0f) // pull it away: two pieces, one band
        state.commitLayout()
        val a = state.item(ids[0])!!.pos!!
        val b = state.item(ids[1])!!.pos!!
        assertEquals("Flügel", state.groupAt((a.x + b.x) / 2f, a.y)?.name, "the band covers the gap")
    }

    @Test
    fun aSubgroupsFrameWinsOverItsParents() {
        val (state, ids) = boardWithAGroup()
        val wings = state.sortedGroups.single().id
        state.clearSelection(); state.clickItem(ids[1], ctrl = false, shift = false)
        state.groupSelection("Membran", parentId = wings)
        val inner = state.item(ids[1])!!.pos!!
        assertEquals("Membran", state.groupAt(inner.x, inner.y)?.name, "innermost")
    }

    // ---- Dropping ----

    @Test
    fun aLooseCardDroppedOnTheFrameJoinsTheGroup() {
        val (state, ids) = boardWithAGroup()
        val target = state.item(ids[0])!!.pos!!
        state.clearSelection(); state.clickItem(ids[2], ctrl = false, shift = false)
        val loose = state.item(ids[2])!!.pos!!
        state.nudgeSelection(target.x - loose.x + 40f, target.y - loose.y + 40f) // onto the first card

        val joined = state.dropIntoGroupAt(ids[2])

        assertEquals("Flügel", joined?.name)
        assertEquals(listOf(state.sortedGroups.single().id), state.item(ids[2])!!.groups)
    }

    @Test
    fun aCardAlreadyInTheGroupIsNotReFiledByMovingAboutInsideIt() {
        val (state, ids) = boardWithAGroup()
        val before = state.item(ids[0])!!.groups
        assertNull(state.dropIntoGroupAt(ids[0]), "nothing changed")
        assertEquals(before, state.item(ids[0])!!.groups)
    }

    @Test
    fun aCardFromOneGroupDroppedOnAnotherMoves() {
        val (state, ids) = boardWithAGroup()
        state.clearSelection(); state.clickItem(ids[2], ctrl = false, shift = false)
        val other = state.groupSelection("Köpfe")!!
        val target = state.item(ids[0])!!.pos!!
        val moving = state.item(ids[2])!!.pos!!
        state.nudgeSelection(target.x - moving.x + 30f, target.y - moving.y + 30f)

        state.dropIntoGroupAt(ids[2])

        assertEquals(listOf(state.sortedGroups.first { it.name == "Flügel" }.id), state.item(ids[2])!!.groups)
        assertNull(state.groupById(other), "Köpfe held nothing else and is tidied away")
    }

    @Test
    fun aCardLetGoOnEmptyBoardStaysLoose() {
        val (state, ids) = boardWithAGroup()
        state.clearSelection(); state.clickItem(ids[3], ctrl = false, shift = false)
        state.nudgeSelection(0f, BoardState.BASE_SIZE * 6f) // well away from everything
        assertNull(state.dropIntoGroupAt(ids[3]))
        assertTrue(state.item(ids[3])!!.groups.isEmpty())
    }

    // ---- On the real canvas ----

    @Test
    fun draggingACardOntoAFrameFilesItThere() {
        val (state, ids) = boardWithAGroup()
        rule.setContent {
            BoardCanvas(state, ThumbCache(config), textured = false, modifier = Modifier.fillMaxSize())
        }
        rule.waitForIdle()
        val canvas = rule.onNodeWithTag("canvas").fetchSemanticsNode().size
        state.fitAll(canvas.width.toFloat(), canvas.height.toFloat())
        rule.waitForIdle()
        fun screen(bx: Float, by: Float) = Offset((bx - state.camX) * state.zoom + canvas.width / 2f, (by - state.camY) * state.zoom + canvas.height / 2f)
        val loose = state.item(ids[3])!!.pos!!
        val target = state.item(ids[0])!!.pos!!
        val from = screen(loose.x, loose.y)
        val to = screen(target.x + 30f, target.y + 30f)

        rule.onNodeWithTag("canvas").performMouseInput {
            moveTo(from)
            press()
            moveTo(Offset((from.x + to.x) / 2f, (from.y + to.y) / 2f))
            moveTo(to)
            release()
        }
        rule.waitForIdle()

        assertEquals("Flügel", state.groupById(state.item(ids[3])!!.groups.firstOrNull())?.name, "let go over the frame: filed")
    }

    // ---- Settings and Hotkeys as menu points ----

    @Test
    fun theStartMenuShowsSettingsAndHotkeys() {
        val settings = Settings(config)
        val app = AppState(settings)
        val boards = BoardState(settings, host)
        rule.setContent {
            MenuScreen(app, boardButton = { BoardMenuButton(boards) }, extras = { MenuExtras(app, boards) })
        }
        rule.waitForIdle()
        rule.onNodeWithTag("menu-settings").assertIsDisplayed()
        rule.onNodeWithTag("menu-hotkeys").assertIsDisplayed()
    }

    @Test
    fun theBoardsOverflowOpensSettings() {
        val (state, _) = boardWithAGroup()
        rule.setContent {
            BoardScreen(state, ThumbCache(config), isFullscreen = false, setFullscreen = {})
            BoardDialogs(state)
        }
        rule.waitForIdle()
        rule.onNodeWithTag("board-more").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("board-settings").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("settings-snap").assertIsDisplayed()
        rule.onNode(androidx.compose.ui.test.hasText("Board settings")).assertIsDisplayed()
        rule.onNode(androidx.compose.ui.test.hasText("Reference folder")).assertDoesNotExist()
    }

    @Test
    fun theBoardsHotkeysShowOnlyTheBoards() {
        val (state, _) = boardWithAGroup()
        rule.setContent {
            BoardScreen(state, ThumbCache(config), isFullscreen = false, setFullscreen = {})
            BoardDialogs(state)
        }
        rule.waitForIdle()
        rule.onNodeWithTag("board-more").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("board-hotkeys").performClick()
        rule.waitForIdle()
        rule.onNode(androidx.compose.ui.test.hasText("Idea Board hotkeys")).assertIsDisplayed()
        rule.onNode(androidx.compose.ui.test.hasText("Drawing session")).assertDoesNotExist()
    }

    @Test
    fun settingsAndHotkeysComeFirstInTheOverflowAndTheThemeRowCycles() {
        val (state, _) = boardWithAGroup()
        rule.setContent {
            BoardScreen(state, ThumbCache(config), isFullscreen = false, setFullscreen = {})
            BoardDialogs(state)
        }
        rule.waitForIdle()
        rule.onNodeWithTag("board-more").performClick()
        rule.waitForIdle()
        val settings = rule.onNodeWithTag("board-settings").fetchSemanticsNode().boundsInRoot.top
        val hotkeys = rule.onNodeWithTag("board-hotkeys").fetchSemanticsNode().boundsInRoot.top
        val theme = rule.onNodeWithTag("board-theme").fetchSemanticsNode().boundsInRoot.top
        val close = rule.onNodeWithTag("board-close").fetchSemanticsNode().boundsInRoot.top
        assertTrue(settings < hotkeys && hotkeys < theme && theme < close, "Settings, Hotkeys, then the rest")

        val before = state.theme
        rule.onNodeWithTag("board-theme").performClick()
        rule.waitForIdle()
        assertTrue(state.theme != before, "one row cycles the theme: $before -> ${state.theme}")
    }
}
