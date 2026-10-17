package de.creaflect.actiondraw.io

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SafeFileTest {
    private val dir: File = Files.createTempDirectory("safefile").toFile()
    private val safe = SafeFile(File(dir, "sidecar.json"))

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    @Test
    fun aWriteKeepsThePreviousVersionAsTheBackup() {
        assertTrue(safe.write("one"))
        assertFalse(safe.backup.exists(), "a first write has nothing to back up")
        assertTrue(safe.write("two"))
        assertEquals("two", safe.file.readText())
        assertEquals("one", safe.backup.readText())
        assertFalse(File(dir, "sidecar.json.tmp").exists(), "the temp file is renamed, not left behind")
    }

    @Test
    fun aFileThatDoesNotDecodeFallsBackToTheBackup() {
        safe.write("1")
        safe.write("2")
        safe.file.writeText("not a number")
        val loaded = safe.load { it.toInt() }
        assertEquals(SafeFile.Loaded(1, fromBackup = true), loaded)
    }

    @Test
    fun theFileItselfComesFirst() {
        safe.write("1")
        safe.write("2")
        assertEquals(SafeFile.Loaded(2, fromBackup = false), safe.load { it.toInt() })
    }

    @Test
    fun nothingReadableIsNothing() {
        assertNull(safe.load { it.toInt() }, "no file at all")
        safe.write("x")
        safe.write("y")
        assertNull(safe.load { it.toInt() }, "neither the file nor its backup decodes")
    }

    @Test
    fun aByteOrderMarkIsNotPartOfTheText() {
        safe.file.writeText("\uFEFF42")
        assertEquals(42, safe.load { it.toInt() }?.value)
    }

    @Test
    fun deleteTakesTheBackupAlong() {
        safe.write("1")
        safe.write("2")
        assertTrue(safe.delete())
        assertFalse(safe.file.exists())
        assertFalse(safe.backup.exists())
        assertFalse(safe.delete(), "nothing left to delete")
    }
}
