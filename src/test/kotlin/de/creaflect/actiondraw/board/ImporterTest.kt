package de.creaflect.actiondraw.board

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImporterTest {
    private val root: File = Files.createTempDirectory("actiondraw-import-root").toFile()
    private val outside: File = Files.createTempDirectory("actiondraw-import-src").toFile()

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
        outside.deleteRecursively()
    }

    @Test
    fun filesInsideTheBoardFolderAreReferencedInPlace() {
        val inner = File(root, "wings").apply { mkdirs() }.let { File(it, "a.jpg").apply { createNewFile() } }
        val items = Importer.importFiles(root, listOf(inner), groupId = "g1", existingPaths = emptySet()).items
        assertEquals(listOf("wings/a.jpg"), items.map { it.path })
        assertEquals(listOf("g1"), items.single().groups)
        assertFalse(File(root, Importer.IMPORT_DIR).exists(), "in-root files must not be copied")
    }

    @Test
    fun externalFilesAreCopiedIntoImported() {
        val src = File(outside, "b.jpg").apply { writeText("data") }
        val items = Importer.importFiles(root, listOf(src), groupId = null, existingPaths = emptySet()).items
        assertEquals(listOf("${Importer.IMPORT_DIR}/b.jpg"), items.map { it.path })
        assertTrue(File(root, "${Importer.IMPORT_DIR}/b.jpg").isFile)
        assertTrue(src.isFile, "the original stays untouched")
    }

    @Test
    fun nameCollisionsGetNumberedSuffixes() {
        File(root, Importer.IMPORT_DIR).mkdirs()
        File(root, "${Importer.IMPORT_DIR}/b.jpg").createNewFile()
        File(root, "${Importer.IMPORT_DIR}/b (2).jpg").createNewFile()
        val src = File(outside, "b.jpg").apply { writeText("data") }

        val items = Importer.importFiles(root, listOf(src), null, emptySet()).items
        assertEquals(listOf("${Importer.IMPORT_DIR}/b (3).jpg"), items.map { it.path })
    }

    @Test
    fun directoriesAreExpandedAndNonImagesSkipped() {
        File(outside, "sub").mkdirs()
        File(outside, "sub/c.png").createNewFile()
        File(outside, "sub/notes.txt").createNewFile()
        val items = Importer.importFiles(root, listOf(outside), null, emptySet()).items
        assertEquals(listOf("${Importer.IMPORT_DIR}/c.png"), items.map { it.path })
    }

    @Test
    fun pathsAlreadyOnTheBoardAreSkipped() {
        val inner = File(root, "a.jpg").apply { createNewFile() }
        val outcome = Importer.importFiles(root, listOf(inner), null, existingPaths = setOf("a.jpg"))
        assertTrue(outcome.items.isEmpty())
        assertEquals(1, outcome.duplicates, "a duplicate is reported, not silently dropped")
    }

    @Test
    fun droppedFilesOfAnUnreadableFormatAreReported() {
        val good = File(outside, "a.jpg").apply { writeText("x") }
        val bad = File(outside, "notes.txt").apply { writeText("x") }
        val alsoBad = File(outside, "clip.mp4").apply { writeText("x") }

        val outcome = Importer.importFiles(root, listOf(good, bad, alsoBad), null, emptySet())
        assertEquals(1, outcome.items.size)
        assertEquals(
            listOf("clip.mp4", "notes.txt"),
            outcome.unsupported.map { it.name }.sorted(),
            "everything that could not be imported comes back so the board can say so",
        )
    }
}
