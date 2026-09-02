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

    @Test
    fun placeMissingCascadesInRowsBelowExistingCards() {
        val placed = NoteItem(id = "p", text = "x", pos = ItemPos(0f, 0f))
        val unplaced = (1..6).map { NoteItem(id = "n$it", text = "x") }
        val board = BoardState.placeMissing(BoardFile(items = listOf(placed) + unplaced))

        assertTrue(board.items.all { it.pos != null }, "every card must get a position")
        val newYs = board.items.filter { it.id != "p" }.map { it.pos!!.y }.distinct().sorted()
        assertEquals(2, newYs.size, "6 cards in rows of 5 -> two rows")
        assertTrue(newYs.all { it > 0f }, "new rows start below the already placed card")
    }

    @Test
    fun switchingToFreeformPlacesCardsAndPersists() {
        val state = newState()
        state.createBoard(home, "Test")
        val file = File(state.root!!, "a.jpg").apply { createNewFile() }
        state.importExternal(listOf(file))
        assertNull(state.board!!.items.single().pos)

        state.setLayout(BoardLayouts.FREE)
        assertTrue(state.board!!.items.single().pos != null)

        val reopened = newState()
        reopened.openBoard(state.root!!)
        assertEquals(BoardLayouts.FREE, reopened.layout)
        assertTrue(reopened.board!!.items.single().pos != null)
    }

    @Test
    fun moveResizeRotateSurviveCommitAndReopen() {
        val state = newState()
        state.createBoard(home, "Test")
        val file = File(state.root!!, "a.jpg").apply { createNewFile() }
        state.importExternal(listOf(file))
        state.setLayout(BoardLayouts.FREE)
        val id = state.board!!.items.single().id
        val before = state.board!!.items.single().pos!!

        state.clearSelection() // dragBy without selection moves just the given card
        state.dragBy(id, 40f, -20f)
        state.resizeBy(id, 2f)
        state.rotateBy(id, 30f)
        state.commitLayout()

        val reopened = newState()
        reopened.openBoard(state.root!!)
        val pos = reopened.board!!.items.single().pos!!
        assertEquals(before.x + 40f, pos.x)
        assertEquals(before.y - 20f, pos.y)
        assertEquals(before.scale * 2f, pos.scale)
        assertEquals(30f, pos.rotation)
    }

    @Test
    fun cameraIsRememberedAcrossReopen() {
        val state = newState()
        state.createBoard(home, "Test")
        state.pan(100f, 50f)
        state.setZoom(2f, state.camX, state.camY)
        val root = state.root!!
        state.closeBoard() // commits the camera

        val reopened = newState()
        reopened.openBoard(root)
        assertEquals(100f, reopened.camX)
        assertEquals(50f, reopened.camY)
        assertEquals(2f, reopened.zoom)
    }

    @Test
    fun bringToFrontMovesTheCardToTheEndOfTheZOrder() {
        val state = newState()
        state.createBoard(home, "Test")
        state.saveNote(null, "first")
        state.saveNote(null, "second")
        val firstId = state.board!!.items.first().id

        state.bringToFront(firstId)
        assertEquals(firstId, state.board!!.items.last().id)
    }

    @Test
    fun sendToBackMovesTheCardToTheStart() {
        val state = newState()
        state.createBoard(home, "Test")
        state.saveNote(null, "photo")
        state.saveNote(null, "note on top") // created later -> in front
        val lastId = state.board!!.items.last().id

        state.sendToBack(lastId)
        assertEquals(lastId, state.board!!.items.first().id)
    }

    @Test
    fun stepInGroupReordersOnlyAmongTheGroupsOwnCards() {
        val state = newState()
        state.createBoard(home, "Test")
        state.addGroup("G")
        val groupId = state.sortedGroups.single().id
        // Array: A(G), X(inbox), B(G) — X sits between the two group members.
        state.saveNote(null, "A")
        val a = state.board!!.items.last().id
        state.saveNote(null, "X")
        state.saveNote(null, "B")
        val b = state.board!!.items.last().id
        state.moveToGroup(setOf(a, b), groupId)
        state.moveToGroup(state.board!!.items.filterNot { it.id == a || it.id == b }.map { it.id }.toSet(), null)

        state.stepInGroup(a, groupId, forward = true) // A moves past B, its group neighbour
        assertEquals(listOf(b, a), state.itemsIn(groupId).map { it.id })

        state.stepInGroup(a, groupId, forward = true) // already last in the group: no change
        assertEquals(listOf(b, a), state.itemsIn(groupId).map { it.id })
    }

    @Test
    fun stepZSkipsCardsHiddenByTheTagFilter() {
        val state = newState()
        state.createBoard(home, "Test")
        val root = state.root!!
        val files = listOf("a.jpg", "b.jpg", "c.jpg").map { File(root, it).apply { createNewFile() } }
        state.importExternal(files)
        val (a, b, c) = state.board!!.items.map { it.id }
        state.applyTags(setOf(a, c), before = emptySet(), after = setOf("wing"))
        state.toggleFilterTag("wing") // visible: a, c — b is hidden between them

        state.stepZ(a, forward = true) // one visible level up -> past c (and past hidden b)
        assertEquals(listOf(b, c, a), state.board!!.items.map { it.id })
    }

    @Test
    fun toGroupEdgeMovesToStartAndEnd() {
        val state = newState()
        state.createBoard(home, "Test")
        state.saveNote(null, "1")
        state.saveNote(null, "2")
        state.saveNote(null, "3")
        val ids = state.board!!.items.map { it.id }

        state.toGroupEdge(ids[2], null, toEnd = false) // last card to the Inbox's start
        assertEquals(listOf(ids[2], ids[0], ids[1]), state.itemsIn(null).map { it.id })

        state.toGroupEdge(ids[2], null, toEnd = true) // and back to its end
        assertEquals(listOf(ids[0], ids[1], ids[2]), state.itemsIn(null).map { it.id })
    }

    /** Board with three pictures a.jpg/b.jpg/c.jpg, returned with their ids in display order. */
    private fun boardWithThreeImages(state: BoardState): List<String> {
        state.createBoard(home, "Test")
        val root = state.root!!
        val files = listOf("a.jpg", "b.jpg", "c.jpg").map { File(root, it).apply { createNewFile() } }
        state.importExternal(files)
        state.clearSelection()
        return state.board!!.items.map { it.id }
    }

    @Test
    fun viewerShowsTheSelectionStartingAtTheClickedPicture() {
        val state = newState()
        val ids = boardWithThreeImages(state)
        state.clickItem(ids[0], ctrl = false, shift = false)
        state.clickItem(ids[2], ctrl = true, shift = false) // selection = a + c

        state.openViewer(ids[2])
        assertEquals(listOf(ids[0], ids[2]), state.viewerIds, "only the selected pictures")
        assertEquals(1, state.viewerIndex, "opens on the picture that was clicked")
        assertTrue(state.viewerOpen)
    }

    @Test
    fun viewerWithoutASelectionShowsEverythingOnScreen() {
        val state = newState()
        val ids = boardWithThreeImages(state)
        state.openViewer()
        assertEquals(ids, state.viewerIds)
    }

    @Test
    fun viewerCarouselWrapsAroundInBothDirections() {
        val state = newState()
        val ids = boardWithThreeImages(state)
        state.openViewer(ids[0])

        state.viewerStep(-1)
        assertEquals(2, state.viewerIndex, "stepping back from the first wraps to the last")
        state.viewerStep(1)
        assertEquals(0, state.viewerIndex, "and forward from the last wraps to the first")
        assertEquals(ids[0], state.focusId, "the board follows the carousel")
    }

    @Test
    fun viewerNotesAreNeverIncluded() {
        val state = newState()
        boardWithThreeImages(state)
        state.saveNote(null, "just a note")
        state.clearSelection()

        state.openViewer()
        assertEquals(3, state.viewerIds.size)
        assertTrue(state.viewerIds.all { state.item(it) is ImageItem })
    }

    @Test
    fun removingTheViewedCardKeepsTheViewerConsistent() {
        val state = newState()
        val ids = boardWithThreeImages(state)
        state.openViewer(ids[2])
        assertEquals(2, state.viewerIndex)

        state.removeItems(setOf(ids[2]))
        assertEquals(listOf(ids[0], ids[1]), state.viewerIds)
        assertEquals(1, state.viewerIndex, "index clamps into the shortened carousel")
        assertTrue(state.viewerItem != null)
    }

    @Test
    fun viewerRespectsTheTagFilter() {
        val state = newState()
        val ids = boardWithThreeImages(state)
        state.applyTags(setOf(ids[1]), before = emptySet(), after = setOf("wing"))
        state.toggleFilterTag("wing")

        state.openViewer()
        assertEquals(listOf(ids[1]), state.viewerIds, "hidden pictures stay out of the carousel")
    }

    @Test
    fun closingTheViewerLeavesNothingBehind() {
        val state = newState()
        val ids = boardWithThreeImages(state)
        state.openViewer(ids[1])
        state.closeViewer()
        assertTrue(!state.viewerOpen && state.viewerIds.isEmpty())
        assertEquals(0, state.viewerIndex)
    }

    @Test
    fun availableBoardsListsTheHomeAndRecentOnes() {
        val state = newState()
        state.createBoard(home, "Alpha")
        state.closeBoard()
        state.createBoard(home, "Beta")
        state.closeBoard()
        // A board outside the home is known only through the recent list.
        val elsewhere = File(outside, "Gamma").apply { mkdirs() }
        state.openBoard(elsewhere)
        state.closeBoard()

        val names = state.availableBoards().map { it.first }.toSet()
        assertEquals(setOf("Alpha", "Beta", "Gamma"), names)
    }
}
