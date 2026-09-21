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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What a press on the canvas reaches must be what is drawn there. A card or a frame is positioned
 * by a layer transform; if its hit box stayed where it was laid out — the canvas' top-left — a
 * press on something drawn there would be swallowed by a box that answers nothing, and the
 * canvas underneath would pan instead: every card on the board moving with the one the user
 * meant to drag.
 */
@OptIn(ExperimentalTestApi::class)
class CanvasHitTest {
    @get:Rule
    val rule = createComposeRule()

    private val home: File = Files.createTempDirectory("canvas-hit").toFile()
    private val config: File = Files.createTempDirectory("canvas-hit-cfg").toFile()
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

    private fun board(): BoardState {
        val state = BoardState(Settings(config), host)
        state.createBoard(home, "Hit")
        state.setLayout(BoardLayouts.FREE)
        return state
    }

    private fun BoardState.addCards(vararg names: String): List<String> {
        val root = root!!
        importExternal(names.map { File(root, it).apply { createNewFile() } })
        return board!!.items.takeLast(names.size).map { it.id }
    }

    private fun BoardState.place(id: String, x: Float, y: Float) {
        val pos = item(id)!!.pos!!
        clearSelection()
        clickItem(id, ctrl = false, shift = false)
        nudgeSelection(x - pos.x, y - pos.y)
        commitLayout()
        clearSelection()
    }

    private fun BoardState.group(name: String, vararg ids: String): String {
        clearSelection()
        ids.forEach { clickItem(it, ctrl = true, shift = false) }
        val group = groupSelection(name)!!
        clearSelection()
        return group
    }

    private fun show(state: BoardState) {
        rule.setContent {
            BoardCanvas(state, ThumbCache(config), textured = false, modifier = Modifier.fillMaxSize())
        }
        rule.waitForIdle()
    }

    private fun BoardState.toScreen(bx: Float, by: Float): Offset {
        val canvas = rule.onNodeWithTag("canvas").fetchSemanticsNode().size
        return Offset((bx - camX) * zoom + canvas.width / 2f, (by - camY) * zoom + canvas.height / 2f)
    }

    private fun BoardState.toBoard(at: Offset): Pair<Float, Float> {
        val canvas = rule.onNodeWithTag("canvas").fetchSemanticsNode().size
        return ((at.x - canvas.width / 2f) / zoom + camX) to ((at.y - canvas.height / 2f) / zoom + camY)
    }

    private fun drag(from: Offset, by: Offset) {
        rule.onNodeWithTag("canvas").performMouseInput {
            moveTo(from)
            press()
            moveBy(by / 2f)
            moveBy(by / 2f)
            release()
        }
        rule.waitForIdle()
    }

    private fun click(at: Offset) {
        rule.onNodeWithTag("canvas").performMouseInput {
            moveTo(at)
            press()
            release()
        }
        rule.waitForIdle()
    }

    /**
     * Group "Oben" is drawn in the canvas' top-left; group "Breit", made later, is wide and
     * drawn elsewhere. Dragging Oben's frame must move Oben — not pan the board.
     */
    @Test
    fun aFrameDrawnWhereALaterFrameWasLaidOutIsStillTheOnesThatIsDragged() {
        val state = board()
        val (a1, a2, b1, b2, b3) = state.addCards("a1.jpg", "a2.jpg", "b1.jpg", "b2.jpg", "b3.jpg")
        show(state)
        val canvas = rule.onNodeWithTag("canvas").fetchSemanticsNode().size
        // Oben: two cards, drawn a little in from the top-left corner.
        val (ox, oy) = state.toBoard(Offset(200f, 160f))
        state.place(a1, ox, oy)
        state.place(a2, ox + BoardState.BASE_SIZE * 0.9f, oy)
        // Breit: three cards in a wide row, drawn around the centre and lower.
        val (bx, by) = state.toBoard(Offset(canvas.width / 2f, canvas.height * 0.75f))
        state.place(b1, bx - BoardState.BASE_SIZE * 1.4f, by)
        state.place(b2, bx, by)
        state.place(b3, bx + BoardState.BASE_SIZE * 1.4f, by)
        val oben = state.group("Oben", a1, a2)
        state.group("Breit", b1, b2, b3)
        rule.waitForIdle()

        // A point in Oben's frame just under its second card: on the frame, not on a card.
        val second = state.item(a2)!!.pos!!
        val onFrame = state.toScreen(second.x, second.y + BoardState.BASE_SIZE / 2f + 8f)
        val (fx, fy) = state.toBoard(onFrame)
        assertEquals(oben, state.groupAt(fx, fy)?.id, "the press point is on Oben's frame")
        val cam = state.camX to state.camY
        val before = state.item(b1)!!.pos!!

        drag(onFrame, Offset(90f, 60f))

        assertEquals(cam, state.camX to state.camY, "the board did not pan")
        // Compose keeps the touch-slop distance (a few px) of every drag, so the group ends a
        // little short of the pointer's full travel — but well past halfway, in the right direction.
        val moved = state.item(a2)!!.pos!!
        assertEquals(second.x + 90f, moved.x, 20f)
        assertEquals(second.y + 60f, moved.y, 20f)
        assertEquals(before, state.item(b1)!!.pos, "Breit stayed where it was")
    }

    /**
     * Group "Unten" sits in the empty corner of a later, L-shaped group's bounding box: inside
     * that rectangle, outside that shape. A press on Unten's frame must reach Unten — the L's
     * box, which has nothing to say there, must not swallow it and leave the canvas to pan.
     */
    @Test
    fun aFrameInsideALaterFramesBoxButOutsideItsShapeIsStillTheOneThatIsDragged() {
        val state = board()
        val (u, l1, l2, l3) = state.addCards("u.jpg", "l1.jpg", "l2.jpg", "l3.jpg")
        show(state)
        val canvas = rule.onNodeWithTag("canvas").fetchSemanticsNode().size
        val (cx, cy) = state.toBoard(Offset(canvas.width / 2f, canvas.height / 2f))
        val step = BoardState.BASE_SIZE * 1.35f
        // The L: a corner card, one to its right, one below it. The notch is bottom-right.
        state.place(l1, cx - step / 2, cy - step / 2)
        state.place(l2, cx + step / 2, cy - step / 2)
        state.place(l3, cx - step / 2, cy + step / 2)
        // Unten: one card in the notch, made first so the L is the later, topmost frame.
        state.place(u, cx + step / 2, cy + step / 2)
        val unten = state.group("Unten", u)
        val ell = state.group("Ell", l1, l2, l3)
        rule.waitForIdle()

        val lHull = state.groupHulls.first { it.group.id == ell }
        val card = state.item(u)!!.pos!!
        // Just under Unten's card: on Unten's frame, inside the L's bounding box, off the L's shape.
        val fx = card.x
        val fy = card.y + BoardState.BASE_SIZE / 2f + 8f
        assertTrue(fx < lHull.right && fy < lHull.bottom, "the point is inside the L's bounding box")
        assertEquals(unten, state.groupAt(fx, fy)?.id, "and on Unten's frame, not the L's")
        val onFrame = state.toScreen(fx, fy)
        val cam = state.camX to state.camY
        val lBefore = state.item(l1)!!.pos!!

        drag(onFrame, Offset(80f, 50f))

        assertEquals(cam, state.camX to state.camY, "the board did not pan")
        val moved = state.item(u)!!.pos!!
        assertEquals(card.x + 80f, moved.x, 20f)
        assertEquals(card.y + 50f, moved.y, 20f)
        assertEquals(lBefore, state.item(l1)!!.pos, "the L stayed where it was")
    }

    /** A card drawn in the top-left, with later cards elsewhere, still answers a click. */
    @Test
    fun aCardDrawnWhereALaterCardWasLaidOutIsStillClickable() {
        val state = board()
        val (first, later) = state.addCards("first.jpg", "later.jpg")
        show(state)
        val canvas = rule.onNodeWithTag("canvas").fetchSemanticsNode().size
        val (fx, fy) = state.toBoard(Offset(150f, 150f))
        state.place(first, fx, fy)
        val (lx, ly) = state.toBoard(Offset(canvas.width * 0.7f, canvas.height * 0.7f))
        state.place(later, lx, ly)
        rule.waitForIdle()

        click(state.toScreen(fx, fy))

        assertNotNull(state.item(first))
        assertEquals(setOf(first), state.selection, "the click reached the card that is drawn there")
    }

    private operator fun <T> List<T>.component5(): T = this[4]
}
