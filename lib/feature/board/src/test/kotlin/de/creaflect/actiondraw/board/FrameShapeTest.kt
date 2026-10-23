package de.creaflect.actiondraw.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Which boxes form one piece of a frame. What joins separate pieces is covered in RemarksTest. */
class FrameShapeTest {
    private fun box(l: Float, t: Float, r: Float, b: Float) = listOf(l, t, r, b)

    @Test
    fun boxesThatOverlapAreOnePiece() {
        val pieces = FrameShape.components(listOf(box(0f, 0f, 100f, 100f), box(80f, 20f, 180f, 120f)))
        assertEquals(listOf(box(0f, 0f, 180f, 120f)), pieces)
        assertTrue(FrameShape.connectors(listOf(box(0f, 0f, 100f, 100f), box(80f, 20f, 180f, 120f))).isEmpty())
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
        assertTrue(FrameShape.connectors(boxes).isEmpty())
    }
}
