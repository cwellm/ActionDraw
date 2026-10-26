package de.creaflect.actiondraw.board

import de.creaflect.actiondraw.FileLib
import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.SessionSetup
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
 * Deleting a board is the only destructive thing the app does, so the safe reading is the
 * default: removing a board leaves every picture untouched, and erasing the folder is a separate,
 * guarded step.
 */
class DeleteBoardTest {
    private val home: File = Files.createTempDirectory("delete-home").toFile()
    private val config: File = Files.createTempDirectory("delete-cfg").toFile()

    /** What the files say, read the way the app reads them. */
    private val files = FileLib(config)

    private val host = object : BoardHost {
        override fun startSession(root: File, images: List<File>, setup: SessionSetup?) = Unit
        override fun showBoard() = Unit
        override fun showBoardList() = Unit
        override fun leaveBoard() = Unit
        override fun currentSetup() = SessionSetup(null, 120, true, ViewMode.NONE, GridMode.OFF)
    }

    @AfterTest
    fun cleanup() {
        home.deleteRecursively()
        config.deleteRecursively()
    }

    private fun boardWithAPicture(name: String = "Doomed"): Pair<BoardState, File> {
        val state = BoardState(FileLib(config), host)
        state.createBoard(home, name)
        val root = state.root!!
        val picture = File(root, "a.jpg").apply { writeText("pretend picture") }
        state.importExternal(listOf(picture))
        return state to root
    }

    @Test
    fun forgettingABoardKeepsEveryPicture() {
        val (state, root) = boardWithAPicture()

        val message = state.deleteBoard(root, Deletion.FORGET)

        assertFalse(files.boards.isBoard(root), "the board file is gone")
        assertTrue(root.isDirectory, "but the folder stays")
        assertTrue(File(root, "a.jpg").isFile, "and so does the picture")
        assertTrue(message.contains("still in"), "the message says the pictures are safe: $message")
        assertTrue(state.available().none { it.dir.absolutePath == root.absolutePath })
    }

    @Test
    fun deletingTheFolderRemovesEverything() {
        val (state, root) = boardWithAPicture()

        state.deleteBoard(root, Deletion.DELETE_FOLDER)

        assertFalse(root.exists(), "the folder and its pictures are gone")
    }

    @Test
    fun deletingTheOpenBoardClosesIt() {
        val (state, root) = boardWithAPicture()
        assertTrue(state.isOpen)

        state.deleteBoard(root, Deletion.FORGET)

        assertNull(state.board, "the board on screen cannot outlive its file")
        assertTrue(state.selection.isEmpty())
    }

    @Test
    fun aFolderThatIsNotABoardIsNeverErased() {
        val state = BoardState(FileLib(config), host)
        val ordinary = File(home, "just my pictures").apply { mkdirs() }
        File(ordinary, "holiday.jpg").writeText("not ActionDraw's to delete")

        val message = state.deleteBoard(ordinary, Deletion.DELETE_FOLDER)

        assertTrue(ordinary.isDirectory, "an ordinary folder must survive")
        assertTrue(File(ordinary, "holiday.jpg").isFile)
        assertTrue(message.startsWith("Refusing"), "and it says so: $message")
    }

    @Test
    fun theBoardsHomeItselfIsNeverErased() {
        val (state, _) = boardWithAPicture()
        // Make the home look like a board, so only the guard stands between it and deletion.
        files.boards.saveNow(home, Board(name = "Home"))

        val message = state.deleteBoard(home, Deletion.DELETE_FOLDER)

        assertTrue(home.isDirectory, "the folder holding every board must survive")
        assertTrue(message.startsWith("Refusing"))
    }

    @Test
    fun deletingAVanishedBoardSaysSoInsteadOfFailing() {
        val (state, root) = boardWithAPicture()
        root.deleteRecursively()
        assertEquals("That folder is gone already.", state.deleteBoard(root, Deletion.FORGET))
    }

    // ---- Reported 2026-09-05: a deleted board's folder blocked its own name ----

    @Test
    fun aDeletedBoardsNameCanBeUsedAgain() {
        val (state, root) = boardWithAPicture("Test")
        state.deleteBoard(root, Deletion.FORGET)

        val error = state.createBoard(home, "Test")

        assertNull(error, "the name is free again: $error")
        assertTrue(state.isOpen, "and the new board opened")
    }
}
