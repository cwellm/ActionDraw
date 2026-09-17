package de.creaflect.actiondraw.board

import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.SessionSetup
import de.creaflect.actiondraw.Settings
import de.creaflect.actiondraw.ViewMode
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Boards inside boards. Nesting is stored nowhere: a sub-board is a board whose folder lies
 * inside another board's folder, which the registry's recorded paths already say.
 */
class SubBoardTest {
    private val home: File = Files.createTempDirectory("sub-home").toFile()
    private val config: File = Files.createTempDirectory("sub-cfg").toFile()

    private val host = object : BoardHost {
        override fun startSession(root: File, images: List<File>, setup: SessionSetup?) = Unit
        override fun showBoard() = Unit
        override fun showBoardList() = Unit
        override fun leaveBoard() = Unit
        override fun currentSetup() = SessionSetup(null, 120, true, ViewMode.NONE, GridMode.OFF)
    }

    private fun newState() = BoardState(Settings(config), host).also { it.setBoardsHomeDir(home) }

    @AfterTest
    fun cleanup() {
        home.deleteRecursively()
        config.deleteRecursively()
    }

    /** Drachenbuch, with Flügel inside it. */
    private fun nested(): Triple<BoardState, File, File> {
        val state = newState()
        state.createBoard(home, "Drachenbuch")
        val parent = state.root!!
        state.createBoard(parent, "Flügel")
        val child = state.root!!
        return Triple(state, parent, child)
    }

    @Test
    fun aBoardMadeInsideAnotherIsItsSubBoard() {
        val (state, parent, child) = nested()

        assertTrue(child.absolutePath.startsWith(parent.absolutePath), "it lives in there: $child")
        assertEquals("Drachenbuch", state.parentBoard?.name, "and knows whose it is")

        state.openBoard(parent)
        assertEquals(listOf("Flügel"), state.subBoards.map { it.name })
        assertNull(state.parentBoard, "the top of a tree has nobody above it")
    }

    @Test
    fun makingASubBoardDoesNotMoveWhereNewBoardsGo() {
        val (state, parent, _) = nested()
        assertEquals(
            home.absolutePath,
            state.boardsHome().absolutePath,
            "nesting a board is not a decision about where top-level boards live",
        )
        assertTrue(parent.parentFile.samePath(home))
    }

    @Test
    fun theTreeListsChildrenUnderTheirParent() {
        val (state, parent, _) = nested()
        state.createBoard(parent, "Köpfe")
        state.createBoard(home, "Anatomie")

        val tree = state.boardTree()

        assertEquals(
            listOf("Anatomie" to 0, "Drachenbuch" to 0, "Flügel" to 1, "Köpfe" to 1),
            tree.map { it.name to it.depth },
            "depth first, alphabetically within a level",
        )
        assertEquals("Drachenbuch", tree.first { it.name == "Flügel" }.parent)
        assertNull(tree.first { it.name == "Anatomie" }.parent)
    }

    @Test
    fun nestingGoesDeeperThanOneLevelAndPicksTheNearestParent() {
        val (state, parent, child) = nested()
        state.createBoard(child, "Membran")

        assertEquals("Flügel", state.parentBoard?.name, "the nearest board above, not the outermost")
        state.openBoard(parent)
        assertEquals(listOf("Flügel"), state.subBoards.map { it.name }, "only the direct children")
        assertEquals(2, state.subBoardCount(parent), "but everything below counts as nested")
    }

    @Test
    fun aBoardMerelySharingAPrefixIsNotInsideAnother() {
        val state = newState()
        state.createBoard(home, "Drachen")
        state.createBoard(home, "Drachen2")

        assertNull(state.parentBoard, "Drachen2 sits next to Drachen, not in it")
        assertEquals(listOf(0, 0), state.boardTree().map { it.depth })
    }

    @Test
    fun deletingAParentsFolderForgetsTheBoardsThatWentWithIt() {
        val (state, parent, child) = nested()
        assertEquals(1, state.subBoardCount(parent))

        state.deleteBoard(parent, BoardState.Deletion.DELETE_FOLDER)

        assertFalse(child.exists(), "the nested folder went with its parent")
        val fresh = newState()
        assertNull(fresh.entryFor(child), "and its record went too, rather than lingering")
        assertTrue(fresh.availableBoards().isEmpty())
    }

    @Test
    fun removingOnlyTheBoardFileLeavesTheSubBoardAlone() {
        val (state, parent, child) = nested()

        state.deleteBoard(parent, BoardState.Deletion.FORGET)

        assertTrue(child.isDirectory, "the sub-board's folder is untouched")
        assertTrue(BoardStore.exists(child), "and it is still a board")
        val fresh = newState()
        assertEquals(listOf("Flügel"), fresh.boardTree().map { it.name })
        assertEquals(0, fresh.boardTree().single().depth, "now a root, its parent being gone")
    }

    private fun File.samePath(other: File) = absolutePath.equals(other.absolutePath, ignoreCase = true)
}
