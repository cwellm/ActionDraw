package de.creaflect.actiondraw.board

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A group's frame is shaped to its cards, and only the shape answers a click. The case that
 * proves it is an L: the empty corner inside the L's bounding box is not part of the group, so a
 * click there must not pick the group up — with the old rectangle it would have.
 */
@OptIn(ExperimentalTestApi::class)
class FrameClickTest {
    @get:Rule
    val rule = createComposeRule()

    private val home: File = Files.createTempDirectory("frame-click").toFile()
    private val config: File = Files.createTempDirectory("frame-cfg").toFile()
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

    /**
     * Three cards in one group as an L: two in a row, the third under the left one. The empty
     * corner is under the right card.
     */
    private fun lShapedGroup(): Pair<BoardState, String> {
        val state = BoardState(Settings(config), host)
        state.createBoard(home, "L")
        val root = state.root!!
        state.importExternal(listOf("a.jpg", "b.jpg", "c.jpg").map { File(root, it).apply { createNewFile() } })
        state.setLayout(BoardLayouts.FREE)
        val ids = state.board!!.items.map { it.id }
        // Placed in a row centred on the origin; move the third card under the first.
        state.clearSelection(); state.clickItem(ids[2], ctrl = false, shift = false)
        val first = state.item(ids[0])!!.pos!!
        val third = state.item(ids[2])!!.pos!!
        state.nudgeSelection(first.x - third.x, BoardState.BASE_SIZE * 1.2f) // close enough that the padded boxes touch
        state.commitLayout()
        state.selectAll()
        val group = state.groupSelection("Ecke")!!
        state.clearSelection()
        rule.setContent {
            BoardCanvas(state, ThumbCache(config), textured = false, modifier = Modifier.fillMaxSize())
        }
        rule.waitForIdle()
        val canvas = rule.onNodeWithTag("canvas").fetchSemanticsNode().size
        state.fitAll(canvas.width.toFloat(), canvas.height.toFloat())
        rule.waitForIdle()
        return state to group
    }

    private fun BoardState.toScreen(bx: Float, by: Float): Offset {
        val canvas = rule.onNodeWithTag("canvas").fetchSemanticsNode().size
        return Offset((bx - camX) * zoom + canvas.width / 2f, (by - camY) * zoom + canvas.height / 2f)
    }

    private fun click(at: Offset) {
        rule.onNodeWithTag("canvas").performMouseInput {
            moveTo(at)
            press()
            release()
        }
        rule.waitForIdle()
    }

    @Test
    fun aClickInTheEmptyCornerOfAnLDoesNotSelectTheGroup() {
        val (state, group) = lShapedGroup()
        val hull = state.groupHulls.single()
        assertTrue(hull.connectors.isEmpty(), "an L is one piece: its boxes touch")
        // The empty corner: the bottom-right of the bounding box, well inside it.
        val notch = state.toScreen(hull.right - 60f, hull.bottom - 60f)
        val ids = state.board!!.items.map { it.id }
        val third = state.item(ids[2])!!.pos!!
        assertTrue(third.x < hull.right - 200f, "the third card really is on the left, leaving the corner empty")

        click(notch)

        assertTrue(state.selection.size <= 1, "a click in the notch is not a click on the group: ${state.selection.size}")
        assertTrue(group !in state.selection)
    }

    /** Two cards in one group, pulled well apart. */
    private fun separatedPair(): Pair<BoardState, String> {
        val state = BoardState(Settings(config), host)
        state.createBoard(home, "Apart")
        val root = state.root!!
        state.importExternal(listOf("a.jpg", "b.jpg").map { File(root, it).apply { createNewFile() } })
        state.setLayout(BoardLayouts.FREE)
        val ids = state.board!!.items.map { it.id }
        state.clearSelection(); state.clickItem(ids[1], ctrl = false, shift = false)
        state.nudgeSelection(BoardState.BASE_SIZE * 2.5f, BoardState.BASE_SIZE * 0.4f) // a clear gap, slightly askew
        state.commitLayout()
        state.selectAll()
        val group = state.groupSelection("Paar")!!
        state.clearSelection()
        rule.setContent {
            BoardCanvas(state, ThumbCache(config), textured = false, modifier = Modifier.fillMaxSize())
        }
        rule.waitForIdle()
        val canvas = rule.onNodeWithTag("canvas").fetchSemanticsNode().size
        state.fitAll(canvas.width.toFloat(), canvas.height.toFloat())
        rule.waitForIdle()
        return state to group
    }

    @Test
    fun theSpaceBetweenTwoSeparatedCardsBelongsToTheGroup() {
        val (state, _) = separatedPair()
        val hull = state.groupHulls.single()
        assertEquals(1, hull.connectors.size, "two pieces, one band between them")
        val ids = state.board!!.items.map { it.id }
        val a = state.item(ids[0])!!.pos!!
        val b = state.item(ids[1])!!.pos!!
        // Halfway between the two cards: on neither card, but inside the band that joins them.
        val between = state.toScreen((a.x + b.x) / 2f, (a.y + b.y) / 2f)

        click(between)

        assertEquals(2, state.selection.size, "a click between the pictures picks the group up — the frame never thins to a line there")
    }

    @Test
    fun aClickOnTheFrameItselfSelectsTheGroup() {
        val (state, _) = lShapedGroup()
        val hull = state.groupHulls.single()
        // Just inside the frame's top-left corner: on the padding band, off any card.
        val onFrame = state.toScreen(hull.left + 8f, hull.top + 8f)

        click(onFrame)

        assertEquals(3, state.selection.size, "the frame picks the whole group up")
    }
}
