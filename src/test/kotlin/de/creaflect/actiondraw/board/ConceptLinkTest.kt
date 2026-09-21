package de.creaflect.actiondraw.board

import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.SessionSetup
import de.creaflect.actiondraw.Settings
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.concept.ConceptHost
import de.creaflect.actiondraw.concept.ConceptState
import de.creaflect.actiondraw.concept.ConceptStore
import de.creaflect.actiondraw.image.SeenStore
import de.creaflect.actiondraw.image.relKey
import de.creaflect.actiondraw.isInside
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A concept linked onto a board: one group, borrowed cards, the concept's files — and the
 * board's hands kept off what is the concept's.
 */
class ConceptLinkTest {
    private val home: File = Files.createTempDirectory("link-boards").toFile()
    private val conceptsHome: File = Files.createTempDirectory("link-concepts").toFile()
    private val elsewhere: File = Files.createTempDirectory("link-else").toFile()
    private val config: File = Files.createTempDirectory("link-cfg").toFile()
    private var started: List<File> = emptyList()
    private var shownConcept: String? = null

    private lateinit var boards: BoardState

    private val conceptHost = object : ConceptHost {
        override fun showConcepts() = Unit
        override fun showConcept() = Unit
        override fun leaveConcepts() = Unit
        override fun boardsFor(conceptId: String) = boards.boardsFor(conceptId)
        override fun setLinked(conceptId: String, board: File, linked: Boolean) {
            boards.setLinked(conceptId, board, linked)
        }
        override fun unlinkEverywhere(conceptId: String) = boards.unlinkEverywhere(conceptId)
    }
    private val boardHost = object : BoardHost {
        override fun startSession(root: File, images: List<File>, setup: SessionSetup?) { started = images }
        override fun showBoard() = Unit
        override fun showBoardList() = Unit
        override fun leaveBoard() = Unit
        override fun currentSetup() = SessionSetup(null, 120, true, ViewMode.NONE, GridMode.OFF)
        override fun showConcept(id: String) { shownConcept = id }
    }

    private val concepts: ConceptState = ConceptState(Settings(config), conceptHost).also { it.setConceptsHomeDir(conceptsHome) }

    @AfterTest
    fun cleanup() {
        listOf(home, conceptsHome, elsewhere, config).forEach { it.deleteRecursively() }
    }

    /** A dragon with a picture, a note and a link; a board with one picture of its own. */
    private fun setUp(): Pair<String, BoardState> {
        concepts.createConcept("Drache", "creature")
        concepts.addPictures(listOf(File(elsewhere, "wing.jpg").apply { createNewFile() }))
        concepts.saveNote(null, "# Wings\nmembrane folds")
        concepts.saveLink(null, "https://example.com/dragons", "Dragons")
        val id = concepts.concept!!.id

        boards = BoardState(Settings(config), boardHost, concepts = concepts)
        boards.setBoardsHomeDir(home)
        boards.createBoard(home, "Buch")
        boards.importExternal(listOf(File(boards.root!!, "own.jpg").apply { createNewFile() }))
        return id to boards
    }

    private fun BoardState.conceptGroup(id: String): BoardGroup? = groupById(ConceptLink.groupId(id))
    private fun BoardState.borrowed(id: String): List<BoardItem> = itemsIn(ConceptLink.groupId(id))

    @Test
    fun linkingShowsTheConceptAsAGroupOfBorrowedCards() {
        val (id, board) = setUp()

        assertTrue(board.linkConcept(id))

        val group = assertNotNull(board.conceptGroup(id))
        assertTrue(group.isConcept)
        assertEquals("Drache", group.name)
        assertEquals(id, group.conceptId)
        assertEquals(listOf(id), board.linkedConcepts)
        val cards = board.borrowed(id)
        assertEquals(3, cards.size, "picture, note and link")
        assertTrue(cards.all(board::isBorrowed))
        val picture = cards.filterIsInstance<ImageItem>().single()
        val file = assertNotNull(board.fileOf(picture))
        assertTrue(file.isInside(concepts.root!!), "read from the concept's folder: $file")
        assertTrue(file.isFile)
        assertEquals(listOf(id), board.board!!.concepts, "and the link is in the board file")
        assertFalse(board.linkConcept(id), "linking twice is nothing")
    }

    @Test
    fun aLinkedGroupIsDrawnFromLikeAnyOther() {
        val (id, board) = setUp()
        board.linkConcept(id)

        board.drawGroup(ConceptLink.groupId(id))

        assertEquals(1, started.size)
        assertTrue(started.single().isInside(concepts.root!!))
    }

    @Test
    fun practiceMemoryOfABorrowedCardIsKeyedTheWayASessionWritesIt() {
        val (id, board) = setUp()
        board.linkConcept(id)
        val picture = board.borrowed(id).filterIsInstance<ImageItem>().single()
        assertEquals(BoardState.Practice.UNSEEN, board.practiceOf(picture))

        // A session started from this board records the concept's file relative to the board.
        SeenStore.write(board.root!!, setOf(relKey(board.root!!, board.fileOf(picture)!!)))
        board.refreshPractice()

        assertEquals(BoardState.Practice.SEEN, board.practiceOf(picture))
    }

    @Test
    fun aConceptGroupIsNotTheBoardsToChange() {
        val (id, board) = setUp()
        board.linkConcept(id)
        val gid = ConceptLink.groupId(id)
        val ownCard = board.board!!.items.first { !board.isBorrowed(it) }.id
        board.clearSelection()
        board.clickItem(ownCard, ctrl = false, shift = false)
        val ownGroup = assertNotNull(board.groupSelection("Eigene"))
        board.clearSelection()

        board.renameGroup(gid, "Lindwurm")
        board.cycleGroupColor(gid)
        assertEquals("Drache", board.conceptGroup(id)!!.name)
        assertNull(board.conceptGroup(id)!!.color)

        assertFalse(board.setGroupParent(gid, ownGroup), "a concept group does not nest")
        assertFalse(board.setGroupParent(ownGroup, gid), "and nothing nests inside it")
        assertTrue(board.possibleParents(ownGroup).none { it.isConcept })
        assertNull(board.groupById(ownGroup)!!.parentId)
    }

    @Test
    fun deletingOrDissolvingAConceptGroupOnlyUnlinksIt() {
        val (id, board) = setUp()
        board.linkConcept(id)

        board.deleteGroup(ConceptLink.groupId(id))

        assertNull(board.conceptGroup(id))
        assertTrue(board.linkedConcepts.isEmpty())
        assertTrue(board.board!!.items.none(board::isBorrowed), "the borrowed cards went with it")
        assertEquals(1, board.board!!.items.size, "the board's own card stays")
        assertEquals(3, ConceptStore.peek(concepts.root!!)!!.items.size, "the concept is untouched")

        board.linkConcept(id)
        board.ungroup(ConceptLink.groupId(id))
        assertNull(board.conceptGroup(id))
    }

    @Test
    fun borrowedCardsStayWithTheirConcept() {
        val (id, board) = setUp()
        board.linkConcept(id)
        val gid = ConceptLink.groupId(id)
        val borrowedIds = board.borrowed(id).map { it.id }.toSet()
        val ownId = board.board!!.items.first { !board.isBorrowed(it) }.id

        board.removeItems(borrowedIds)
        assertEquals(3, board.borrowed(id).size, "not removed")
        assertTrue(board.importNotice!!.contains("stay with their concept"), board.importNotice)

        board.removeFromGroup(borrowedIds)
        board.ungroupItems(borrowedIds)
        board.moveToGroup(borrowedIds, null)
        assertEquals(3, board.borrowed(id).size, "not moved out")

        // Grouping a mixed selection groups only the board's own card.
        board.clearSelection()
        (borrowedIds + ownId).forEach { board.clickItem(it, ctrl = true, shift = false) }
        val newGroup = assertNotNull(board.groupSelection("Eigene"))
        assertEquals(listOf(ownId), board.itemsIn(newGroup).map { it.id })
        assertEquals(3, board.borrowed(id).size)
        assertTrue(board.conceptGroup(id) != null, "an empty concept group is never pruned away either")
        assertEquals(gid, board.borrowed(id).first().groups.single())
    }

    @Test
    fun anEmptyConceptGroupIsNeverPrunedAway() {
        val (_, board) = setUp()
        concepts.createConcept("Leer") // nothing in it yet
        val empty = concepts.concept!!.id
        board.linkConcept(empty)
        assertNotNull(board.conceptGroup(empty))

        // Grouping and ungrouping prune groups that hold nothing — a link is not a container.
        val own = board.board!!.items.first { !board.isBorrowed(it) }.id
        board.clearSelection()
        board.clickItem(own, ctrl = false, shift = false)
        val group = assertNotNull(board.groupSelection("Eigene"))
        board.ungroupItems(setOf(own))

        assertNull(board.groupById(group), "the ordinary group, left empty, is gone")
        assertNotNull(board.conceptGroup(empty), "the empty concept group is still the link")
    }

    @Test
    fun whatTheConceptGainsOrLosesTheBoardSeesOnReopen() {
        val (id, board) = setUp()
        board.linkConcept(id)
        val picture = board.borrowed(id).filterIsInstance<ImageItem>().single()
        board.toggleStar(setOf(picture.id))
        val noteId = board.borrowed(id).filterIsInstance<NoteItem>().single().id

        concepts.addPictures(listOf(File(elsewhere, "tail.jpg").apply { createNewFile() }))
        concepts.removeItems(setOf(noteId))
        board.openBoard(board.root!!)

        val cards = board.borrowed(id)
        assertEquals(3, cards.size, "one picture more, the note gone")
        assertEquals(2, cards.filterIsInstance<ImageItem>().size)
        assertTrue(cards.none { it.id == noteId })
        assertTrue(cards.filterIsInstance<ImageItem>().first { it.id == picture.id }.starred, "the board's star survives")
    }

    @Test
    fun theConceptsOwnArrangementNeverReachesABoard() {
        val (id, board) = setUp()
        concepts.setLayout(BoardLayouts.FREE) // places every card in the concept's own space
        assertTrue(concepts.items.all { it.pos != null })

        board.linkConcept(id)

        assertTrue(board.borrowed(id).all { it.pos == null }, "a grid board: no places, and none borrowed")
    }

    @Test
    fun aRenamedConceptRenamesItsGroupOnEveryBoard() {
        val (id, board) = setUp()
        board.linkConcept(id)
        concepts.rename("Feuerdrache")

        board.openBoard(board.root!!)

        assertEquals("Feuerdrache", board.conceptGroup(id)!!.name)
    }

    @Test
    fun movingTheBoardsOwnCardIntoTheConceptGroupAddsItToTheConcept() {
        val (id, board) = setUp()
        board.linkConcept(id)
        board.setLayout(BoardLayouts.FREE)
        val own = board.board!!.items.filterIsInstance<ImageItem>().first { !board.isBorrowed(it) }
        val place = assertNotNull(own.pos)
        val before = ConceptStore.peek(concepts.root!!)!!.items.filterIsInstance<ImageItem>().size

        board.moveToGroup(setOf(own.id), ConceptLink.groupId(id))

        val inConcept = ConceptStore.peek(concepts.root!!)!!.items.filterIsInstance<ImageItem>()
        assertEquals(before + 1, inConcept.size, "the concept has it now")
        assertTrue(File(concepts.root!!, inConcept.last().path).isFile, "copied into the concept's folder")
        assertNull(board.item(own.id), "the board's own copy made way")
        val borrowed = board.borrowed(id).filterIsInstance<ImageItem>()
        assertEquals(2, borrowed.size)
        assertEquals(place, borrowed.first { it.id == inConcept.last().id }.pos, "in the same place")
        assertTrue(board.importNotice!!.contains("belongs to Drache"), board.importNotice)
    }

    @Test
    fun linksAreSetAndClearedOnClosedBoardsToo() {
        val (id, board) = setUp()
        val a = board.root!!
        board.createBoard(home, "Zweites") // opens it; "Buch" is closed now
        val b = board.root!!

        assertTrue(board.setLinked(id, a, linked = true))
        val stored = assertNotNull(BoardStore.peek(a))
        assertEquals(listOf(id), stored.concepts)
        assertEquals(3, stored.items.count { ConceptLink.isBorrowed(it) })
        assertTrue(stored.groups.any { it.conceptId == id })
        assertEquals(listOf("Buch" to true, "Zweites" to false), board.boardsFor(id).map { it.name to it.linked })

        assertEquals(listOf("Buch"), board.unlinkEverywhere(id))
        assertTrue(BoardStore.peek(a)!!.concepts.isEmpty())
        assertTrue(BoardStore.peek(a)!!.items.none { ConceptLink.isBorrowed(it) })
        assertTrue(BoardStore.peek(b)!!.concepts.isEmpty())
    }

    @Test
    fun deletingAConceptTakesItOffEveryBoardFirst() {
        val (id, board) = setUp()
        board.linkConcept(id)

        val message = concepts.deleteConcept(concepts.root!!, alsoFolder = true)

        assertTrue(message.contains("Unlinked from Buch"), message)
        assertNull(board.conceptGroup(id))
        assertTrue(board.board!!.items.none(board::isBorrowed))
        assertEquals(1, board.board!!.items.size)
    }

    @Test
    fun aClosedBoardOpenedLaterSeesTheConcept() {
        val (id, board) = setUp()
        val a = board.root!!
        board.createBoard(home, "Zweites")
        board.setLinked(id, a, linked = true)

        board.openBoard(a)

        assertEquals("Drache", board.conceptGroup(id)!!.name)
        assertEquals(3, board.borrowed(id).size)
        assertNotNull(board.fileOf(board.borrowed(id).filterIsInstance<ImageItem>().single()))
    }

    @Test
    fun validateKeepsBorrowedPicturesThatAreNotInTheBoardsFolder() {
        val root = File(home, "Loose").apply { mkdirs() }
        val borrowed = ImageItem(id = "b", path = ConceptLink.borrowedPath("c1", "_imported/wing.jpg"), groups = listOf(ConceptLink.groupId("c1")))
        val lost = ImageItem(id = "l", path = "gone.jpg")
        val board = BoardFile(name = "Loose", items = listOf(borrowed, lost))

        val checked = BoardStore.validate(board, root)

        assertEquals(listOf("b"), checked.items.map { it.id }, "the borrowed card stays, the lost one goes")
    }

    @Test
    fun openConceptFromTheBoardGoesThroughTheHost() {
        val (id, board) = setUp()
        board.linkConcept(id)
        board.showConcept(id)
        assertEquals(id, shownConcept)
    }
}
