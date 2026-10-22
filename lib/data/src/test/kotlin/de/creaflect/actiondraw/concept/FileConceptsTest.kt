package de.creaflect.actiondraw.concept

import de.creaflect.actiondraw.FileLib
import de.creaflect.actiondraw.HeldRun
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The concepts over the files: found by id wherever they live, written off the caller's thread. */
class FileConceptsTest {
    private val home: File = Files.createTempDirectory("file-concepts").toFile()
    private val elsewhere: File = Files.createTempDirectory("file-concepts-else").toFile()

    @AfterTest
    fun cleanup() {
        home.deleteRecursively()
        elsewhere.deleteRecursively()
    }

    @Test
    fun aConceptIsFoundByIdAfterItsFolderMovedHomes() {
        val concepts = FileLib(home).concepts
        val dir = assertIs<ConceptCreation.Created>(concepts.create(Concept(id = "c1", name = "Drache"))).dir
        val moved = File(elsewhere, "Drache")
        assertTrue(dir.renameTo(moved))
        ConceptRegistry(File(home, ".actiondraw")).repath(dir, moved)

        assertEquals(moved.absolutePath, FileLib(home).concepts.byId("c1")?.path, "the registry knows where it went")
    }

    @Test
    fun aSaveLeavesTheCallersThreadAndAReadWaitsForTheNewest() {
        HeldRun().use { held ->
            val concepts = FileLib(home, held.run).concepts
            val dir = File(home, "Drache").apply { mkdirs() }
            concepts.save(dir, Concept(id = "c1", name = "first"))
            concepts.save(dir, Concept(id = "c1", name = "second"))
            assertFalse(File(dir, ConceptStore.FILE_NAME).exists(), "nothing written yet")

            held.releaseSoon()
            assertEquals("second", concepts.peek(dir)?.name, "a read waits for the write, and the newest won")
        }
    }

    @Test
    fun documentsStayInsideTheirConcept() {
        val concepts = FileLib(home).concepts
        val dir = assertIs<ConceptCreation.Created>(concepts.create(Concept(id = "c1", name = "Drache"))).dir
        val outside = File(dir.parentFile, "outside.md").apply { writeText("mine") }

        val path = assertNotNull(concepts.writeDocument(dir, null, "Anatomy: wings", "# Anatomy"))
        assertEquals("${ConceptStore.DOCS_DIR}/Anatomy_ wings.md", path, "named after its title, safely")
        assertEquals("# Anatomy", concepts.readDocument(dir, path))

        assertNull(concepts.writeDocument(dir, "../outside.md", "x", "overwritten"), "a path out of the concept is refused")
        concepts.deleteDocument(dir, "../outside.md")
        assertEquals("mine", outside.readText(), "and nothing outside it is touched")

        concepts.deleteDocument(dir, path)
        assertNull(concepts.readDocument(dir, path))
    }
}
