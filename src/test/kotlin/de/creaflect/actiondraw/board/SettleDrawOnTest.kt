package de.creaflect.actiondraw.board

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.SessionSetup
import de.creaflect.actiondraw.Settings
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.board.ui.BoardCanvas
import de.creaflect.actiondraw.board.ui.WOBBLE_AMPLITUDE
import de.creaflect.actiondraw.board.ui.frameUnion
import de.creaflect.actiondraw.board.ui.handDrawn
import de.creaflect.actiondraw.board.ui.partOfOutline
import de.creaflect.actiondraw.image.ThumbCache
import de.creaflect.actiondraw.ui.Lifted
import de.creaflect.actiondraw.ui.LocalReducedMotion
import de.creaflect.actiondraw.ui.Motion
import de.creaflect.actiondraw.ui.leanFor
import org.jetbrains.skia.PathMeasure
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Motion on the board (F9.4): a card lifts, leans and lands without its drag going anywhere else,
 * and a new group's frame is drawn on once — with a hand's wobble that is part of the frame's one
 * path, so the drawn path stays the hit-tested path.
 */
@OptIn(ExperimentalTestApi::class)
class SettleDrawOnTest {
    @get:Rule
    val rule = createComposeRule()

    private val home: File = Files.createTempDirectory("settle-home").toFile()
    private val config: File = Files.createTempDirectory("settle-cfg").toFile()
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

    private fun freeBoard(): Pair<BoardState, List<String>> {
        val state = BoardState(Settings(config), host)
        state.createBoard(home, "Settle")
        val root = state.root!!
        state.importExternal(listOf("a.jpg", "b.jpg", "c.jpg").map { File(root, it).apply { createNewFile() } })
        state.setLayout(BoardLayouts.FREE)
        state.clearSelection()
        return state to state.board!!.items.map { it.id }
    }

    @Test
    fun aCardLeansIntoItsSpeedButNoFurtherThanFourDegrees() {
        assertEquals(0f, leanFor(0f))
        assertEquals(1.5f, leanFor(3f))
        assertEquals(Motion.MAX_LEAN, leanFor(400f))
        assertEquals(-Motion.MAX_LEAN, leanFor(-400f))
    }

    @Test
    fun aDraggedCardLiftsAndLandsAndStillGoesExactlyWhereThePointerTookIt() {
        val (state, ids) = freeBoard()
        rule.setContent { BoardCanvas(state, ThumbCache(config), textured = false, modifier = Modifier.fillMaxSize()) }
        rule.waitForIdle()
        val canvas = rule.onNodeWithTag("canvas").fetchSemanticsNode().size
        state.fitAll(canvas.width.toFloat(), canvas.height.toFloat())
        rule.waitForIdle()
        val id = ids[0]
        val before = state.item(id)!!.pos!!
        val from = Offset((before.x - state.camX) * state.zoom + canvas.width / 2f, (before.y - state.camY) * state.zoom + canvas.height / 2f)
        val card = rule.onNodeWithTag("card-$id")
        card.assert(SemanticsMatcher.expectValue(Lifted, false))

        // Mouse-sized steps, sideways and down: a mouse moves a few pixels at a time.
        val steps = 40
        val step = Offset(3f, 1f)
        rule.onNodeWithTag("canvas").performMouseInput {
            moveTo(from)
            press()
            repeat(steps) { moveBy(step) }
        }
        rule.waitForIdle()
        card.assert(SemanticsMatcher.expectValue(Lifted, true))
        rule.onNodeWithTag("canvas").performMouseInput { release() }
        rule.waitForIdle()
        card.assert(SemanticsMatcher.expectValue(Lifted, false))

        // The lean is drawn only: the card went where the pointer went, to the pixel. Snapping
        // to the other cards' centre lines is off by default, so nothing else moves it.
        val after = state.item(id)!!.pos!!
        assertEquals(before.x + steps * step.x / state.zoom, after.x, 1f / state.zoom, "x")
        assertEquals(before.y + steps * step.y / state.zoom, after.y, 1f / state.zoom, "y")
    }

    @Test
    fun aNewGroupsFrameIsDrawnOnceAndThenSimplyThere() {
        val (state, ids) = freeBoard()
        rule.mainClock.autoAdvance = false
        rule.setContent { BoardCanvas(state, ThumbCache(config), textured = false, modifier = Modifier.fillMaxSize()) }
        rule.waitForIdle()
        ids.take(2).forEach { state.clickItem(it, ctrl = true, shift = false) }
        val group = state.groupSelection("Wings")!!
        assertEquals(group, state.freshGroupId, "a new group is fresh")
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        assertEquals(group, state.freshGroupId, "still being drawn")
        rule.mainClock.advanceTimeBy(Motion.DRAW_ON_MS + 100L)
        rule.waitForIdle()
        assertNull(state.freshGroupId, "drawn, and from now on simply there")
    }

    @Test
    fun withReducedMotionTheFrameIsThereAtOnce() {
        val (state, ids) = freeBoard()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalReducedMotion provides true) {
                BoardCanvas(state, ThumbCache(config), textured = false, modifier = Modifier.fillMaxSize())
            }
        }
        rule.waitForIdle()
        ids.take(2).forEach { state.clickItem(it, ctrl = true, shift = false) }
        state.groupSelection("Wings")
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        assertNull(state.freshGroupId)
    }

    @Test
    fun theWobbleIsPartOfTheFrameStaysCloseAndStaysPutWhenZooming() {
        val boxes = listOf(listOf(0f, 0f, 300f, 200f), listOf(260f, 150f, 520f, 380f))
        val smooth = frameUnion(boxes, emptyList(), originX = -40f, originY = -40f, zoom = 1f)
        val wobbly = handDrawn(smooth, seed = 11, zoom = 1f)
        val a = smooth.bounds
        val b = wobbly.bounds
        val reach = WOBBLE_AMPLITUDE + 0.5f
        assertTrue(abs(a.left - b.left) <= reach && abs(a.right - b.right) <= reach && abs(a.top - b.top) <= reach && abs(a.bottom - b.bottom) <= reach, "close: $a vs $b")
        assertNotEquals(smooth.bounds, wobbly.bounds, "but not the same line")
        assertTrue(wobbly.contains(190f, 140f), "the middle is inside")
        assertEquals(wobbly.bounds, handDrawn(smooth, seed = 11, zoom = 1f).bounds, "the same group, the same wobble")

        // Zoomed in twice as far, it is the same wobble twice as large — it does not swim.
        val twice = handDrawn(frameUnion(boxes, emptyList(), originX = -40f, originY = -40f, zoom = 2f), seed = 11, zoom = 2f).bounds
        assertEquals(b.left * 2f, twice.left, 1f)
        assertEquals(b.right * 2f, twice.right, 1f)
        assertEquals(b.bottom * 2f, twice.bottom, 1f)
    }

    @Test
    fun theOutlineIsDrawnOnInPart() {
        val smooth = frameUnion(listOf(listOf(0f, 0f, 300f, 200f)), emptyList(), 0f, 0f, 1f)
        fun length(p: org.jetbrains.skia.Path): Float {
            val m = PathMeasure(p, false)
            var sum = 0f
            do sum += m.length while (m.nextContour())
            return sum
        }
        val whole = length(smooth)
        assertEquals(0f, length(partOfOutline(smooth, 0f)), 0.5f)
        assertEquals(whole / 2f, length(partOfOutline(smooth, 0.5f)), 2f)
        assertEquals(whole, length(partOfOutline(smooth, 1f)), 2f)
    }
}
