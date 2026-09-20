package de.creaflect.actiondraw.board

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.onRoot
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
import kotlin.test.assertTrue

/**
 * A `[text](url)` inside a note must open when clicked — through the real card, because whether
 * a click reaches the link or is swallowed by the card's own selection handling is wiring, and
 * wiring is what no unit test sees.
 */
@OptIn(ExperimentalTestApi::class)
class NoteLinkTest {
    @get:Rule
    val rule = createComposeRule()

    private val home: File = Files.createTempDirectory("note-link").toFile()
    private val config: File = Files.createTempDirectory("note-cfg").toFile()
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

    /** A free-mode board with one note whose whole first line is a link. */
    private fun boardWithALinkNote(): Pair<BoardState, MutableList<String>> {
        val state = BoardState(Settings(config), host)
        state.createBoard(home, "Notes")
        // The note first, then the layout: switching to Free is what places the cards.
        state.saveNote(null, "[wing references](https://example.com/wings)\nmore text below")
        state.setLayout(BoardLayouts.FREE)
        val opened = mutableListOf<String>()
        state.linkOpener = { opened += it }
        return state to opened
    }

    /**
     * Shows the canvas with the camera on the note, and returns where the note's text really is.
     * A lone card is placed well off to the left, so without fitting the camera first its text
     * clips to a zero rectangle — measured, after the group-label episode taught the lesson.
     */
    private fun showAndLocate(state: BoardState, noteId: String): androidx.compose.ui.geometry.Rect {
        rule.setContent {
            BoardCanvas(state, ThumbCache(config), textured = false, modifier = Modifier.fillMaxSize())
            BoardDialogs(state)
        }
        rule.waitForIdle()
        val canvas = rule.onNodeWithTag("canvas").fetchSemanticsNode().size
        state.fitAll(canvas.width.toFloat(), canvas.height.toFloat())
        rule.waitForIdle()
        return rule.onNodeWithTag("note-$noteId", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    }

    @Test
    fun clickingALinkInsideANoteOpensIt() {
        val (state, opened) = boardWithALinkNote()
        val note = state.board!!.items.single()
        val strip = showAndLocate(state, note.id)

        // A document note shows its title on the board; the text, links included, is in the
        // popup a click on the strip opens.
        rule.onNodeWithTag("canvas").performMouseInput { moveTo(strip.center); press(); release() }
        rule.waitForIdle()
        assertTrue(state.editor is BoardEditor.ShowNote, "the strip opens the note: ${state.editor}")
        assertEquals(emptyList(), opened, "opening the note is not opening its link")

        // The link is the first thing on the popup's first line; click just inside its top-left.
        val popup = rule.onNodeWithTag("note-popup", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        rule.onRoot().performMouseInput {
            moveTo(Offset(popup.left + 14f, popup.top + 8f))
            press()
            release()
        }
        rule.waitForIdle()

        assertEquals(listOf("https://example.com/wings"), opened)
    }

    @Test
    fun clickingTheNoteAwayFromTheLinkOpensNothing() {
        val (state, opened) = boardWithALinkNote()
        val note = state.board!!.items.single()
        val bounds = showAndLocate(state, note.id)

        rule.onNodeWithTag("canvas").performMouseInput {
            moveTo(Offset(bounds.right - 8f, bounds.bottom - 8f))
            press()
            release()
        }
        rule.waitForIdle()

        assertEquals(emptyList(), opened, "a click on the strip opens the note to read, never a link")
    }
}
