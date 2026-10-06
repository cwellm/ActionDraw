package de.creaflect.actiondraw.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.SessionSetup
import de.creaflect.actiondraw.Settings
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.board.ui.BoardCanvas
import de.creaflect.actiondraw.image.ThumbCache
import de.creaflect.actiondraw.ui.Atelier
import de.creaflect.actiondraw.ui.AtelierTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The board re-dressed (F9.7): what holds a card to the board says what it is — a pin through a
 * print, tape over a sketch that can be continued — and a selected card's rotate handle takes the
 * pin's place rather than sitting on top of it.
 */
class BoardDressTest {
    @get:Rule
    val rule = createComposeRule()

    private val home: File = Files.createTempDirectory("dress-home").toFile()
    private val config: File = Files.createTempDirectory("dress-cfg").toFile()
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

    /** A print and a sketch (a picture with its `.sketch.json` beside it), laid out free. */
    private fun board(): Pair<BoardState, List<String>> {
        val state = BoardState(Settings(config), host)
        state.createBoard(home, "Dress")
        val root = state.root!!
        val files = listOf("print.jpg", "sketch.png").map { File(root, it).apply { createNewFile() } }
        File(root, "sketch.sketch.json").writeText("{}")
        state.importExternal(files)
        state.setLayout(BoardLayouts.FREE)
        state.clearSelection()
        val items = state.board!!.items.filterIsInstance<ImageItem>()
        return state to listOf(items.single { it.path == "print.jpg" }.id, items.single { it.path == "sketch.png" }.id)
    }

    private fun show(state: BoardState) {
        // On the table, as in the room: with nothing behind it, the test window keeps the last
        // frame's pixels wherever nothing draws, and a pin taken out would seem to linger.
        rule.setContent {
            AtelierTheme {
                Box(Modifier.fillMaxSize().background(Atelier.Ground)) {
                    BoardCanvas(state, ThumbCache(config), textured = false, modifier = Modifier.fillMaxSize())
                }
            }
        }
        rule.waitForIdle()
        val canvas = rule.onNodeWithTag("canvas").fetchSemanticsNode().size
        state.fitAll(canvas.width.toFloat(), canvas.height.toFloat())
        rule.waitForIdle()
    }

    /** What sits at the middle of the card's top edge: pin-blue pixels and tape pixels. */
    private fun topOf(id: String): Pair<Int, Int> {
        val map = rule.onNodeWithTag("card-$id").captureToImage().toPixelMap()
        val band = (14 * rule.density.density).toInt()
        var pin = 0
        var tape = 0
        for (x in (map.width / 2 - band * 2).coerceAtLeast(0) until (map.width / 2 + band * 2).coerceAtMost(map.width)) {
            for (y in 0 until band.coerceAtMost(map.height)) {
                val c = map[x, y]
                // Ultramarine's body (red under 0.4 of its blue, the shaded side too), not its
                // highlight and not the selection's lighter blue, which is nearer 0.6.
                if (c.blue > 0.35f && c.red < 0.4f * c.blue) pin++
                // Paper you can see through: warmer and yellower than the print's border.
                if (c.red > 0.85f && c.blue < 0.82f && c.red - c.blue > 0.1f) tape++
            }
        }
        return pin to tape
    }

    @Test
    fun aPrintIsPinnedAndASketchIsTaped() {
        val (state, ids) = board()
        show(state)
        val (printPin, printTape) = topOf(ids[0])
        val (sketchPin, sketchTape) = topOf(ids[1])
        assertTrue(printPin > 10, "a pin through the print: $printPin pixels")
        assertEquals(0, printTape, "no tape on a print")
        assertTrue(sketchTape > 40, "tape over the sketch: $sketchTape pixels")
        assertEquals(0, sketchPin, "no pin through a sketch")
    }

    @Test
    fun aSelectedCardShowsItsHandleWhereThePinWas() {
        val (state, ids) = board()
        show(state)
        assertTrue(topOf(ids[0]).first > 10, "pinned while it rests")
        state.clickItem(ids[0], ctrl = false, shift = false)
        rule.waitForIdle()
        assertEquals(0, topOf(ids[0]).first, "the rotate handle, not a pin under it")
        // With two selected there are no handles, so both are held again.
        state.clickItem(ids[1], ctrl = true, shift = false)
        rule.waitForIdle()
        assertTrue(topOf(ids[0]).first > 10, "pinned again beside another selected card")
    }
}
