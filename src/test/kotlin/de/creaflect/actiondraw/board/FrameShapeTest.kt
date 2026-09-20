package de.creaflect.actiondraw.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The frame a group gets: the union of its padded boxes, bridged where the pieces do not touch. */
class FrameShapeTest {
    private fun box(l: Float, t: Float, r: Float, b: Float) = listOf(l, t, r, b)

    @Test
    fun boxesThatOverlapAreOnePiece() {
        val pieces = FrameShape.components(listOf(box(0f, 0f, 100f, 100f), box(80f, 20f, 180f, 120f)))
        assertEquals(listOf(box(0f, 0f, 180f, 120f)), pieces)
        assertTrue(FrameShape.bridges(listOf(box(0f, 0f, 100f, 100f), box(80f, 20f, 180f, 120f))).isEmpty())
    }

    @Test
    fun boxesThatMerelyTouchStillJoin() {
        val pieces = FrameShape.components(listOf(box(0f, 0f, 100f, 100f), box(100f, 0f, 200f, 100f)))
        assertEquals(1, pieces.size)
    }

    @Test
    fun anLShapeIsOnePieceWithNoBridge() {
        // A row of two, and one below the first: every box touches another.
        val boxes = listOf(box(0f, 0f, 100f, 100f), box(90f, 0f, 190f, 100f), box(0f, 90f, 100f, 190f))
        assertEquals(1, FrameShape.components(boxes).size)
        assertTrue(FrameShape.bridges(boxes).isEmpty())
    }

    @Test
    fun twoPiecesApartGetOneBridgeBetweenTheirNearestEdges() {
        val a = box(0f, 0f, 100f, 100f)
        val b = box(300f, 0f, 400f, 100f)
        val bridges = FrameShape.bridges(listOf(a, b))
        assertEquals(1, bridges.size)
        val bridge = bridges.single()
        assertEquals(100f, bridge[0] + FrameShape.BRIDGE_WIDTH / 2f, "starts at a's right edge")
        assertEquals(300f, bridge[2] - FrameShape.BRIDGE_WIDTH / 2f, "ends at b's left edge")
        assertTrue(bridge[1] > 0f && bridge[3] < 100f, "and runs through the middle of the row")
    }

    @Test
    fun threePiecesInARowGetTwoBridgesNotThree() {
        val boxes = listOf(box(600f, 0f, 700f, 100f), box(0f, 0f, 100f, 100f), box(300f, 0f, 400f, 100f))
        val bridges = FrameShape.bridges(boxes)
        assertEquals(2, bridges.size, "neighbours are bridged, not every pair")
        assertTrue(bridges.all { it[2] - it[0] < 250f }, "each bridge spans one gap, not the whole row")
    }

    @Test
    fun aPieceBelowAnotherIsBridgedVertically() {
        val bridges = FrameShape.bridges(listOf(box(0f, 0f, 100f, 100f), box(0f, 300f, 100f, 400f)))
        val bridge = bridges.single()
        assertTrue(bridge[3] - bridge[1] > bridge[2] - bridge[0], "taller than it is wide")
        assertEquals(100f, bridge[1] + FrameShape.BRIDGE_WIDTH / 2f)
        assertEquals(300f, bridge[3] - FrameShape.BRIDGE_WIDTH / 2f)
    }
}
