package de.creaflect.actiondraw

import de.creaflect.actiondraw.board.ImageItem
import de.creaflect.actiondraw.handoff.Handed
import de.creaflect.actiondraw.handoff.Route
import de.creaflect.actiondraw.lens.LensPicture
import de.creaflect.actiondraw.platform.FileDialogs
import de.creaflect.actiondraw.platform.HostPreferences
import de.creaflect.actiondraw.platform.SystemClipboard
import de.creaflect.actiondraw.practice.PracticePage
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
        assertTrue(rooms.sketch.boards().any { it.dir.absolutePath == board.absolutePath }, "Live Sketch is told which boards there are")
        rooms.sketch.newSketch()
        rooms.sketch.saveToBoard(board)
        assertTrue(rooms.boards.board!!.items.filterIsInstance<ImageItem>().any { it.path.endsWith(".png") }, "and the board takes the picture")
    }

    @Test
    fun thePracticeRoomDrawsFromABoard() {
        val rooms = rooms()
        val board = rooms.boardWithPictures()
        val listed = rooms.practice.boards().single()
        assertEquals(board.absolutePath, listed.dir.absolutePath, "the boards are the board room's")
        rooms.practice.selectBoard(listed)
        assertEquals(2, rooms.practice.totalCount, "and so are the pictures on one")
    }

    @Test
    fun aSketchFromASessionWaitsForItAndReturnsToIt() {
        val rooms = rooms()
        val poses = File(root, "poses").apply { mkdirs() }
        File(poses, "pose.png").createNewFile()
        rooms.practice.selectFolder(poses)
        rooms.practice.start()
        rooms.app.showPractice()
        rooms.practice.handOff(Room.SKETCH)
        assertEquals(Screen.Sketch, rooms.app.screen)
        assertEquals(File(poses, "pose.png"), rooms.sketch.reference, "the picture on screen beside the page")
        assertTrue(rooms.practice.isPaused, "the session waits, paused")
        rooms.app.home(Screen.Sketch)
        assertEquals(Screen.Practice, rooms.app.screen, "and home from the sketch is the session")
    }

    @Test
    fun theSeamKnowsWhichRoomTakesWhat() {
        val rooms = rooms()
        val board = rooms.boardWithPictures()
        val photo = File(board, "a.jpg")
        assertEquals(
            listOf(Route(Room.SKETCH, "Sketch over"), Route(Room.COLLAGE, "To a collage"), Route(Room.LENS, "Check the values")),
            rooms.handoff.routes(Handed.Picture(photo), Room.BOARDS),
            "a picture: drawn over, cut apart, looked at",
        )
        assertEquals(
            listOf(Route(Room.SKETCH, "Sketch over"), Route(Room.COLLAGE, "To a collage")),
            rooms.handoff.routes(Handed.Picture(photo), Room.LENS),
            "never into the room it comes from",
        )
        assertEquals(
            listOf(Route(Room.COLLAGE, "To a collage"), Route(Room.LENS, "Check the values")),
            rooms.handoff.routes(Handed.Drawn("page") { png() }, Room.SKETCH),
            "a page that is no file: cut apart, looked at",
        )
        File(board, "a.sketch.json").writeText("{}")
        assertEquals("Continue sketch", rooms.handoff.routes(Handed.Picture(photo), Room.BOARDS).first().label, "a sketched picture is gone on with")
    }

    @Test
    fun aPictureGoesFromTheLensRoomIntoSketchAndBackRetracesTheWay() {
        val rooms = rooms()
        val board = rooms.boardWithPictures()
        rooms.app.showBoard()
        val card = rooms.boards.board!!.items.filterIsInstance<ImageItem>().first()
        rooms.boards.handOff(card, Room.LENS)
        rooms.lens.handOff(Room.SKETCH)
        assertEquals(Screen.Sketch, rooms.app.screen)
        assertEquals(rooms.boards.fileOf(card), rooms.sketch.reference)
        rooms.sketch.leave()
        assertEquals(Screen.Lens, rooms.app.screen, "Back: the Lens room it came from")
        rooms.lens.leave()
        assertEquals(Screen.Board, rooms.app.screen, "and the board before it")
        assertTrue(board.isDirectory)
    }

    /** A small picture, as PNG bytes. */
    private fun png(): ByteArray =
        ByteArrayOutputStream().also { ImageIO.write(BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()

    @Test
    fun aCardIsCheckedInTheLensRoomAndBackIsTheBoard() {
        val rooms = rooms()
        rooms.boardWithPictures()
        rooms.app.showBoard()
        val card = rooms.boards.board!!.items.filterIsInstance<ImageItem>().first()
        rooms.boards.handOff(card, Room.LENS)
        assertEquals(Screen.Lens, rooms.app.screen)
        assertEquals(rooms.boards.fileOf(card), rooms.lens.file, "the card's picture")
        rooms.lens.leave()
        assertEquals(Screen.Board, rooms.app.screen, "and Back is the board it came from")
    }

    @Test
    fun aSketchsPageIsCheckedInTheLensRoomAndBackIsTheSketch() {
        val rooms = rooms()
        rooms.app.enter(Screen.Sketch)
        rooms.sketch.newSketch()
        rooms.sketch.handOff(Room.LENS)
        assertEquals(Screen.Lens, rooms.app.screen)
        assertIs<LensPicture.Pasted>(rooms.lens.picture, "the page as it is now")
        rooms.lens.leave()
        assertEquals(Screen.Sketch, rooms.app.screen)
    }

    @Test
    fun aCardIsLaidOnTheCollageAndBackIsTheBoard() {
        val rooms = rooms()
        rooms.boards.createBoard(boardsHome, "Wings")
        val dir = rooms.boards.root!!
        rooms.boards.importExternal(listOf(File(root, "cliff.png").apply { writeBytes(png()) }))
        rooms.app.showBoard()
        val card = rooms.boards.board!!.items.filterIsInstance<ImageItem>().single()
        rooms.boards.handOff(card, Room.COLLAGE)
        assertEquals(Screen.Collage, rooms.app.screen)
        val piece = rooms.collage.collage.pieces.single()
        assertEquals(rooms.boards.fileOf(card)?.absoluteFile, piece.source, "cut from the card's picture, where it is on the board")
        assertTrue(rooms.collage.file!!.isFile, "the collage kept")
        rooms.collage.leave()
        assertEquals(Screen.Board, rooms.app.screen, "and Back is the board it came from")
        assertTrue(dir.isDirectory)
    }

    @Test
    fun aSketchsPageIsLaidOnTheCollageAndBackIsTheSketch() {
        val rooms = rooms()
        rooms.app.enter(Screen.Sketch)
        rooms.sketch.newSketch()
        rooms.sketch.handOff(Room.COLLAGE)
        assertEquals(Screen.Collage, rooms.app.screen)
        val piece = rooms.collage.collage.pieces.single()
        assertTrue(piece.source.isFile, "the page as it is now, written beside the collage")
        rooms.collage.leave()
        assertEquals(Screen.Sketch, rooms.app.screen)
    }

    @Test
    fun theLensRoomPinsItsViewToABoard() {
        val rooms = rooms()
        val board = rooms.boardWithPictures()
        assertEquals(listOf(board.absolutePath), rooms.lens.boards().map { it.dir.absolutePath }, "it is told which boards there are")
        rooms.lens.open(File(board, "a.jpg"))
        rooms.lens.pinTo(board)
        rooms.lens.finish(png())
        assertTrue(rooms.boards.board!!.items.filterIsInstance<ImageItem>().any { it.path.endsWith("a - seen.png") }, "and the board takes it")
    }

    @Test
    fun theHostsServicesReachEveryRoom() {
        val opened = mutableListOf<String>()
        val copied = mutableListOf<String>()
        val clipboard = object : SystemClipboard by SystemClipboard.None {
            override fun copyText(text: String) {
                copied += text
            }
            override fun paste() = SystemClipboard.Pasted.Bitmap(png())
        }
        val dialogs = object : FileDialogs by FileDialogs.None {}
        val host = HostServices(clipboard, { url -> opened += url; true }, dialogs, HostPreferences { true })
        val rooms = Rooms(FileLib(File(root, "home")), host)

        assertTrue(rooms.app.reducedMotion, "the system's preference, until the person chooses")
        assertTrue(
            listOf(rooms.practice.dialogs, rooms.boards.dialogs, rooms.concepts.dialogs, rooms.sketch.dialogs, rooms.lens.dialogs, rooms.collage.dialogs).all { it === dialogs },
            "the host's dialogs, in every room",
        )
        assertTrue(rooms.lens.paste(), "the Lens room looks at the host's clipboard")
        assertTrue(rooms.collage.paste(), "and so does the Collage room")

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
