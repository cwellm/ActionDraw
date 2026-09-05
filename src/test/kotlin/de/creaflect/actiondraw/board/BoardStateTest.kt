package de.creaflect.actiondraw.board

import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.SessionPlans
import de.creaflect.actiondraw.SessionSetup
import de.creaflect.actiondraw.Settings
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.image.RedoStore
import de.creaflect.actiondraw.image.SeenStore
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Behavioural tests for BoardState against real temp folders, with a recording fake host. */
class BoardStateTest {
    private val home: File = Files.createTempDirectory("actiondraw-boards-home").toFile()
    private val outside: File = Files.createTempDirectory("actiondraw-outside").toFile()
    private val settingsDir: File = Files.createTempDirectory("actiondraw-config").toFile()

    private class FakeHost : BoardHost {
        var shown = 0
        var left = 0
        var listShown = 0
        var lastSession: Pair<File, List<File>>? = null
        var lastSetup: SessionSetup? = null

        /** What the practice side would report as "current settings" for `Use current`. */
        var setup = SessionSetup(null, 120, true, ViewMode.NONE, GridMode.OFF)

        override fun startSession(root: File, images: List<File>, setup: SessionSetup?) {
            lastSession = root to images
            lastSetup = setup
        }

        override fun showBoard() {
            shown++
        }

        override fun showBoardList() {
            listShown++
        }

        override fun leaveBoard() {
            left++
        }

        override fun currentSetup(): SessionSetup = setup
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

    // ---- M2: search, practice, recipe, pinning, reordering ----

    @Test
    fun searchMatchesFileNamesCaptionsTagsAndNoteText() {
        val state = newState()
        val ids = boardWithThreeImages(state)
        state.setCaption(ids[1], "membrane folds")
        state.applyTags(setOf(ids[2]), before = emptySet(), after = setOf("wing"))
        state.saveNote(null, "collect more dragons")
        state.clearSelection()

        state.search("a.jpg")
        assertEquals(listOf(ids[0]), state.visibleOrder.map { it.id }, "matches the file name")

        state.search("membrane")
        assertEquals(listOf(ids[1]), state.visibleOrder.map { it.id }, "matches the caption")

        state.search("wing")
        assertEquals(listOf(ids[2]), state.visibleOrder.map { it.id }, "matches a tag")

        state.search("dragons")
        assertEquals(1, state.visibleOrder.size, "matches note text")

        state.clearFilter()
        assertEquals(4, state.visibleOrder.size)
    }

    @Test
    fun practiceBadgesAndSmartSectionsFollowTheSeenAndRedoStores() {
        val state = newState()
        val ids = boardWithThreeImages(state)
        val root = state.root!!
        assertTrue(state.smartSections.isEmpty(), "a board without history has no smart sections")

        SeenStore.write(root, setOf("a.jpg"))
        RedoStore.write(root, setOf("b.jpg"))
        state.refreshPractice()

        val byPath = state.board!!.items.filterIsInstance<ImageItem>().associateBy { it.path }
        assertEquals(BoardState.Practice.SEEN, state.practiceOf(byPath.getValue("a.jpg")))
        assertEquals(BoardState.Practice.REDO, state.practiceOf(byPath.getValue("b.jpg")))
        assertEquals(BoardState.Practice.UNSEEN, state.practiceOf(byPath.getValue("c.jpg")))

        val sections = state.smartSections.toMap()
        assertEquals(listOf(ids[1]), sections.getValue("⟳ Redo").map { it.id })
        assertEquals(listOf(ids[2]), sections.getValue("Never drawn").map { it.id })
    }

    @Test
    fun aBoardsSessionRecipeIsStoredAndHandedToTheSession() {
        val state = newState()
        boardWithThreeImages(state)
        state.saveRecipe(SessionRecipe(plan = null, intervalSeconds = 60, autoAdvance = false, viewMode = "NOTAN"))

        state.drawGroup(null)
        val setup = host.lastSetup!!
        assertEquals(60, setup.intervalSeconds)
        assertEquals(false, setup.autoAdvance)
        assertEquals(ViewMode.NOTAN, setup.viewMode)
        assertNull(setup.plan)

        // And it survives a reopen.
        val reopened = newState()
        reopened.openBoard(state.root!!)
        assertEquals(60, reopened.recipe?.intervalSeconds)
    }

    @Test
    fun aBoardRemembersTheLightItWasDrawnUnder() {
        val state = newState()
        boardWithThreeImages(state)
        host.setup = SessionSetup(null, 45, true, ViewMode.NOTAN, GridMode.OFF, temperature = -0.4f)
        state.rememberCurrentSetup()

        val reopened = newState()
        reopened.openBoard(state.root!!)
        reopened.drawGroup(null)
        assertEquals(-0.4f, host.lastSetup!!.temperature, 0.0001f)
    }

    @Test
    fun aBoardFromBeforeTheSliderStillOpensAndKeepsItsLight() {
        val state = newState()
        boardWithThreeImages(state)
        // What an older build wrote: no temperature field at all, and a view mode that is gone.
        val file = File(state.root!!, BoardStore.FILE_NAME)
        file.writeText(
            file.readText().replace(
                "\"items\"",
                "\"session\":{\"plan\":null,\"intervalSeconds\":90,\"autoAdvance\":true,\"viewMode\":\"WARM\"},\"items\"",
            ),
        )

        val reopened = newState()
        reopened.openBoard(state.root!!)
        assertEquals(90, reopened.recipe?.intervalSeconds, "the old recipe still parses")

        reopened.drawGroup(null)
        val setup = host.lastSetup!!
        assertEquals(ViewMode.NONE, setup.viewMode, "the retired mode is no longer a mode")
        assertEquals(0.6f, setup.temperature, 0.0001f, "but its warm light survives on the slider")
    }

    @Test
    fun rememberCurrentSetupTakesWhateverThePracticeSideIsSetTo() {
        val state = newState()
        boardWithThreeImages(state)
        host.setup = SessionSetup(SessionPlans.ALL.first(), 30, false, ViewMode.SQUINT, GridMode.PHI)

        state.rememberCurrentSetup()
        val recipe = state.recipe!!
        assertEquals(SessionPlans.ALL.first().name, recipe.plan)
        assertEquals("SQUINT", recipe.viewMode)
        assertEquals("PHI", recipe.grid)
    }

    @Test
    fun withoutARecipeTheSessionKeepsTheMenusSettings() {
        val state = newState()
        boardWithThreeImages(state)
        state.drawGroup(null)
        assertNull(host.lastSetup, "no recipe means the menu's settings are left alone")
    }

    @Test
    fun pinningCopiesAPictureOntoAnotherBoardWithoutOpeningIt() {
        val state = newState()
        state.createBoard(home, "Target")
        val target = state.root!!
        state.closeBoard()
        val loose = File(outside, "pinned.jpg").apply { writeText("x") }

        val message = state.pinTo(target, listOf(loose))

        assertTrue(message.startsWith("Pinned 1"), "got: $message")
        assertTrue(File(target, "${Importer.IMPORT_DIR}/pinned.jpg").isFile)
        val stored = BoardStore.peek(target)!!
        assertEquals(listOf("${Importer.IMPORT_DIR}/pinned.jpg"), stored.items.filterIsInstance<ImageItem>().map { it.path })
        assertNull(state.board, "pinning must not open the target board")
    }

    @Test
    fun pinningTheSamePictureTwiceIsReported() {
        val state = newState()
        state.createBoard(home, "Target")
        val target = state.root!!
        val loose = File(outside, "pinned.jpg").apply { writeText("x") }

        state.pinTo(target, listOf(loose))
        val second = state.pinTo(target, listOf(loose))
        // The copy lands under a fresh name, so this is a second card rather than a duplicate.
        assertTrue(second.startsWith("Pinned 1"), "got: $second")
        assertEquals(2, BoardStore.peek(target)!!.items.size)
    }

    @Test
    fun dropOnMovesACardToTheTargetsPlaceInBothDirections() {
        val state = newState()
        val ids = boardWithThreeImages(state)

        state.dropOn(ids[0], ids[2], groupId = null) // drag the first onto the last
        assertEquals(listOf(ids[1], ids[2], ids[0]), state.itemsIn(null).map { it.id })

        state.dropOn(ids[0], ids[1], groupId = null) // and back to the front
        assertEquals(listOf(ids[0], ids[1], ids[2]), state.itemsIn(null).map { it.id })
    }

    @Test
    fun openingTheBoardListGoesThroughTheHost() {
        val state = newState()
        state.openBoardList()
        assertEquals(1, host.listShown)
    }

    // ---- M3: links, note styling, templates, marquee, snapping, strip ----

    @Test
    fun aTemplateGivesANewBoardItsStarterGroups() {
        val state = newState()
        val creature = BoardTemplate.ALL.first { it.name == "Creature design" }
        state.createBoard(home, "Drachen", creature)
        assertEquals(creature.groups, state.sortedGroups.map { it.name })
    }

    @Test
    fun aFetchedPreviewLandsOnTheCardAndSurvivesAReopen() {
        val state = newState()
        state.createBoard(home, "Test")
        state.saveLink(null, "https://example.com/wings", "Bat wings")
        val id = state.board!!.items.filterIsInstance<LinkItem>().single().id

        // A stubbed fetcher: nothing in the suite ever opens a socket.
        val page = LinkPreview.Fetched(
            "text/html",
            """<meta property="og:image" content="https://cdn.example.com/w.png">""".toByteArray(),
            "https://example.com/wings",
        )
        state.fetchLinkPreview(id) { url ->
            if (url.endsWith(".png")) LinkPreview.Fetched("image/png", byteArrayOf(1, 2, 3), url) else page
        }

        val preview = (state.item(id) as LinkItem).preview
        assertNotNull(preview, "the card now points at a picture")
        assertTrue(preview.startsWith(LinkPreview.PREVIEW_DIR), "which lives inside the board folder")
        assertTrue(File(state.root!!, preview).isFile)

        val reopened = newState()
        reopened.openBoard(state.root!!)
        val same = reopened.board!!.items.filterIsInstance<LinkItem>().single()
        assertEquals(preview, same.preview, "the board is offline again and still shows it")
        assertNotNull(reopened.previewFileOf(same))

        reopened.clearLinkPreview(same.id)
        assertNull((reopened.item(same.id) as LinkItem).preview)
    }

    @Test
    fun aFailedFetchLeavesTheCardAloneAndSaysWhy() {
        val state = newState()
        state.createBoard(home, "Test")
        state.saveLink(null, "https://example.com/wings", "Bat wings")
        val id = state.board!!.items.filterIsInstance<LinkItem>().single().id

        state.fetchLinkPreview(id) { null }

        assertNull((state.item(id) as LinkItem).preview, "no half-attached preview")
        assertNotNull(state.importNotice, "and the board says so rather than failing silently")
    }

    @Test
    fun linkCardsAreStoredSearchableAndEditable() {
        val state = newState()
        state.createBoard(home, "Test")
        state.saveLink(null, "https://example.com/wings", "Bat wings")
        val link = state.board!!.items.filterIsInstance<LinkItem>().single()
        assertEquals("https://example.com/wings", link.url)

        state.search("bat")
        assertEquals(listOf(link.id), state.visibleOrder.map { it.id }, "matches the title")
        state.search("example.com")
        assertEquals(listOf(link.id), state.visibleOrder.map { it.id }, "matches the address")
        state.clearFilter()

        state.saveLink(link.id, "https://example.com/other", "Other")
        assertEquals("Other", (state.item(link.id) as LinkItem).title)

        val reopened = newState()
        reopened.openBoard(state.root!!)
        assertEquals(1, reopened.board!!.items.filterIsInstance<LinkItem>().size)
    }

    @Test
    fun aLinkWithoutAnAddressIsNotCreated() {
        val state = newState()
        state.createBoard(home, "Test")
        state.saveLink(null, "   ", "nothing")
        assertTrue(state.board!!.items.isEmpty())
    }

    @Test
    fun notesRememberTheirPaperColourAndHeading() {
        val state = newState()
        state.createBoard(home, "Test")
        state.saveNote(null, "Wings **from behind**")
        val id = state.board!!.items.single().id

        state.setNoteColor(id, "#FFE082")
        state.toggleNoteHeading(id)

        val reopened = newState()
        reopened.openBoard(state.root!!)
        val note = reopened.board!!.items.filterIsInstance<NoteItem>().single()
        assertEquals("#FFE082", note.color)
        assertTrue(note.heading)
        assertEquals("Wings **from behind**", note.text, "the markers stay in the file")
    }

    @Test
    fun theMarqueeSelectsEveryCardItTouches() {
        val state = newState()
        val ids = boardWithThreeImages(state)
        state.setLayout(BoardLayouts.FREE)
        val positions = state.board!!.items.associate { it.id to it.pos!! }

        // A band around the first two cards only.
        val first = positions.getValue(ids[0])
        val second = positions.getValue(ids[1])
        state.startMarquee(first.x - 10f, first.y - 10f)
        state.updateMarquee(second.x + 10f, second.y + 10f)
        state.commitMarquee()

        assertEquals(setOf(ids[0], ids[1]), state.selection)
        assertNull(state.marquee, "the band disappears once it has selected")
    }

    @Test
    fun snappingLinesACardUpWithItsNeighbourAndCanBeTurnedOff() {
        val state = newState()
        val ids = boardWithThreeImages(state)
        state.setLayout(BoardLayouts.FREE)
        state.clearSelection()
        val anchor = state.board!!.items.first { it.id == ids[0] }.pos!!

        // Almost aligned: snapping should pull it onto the neighbour's centre line.
        val (x, y) = state.snapPosition(ids[1], anchor.x + 4f, anchor.y + 400f, threshold = 10f)
        assertEquals(anchor.x, x, "x snapped to the neighbour")
        assertEquals(anchor.y + 400f, y, "y was too far to snap")
        assertEquals(anchor.x, state.snapGuideX, "and a guide is drawn where it snapped")

        state.snapping = false
        val (freeX, _) = state.snapPosition(ids[1], anchor.x + 4f, anchor.y + 400f, threshold = 10f)
        assertEquals(anchor.x + 4f, freeX, "with snapping off the card goes where it is dragged")
        assertNull(state.snapGuideX)
    }

    @Test
    fun theFloatingStripFollowsTheSelectionAndWrapsAround() {
        val state = newState()
        val ids = boardWithThreeImages(state)
        state.clickItem(ids[0], ctrl = false, shift = false)
        state.clickItem(ids[1], ctrl = true, shift = false)

        state.openStrip()
        assertTrue(state.stripOpen)
        assertEquals(listOf(ids[0], ids[1]), state.stripIds, "only the selected pictures")
        assertEquals(1, state.stripIndex, "opens on the card that has focus")

        state.stripStep(-1)
        assertEquals(0, state.stripIndex)
        state.stripStep(-1)
        assertEquals(1, state.stripIndex, "stepping back from the first wraps to the last")
        state.stripGoTo(ids[0])
        assertEquals(0, state.stripIndex)

        state.closeStrip()
        assertFalse(state.stripOpen)
    }

    // ---- Groups on the canvas (feedback round 2) ----

    @Test
    fun aGroupGetsAHullAroundItsCardsOnTheCanvas() {
        val state = newState()
        val ids = boardWithThreeImages(state)
        state.addGroup("Wings")
        val groupId = state.sortedGroups.single().id
        state.moveToGroup(setOf(ids[0], ids[1]), groupId)
        state.setLayout(BoardLayouts.FREE)

        val hull = state.groupHulls.single()
        assertEquals("Wings", hull.group.name)
        assertEquals(2, hull.count, "only the group's own cards count")

        // The hull encloses both cards, with room to spare.
        val members = state.board!!.items.filter { it.id in setOf(ids[0], ids[1]) }.map { it.pos!! }
        val half = BoardState.BASE_SIZE / 2
        assertTrue(hull.left < members.minOf { it.x } - half, "padded on the left")
        assertTrue(hull.right > members.maxOf { it.x } + half, "padded on the right")
        assertTrue(hull.top < members.minOf { it.y } - half)
        assertTrue(hull.bottom > members.maxOf { it.y } + half)
    }

    @Test
    fun anEmptyOrUnplacedGroupHasNoHull() {
        val state = newState()
        boardWithThreeImages(state)
        state.addGroup("Nobody home")
        state.setLayout(BoardLayouts.FREE)
        assertTrue(state.groupHulls.isEmpty(), "a group without cards draws nothing")
    }

    @Test
    fun everyGroupHasAColourEvenWithoutOneSet() {
        val state = newState()
        state.createBoard(home, "Test")
        state.addGroup("A")
        state.addGroup("B")
        val (first, second) = state.sortedGroups

        assertNull(first.color, "no colour was picked")
        assertTrue(state.accentOfGroup(first).startsWith("#"), "but one is derived for drawing")
        assertTrue(
            state.accentOfGroup(first) != state.accentOfGroup(second),
            "neighbouring groups must be told apart",
        )

        state.cycleGroupColor(first.id)
        val recoloured = state.sortedGroups.first()
        assertEquals(recoloured.color, state.accentOfGroup(recoloured), "an explicit colour wins")
    }

    @Test
    fun draggingAGroupMovesEveryCardInItAndLeavesTheRestAlone() {
        val state = newState()
        val ids = boardWithThreeImages(state)
        state.addGroup("Wings")
        val groupId = state.sortedGroups.single().id
        state.moveToGroup(setOf(ids[0], ids[1]), groupId)
        state.setLayout(BoardLayouts.FREE)
        val before = state.board!!.items.associate { it.id to it.pos!! }

        state.dragGroupBy(groupId, 40f, -25f)
        state.commitLayout()

        val after = state.board!!.items.associate { it.id to it.pos!! }
        listOf(ids[0], ids[1]).forEach { id ->
            assertEquals(before.getValue(id).x + 40f, after.getValue(id).x, "group member moved")
            assertEquals(before.getValue(id).y - 25f, after.getValue(id).y)
        }
        assertEquals(before.getValue(ids[2]).x, after.getValue(ids[2]).x, "the ungrouped card stayed")

        // And it is on disk, not only in memory.
        val reopened = newState()
        reopened.openBoard(state.root!!)
        assertEquals(after.getValue(ids[0]).x, reopened.board!!.items.first { it.id == ids[0] }.pos!!.x)
    }

    @Test
    fun aSingleCardStillMovesOnItsOwnAfterAGroupDrag() {
        val state = newState()
        val ids = boardWithThreeImages(state)
        state.addGroup("Wings")
        val groupId = state.sortedGroups.single().id
        state.moveToGroup(setOf(ids[0], ids[1]), groupId)
        state.setLayout(BoardLayouts.FREE)

        // Dragging the group must not select it, or the next card drag would move everything.
        state.clearSelection()
        state.dragGroupBy(groupId, 10f, 10f)
        state.commitLayout()
        assertTrue(state.selection.isEmpty(), "a group drag leaves the selection alone")

        val before = state.board!!.items.associate { it.id to it.pos!! }
        state.dragBy(ids[0], 15f, 0f)
        state.commitLayout()
        val after = state.board!!.items.associate { it.id to it.pos!! }
        assertEquals(before.getValue(ids[0]).x + 15f, after.getValue(ids[0]).x, "the card moved")
        assertEquals(before.getValue(ids[1]).x, after.getValue(ids[1]).x, "its group mate did not")
    }

    @Test
    fun theGroupLabelSelectsTheWholeGroup() {
        val state = newState()
        val ids = boardWithThreeImages(state)
        state.addGroup("Wings")
        val groupId = state.sortedGroups.single().id
        state.moveToGroup(setOf(ids[0], ids[1]), groupId)
        state.setLayout(BoardLayouts.FREE)

        state.selectGroup(groupId)
        assertEquals(setOf(ids[0], ids[1]), state.selection)
    }

    @Test
    fun aGroupedCardCarriesItsGroupsAccent() {
        val state = newState()
        val ids = boardWithThreeImages(state)
        state.addGroup("Wings")
        val groupId = state.sortedGroups.single().id
        state.moveToGroup(setOf(ids[0]), groupId)

        val grouped = state.item(ids[0])!!
        val loose = state.item(ids[2])!!
        assertEquals(state.accentOfGroup(state.sortedGroups.single()), state.accentOf(grouped))
        assertNull(state.accentOf(loose), "an ungrouped card shows no accent")
    }

    // ---- Grouping and ungrouping (feedback round 3) ----

    @Test
    fun groupingTheSelectionMakesAGroupThatIsVisibleOnTheCanvas() {
        val state = newState()
        val ids = boardWithThreeImages(state)
        state.setLayout(BoardLayouts.FREE)
        state.clickItem(ids[0], ctrl = false, shift = false)
        state.clickItem(ids[1], ctrl = true, shift = false)

        val groupId = state.groupSelection("Wings")

        assertEquals("Wings", state.sortedGroups.single().name)
        assertEquals(setOf(ids[0], ids[1]), state.itemsIn(groupId).map { it.id }.toSet())
        // The point of the exercise: a group made this way draws an area straight away.
        assertEquals(1, state.groupHulls.size)
        assertEquals(2, state.groupHulls.single().count)
    }

    @Test
    fun groupingNothingDoesNothing() {
        val state = newState()
        boardWithThreeImages(state)
        state.clearSelection()
        assertNull(state.groupSelection("Empty"))
        assertTrue(state.sortedGroups.isEmpty())
    }

    @Test
    fun ungroupingCardsTakesThemOutAndTidiesTheEmptyGroupAway() {
        val state = newState()
        val ids = boardWithThreeImages(state)
        state.clickItem(ids[0], ctrl = false, shift = false)
        state.clickItem(ids[1], ctrl = true, shift = false)
        val groupId = state.groupSelection("Wings")!!

        state.ungroupItems(setOf(ids[0]))
        assertEquals(listOf(ids[1]), state.itemsIn(groupId).map { it.id }, "only that card left")
        assertEquals(1, state.sortedGroups.size, "the group still holds someone")

        state.ungroupItems(setOf(ids[1]))
        assertTrue(state.sortedGroups.isEmpty(), "a group with nothing in it is removed")
        assertEquals(3, state.itemsIn(null).size, "every card is back in the Inbox")
    }

    @Test
    fun ungroupingAWholeGroupKeepsItsCards() {
        val state = newState()
        val ids = boardWithThreeImages(state)
        state.selectAll()
        val groupId = state.groupSelection("Everything")!!

        state.ungroup(groupId)

        assertTrue(state.sortedGroups.isEmpty())
        assertEquals(3, state.board!!.items.size, "the cards themselves stay")
        assertTrue(state.board!!.items.all { it.groups.isEmpty() })
    }

    @Test
    fun theDrawerCanFoldGroupsAwayAndBack() {
        val state = newState()
        val ids = boardWithThreeImages(state)
        state.selectAll()
        val groupId = state.groupSelection("Wings")!!

        assertTrue(groupId !in state.drawerCollapsed)
        state.toggleDrawerGroup(groupId)
        assertTrue(groupId in state.drawerCollapsed)
        state.toggleDrawerGroup(groupId)
        assertTrue(groupId !in state.drawerCollapsed)
    }

    @Test
    fun revealingACardSelectsItAndBringsTheCameraOver() {
        val state = newState()
        val ids = boardWithThreeImages(state)
        state.setLayout(BoardLayouts.FREE)
        val target = state.board!!.items.first { it.id == ids[2] }.pos!!

        state.revealItem(ids[2])

        assertEquals(setOf(ids[2]), state.selection)
        assertEquals(target.x, state.camX, "the camera centres on the card")
        assertEquals(target.y, state.camY)
    }

    @Test
    fun revealingAGroupSelectsItAndCentresOnItsArea() {
        val state = newState()
        val ids = boardWithThreeImages(state)
        state.setLayout(BoardLayouts.FREE)
        state.clickItem(ids[0], ctrl = false, shift = false)
        state.clickItem(ids[1], ctrl = true, shift = false)
        val groupId = state.groupSelection("Wings")!!
        val hull = state.groupHulls.single()

        state.pan(900f, 900f) // look somewhere else first
        state.revealGroup(groupId)

        assertEquals(setOf(ids[0], ids[1]), state.selection)
        assertEquals((hull.left + hull.right) / 2, state.camX)
    }

    @Test
    fun theHullEnclosesTallCardsNotJustSquareOnes() {
        val state = newState()
        val ids = boardWithThreeImages(state)
        state.setLayout(BoardLayouts.FREE)
        // A portrait picture is far taller than it is wide.
        state.recordAspect(ids[0], 0.5f)
        state.clickItem(ids[0], ctrl = false, shift = false)
        val groupId = state.groupSelection("Tall")!!

        val hull = state.groupHulls.single()
        val pos = state.item(ids[0])!!.pos!!
        val halfHeight = BoardState.BASE_SIZE / 0.5f / 2f
        assertTrue(
            hull.bottom >= pos.y + halfHeight,
            "the hull must reach past the bottom of a portrait card (${hull.bottom} vs ${pos.y + halfHeight})",
        )
        assertTrue(hull.top <= pos.y - halfHeight, "and past its top")
        assertEquals(groupId, hull.group.id)
    }

    @Test
    fun theMarqueeCatchesACardByItsRealShape() {
        val state = newState()
        val ids = boardWithThreeImages(state)
        state.setLayout(BoardLayouts.FREE)
        state.recordAspect(ids[0], 0.5f) // tall
        state.clearSelection()
        val pos = state.item(ids[0])!!.pos!!

        // A band that only touches the lower half of the tall card - below a square card's edge.
        val y = pos.y + BoardState.BASE_SIZE * 0.8f
        state.startMarquee(pos.x - 5f, y)
        state.updateMarquee(pos.x + 5f, y + 10f)
        state.commitMarquee()

        assertTrue(ids[0] in state.selection, "the tall card reaches down that far")
    }

    @Test
    fun autoPlacementLeavesRoomBetweenRows() {
        val cards = (1..7).map { NoteItem(id = "n$it", text = "x") }
        val board = BoardState.placeMissing(BoardFile(items = cards))
        val rows = board.items.mapNotNull { it.pos?.y }.distinct().sorted()
        assertEquals(2, rows.size, "7 cards in rows of 5 -> two rows")
        assertTrue(
            rows[1] - rows[0] > BoardState.BASE_SIZE * 1.5f,
            "rows need more room than a card's width, or tall cards overlap",
        )
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
