package de.creaflect.actiondraw

import de.creaflect.actiondraw.image.SeenStore
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeenStoreTest {
    private val dir: File = Files.createTempDirectory("actiondraw-seen").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    @Test
    fun readWriteClearRoundTrip() {
        assertTrue(SeenStore.read(dir).isEmpty())

        SeenStore.write(dir, setOf("a.jpg", "b.png"))
        assertEquals(setOf("a.jpg", "b.png"), SeenStore.read(dir))

        SeenStore.clear(dir)
        assertTrue(SeenStore.read(dir).isEmpty())
    }
}
