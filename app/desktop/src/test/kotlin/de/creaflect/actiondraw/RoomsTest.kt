package de.creaflect.actiondraw

import de.creaflect.actiondraw.board.ImageItem
import de.creaflect.actiondraw.platform.FileDialogs
import de.creaflect.actiondraw.platform.HostPreferences
import de.creaflect.actiondraw.platform.SystemClipboard
import de.creaflect.actiondraw.practice.PracticePage
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The rooms put together: every question one room asks another reaches the room that answers it.
 * The rooms know nothing of each other; [Rooms] is the one place that does, and these are its
 * routes, one by one.
 */
class RoomsTest {
    private val root: File = Files.createTempDirectory("rooms").toFile()
    private val boardsHome = File(root, "boards").apply { mkdirs() }

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
    }

    private fun rooms() = Rooms(FileLib(File(root, "home"))).also {
        it.boards.setBoardsHomeDir(boardsHome)
        it.concepts.setConceptsHomeDir(File(root, "concepts"))
    }

    /** A board with two pictures on it, open. */
    private fun Rooms.boardWithPictures(): File {
        boards.createBoard(boardsHome, "Wings")
        val dir = boards.root!!
        boards.importExternal(listOf("a.jpg", "b.jpg").map { File(dir, it).apply { createNewFile() } })
        return dir
    }

    @Test
    fun aBoardsSessionRunsInThePracticeRoomsWindow() {
        val rooms = rooms()
        rooms.boardWithPictures()
        rooms.boards.drawBoard()
        assertEquals(PracticePage.Session, rooms.practice.windowPage, "the session's own window")
        assertEquals(2, rooms.practice.totalCount, "with the board's pictures")
    }

    @Test
    fun aBoardRemembersWhatThePracticeRoomIsSetTo() {
        val rooms = rooms()
        rooms.boardWithPictures()
        rooms.practice.intervalSeconds = 45
        rooms.boards.rememberCurrentSetup()
        assertEquals(45, rooms.boards.recipe?.intervalSeconds)
    }

    @Test
    fun theBoardsAndAConceptAnswerEachOther() {
        val rooms = rooms()
        rooms.concepts.createConcept("Drache", "creature")
        val id = rooms.concepts.concept!!.id
        rooms.concepts.closeConcept()
        val board = rooms.boardWithPictures()

        assertTrue(rooms.boards.linkConcept(id), "the board reads the concept")
        assertEquals(listOf(board.absolutePath), rooms.concepts.boardsFor(id).filter { it.linked }.map { it.dir.absolutePath }, "the concept asks the boards which link it")

        rooms.boards.showConcept(id)
        assertEquals(Screen.Concept, rooms.app.screen, "and a board opens it in its own room")
        assertEquals(id, rooms.concepts.concept?.id)
    }

    @Test
    fun theSketchIsSavedIntoABoard() {
        val rooms = rooms()
        val board = rooms.boardWithPictures()
        assertTrue(rooms.sketch.boards().any { it.second.absolutePath == board.absolutePath }, "Live Sketch is told which boards there are")
        rooms.sketch.newSketch()
        rooms.sketch.saveToBoard(board)
        assertTrue(rooms.boards.board!!.items.filterIsInstance<ImageItem>().any { it.path.endsWith(".png") }, "and the board takes the picture")
    }

    @Test
    fun thePracticeRoomDrawsFromABoard() {
        val rooms = rooms()
        val board = rooms.boardWithPictures()
        val boards = rooms.practice.practiceBoards!!
        assertEquals(listOf(board.absolutePath), boards.boards().map { it.second.absolutePath })
        assertEquals(2, boards.pictures(board).size)
    }

    @Test
    fun aSketchFromASessionWaitsForItAndReturnsToIt() {
        val rooms = rooms()
        rooms.app.showPractice()
        val picture = File(root, "pose.png").apply { createNewFile() }
        rooms.sketchFromSession(picture)
        assertEquals(Screen.Sketch, rooms.app.screen)
        assertTrue(rooms.practice.isPaused, "the session waits, paused")
        rooms.app.sketchHome()
        assertEquals(Screen.Practice, rooms.app.screen, "and home from the sketch is the session")
    }

    @Test
    fun theHostsServicesReachEveryRoom() {
        val opened = mutableListOf<String>()
        val copied = mutableListOf<String>()
        val clipboard = object : SystemClipboard by SystemClipboard.None {
            override fun copyText(text: String) {
                copied += text
            }
        }
        val dialogs = object : FileDialogs by FileDialogs.None {}
        val host = HostServices(clipboard, { url -> opened += url; true }, dialogs, HostPreferences { true })
        val rooms = Rooms(FileLib(File(root, "home")), host)

        assertTrue(rooms.app.reducedMotion, "the system's preference, until the person chooses")
        assertTrue(listOf(rooms.practice.dialogs, rooms.boards.dialogs, rooms.concepts.dialogs, rooms.sketch.dialogs).all { it === dialogs }, "the host's dialogs, in every room")

        rooms.boards.setBoardsHomeDir(boardsHome)
        rooms.boards.createBoard(boardsHome, "Notes")
        rooms.boards.saveLink(null, "https://example.com/a", "A")
        rooms.boards.openUrl("https://example.com/a")
        rooms.concepts.openUrl("https://example.com/b")
        assertEquals(listOf("https://example.com/a", "https://example.com/b"), opened, "the host's browser, for the board and the concept")

        rooms.boards.saveNote(null, "wing study")
        rooms.boards.selectAll()
        rooms.boards.copySelection()
        assertEquals(listOf("wing study"), copied, "and the host's clipboard")
    }
}
