package de.creaflect.actiondraw.concept

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import de.creaflect.actiondraw.Settings
import de.creaflect.actiondraw.board.BoardLayouts
import de.creaflect.actiondraw.board.BoardLink
import de.creaflect.actiondraw.board.ImageItem
import de.creaflect.actiondraw.board.ItemPos
import de.creaflect.actiondraw.board.LinkItem
import de.creaflect.actiondraw.board.NoteItem
import de.creaflect.actiondraw.concept.ui.ConceptDialogs
import de.creaflect.actiondraw.concept.ui.ConceptListScreen
import de.creaflect.actiondraw.concept.ui.ConceptScreen
import de.creaflect.actiondraw.image.ThumbCache
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A concept is a thing that lives once: a folder of its own with a sidecar, pictures, notes,
 * links and documents, found again by id wherever its folder goes.
 */
class ConceptTest {
    @get:Rule
    val rule = createComposeRule()

    private val home: File = Files.createTempDirectory("concept-home").toFile()
    private val elsewhere: File = Files.createTempDirectory("concept-else").toFile()
    private val config: File = Files.createTempDirectory("concept-cfg").toFile()
    private var shown = 0
    private var listed = 0
    private var left = 0
    private val host = object : ConceptHost {
        override fun showConcepts() { listed++ }
        override fun showConcept() { shown++ }
        override fun leaveConcepts() { left++ }
    }

    @After
    fun cleanup() {
        home.deleteRecursively()
        elsewhere.deleteRecursively()
        config.deleteRecursively()
    }

    private fun newState() = ConceptState(Settings(config), host).also { it.setConceptsHomeDir(home) }

    // ---- A concept as a folder ----

    @Test
    fun creatingAConceptMakesAFolderASidecarAndARecord() {
        val state = newState()
        assertNull(state.createConcept("Drache", "creature"))

        val dir = state.root!!
        assertTrue(dir.isDirectory && dir.parentFile.absolutePath == home.absolutePath, "in the concepts home")
        assertTrue(ConceptStore.exists(dir))
        val entry = state.entryFor(dir)!!
        assertEquals("Drache", entry.name)
        assertEquals("creature", entry.kind)
        assertEquals(state.concept!!.id, entry.id, "recorded under the concept's own id")
        assertEquals(1, shown, "and opened")
    }

    @Test
    fun aConceptFolderFoundUnderTheHomeIsAdopted() {
        val dir = File(home, "Legacy").apply { mkdirs() }
        ConceptStore.save(dir, ConceptFile(name = "Legacy", kind = "prop")) // no id yet, as a hand-written file might be

        val listed = newState().availableConcepts()

        assertEquals(listOf("Legacy"), listed.map { it.name })
        assertTrue(listed.single().id.isNotBlank(), "given an id on adoption")
        assertEquals(listed.single().id, ConceptStore.peek(dir)!!.id, "and the id written back into the file")
    }

    @Test
    fun theListIsByKindThenName() {
        val state = newState()
        state.createConcept("Zora", "character")
        state.createConcept("Drache", "creature")
        state.createConcept("Anna", "character")
        assertEquals(listOf("Anna", "Zora", "Drache"), state.availableConcepts().map { it.name })
    }

    @Test
    fun renamingAndReKindingUpdateTheRecord() {
        val state = newState()
        state.createConcept("Drache")
        state.rename("Feuerdrache")
        state.setKind("creature")
        val entry = state.entryFor(state.root!!)!!
        assertEquals("Feuerdrache" to "creature", entry.name to entry.kind)
        assertEquals("Feuerdrache", ConceptStore.peek(state.root!!)!!.name)
    }

    @Test
    fun aConceptIsFoundByIdAfterItsFolderMovedHomes() {
        val state = newState()
        state.createConcept("Drache")
        val id = state.concept!!.id
        val moved = File(elsewhere, "Drache")
        state.root!!.renameTo(moved)
        ConceptRegistry(config).repath(state.root!!, moved)

        val fresh = newState()
        assertTrue(fresh.openById(id), "the registry knows where it went")
        assertEquals(moved.absolutePath, fresh.root!!.absolutePath)
    }

    @Test
    fun deletingKeepsOrRemovesTheFolderAsAsked() {
        val state = newState()
        state.createConcept("Keep")
        val keep = state.root!!
        File(keep, "sketch.png").writeText("picture")
        state.deleteConcept(keep, alsoFolder = false)
        assertTrue(File(keep, "sketch.png").isFile, "the files stay")
        assertFalse(ConceptStore.exists(keep), "the concept is gone")
        assertNull(state.entryFor(keep))

        state.createConcept("Gone")
        val gone = state.root!!
        state.deleteConcept(gone, alsoFolder = true)
        assertFalse(gone.exists())
        assertNull(state.root, "the open concept was the one deleted")
    }

    @Test
    fun aCorruptSidecarWithNoBackupIsReportedNotCrashed() {
        val dir = File(home, "Broken").apply { mkdirs() }
        File(dir, ConceptStore.FILE_NAME).writeText("{ not json")
        val state = newState()
        state.openConcept(dir)
        assertTrue(state.openFailed)
        assertFalse(state.isOpen)
    }

    // ---- What a concept holds ----

    @Test
    fun picturesAreCopiedInAndDuplicatesCounted() {
        val state = newState()
        state.createConcept("Drache")
        val outside = File(elsewhere, "wing.jpg").apply { writeText("picture") }

        state.addPictures(listOf(outside))
        val copied = state.items.filterIsInstance<ImageItem>().single()
        assertTrue(copied.path.startsWith("_imported/"), "copied into the concept, like a board: ${copied.path}")
        val inside = File(state.root!!, copied.path)
        assertTrue(inside.isFile)

        // The copy is now the concept's own; adding it again is a duplicate (same rule as boards).
        state.addPictures(listOf(inside))

        assertEquals(1, state.items.filterIsInstance<ImageItem>().size)
        assertTrue(state.notice?.contains("lready here") == true, state.notice)
    }

    @Test
    fun notesAndLinksRoundTrip() {
        val state = newState()
        state.createConcept("Drache")
        state.saveNote(null, "# Wings\nmembrane folds")
        state.saveLink(null, "https://example.com/dragons", "Dragons")

        val fresh = newState().also { it.openConcept(state.root!!) }
        assertEquals("Wings", fresh.items.filterIsInstance<NoteItem>().single().title)
        assertEquals("Dragons", fresh.items.filterIsInstance<LinkItem>().single().title)

        fresh.removeItems(setOf(fresh.items.first().id))
        assertEquals(1, fresh.items.size)
    }

    @Test
    fun aDocumentIsAMarkdownFileInTheConceptsFolder() {
        val state = newState()
        state.createConcept("Drache")

        val path = state.saveDocument(null, "# Anatomy\n\nSix limbs: four legs and two wings.")!!

        assertEquals("${ConceptStore.DOCS_DIR}/Anatomy.md", path, "named from its heading")
        assertEquals(listOf(path), state.concept!!.documents)
        assertTrue(File(state.root!!, path).readText().startsWith("# Anatomy"))
        assertEquals("Anatomy", state.documentTitle(path))

        state.saveDocument(path, "# Anatomy\n\nRevised.")
        assertEquals(listOf(path), state.concept!!.documents, "editing keeps the one file")
        assertTrue(state.readDocument(path)!!.contains("Revised"))

        val fresh = newState().also { it.openConcept(state.root!!) }
        assertEquals(listOf(path), fresh.concept!!.documents, "listed after a reopen")

        fresh.deleteDocument(path)
        assertFalse(File(fresh.root!!, path).exists())
        assertTrue(fresh.concept!!.documents.isEmpty())
    }

    @Test
    fun twoDocumentsWithTheSameTitleGetTheirOwnFiles() {
        val state = newState()
        state.createConcept("Drache")
        val a = state.saveDocument(null, "# Notes\none")
        val b = state.saveDocument(null, "# Notes\ntwo")
        assertTrue(a != b, "$a vs $b")
        assertEquals(2, state.concept!!.documents.size)
    }

    @Test
    fun aDocumentWhoseFileVanishedIsDroppedOnLoad() {
        val state = newState()
        state.createConcept("Drache")
        val path = state.saveDocument(null, "# Lost")!!
        File(state.root!!, path).delete()

        val fresh = newState().also { it.openConcept(state.root!!) }
        assertTrue(fresh.concept!!.documents.isEmpty())
    }

    // ---- On screen ----

    @Test
    fun theListShowsAConceptAndItsPageRendersItsDocument() {
        val state = newState()
        state.createConcept("Drache", "creature")
        state.saveDocument(null, "# Anatomy\n\nSix limbs.")
        state.closeConcept()

        rule.setContent { ConceptListScreen(state, ThumbCache(config)) }
        rule.waitForIdle()
        rule.onNodeWithTag("concept-Drache").assertIsDisplayed()

        state.openConcept(state.availableConcepts().single().dir)
        rule.setContent { ConceptScreen(state, ThumbCache(config)) }
        rule.waitForIdle()
        rule.onNodeWithTag("document-Anatomy.md", useUnmergedTree = true).assertIsDisplayed()
        assertNotNull(state.concept)
    }

    // ---- Boards, as the concept side sees them ----

    @Test
    fun theDialogsNameTheBoardsAndToggleALink() {
        val buch = File(home, "Buch")
        val zweites = File(home, "Zweites")
        val toggled = mutableListOf<Pair<String, Boolean>>()
        val boardsHost = object : ConceptHost {
            override fun showConcepts() = Unit
            override fun showConcept() = Unit
            override fun leaveConcepts() = Unit
            override fun boardsFor(conceptId: String) = listOf(BoardLink("Buch", buch, true), BoardLink("Zweites", zweites, false))
            override fun setLinked(conceptId: String, board: File, linked: Boolean) { toggled += board.name to linked }
        }
        val state = ConceptState(Settings(config), boardsHost).also { it.setConceptsHomeDir(home) }
        state.createConcept("Drache")

        state.openEditor(ConceptEditor.DeleteConcept(state.root!!, "Drache"))
        rule.setContent { ConceptDialogs(state) }
        rule.waitForIdle()
        rule.onNodeWithTag("delete-linked-boards", useUnmergedTree = true).assertTextEquals("Linked on: Buch")

        state.openEditor(ConceptEditor.LinkToBoards)
        rule.waitForIdle()
        rule.onNodeWithTag("board-link-Zweites").performClick()
        rule.waitForIdle()
        assertEquals(listOf("Zweites" to true), toggled)
    }

    // ---- The free layout ----

    @Test
    fun theFreeLayoutPlacesEveryCardAndKeepsTheArrangement() {
        val state = newState()
        state.createConcept("Drache")
        state.addPictures(listOf(File(elsewhere, "wing.jpg").apply { createNewFile() }))
        state.saveNote(null, "# Wings")
        assertEquals(BoardLayouts.GRID, state.layout)
        assertTrue(state.items.all { it.pos == null }, "a grid has no places")

        state.setLayout(BoardLayouts.FREE)
        assertTrue(state.items.all { it.pos != null }, "switching to Free places what has no place")
        state.saveLink(null, "https://example.com", "Dragons")
        assertTrue(state.items.all { it.pos != null }, "and so does adding while Free")

        val picture = state.items.first()
        val before = picture.pos!!
        state.dragBy(picture.id, 40f, -10f)
        state.commitLayout()
        state.pan(100f, 50f)
        state.setZoom(2f, 100f, 50f)
        state.commitCamera()

        val fresh = newState().also { it.openConcept(state.root!!) }
        assertEquals(BoardLayouts.FREE, fresh.layout)
        assertEquals(ItemPos(before.x + 40f, before.y - 10f), fresh.item(picture.id)!!.pos)
        assertEquals(Triple(100f, 50f, 2f), Triple(fresh.camX, fresh.camY, fresh.zoom))
    }

    @Test
    fun aPicturesShapeIsRememberedOnceKnown() {
        val state = newState()
        state.createConcept("Drache")
        state.addPictures(listOf(File(elsewhere, "wing.jpg").apply { createNewFile() }))
        val id = state.items.single().id
        assertEquals(1f, state.aspectOf(state.item(id)!!), "square until decoded")

        state.rememberAspect(id, 1.5f)

        assertEquals(1.5f, state.aspectOf(state.item(id)!!))
        assertEquals(1.5f, (ConceptStore.peek(state.root!!)!!.items.single() as ImageItem).aspect, "and written down")
    }

    @Test
    fun freeShowsACanvasWithTheCardsInPlace() {
        val state = newState()
        state.createConcept("Drache")
        state.saveNote(null, "# Wings")
        state.setLayout(BoardLayouts.FREE)
        val id = state.items.single().id

        rule.setContent { ConceptScreen(state, ThumbCache(config)) }
        rule.waitForIdle()
        rule.onNodeWithTag("concept-canvas").assertExists()
        rule.onNodeWithTag("concept-card-$id", useUnmergedTree = true).assertExists()

        rule.onNodeWithTag("concept-layout-Grid").performClick()
        rule.waitForIdle()
        assertEquals(BoardLayouts.GRID, state.layout)
        rule.onNodeWithTag("concept-canvas").assertDoesNotExist()
    }

    // ---- Typing ----

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aNewNoteTakesTheFirstKeystrokeItself() {
        val state = newState()
        state.createConcept("Drache")
        state.openEditor(ConceptEditor.EditNote(null))
        rule.setContent { ConceptDialogs(state) }
        rule.waitForIdle()

        // Nothing clicked first: the note's field must already hold the focus, so a Space goes to
        // it — and not to the scrim, which used to take it as a click and close the note.
        rule.onNodeWithTag("concept-note-text").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.Spacebar) }
        rule.waitForIdle()

        assertNotNull(state.editor, "still open")
        rule.onNodeWithTag("concept-note-text").assertIsFocused()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun enterInTheNameSavesTheRename() {
        val state = newState()
        state.createConcept("Drache")
        state.openEditor(ConceptEditor.Rename)
        rule.setContent { ConceptDialogs(state) }
        rule.waitForIdle()

        rule.onNodeWithTag("concept-rename-name").performTextInput("Feuer")
        rule.onNodeWithTag("concept-rename-name").performKeyInput { pressKey(Key.Enter) }
        rule.waitForIdle()

        val name = state.concept!!.name
        assertTrue(name.contains("Feuer") && name != "Drache", "Enter is the Save button: $name")
        assertNull(state.editor, "and the dialog closed")
    }
}
