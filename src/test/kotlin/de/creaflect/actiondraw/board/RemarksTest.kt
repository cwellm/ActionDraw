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
 * The remarks from using M5 (2026-09-21): a card leaves its group from the right-click menu,
 * snapping is a preference that starts off, and a group's frame always covers the space between
 * its pictures.
 */
class RemarksTest {
    private val home: File = Files.createTempDirectory("remarks-home").toFile()
    private val config: File = Files.createTempDirectory("remarks-cfg").toFile()
    private val host = object : BoardHost {
        override fun startSession(root: File, images: List<File>, setup: SessionSetup?) = Unit
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

    private fun newState() = BoardState(Settings(config), host)

    /** Flügel (two cards) holding Membran (one card), one loose card. */
    private fun tree(): Triple<BoardState, List<String>, Pair<String, String>> {
        val state = newState()
        state.createBoard(home, "Drachen")
        val root = state.root!!
        state.importExternal(listOf("a.jpg", "b.jpg", "c.jpg", "d.jpg").map { File(root, it).apply { createNewFile() } })
        val ids = state.board!!.items.map { it.id }
        state.clearSelection(); ids.take(2).forEach { state.clickItem(it, ctrl = true, shift = false) }
        val wings = state.groupSelection("Flügel")!!
        state.clearSelection(); state.clickItem(ids[2], ctrl = false, shift = false)
        val membrane = state.groupSelection("Membran", parentId = wings)!!
        state.clearSelection()
        return Triple(state, ids, wings to membrane)
    }

    // ---- Remove from group ----

    @Test
    fun removingACardFromATopLevelGroupPutsItInTheInbox() {
        val (state, ids, groups) = tree()
        state.removeFromGroup(setOf(ids[0]))
        assertTrue(state.item(ids[0])!!.groups.isEmpty(), "in the Inbox")
        assertEquals(listOf(groups.first), state.item(ids[1])!!.groups, "the other card stays")
    }

    @Test
    fun removingACardFromASubgroupLiftsItIntoTheParent() {
        val (state, ids, groups) = tree()
        val (wings, membrane) = groups
        state.removeFromGroup(setOf(ids[2]))
        assertEquals(listOf(wings), state.item(ids[2])!!.groups, "out of Membran, still in Flügel")
        assertNull(state.groupById(membrane), "Membran held nothing else and is tidied away")
    }

    @Test
    fun removingACardThatIsInNoGroupChangesNothing() {
        val (state, ids, _) = tree()
        state.removeFromGroup(setOf(ids[3]))
        assertTrue(state.item(ids[3])!!.groups.isEmpty())
        assertEquals(2, state.sortedGroups.size, "no group was touched")
    }

    // ---- Snapping is a preference, off unless asked for ----

    @Test
    fun snappingStartsOff() {
        assertFalse(newState().snapping)
    }

    @Test
    fun snappingIsRememberedAcrossRuns() {
        val first = newState()
        first.setSnappingPreference(true)
        assertTrue(first.snapping)

        assertTrue(newState().snapping, "a fresh state on the same settings remembers the choice")

        first.setSnappingPreference(false)
        assertFalse(newState().snapping)
    }

    // ---- The frame covers the space between the pictures ----

    private fun box(l: Float, t: Float, r: Float, b: Float) = listOf(l, t, r, b)

    @Test
    fun twoPiecesApartAreJoinedByAFullBandNotAThinBridge() {
        val a = box(0f, 0f, 100f, 100f)
        val b = box(300f, 20f, 400f, 120f)
        val connectors = FrameShape.connectors(listOf(a, b))
        assertEquals(1, connectors.size)
        val polygon = connectors.single()
        val xs = polygon.filterIndexed { i, _ -> i % 2 == 0 }
        val ys = polygon.filterIndexed { i, _ -> i % 2 == 1 }
        assertEquals(0f, xs.min(), "the band reaches back into the first piece")
        assertEquals(400f, xs.max(), "and forward into the second")
        assertEquals(0f, ys.min())
        assertEquals(120f, ys.max())
        assertTrue(polygon.size / 2 >= 4, "a real area, not a line: ${polygon.size / 2} corners")
    }

    @Test
    fun theBandIsTheConvexHullOfBothPieces() {
        // A diagonal pair: the hull is a hexagon, so the space between is covered corner to corner.
        val hull = FrameShape.convexHull(
            listOf(0f to 0f, 100f to 0f, 100f to 100f, 0f to 100f, 300f to 200f, 400f to 200f, 400f to 300f, 300f to 300f),
        )
        assertEquals(6, hull.size / 2, "six corners for two boxes set diagonally")
    }

    @Test
    fun anLStillGetsNoConnector() {
        val boxes = listOf(box(0f, 0f, 100f, 100f), box(90f, 0f, 190f, 100f), box(0f, 90f, 100f, 190f))
        assertTrue(FrameShape.connectors(boxes).isEmpty(), "everything touches: one piece, nothing to join")
    }

    @Test
    fun threePiecesInARowGetTwoBandsNeighbourToNeighbour() {
        val boxes = listOf(box(600f, 0f, 700f, 100f), box(0f, 0f, 100f, 100f), box(300f, 0f, 400f, 100f))
        val bands = FrameShape.connectors(boxes)
        assertEquals(2, bands.size)
        bands.forEach { polygon ->
            val xs = polygon.filterIndexed { i, _ -> i % 2 == 0 }
            assertTrue(xs.max() - xs.min() <= 400f, "each band spans one gap and its two pieces, not the whole row")
        }
    }
}
