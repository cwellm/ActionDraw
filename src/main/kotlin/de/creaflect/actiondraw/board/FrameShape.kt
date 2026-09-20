package de.creaflect.actiondraw.board

import kotlin.math.max
import kotlin.math.min

/**
 * The shape of a group's frame: the union of its cards' padded boxes, plus a thin bridge between
 * any pieces that do not touch, so a group never reads as two groups.
 *
 * Pure geometry in board units, so the shape a group gets can be tested without a screen. The
 * canvas turns [boxes] and [bridges] into one Skia path (union of rounded rectangles) and both
 * draws and hit-tests that path — what shows is what clicks.
 *
 * A box is `[left, top, right, bottom]`.
 */
object FrameShape {
    /** Width of the connector between two pieces that do not touch, in board units. */
    const val BRIDGE_WIDTH = 26f

    /**
     * Bridges for [boxes]: none when everything already touches, otherwise one per gap between
     * neighbouring pieces, ordered left to right so a group in three clusters gets two bridges,
     * not three. Each bridge is itself a box, running between the two pieces' nearest edges.
     */
    fun bridges(boxes: List<List<Float>>): List<List<Float>> {
        val pieces = components(boxes)
        if (pieces.size < 2) return emptyList()
        val ordered = pieces.sortedBy { (it[0] + it[2]) / 2f }
        return ordered.zipWithNext { a, b -> bridge(a, b) }
    }

    /**
     * Connected pieces of the union: boxes that overlap or touch belong to one piece, whose bounds
     * are returned. Two cards a whole card apart are two pieces.
     */
    fun components(boxes: List<List<Float>>): List<List<Float>> {
        val remaining = boxes.toMutableList()
        val pieces = mutableListOf<List<Float>>()
        while (remaining.isNotEmpty()) {
            var piece = remaining.removeAt(0)
            var grew = true
            while (grew) {
                grew = false
                val it = remaining.iterator()
                while (it.hasNext()) {
                    val box = it.next()
                    if (touches(piece, box)) {
                        piece = listOf(min(piece[0], box[0]), min(piece[1], box[1]), max(piece[2], box[2]), max(piece[3], box[3]))
                        it.remove()
                        grew = true
                    }
                }
            }
            pieces += piece
        }
        return pieces
    }

    private fun touches(a: List<Float>, b: List<Float>): Boolean =
        a[0] <= b[2] && b[0] <= a[2] && a[1] <= b[3] && b[1] <= a[3]

    /** A thin box from the nearest point of [a] to the nearest point of [b], centre line to centre line. */
    private fun bridge(a: List<Float>, b: List<Float>): List<Float> {
        val ax = ((a[0] + a[2]) / 2f).coerceIn(b[0], b[2]).coerceIn(a[0], a[2])
        val ay = ((a[1] + a[3]) / 2f).coerceIn(b[1], b[3]).coerceIn(a[1], a[3])
        val bx = ax.coerceIn(b[0], b[2])
        val by = ay.coerceIn(b[1], b[3])
        // From a's edge to b's edge, one bridge-width thick, in whichever direction the gap runs.
        val half = BRIDGE_WIDTH / 2f
        return listOf(min(ax, bx) - half, min(ay, by) - half, max(ax, bx) + half, max(ay, by) + half)
    }
}
