package de.creaflect.actiondraw.io

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FoldersTest {
    private val tmp: File = Files.createTempDirectory("folders").toFile()

    @AfterTest
    fun cleanup() {
        tmp.deleteRecursively()
    }

    @Test
    fun aTakenFolderNameGetsANumberAndKeepsItsDots() {
        File(tmp, "v1.2").mkdirs()
        File(tmp, "v1.2 (2)").mkdirs()
        assertEquals(File(tmp, "v1.2 (3)"), Folders.freeFolder(File(tmp, "v1.2")))
        assertEquals(File(tmp, "free"), Folders.freeFolder(File(tmp, "free")))
    }

    @Test
    fun aTakenFileNameGetsANumberBeforeItsExtension() {
        File(tmp, "foo.jpg").writeText("")
        assertEquals(File(tmp, "foo (2).jpg"), Folders.freeFile(File(tmp, "foo.jpg")))
        File(tmp, "README").writeText("")
        assertEquals(File(tmp, "README (2)"), Folders.freeFile(File(tmp, "README")))
    }

    @Test
    fun aMovedFolderTakesEverythingAlong() {
        val from = File(tmp, "board").apply { mkdirs() }
        File(from, "sub").mkdirs()
        File(from, "sub/a.png").writeText("a")
        val to = File(tmp, "elsewhere/board").apply { parentFile.mkdirs() }

        assertEquals(Folders.Move.MOVED, Folders.move(from, to))
        assertFalse(from.exists())
        assertEquals("a", File(to, "sub/a.png").readText())
    }

    @Test
    fun aFolderIsNeverMovedIntoItself() {
        val from = File(tmp, "board").apply { mkdirs() }
        assertEquals(Folders.Move.INTO_ITSELF, Folders.move(from, File(from, "inner")))
        assertEquals(Folders.Move.INTO_ITSELF, Folders.move(from, from))
        assertTrue(from.isDirectory)
        assertFalse(File(from, "inner").exists())
    }

    @Test
    fun aTakenDestinationIsLeftAlone() {
        val from = File(tmp, "board").apply { mkdirs() }
        val to = File(tmp, "taken").apply { mkdirs() }
        File(to, "theirs.txt").writeText("keep me")

        assertEquals(Folders.Move.FAILED, Folders.move(from, to))
        assertTrue(from.isDirectory)
        assertEquals("keep me", File(to, "theirs.txt").readText())
    }

    @Test
    fun aDriveRootAndTheHomeAreProtected() {
        val dirs = AppDirs(userHome = File(tmp, "home").apply { mkdirs() })
        assertTrue(Folders.isProtected(File.listRoots().first(), dirs), "a drive root")
        assertTrue(Folders.isProtected(dirs.userHome, dirs), "the home")
        assertTrue(Folders.isProtected(File(dirs.userHome, "board/.."), dirs), "the home, reached a roundabout way")
        assertFalse(Folders.isProtected(File(dirs.userHome, "ActionDraw Boards"), dirs), "a folder inside it")
    }

    @Test
    fun deleteTreeRefusesTheHome() {
        // The home here is a temp folder: if the guard ever breaks, this is all it can take.
        val dirs = AppDirs(userHome = File(tmp, "home").apply { mkdirs() })
        File(dirs.userHome, "keep.txt").writeText("x")

        assertFalse(Folders.deleteTree(dirs.userHome, dirs))
        assertTrue(File(dirs.userHome, "keep.txt").exists())

        val board = File(dirs.userHome, "board").apply { mkdirs() }
        File(board, "a.png").writeText("a")
        assertTrue(Folders.deleteTree(board, dirs))
        assertFalse(board.exists())
    }
}
