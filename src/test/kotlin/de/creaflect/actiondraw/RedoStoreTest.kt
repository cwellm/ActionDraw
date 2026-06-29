package de.creaflect.actiondraw

import de.creaflect.actiondraw.image.RedoStore
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedoStoreTest {
    private val dir: File = Files.createTempDirectory("actiondraw-redo").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    @Test
    fun readWriteClearRoundTrip() {
        assertTrue(RedoStore.read(dir).isEmpty())

        RedoStore.write(dir, setOf("a.jpg", "b.png"))
        assertEquals(setOf("a.jpg", "b.png"), RedoStore.read(dir))

        RedoStore.clear(dir)
        assertTrue(RedoStore.read(dir).isEmpty())
    }

    @Test
    fun writingEmptySetRemovesTheFile() {
        RedoStore.write(dir, setOf("x.jpg"))
        RedoStore.write(dir, emptySet())
        assertTrue(RedoStore.read(dir).isEmpty())
        assertFalse(File(dir, RedoStore.FILE_NAME).exists())
    }
}
