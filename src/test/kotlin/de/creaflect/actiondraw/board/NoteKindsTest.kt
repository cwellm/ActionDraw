package de.creaflect.actiondraw.board

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.SessionSetup
import de.creaflect.actiondraw.Settings
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.board.ui.BoardCanvas
import de.creaflect.actiondraw.board.ui.BoardDialogs
import de.creaflect.actiondraw.image.ThumbCache
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Two kinds of note, links that open on a click, and a drop that files a card only when it is
 * let go on a group's cards — the remarks of 2026-09-22.
 */
@OptIn(ExperimentalTestApi::class)
class NoteKindsTest {
    @get:Rule
    val rule = createComposeRule()

    private val home: File = Files.createTempDirectory("kinds-home").toFile()
    private val config: File = Files.createTempDirectory("kinds-cfg").toFile()
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

    private fun board(): BoardState = BoardState(Settings(config), host).also { it.createBoard(home, "Kinds") }

    // ---- What a document note shows ----

    @Test
    fun theTitleIsTheFirstHeadingElseTheFirstLine() {
        assertEquals("Flügel", NoteItem("n", "some intro\n# Flügel\nmore").title, "a heading anywhere wins")
        assertEquals("membrane folds", NoteItem("n", "membrane folds\nsecond line").title, "else the first line")
        assertEquals("bat wings", NoteItem("n", "**bat** *wings*").title, "markers stripped")
        assertEquals("ref", NoteItem("n", "[ref](https://a.b)").title, "a link reads as its text")
        assertEquals("Note", NoteItem("n", "   \n").title, "nothing to show: a plain word")
    }

    @Test
    fun existingNotesAreDocumentsAndKeepTheirTextUntouched() {
        val state = board()
        state.saveNote(null, "# Title\nbody")
        val note = state.board!!.items.single() as NoteItem
        assertEquals(NoteKind.DOCUMENT, note.kind, "the default, so old boards read as before")
        assertEquals("# Title\nbody", note.text)
    }

    // ---- Shapes on the canvas ----

    @Test
    fun aPostItIsAsTallAsItsText() {
        val short = NoteItem("a", "one line", kind = NoteKind.POSTIT)
        val long = NoteItem("b", (1..8).joinToString("\n") { "line $it of a longer thought" }, kind = NoteKind.POSTIT)
        assertTrue(long.estimatedAspect() < short.estimatedAspect(), "more text, taller card, smaller width/height")
        assertTrue(short.estimatedAspect() > 1f, "a one-liner is wider than tall")
    }

    @Test
    fun linksAndDocumentNotesAreStripsPicturesKeepTheirOwnShape() {
        val state = board()
        state.saveLink(null, "https://example.com", "Example")
        state.saveNote(null, "# Doc\nbody")
        val link = state.board!!.items.filterIsInstance<LinkItem>().single()
        val note = state.board!!.items.filterIsInstance<NoteItem>().single()
        assertEquals(BoardState.STRIP_ASPECT, state.aspectOf(link))
        assertEquals(BoardState.STRIP_ASPECT, state.aspectOf(note))
        assertEquals(1.5f, state.aspectOf(ImageItem("i", "p.jpg", aspect = 1.5f)))
    }

    // ---- Dropping files only on the group's cards ----

    /** Two grouped pictures a card apart, and a loose note. */
    private fun groupWithAGap(): Triple<BoardState, List<String>, String> {
        val state = board()
        val root = state.root!!
        state.importExternal(listOf("a.jpg", "b.jpg").map { File(root, it).apply { createNewFile() } })
        state.saveNote(null, "loose thought")
        state.setLayout(BoardLayouts.FREE)
        val ids = state.board!!.items.map { it.id }
        state.clearSelection(); ids.take(2).forEach { state.clickItem(it, ctrl = true, shift = false) }
        val group = state.groupSelection("Flügel")!!
        state.clearSelection(); state.clickItem(ids[1], ctrl = false, shift = false)
        state.nudgeSelection(BoardState.BASE_SIZE * 2.5f, 0f) // apart: the frame bands the gap
        state.commitLayout()
        state.clearSelection()
        return Triple(state, ids, group)
    }

    @Test
    fun aNoteLetGoInTheGapBetweenTwoPicturesIsNotFiled() {
        val (state, ids, _) = groupWithAGap()
        val a = state.item(ids[0])!!.pos!!
        val b = state.item(ids[1])!!.pos!!
        state.clearSelection(); state.clickItem(ids[2], ctrl = false, shift = false)
        val note = state.item(ids[2])!!.pos!!
        state.nudgeSelection((a.x + b.x) / 2f - note.x, a.y - note.y) // into the band, on no card

        assertNull(state.dropIntoGroupAt(ids[2]), "near the group is not on the group")
        assertTrue(state.item(ids[2])!!.groups.isEmpty(), "still loose — and so it will not move with the group")
    }

    @Test
    fun aNoteLetGoOnAPictureOfTheGroupIsFiledAndSaidSo() {
        val (state, ids, group) = groupWithAGap()
        val a = state.item(ids[0])!!.pos!!
        state.clearSelection(); state.clickItem(ids[2], ctrl = false, shift = false)
        val note = state.item(ids[2])!!.pos!!
        state.nudgeSelection(a.x - note.x + 20f, a.y - note.y + 20f) // onto the first picture

        assertEquals("Flügel", state.dropIntoGroupAt(ids[2])?.name)
        assertEquals(listOf(group), state.item(ids[2])!!.groups)
        assertTrue(state.importNotice?.contains("now in Flügel") == true, "never silent: ${state.importNotice}")
    }

    @Test
    fun whileDraggingTheTargetGroupIsKnownAndClearedAfterwards() {
        val (state, ids, group) = groupWithAGap()
        val a = state.item(ids[0])!!.pos!!
        state.clearSelection(); state.clickItem(ids[2], ctrl = false, shift = false)
        val note = state.item(ids[2])!!.pos!!
        state.nudgeSelection(a.x - note.x, a.y - note.y)
        state.trackDropTarget(ids[2])
        assertEquals(group, state.dropTargetGroup, "over a picture of the group: it would join")

        state.dropIntoGroupAt(ids[2])
        assertNull(state.dropTargetGroup, "cleared once let go")
    }

    // ---- On the real canvas ----

    private fun shown(state: BoardState) {
        rule.setContent {
            BoardCanvas(state, ThumbCache(config), textured = false, modifier = Modifier.fillMaxSize())
            BoardDialogs(state)
        }
        rule.waitForIdle()
        val canvas = rule.onNodeWithTag("canvas").fetchSemanticsNode().size
        state.fitAll(canvas.width.toFloat(), canvas.height.toFloat())
        rule.waitForIdle()
    }

    @Test
    fun aSingleClickOnALinkOpensIt() {
        val state = board()
        state.saveLink(null, "https://example.com/wings", "Wings")
        state.setLayout(BoardLayouts.FREE)
        val opened = mutableListOf<String>()
        state.linkOpener = { opened += it }
        shown(state)
        val link = state.board!!.items.single()

        val bounds = rule.onNodeWithTag("link-" + link.id, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("canvas").performMouseInput { moveTo(bounds.center); press(); release() }
        rule.waitForIdle()

        assertEquals(listOf("https://example.com/wings"), opened)
    }

    @Test
    fun aClickOnADocumentNoteOpensItToRead() {
        val state = board()
        state.saveNote(null, "# Flügel\nmembrane folds, ¾ view")
        state.setLayout(BoardLayouts.FREE)
        shown(state)
        val note = state.board!!.items.single()

        rule.onNodeWithTag("note-" + note.id, useUnmergedTree = true).performClick()
        rule.waitForIdle()

        assertIs<BoardEditor.ShowNote>(state.editor)
        rule.onNodeWithTag("note-popup", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aPostItShowsItsTextAsTyped() {
        val state = board()
        state.saveNote(null, "**not bold** just words", kind = NoteKind.POSTIT)
        state.setLayout(BoardLayouts.FREE)
        shown(state)
        val note = state.board!!.items.single()

        rule.onNodeWithTag("postit-" + note.id, useUnmergedTree = true)
            .assertTextEquals("**not bold** just words") // markers and all
    }
}
