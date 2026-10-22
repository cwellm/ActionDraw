package de.creaflect.actiondraw.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.FileLib
import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.SessionSetup
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.board.paletteCaption
import de.creaflect.actiondraw.board.paletteColours
import de.creaflect.actiondraw.board.ui.BoardCanvas
import de.creaflect.actiondraw.board.ui.notePaper
import de.creaflect.actiondraw.concept.ConceptHost
import de.creaflect.actiondraw.concept.ConceptState
import de.creaflect.actiondraw.ui.Atelier
import de.creaflect.actiondraw.ui.AtelierTheme
import de.creaflect.actiondraw.ui.Notes
import de.creaflect.actiondraw.ui.ProvideAtelier
import de.creaflect.actiondraw.ui.paperTooth
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
        val state = BoardState(FileLib(config), host)
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
                Box(Modifier.fillMaxSize().background(Atelier.colors.ground)) {
                    BoardCanvas(state, FileLib(config).pictures, textured = false, modifier = Modifier.fillMaxSize())
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

    @Test
    fun notesAreSlipsAndSheetsOnEverySurface() {
        val postit = NoteItem(id = "a", text = "line of action", kind = NoteKind.POSTIT)
        val sheet = NoteItem(id = "b", text = "# Wings", kind = NoteKind.DOCUMENT)
        assertEquals(Notes.backing, notePaper(postit, Atelier.colors.paper), "a post-it is a yellow slip")
        assertEquals(Atelier.colors.paper, notePaper(sheet, Atelier.colors.paper), "a document is a sheet of paper")
        assertEquals(Color(0xFFC5E1A5), notePaper(postit.copy(color = "#C5E1A5"), Atelier.colors.paper), "a colour chosen is that paper, whole")
    }

    @Test
    fun paperHasItsToothAndTheTableHasNone() {
        rule.setContent {
            ProvideAtelier {
                Row {
                    Box(Modifier.size(80.dp).background(Atelier.colors.paper).paperTooth().testTag("paper"))
                    Box(Modifier.size(80.dp).background(Atelier.colors.paper).testTag("flat"))
                }
            }
        }
        rule.waitForIdle()
        fun shades(tag: String): Int {
            val map = rule.onNodeWithTag(tag).captureToImage().toPixelMap()
            return (0 until map.width).flatMap { x -> (0 until map.height).map { y -> map[x, y] } }.toSet().size
        }
        assertEquals(1, shades("flat"))
        assertTrue(shades("paper") > 20, "the grain shows in the paper: ${shades("paper")} shades")
    }

    @Test
    fun aBorrowedCardWearsItsConceptsTab() {
        val conceptsHome = Files.createTempDirectory("dress-concepts").toFile()
        val concepts = ConceptState(
            FileLib(config),
            object : ConceptHost {
                override fun showConcepts() = Unit
                override fun showConcept() = Unit
                override fun leaveConcepts() = Unit
            },
        ).also { it.setConceptsHomeDir(conceptsHome) }
        concepts.createConcept("Drache", "creature")
        concepts.addPictures(listOf(File(conceptsHome, "wing.jpg").apply { createNewFile() }))
        val state = BoardState(FileLib(config), host, concepts = concepts)
        state.createBoard(home, "Dress")
        state.importExternal(listOf(File(state.root!!, "own.jpg").apply { createNewFile() }))
        assertTrue(state.linkConcept(concepts.concept!!.id))
        state.setLayout(BoardLayouts.FREE)
        val (borrowed, own) = state.board!!.items.partition { state.isBorrowed(it) }
        show(state)
        rule.onNodeWithTag("concept-tab-" + borrowed.single().id).assertExists()
        rule.onNodeWithTag("concept-tab-" + own.single().id).assertDoesNotExist()
        conceptsHome.deleteRecursively()
    }

    @Test
    fun aPaletteIsPinnedAsACardBesideItsPicture() {
        val (state, ids) = board()
        val picture = state.board!!.items.single { it.id == ids[0] } as ImageItem
        state.pinPalette(picture, listOf(0xC0A080, 0x405060, 0x202020))
        val card = state.board!!.items.filterIsInstance<NoteItem>().single()
        assertEquals(NoteKind.PALETTE, card.kind)
        assertEquals(listOf(0xC0A080, 0x405060, 0x202020), card.paletteColours())
        assertEquals("colours of print", card.paletteCaption())
        assertEquals(setOf(card.id), state.selection, "selected, to be moved straight on")
        show(state)
        rule.onNodeWithTag("palette-" + card.id).assertExists()
    }

    @Test
    fun cardsAreLaidDownALittleAskewAndAlwaysTheSameWay() {
        val (state, _) = board()
        state.board!!.items.forEach { item ->
            assertEquals(CardLayout.naturalTilt(item.id), item.pos!!.rotation, "${item.id} lies as its own tilt says")
        }
        val tilts = (1..40).map { CardLayout.naturalTilt("card-$it") }
        assertTrue(tilts.all { it in -1.81f..1.81f }, "a degree or two at most: $tilts")
        assertTrue(tilts.toSet().size > 3, "and not all the same")
    }
}
