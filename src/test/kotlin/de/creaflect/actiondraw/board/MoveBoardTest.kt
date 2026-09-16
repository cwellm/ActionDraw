package de.creaflect.actiondraw.board

import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.isInside
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
 * Rearranging the tree. Nesting is where a board's folder sits, so moving a board in the
 * hierarchy means moving the folder — with its pictures and with any boards inside it. There is
 * no second record of the shape of the tree, so there is nothing that can disagree with the disk.
 */
class MoveBoardTest {
    private val home: File = Files.createTempDirectory("move-home").toFile()
    private val config: File = Files.createTempDirectory("move-cfg").toFile()

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

    /** Two boards side by side at the top level, the first holding a picture. */
    private fun twoBoards(): Triple<BoardState, File, File> {
        val state = newState()
        state.createBoard(home, "Flügel")
        val moving = state.root!!
        state.importExternal(listOf(File(moving, "wing.jpg").apply { writeText("picture") }))
        state.createBoard(home, "Drachenbuch")
        val destination = state.root!!
        return Triple(state, moving, destination)
    }

    // ---- The move ----

    @Test
    fun aBoardCanBecomeASubBoardOfAnotherLaterOn() {
        val (state, moving, destination) = twoBoards()
        assertNull(state.boardTree().first { it.name == "Flügel" }.parent, "starts at the top level")

        state.moveBoard(moving, destination)

        val tree = newState().boardTree()
        assertEquals(listOf("Drachenbuch" to 0, "Flügel" to 1), tree.map { it.name to it.depth })
        assertEquals("Drachenbuch", tree.first { it.name == "Flügel" }.parent)
        assertFalse(moving.exists(), "the folder really moved")
        assertTrue(File(destination, "Flügel").isDirectory)
    }

    @Test
    fun itsPicturesComeWithIt() {
        val (state, moving, destination) = twoBoards()

        state.moveBoard(moving, destination)

        val now = File(destination, "Flügel")
        assertTrue(File(now, "wing.jpg").isFile, "the picture travelled")
        assertTrue(BoardStore.exists(now), "and the board file with it")
        val reopened = newState()
        reopened.openBoard(now)
        assertEquals(1, reopened.board!!.items.size, "the board still knows its card")
    }

    @Test
    fun aSubBoardCanBeLiftedBackOutToTheTopLevel() {
        val (state, moving, destination) = twoBoards()
        state.moveBoard(moving, destination)
        val nested = File(destination, "Flügel")

        state.moveBoard(nested, null)

        val tree = newState().boardTree()
        assertEquals(listOf(0, 0), tree.map { it.depth }, "both stand on their own again")
        assertTrue(File(home, "Flügel").isDirectory)
    }

    @Test
    fun boardsNestedInsideTheMovedOneTravelWithItAndKeepTheirRecords() {
        val (state, moving, destination) = twoBoards()
        state.createBoard(moving, "Membran")
        val inner = state.root!!
        assertTrue(inner.isInside(moving))

        state.moveBoard(moving, destination)

        val fresh = newState()
        val tree = fresh.boardTree()
        assertEquals(
            listOf("Drachenbuch" to 0, "Flügel" to 1, "Membran" to 2),
            tree.map { it.name to it.depth },
            "the whole subtree came along",
        )
        val movedInner = File(File(destination, "Flügel"), "Membran")
        assertTrue(BoardStore.exists(movedInner))
        assertEquals("Membran", fresh.entryFor(movedInner)?.name, "its record followed the folder")
        assertNull(fresh.entryFor(inner), "and no longer points where it used to be")
    }

    @Test
    fun theBoardOnScreenFollowsItsFolder() {
        val (state, moving, destination) = twoBoards()
        state.openBoard(moving)

        state.moveBoard(moving, destination)

        assertEquals(File(destination, "Flügel").absolutePath, state.root!!.absolutePath)
        assertEquals("Flügel", state.board!!.name, "still open, and still itself")
        // And it can still be written to where it now lives.
        state.setTheme(BoardThemes.ALL.last())
        assertEquals(BoardThemes.ALL.last(), BoardStore.peek(state.root!!)?.theme)
    }

    @Test
    fun anOpenSubBoardSurvivesItsParentMoving() {
        val (state, moving, destination) = twoBoards()
        state.createBoard(moving, "Membran")
        val inner = state.root!!
        state.openBoard(inner)

        state.moveBoard(moving, destination)

        assertEquals(
            File(File(destination, "Flügel"), "Membran").absolutePath,
            state.root!!.absolutePath,
            "the open board was inside the one that moved",
        )
        assertEquals("Membran", state.board!!.name)
    }

    // ---- What must not happen ----

    @Test
    fun aBoardCannotBeMovedIntoItself() {
        val (state, moving, _) = twoBoards()
        val message = state.moveBoard(moving, moving)
        assertTrue(message.contains("itself"), message)
        assertTrue(BoardStore.exists(moving), "and nothing happened to it")
    }

    @Test
    fun aBoardCannotBeMovedIntoItsOwnSubBoard() {
        val (state, moving, _) = twoBoards()
        state.createBoard(moving, "Membran")
        val inner = state.root!!

        val message = state.moveBoard(moving, inner)

        assertTrue(message.contains("sub-board"), message)
        assertTrue(BoardStore.exists(moving))
        assertTrue(BoardStore.exists(inner), "the inner board is untouched too")
    }

    @Test
    fun movingSomewhereItAlreadyIsSaysSoRatherThanShufflingFolders() {
        val (state, moving, _) = twoBoards()
        val message = state.moveBoard(moving, null)
        assertTrue(message.contains("already"), message)
        assertTrue(File(home, "Flügel").isDirectory, "left exactly where it was")
    }

    @Test
    fun aNameAlreadyTakenAtTheDestinationGetsItsOwnFolder() {
        val (state, moving, destination) = twoBoards()
        File(destination, "Flügel").mkdirs() // something already sits there
        File(File(destination, "Flügel"), "stranger.txt").writeText("not ours")

        state.moveBoard(moving, destination)

        val landed = newState().boardTree().first { it.name == "Flügel" }.dir
        assertEquals("Flügel (2)", landed.name, "beside the stranger, not merged into it")
        assertTrue(File(File(destination, "Flügel"), "stranger.txt").isFile, "which was left alone")
        assertTrue(File(landed, "wing.jpg").isFile)
    }

    // ---- What the dialog offers ----

    @Test
    fun theDestinationsLeaveOutTheBoardItselfAndEverythingInsideIt() {
        val (state, moving, destination) = twoBoards()
        state.createBoard(moving, "Membran")

        val names = state.moveTargets(moving).map { it.name }

        assertEquals(listOf("Drachenbuch"), names, "not itself, not its own sub-board")
        assertEquals(
            listOf("Flügel", "Membran"),
            state.moveTargets(destination).map { it.name },
            "the other board may go into either of them",
        )
    }
}
