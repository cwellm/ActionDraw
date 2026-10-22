package de.creaflect.actiondraw.board

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.SessionSetup
import de.creaflect.actiondraw.Settings
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.board.ui.BoardScreen
import de.creaflect.actiondraw.image.ThumbCache
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals

/**
 * Quick browse: reaching another board from inside the one you are in. Whether a menu entry
 * actually switches boards is wiring, and wiring is what unit tests of the state never see.
 */
class BoardSwitcherTest {
    @get:Rule
    val rule = createComposeRule()

    private val home: File = Files.createTempDirectory("switch-home").toFile()
    private val config: File = Files.createTempDirectory("switch-cfg").toFile()

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

    /** Drachenbuch with Flügel inside it, opened on Drachenbuch. */
    private fun nestedBoards(): Triple<BoardState, File, File> {
        val state = BoardState(Settings(config), host).also { it.setBoardsHomeDir(home) }
        state.createBoard(home, "Drachenbuch")
        val parent = state.root!!
        state.createBoard(parent, "Flügel")
        val child = state.root!!
        state.createBoard(home, "Anatomie")
        state.openBoard(parent)
        return Triple(state, parent, child)
    }

    private fun show(state: BoardState) {
        rule.setContent {
            BoardScreen(state, ThumbCache(config), isFullscreen = false, setFullscreen = {})
        }
        rule.waitForIdle()
    }

    @Test
    fun theSwitcherJumpsStraightToASubBoard() {
        val (state, _, child) = nestedBoards()
        show(state)

        rule.onNodeWithTag("board-switcher").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("switch-to-Flügel").performClick()
        rule.waitForIdle()

        assertEquals(child.absolutePath, state.root!!.absolutePath)
        assertEquals("Flügel", state.board!!.name)
    }

    @Test
    fun theSwitcherAlsoReachesABoardThatIsNotRelated() {
        val (state, _, _) = nestedBoards()
        show(state)

        rule.onNodeWithTag("board-switcher").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("switch-to-Anatomie").performClick()
        rule.waitForIdle()

        assertEquals("Anatomie", state.board!!.name, "every board is reachable, not only the nested ones")
    }

    @Test
    fun aSubBoardOffersOneTapBackUpToItsParent() {
        val (state, parent, child) = nestedBoards()
        state.openBoard(child)
        show(state)

        rule.onNodeWithTag("board-up").performClick()
        rule.waitForIdle()

        assertEquals(parent.absolutePath, state.root!!.absolutePath)
    }

    @Test
    fun aTopLevelBoardHasNoWayUp() {
        val (state, _, _) = nestedBoards()
        show(state)
        rule.onNodeWithTag("board-up").assertDoesNotExist()
    }
}
