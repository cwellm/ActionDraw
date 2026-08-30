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
        val items = Importer.importFiles(root, listOf(inner), groupId = "g1", existingPaths = emptySet())
        assertEquals(listOf("wings/a.jpg"), items.map { it.path })
        assertEquals(listOf("g1"), items.single().groups)
        assertFalse(File(root, Importer.IMPORT_DIR).exists(), "in-root files must not be copied")
    }

    @Test
    fun externalFilesAreCopiedIntoImported() {
        val src = File(outside, "b.jpg").apply { writeText("data") }
        val items = Importer.importFiles(root, listOf(src), groupId = null, existingPaths = emptySet())
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

        val items = Importer.importFiles(root, listOf(src), null, emptySet())
        assertEquals(listOf("${Importer.IMPORT_DIR}/b (3).jpg"), items.map { it.path })
    }

    @Test
    fun directoriesAreExpandedAndNonImagesSkipped() {
        File(outside, "sub").mkdirs()
        File(outside, "sub/c.png").createNewFile()
        File(outside, "sub/notes.txt").createNewFile()
        val items = Importer.importFiles(root, listOf(outside), null, emptySet())
        assertEquals(listOf("${Importer.IMPORT_DIR}/c.png"), items.map { it.path })
    }

    @Test
    fun pathsAlreadyOnTheBoardAreSkipped() {
        val inner = File(root, "a.jpg").apply { createNewFile() }
        val items = Importer.importFiles(root, listOf(inner), null, existingPaths = setOf("a.jpg"))
        assertTrue(items.isEmpty())
    }
}
