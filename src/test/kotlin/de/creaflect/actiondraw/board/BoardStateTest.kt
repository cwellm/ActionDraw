package de.creaflect.actiondraw.board

import de.creaflect.actiondraw.Settings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Behavioural tests for BoardState against real temp folders, with a recording fake host. */
class BoardStateTest {
    private val home: File = Files.createTempDirectory("actiondraw-boards-home").toFile()
    private val outside: File = Files.createTempDirectory("actiondraw-outside").toFile()
    private val settingsDir: File = Files.createTempDirectory("actiondraw-config").toFile()

    private class FakeHost : BoardHost {
        var shown = 0
        var left = 0
        var lastSession: Pair<File, List<File>>? = null
        override fun startSession(root: File, images: List<File>) {
            lastSession = root to images
        }

        override fun showBoard() {
            shown++
        }

        override fun leaveBoard() {
            left++
        }
    }

    private val host = FakeHost()

    private fun newState() = BoardState(Settings(settingsDir), host, timestamp = { "20260830-120000" })

    @AfterTest
    fun cleanup() {
        home.deleteRecursively()
        outside.deleteRecursively()
        settingsDir.deleteRecursively()
    }

    @Test
    fun createBoardMakesASanitizedFolderWithSidecarAndNavigates() {
        val state = newState()
        assertNull(state.createBoard(home, "Drachen: Buch"))
        val dir = File(home, "Drachen_ Buch")
        assertTrue(BoardStore.exists(dir))
        assertEquals("Drachen: Buch", state.board?.name)
        assertEquals(1, host.shown)
        assertTrue(state.recent.any { it.absolutePath == dir.absolutePath })
    }

    @Test
    fun boardsSurviveReopening() {
        val state = newState()
        state.createBoard(home, "Test")
        state.addGroup("Flügel")
        val groupId = state.sortedGroups.single().id
        state.saveNote(null, "collect more!")
        state.moveToGroup(state.selection, groupId)
        val root = state.root!!

        val reopened = newState()
        reopened.openBoard(root)
        assertEquals(listOf("Flügel"), reopened.sortedGroups.map { it.name })
        val note = reopened.board!!.items.filterIsInstance<NoteItem>().single()
        assertEquals("collect more!", note.text)
        assertEquals(listOf(groupId), note.groups)
    }

    @Test
    fun importReferencesInRootFilesAndCopiesExternalOnes() {
        val state = newState()
        state.createBoard(home, "Test")
        val root = state.root!!
        val inner = File(root, "pics").apply { mkdirs() }.let { File(it, "a.jpg").apply { createNewFile() } }
        val outer = File(outside, "b.jpg").apply { writeText("x") }

        state.importExternal(listOf(inner, outer))

        val paths = state.board!!.items.filterIsInstance<ImageItem>().map { it.path }.toSet()
        assertEquals(setOf("pics/a.jpg", "${Importer.IMPORT_DIR}/b.jpg"), paths)
        assertTrue(File(root, "${Importer.IMPORT_DIR}/b.jpg").isFile)
        assertEquals(2, state.selection.size, "imports become the selection")
    }

    @Test
    fun tagFilterShowsOnlyMatchingImagesAndHidesNotes() {
        val state = newState()
        state.createBoard(home, "Test")
        val root = state.root!!
        val a = File(root, "a.jpg").apply { createNewFile() }
        val b = File(root, "b.jpg").apply { createNewFile() }
        state.importExternal(listOf(a, b))
        state.saveNote(null, "a note")

        val aId = state.board!!.items.filterIsInstance<ImageItem>().first { it.path == "a.jpg" }.id
        state.applyTags(setOf(aId), before = emptySet(), after = setOf("wing"))

        state.toggleFilterTag("wing")
        assertEquals(listOf(aId), state.visibleOrder.map { it.id })

        state.clearFilter()
        assertEquals(3, state.visibleOrder.size)
    }

    @Test
    fun removingCardsNeverDeletesFiles() {
        val state = newState()
        state.createBoard(home, "Test")
        val file = File(state.root!!, "a.jpg").apply { createNewFile() }
        state.importExternal(listOf(file))
        val id = state.board!!.items.single().id

        state.removeItems(setOf(id))
        assertTrue(state.board!!.items.isEmpty())
        assertTrue(file.isFile, "removing a card must not touch the file")
    }

    @Test
    fun drawGroupHandsExactlyItsFilesToTheHost() {
        val state = newState()
        state.createBoard(home, "Test")
        val root = state.root!!
        val a = File(root, "a.jpg").apply { createNewFile() }
        val b = File(root, "b.jpg").apply { createNewFile() }
        state.importExternal(listOf(a, b))
        state.addGroup("Posen")
        val groupId = state.sortedGroups.single().id
        val aId = state.board!!.items.filterIsInstance<ImageItem>().first { it.path == "a.jpg" }.id
        state.moveToGroup(setOf(aId), groupId)

        state.drawGroup(groupId)
        val (sessionRoot, images) = host.lastSession!!
        assertEquals(root.absolutePath, sessionRoot.absolutePath)
        assertEquals(listOf(a.absolutePath), images.map { it.absolutePath })
    }

    @Test
    fun deletingAGroupSendsItsCardsBackToTheInbox() {
        val state = newState()
        state.createBoard(home, "Test")
        val file = File(state.root!!, "a.jpg").apply { createNewFile() }
        state.importExternal(listOf(file))
        state.addGroup("Flügel")
        val groupId = state.sortedGroups.single().id
        val itemId = state.board!!.items.single().id
        state.moveToGroup(setOf(itemId), groupId)
        assertEquals(0, state.itemsIn(null).size)

        state.deleteGroup(groupId)
        assertEquals(listOf(itemId), state.itemsIn(null).map { it.id })
        assertTrue(state.sortedGroups.isEmpty())
    }

    @Test
    fun closingTheBoardNavigatesBack() {
        val state = newState()
        state.createBoard(home, "Test")
        state.closeBoard()
        assertEquals(1, host.left)
        assertNull(state.board)
    }
}
