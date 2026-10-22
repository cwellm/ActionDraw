package de.creaflect.actiondraw.board

import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.SessionSetup
import de.creaflect.actiondraw.Settings
import de.creaflect.actiondraw.ViewMode
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * One level of subgroups: a group inside a group, and no deeper. What "in the group" means once
 * there is a tree — for drawing, counting, selecting, dragging and dissolving — is pinned here.
 */
class SubgroupTest {
    private val home: File = Files.createTempDirectory("subgroup-home").toFile()
    private val config: File = Files.createTempDirectory("subgroup-cfg").toFile()
    private var started: List<File> = emptyList()

    private val host = object : BoardHost {
        override fun startSession(root: File, images: List<File>, setup: SessionSetup?) { started = images }
        override fun showBoard() = Unit
        override fun showBoardList() = Unit
        override fun leaveBoard() = Unit
        override fun currentSetup() = SessionSetup(null, 120, true, ViewMode.NONE, GridMode.OFF)
    }

    @AfterTest
    fun cleanup() {
        home.deleteRecursively()
        config.deleteRecursively()
    }

    /**
     * "Flügel" with two cards, holding the subgroup "Membran" with one card; "Köpfe" beside it
     * with one card; one loose card in the Inbox.
     */
    private fun tree(): Tree {
        val state = BoardState(Settings(config), host)
        state.createBoard(home, "Drachen")
        val root = state.root!!
        val files = listOf("a.jpg", "b.jpg", "c.jpg", "d.jpg", "e.jpg").map { File(root, it).apply { createNewFile() } }
        state.importExternal(files)
        val ids = state.board!!.items.map { it.id }

        state.clearSelection(); ids.take(2).forEach { state.clickItem(it, ctrl = true, shift = false) }
        val wings = state.groupSelection("Flügel")!!
        state.clearSelection(); state.clickItem(ids[2], ctrl = false, shift = false)
        val membrane = state.groupSelection("Membran", parentId = wings)!!
        state.clearSelection(); state.clickItem(ids[3], ctrl = false, shift = false)
        val heads = state.groupSelection("Köpfe")!!
        state.clearSelection()
        return Tree(state, wings, membrane, heads, ids)
    }

    private data class Tree(val state: BoardState, val wings: String, val membrane: String, val heads: String, val ids: List<String>)

    // ---- Structure ----

    @Test
    fun aGroupCanBeMadeInsideAnother() {
        val t = tree()
        assertEquals(t.wings, t.state.groupById(t.membrane)!!.parentId)
        assertEquals("Flügel", t.state.parentOf(t.membrane)?.name)
        assertEquals(listOf("Membran"), t.state.subgroupsOf(t.wings).map { it.name })
        assertNull(t.state.parentOf(t.wings), "a top-level group has nobody above it")
    }

    @Test
    fun exactlyOneLevel() {
        val t = tree()
        t.state.clearSelection(); t.state.clickItem(t.ids[4], ctrl = false, shift = false)
        val deeper = t.state.groupSelection("Adern", parentId = t.membrane)!!
        assertNull(t.state.groupById(deeper)!!.parentId, "asked to nest under a subgroup: it lands at the top instead")

        assertFalse(t.state.setGroupParent(t.wings, t.heads), "a group that has subgroups cannot become one")
        assertFalse(t.state.setGroupParent(t.heads, t.membrane), "and a subgroup cannot be a parent")
        assertTrue(t.state.setGroupParent(t.heads, t.wings), "but a plain group can move inside a top-level one")
        assertEquals(setOf("Köpfe", "Membran"), t.state.subgroupsOf(t.wings).map { it.name }.toSet())
    }

    @Test
    fun sectionsListASubgroupRightUnderItsParent() {
        val t = tree()
        assertEquals(
            listOf(null, "Flügel", "Membran", "Köpfe"),
            t.state.sections.map { it.first?.name },
            "Inbox, then each top-level group followed by its own subgroups",
        )
    }

    @Test
    fun aFileFromTheFutureIsFlattenedToOneLevelOnLoad() {
        val a = BoardGroup(id = "a", name = "A")
        val b = BoardGroup(id = "b", name = "B", parentId = "a")
        val c = BoardGroup(id = "c", name = "C", parentId = "b") // two deep
        val orphan = BoardGroup(id = "o", name = "O", parentId = "gone")
        val fixed = BoardStore.oneLevelDeep(listOf(a, b, c, orphan))
        assertEquals(mapOf("a" to null, "b" to "a", "c" to null, "o" to null), fixed.associate { it.id to it.parentId })
    }

    // ---- What "in the group" means ----

    @Test
    fun aParentCountsDrawsAndSelectsItsSubgroupsCardsToo() {
        val t = tree()
        assertEquals(3, t.state.itemsInTree(t.wings).size, "two of its own and one in Membran")
        assertEquals(2, t.state.itemsIn(t.wings).size, "directly, only its own")

        t.state.drawGroup(t.wings)
        assertEquals(3, started.size, "Draw on the parent draws the subgroup's card as well")

        t.state.selectGroup(t.wings)
        assertEquals(t.ids.take(3).toSet(), t.state.selection)

        t.state.selectGroup(t.membrane)
        assertEquals(setOf(t.ids[2]), t.state.selection, "selecting the subgroup is just the subgroup")
    }

    @Test
    fun draggingTheParentMovesTheSubgroupsCardsWithIt() {
        val t = tree()
        t.state.setLayout(BoardLayouts.FREE)
        val before = t.state.item(t.ids[2])!!.pos!!.x

        t.state.dragGroupBy(t.wings, 100f, 0f)

        assertEquals(before + 100f, t.state.item(t.ids[2])!!.pos!!.x, "the Membran card came along")
        assertEquals(t.state.item(t.ids[3])!!.pos!!.x, t.state.item(t.ids[3])!!.pos!!.x, "Köpfe stayed")
    }

    @Test
    fun theParentsHullEnclosesTheSubgroupsHull() {
        val t = tree()
        t.state.setLayout(BoardLayouts.FREE)
        val hulls = t.state.groupHulls.associateBy { it.group.id }
        val parent = hulls.getValue(t.wings)
        val child = hulls.getValue(t.membrane)
        assertTrue(parent.left < child.left && parent.right > child.right, "wider than its subgroup")
        assertTrue(parent.top < child.top && parent.bottom > child.bottom, "and taller")
        assertEquals(3, parent.count)
        assertEquals(
            listOf(t.wings, t.heads, t.membrane).sorted(),
            t.state.groupHulls.map { it.group.id }.sorted(),
        )
        assertTrue(
            t.state.groupHulls.indexOfFirst { it.group.id == t.wings } < t.state.groupHulls.indexOfFirst { it.group.id == t.membrane },
            "the parent is drawn first, so the subgroup sits on top of its tint",
        )
    }

    @Test
    fun collapsingTheParentFoldsTheSubgroupAway() {
        val t = tree()
        t.state.toggleCollapsed(t.wings)
        assertTrue(t.state.isFolded(t.state.groupById(t.membrane)))
        assertFalse(t.ids[2] in t.state.visibleOrder.map { it.id }, "the Membran card is out of the visible order")
        assertTrue(t.ids[3] in t.state.visibleOrder.map { it.id }, "Köpfe is unaffected")
    }

    // ---- Taking it apart ----

    @Test
    fun dissolvingASubgroupLiftsItsCardsIntoTheParent() {
        val t = tree()
        t.state.ungroup(t.membrane)
        assertNull(t.state.groupById(t.membrane))
        assertEquals(listOf(t.wings), t.state.item(t.ids[2])!!.groups, "now a plain member of Flügel, not of the Inbox")
    }

    @Test
    fun deletingAParentLiftsItsSubgroupsToTheTopAndSendsItsOwnCardsToTheInbox() {
        val t = tree()
        t.state.deleteGroup(t.wings)
        assertNull(t.state.groupById(t.wings))
        assertNull(t.state.groupById(t.membrane)!!.parentId, "Membran is a group in its own right now")
        assertEquals(listOf(t.membrane), t.state.item(t.ids[2])!!.groups, "still holding its card")
        assertTrue(t.state.item(t.ids[0])!!.groups.isEmpty(), "Flügel's own cards went to the Inbox")
    }

    @Test
    fun aParentWhoseOnlyContentIsASubgroupIsNotPrunedAsEmpty() {
        val t = tree()
        // Take Flügel's own two cards out; it still holds Membran.
        t.state.ungroupItems(t.ids.take(2).toSet())
        assertTrue(t.state.groupById(t.wings) != null, "a parent holding a subgroup is not empty")
        assertEquals(t.wings, t.state.groupById(t.membrane)!!.parentId)
    }

    @Test
    fun groupingCardsThatShareAParentOffersThatParent() {
        val t = tree()
        t.state.clearSelection()
        t.state.clickItem(t.ids[0], ctrl = true, shift = false)
        t.state.clickItem(t.ids[2], ctrl = true, shift = false) // one in Flügel, one in Membran
        assertEquals("Flügel", t.state.sharedParentOfSelection()?.name, "both live under Flügel one way or another")

        t.state.startGrouping()
        val editor = t.state.editor as BoardEditor.GroupSelection
        assertEquals(t.wings, editor.parentId, "and the dialog starts with that parent picked")
    }

    /**
     * `source` marks a linked concept's group. A concept group whose link is gone is dropped on
     * open, with its borrowed cards — but only in the form the app writes (id `concept:<id>`).
     * Anything else that carries a source is left as found, and the board's own cards are never
     * touched either way.
     */
    @Test
    fun aConceptGroupWithoutItsLinkGoesButTheBoardsOwnCardsNever() {
        val t = tree()
        val ghostGroup = BoardGroup(id = "concept:x", name = "Ghost", order = 9, source = "concept:x")
        val ghostCard = ImageItem(id = "ghost", path = "concept:x/a.jpg", groups = listOf("concept:x"))
        val odd = t.state.board!!.copy(
            groups = t.state.board!!.groups.map { if (it.id == t.heads) it.copy(source = "concept:y") else it } + ghostGroup,
            items = t.state.board!!.items + ghostCard,
        )
        BoardStore.save(t.state.root!!, odd)

        val reopened = BoardState(Settings(config), host).also { it.openBoard(t.state.root!!) }

        assertNull(reopened.groupById("concept:x"), "no link, no group")
        assertNull(reopened.item("ghost"), "and its borrowed card went with it")
        assertEquals("concept:y", reopened.groupById(t.heads)!!.source, "an odd source is left as found")
        assertEquals(listOf(t.ids[3]), reopened.itemsIn(t.heads).map { it.id }, "with the board's own card still in it")
        assertEquals(5, reopened.board!!.items.size, "not one of the board's own cards lost")
    }
}
