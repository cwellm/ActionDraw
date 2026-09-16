package de.creaflect.actiondraw.board

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.SessionSetup
import de.creaflect.actiondraw.Settings
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.board.ui.BoardCanvas
import de.creaflect.actiondraw.image.ThumbCache
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import androidx.compose.ui.geometry.Offset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Selecting a whole group by clicking it. Reported 2026-09-16: on one board one group could not
 * be clicked at all — only the single cards inside it. Which group and which board it happens to
 * be is the tell: the handle is the little label at the hull's corner, and the hull's padding
 * band shrinks with the zoom while the label keeps its size, so once zoomed out far enough a card
 * lies over the only place you could have clicked.
 */
@OptIn(ExperimentalTestApi::class)
class GroupClickTest {
    @get:Rule
    val rule = createComposeRule()

    private val home: File = Files.createTempDirectory("group-click").toFile()
    private val config: File = Files.createTempDirectory("group-cfg").toFile()

    @After
    fun cleanup() {
        home.deleteRecursively()
        config.deleteRecursively()
    }

    private val host = object : BoardHost {
        override fun startSession(root: File, images: List<File>, setup: SessionSetup?) = Unit
        override fun showBoard() = Unit
        override fun showBoardList() = Unit
        override fun leaveBoard() = Unit
        override fun currentSetup() = SessionSetup(null, 120, true, ViewMode.NONE, GridMode.OFF)
    }

    /** Three cards, all in one group, laid out freely. */
    private fun groupedBoard(): Pair<BoardState, String> {
        val state = BoardState(Settings(config), host)
        state.createBoard(home, "Grouped")
        val root = state.root!!
        val files = listOf("a.jpg", "b.jpg", "c.jpg").map { File(root, it).apply { createNewFile() } }
        state.importExternal(files)
        state.setLayout(BoardLayouts.FREE)
        state.selectAll()
        state.groupSelection("Flügel")
        state.clearSelection()
        return state to state.sortedGroups.single().id
    }

    private fun BoardState.groupMembers(groupId: String): Set<String> =
        board!!.items.filter { groupId in it.groups }.map { it.id }.toSet()

    @Test
    fun clickingAGroupsLabelSelectsTheWholeGroup() {
        val (state, groupId) = groupedBoard()
        rule.setContent {
            BoardCanvas(state, ThumbCache(config), textured = false, modifier = Modifier.fillMaxSize())
        }
        rule.waitForIdle()

        clickTheLabel(groupId)

        assertEquals(state.groupMembers(groupId), state.selection)
    }

    @Test
    fun theLabelStaysClickableWhenZoomedOutOverACard() {
        val (state, groupId) = groupedBoard()
        rule.setContent {
            BoardCanvas(state, ThumbCache(config), textured = false, modifier = Modifier.fillMaxSize())
        }
        // Far enough out that the hull's padding is thinner than the label is tall, so a card
        // now lies across the corner the label sits in.
        state.setZoom(0.25f, state.camX, state.camY)
        rule.waitForIdle()

        clickTheLabel(groupId)

        assertEquals(
            state.groupMembers(groupId),
            state.selection,
            "the label must win over the card lying across it",
        )
    }

    /** Clicks where the label actually is, so whatever is topmost there gets the click. */
    private fun clickTheLabel(groupId: String) {
        val bounds = rule.onNodeWithTag("group-label-$groupId").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("canvas").performMouseInput {
            moveTo(bounds.center)
            press()
            release()
        }
        rule.waitForIdle()
    }

    @Test
    fun aGroupWhoseCornerIsOffTheViewCanStillBeSelected() {
        val (state, groupId) = groupedBoard()
        rule.setContent {
            BoardCanvas(state, ThumbCache(config), textured = false, modifier = Modifier.fillMaxSize())
        }
        rule.waitForIdle()
        // This is the reported case: the group runs off the left of the view and takes its
        // top-left corner -- where its only handle used to sit -- with it.
        val hull = state.groupHulls.single()
        assertTrue(
            (hull.left - state.camX) * state.zoom + canvasSize().width / 2f < 0f,
            "the hull's corner must really be off screen for this test to mean anything",
        )

        clickTheLabel(groupId)

        assertEquals(state.groupMembers(groupId), state.selection)
    }

    @Test
    fun clickingTheGroupItselfSelectsIt() {
        val (state, groupId) = groupedBoard()
        rule.setContent {
            BoardCanvas(state, ThumbCache(config), textured = false, modifier = Modifier.fillMaxSize())
        }
        rule.waitForIdle()

        // Empty space inside the group's area, just below its cards.
        val hull = state.groupHulls.single()
        val size = canvasSize()
        val x = (hull.right - 12f - state.camX) * state.zoom + size.width / 2f
        val y = (hull.bottom - 6f - state.camY) * state.zoom + size.height / 2f
        rule.onNodeWithTag("canvas").performMouseInput {
            moveTo(Offset(x, y))
            press()
            release()
        }
        rule.waitForIdle()

        assertEquals(state.groupMembers(groupId), state.selection, "clicking the group picks it up")
    }

    @Test
    fun draggingTheGroupStillMovesItRatherThanSelectingOnly() {
        val (state, groupId) = groupedBoard()
        rule.setContent {
            BoardCanvas(state, ThumbCache(config), textured = false, modifier = Modifier.fillMaxSize())
        }
        rule.waitForIdle()
        val before = state.freeItems.first { groupId in it.groups }.pos!!.x

        val hull = state.groupHulls.single()
        val size = canvasSize()
        val x = (hull.right - 12f - state.camX) * state.zoom + size.width / 2f
        val y = (hull.bottom - 6f - state.camY) * state.zoom + size.height / 2f
        rule.onNodeWithTag("canvas").performMouseInput {
            moveTo(Offset(x, y))
            press()
            moveTo(Offset(x - 60f, y))
            moveTo(Offset(x - 120f, y))
            release()
        }
        rule.waitForIdle()

        val after = state.freeItems.first { groupId in it.groups }.pos!!.x
        assertTrue(after < before - 50f, "the group moved with the drag: $before -> $after")
    }

    private fun canvasSize() = rule.onNodeWithTag("canvas").fetchSemanticsNode().size
}
