package de.creaflect.actiondraw.board

import de.creaflect.actiondraw.FileLib
import de.creaflect.actiondraw.HeldRun
import de.creaflect.actiondraw.lifecycle.LibRun
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The boards over the files, as the app gets them: a save leaves the caller's thread, and nothing
 * that reads, deletes or moves a board can overtake a write still on its way.
 */
class FileBoardsTest {
    private val home: File = Files.createTempDirectory("file-boards").toFile()

    @AfterTest
    fun cleanup() {
        home.deleteRecursively()
    }

    private fun sidecar(dir: File) = File(dir, BoardStore.FILE_NAME)

    private fun folder(name: String) = File(home, name).apply { mkdirs() }

    @Test
    fun aSaveLeavesTheCallersThreadAndAReadWaitsForTheNewest() {
        HeldRun().use { held ->
            val boards = FileLib(home, held.run).boards
            val dir = folder("Drachen")
            boards.save(dir, Board(name = "first"))
            boards.save(dir, Board(name = "second"))
            assertFalse(sidecar(dir).exists(), "nothing written yet: the caller did not wait for the disk")

            held.releaseSoon()
            assertEquals("second", boards.peek(dir)?.name, "a read waits for the write, and the newest won")
            assertFalse(File(dir, BoardStore.FILE_NAME + ".bak").exists(), "one write for the burst, not one each")
        }
    }

    @Test
    fun deletingABoardWaitsForWhatIsStillToBeWrittenIntoIt() {
        HeldRun().use { held ->
            val boards = FileLib(home, held.run).boards
            val dir = folder("Drachen")
            assertTrue(boards.saveNow(dir, Board(name = "Drachen")))
            boards.save(dir, Board(name = "edited"))

            held.releaseSoon()
            assertEquals(BoardDeletion.FORGOTTEN, boards.delete(dir, Deletion.FORGET))
            held.run.close()
            assertFalse(sidecar(dir).exists(), "the edit landed before the board file went, not after it")
        }
    }

    @Test
    fun movingABoardTakesWhatIsStillToBeWrittenAlong() {
        HeldRun().use { held ->
            val boards = FileLib(home, held.run).boards
            val dir = folder("Drachen")
            assertTrue(boards.saveNow(dir, Board(name = "Drachen")))
            // A sub-board inside the one that moves, with an edit still on its way. Only the
            // sub-board's: the moving board's own would be waited for anyway, by name.
            val inner = File(dir, "Wings").apply { mkdirs() }
            assertTrue(boards.saveNow(inner, Board(name = "Wings")))
            val into = folder("Elsewhere")
            boards.save(inner, Board(name = "edited"))

            held.releaseSoon()
            val moved = assertIs<BoardMove.Moved>(boards.move(dir, into))
            held.run.close()
            assertEquals("edited", boards.peek(File(moved.to, "Wings"))?.name, "the sub-board's edit went with it")
            assertFalse(dir.exists(), "and nothing was written back into the place it left")
        }
    }

    @Test
    fun closingTheRunWritesWhatIsStillPending() {
        val held = HeldRun()
        val boards = FileLib(home, held.run).boards
        val dir = folder("Drachen")
        boards.save(dir, Board(name = "last edit"))

        held.releaseSoon()
        assertTrue(held.run.close(5.seconds))
        assertEquals("last edit", BoardStore.peek(dir)?.name, "closing waited for it")
        held.close()
    }

    @Test
    fun aWriteThatFailsIsReportedNotSwallowed() {
        val run = LibRun()
        val errors = CopyOnWriteArrayList<Throwable>()
        run.writes.onError = { errors += it }
        val notAFolder = File(home, "notes.txt").apply { writeText("x") }

        FileLib(home, run).boards.save(notAFolder, Board(name = "x"))
        run.close()
        assertEquals(1, errors.size, "the board that could not be written is told of")
    }

    @Test
    fun theInlineRunWritesAtOnce() {
        val dir = folder("Drachen")
        FileLib(home).boards.save(dir, Board(name = "now"))
        assertEquals("now", BoardStore.peek(dir)?.name, "a test's lib writes on the caller's thread")
    }

    @Test
    fun deletingAWallpaperTouchesOnlyTheBoardsOwnCopies() {
        val boards = FileLib(home).boards
        val dir = folder("Wall")
        val precious = File(home, "precious.jpg").apply { writeText("mine") }
        val picture = File(home, "paper.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        val copy = assertNotNull(boards.copyWallpaper(dir, picture))
        assertEquals("${BoardStore.WALLPAPER_DIR}/paper.png", copy)
        boards.deleteWallpaper(dir, "../precious.jpg")
        boards.deleteWallpaper(dir, "${BoardStore.WALLPAPER_DIR}/../../precious.jpg")
        assertTrue(precious.isFile, "a path out of the board's wallpaper folder is refused")
        boards.deleteWallpaper(dir, copy)
        assertFalse(File(dir, copy).exists(), "its own copy goes")
    }
}
