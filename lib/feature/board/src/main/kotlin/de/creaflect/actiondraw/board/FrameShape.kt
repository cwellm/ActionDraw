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
    /** Corner radius of the frame's rounded rectangles, in board units. */
    const val RADIUS = 36f

    /**
     * Connectors for [boxes]: none when everything already touches, otherwise one per gap between
     * neighbouring pieces, ordered left to right so a group in three clusters gets two, not three.
     * A connector is the **convex hull of the two pieces** — a full band rather than a thin
     * bridge — so the space between two pictures of a group is always covered: move one picture
     * away and the frame stretches with it instead of thinning to a line. Returned as a polygon,
     * `[x0, y0, x1, y1, …]`, counter-clockwise.
     */
    fun connectors(boxes: List<List<Float>>): List<List<Float>> {
        val pieces = components(boxes)
        if (pieces.size < 2) return emptyList()
        val ordered = pieces.sortedBy { (it[0] + it[2]) / 2f }
        return ordered.zipWithNext { a, b -> convexHull(corners(a) + corners(b)) }
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

    /** True when ([x], [y]) lies inside any of [boxes] or any of the convex [connectors]. */
    fun contains(boxes: List<List<Float>>, connectors: List<List<Float>>, x: Float, y: Float): Boolean =
        boxes.any { x >= it[0] && x <= it[2] && y >= it[1] && y <= it[3] } ||
            connectors.any { insideConvex(it, x, y) }

    /** Point in a convex polygon given as `[x0, y0, x1, y1, …]`, either winding. */
    fun insideConvex(polygon: List<Float>, x: Float, y: Float): Boolean {
        val n = polygon.size / 2
        if (n < 3) return false
        var positive = false
        var negative = false
        for (i in 0 until n) {
            val ax = polygon[2 * i]; val ay = polygon[2 * i + 1]
            val bx = polygon[2 * ((i + 1) % n)]; val by = polygon[2 * ((i + 1) % n) + 1]
            val cross = (bx - ax) * (y - ay) - (by - ay) * (x - ax)
            if (cross > 0f) positive = true
            if (cross < 0f) negative = true
            if (positive && negative) return false
        }
        return true
    }

    private fun corners(box: List<Float>): List<Pair<Float, Float>> =
        listOf(box[0] to box[1], box[2] to box[1], box[2] to box[3], box[0] to box[3])

    /** Andrew's monotone chain; the polygon as a flat list of coordinates. */
    fun convexHull(points: List<Pair<Float, Float>>): List<Float> {
        val sorted = points.distinct().sortedWith(compareBy({ it.first }, { it.second }))
        if (sorted.size < 3) return sorted.flatMap { listOf(it.first, it.second) }
        fun cross(o: Pair<Float, Float>, a: Pair<Float, Float>, b: Pair<Float, Float>) =
            (a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first)
        val lower = mutableListOf<Pair<Float, Float>>()
        for (p in sorted) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0f) lower.removeAt(lower.size - 1)
            lower += p
        }
        val upper = mutableListOf<Pair<Float, Float>>()
        for (p in sorted.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0f) upper.removeAt(upper.size - 1)
            upper += p
        }
        val hull = lower.dropLast(1) + upper.dropLast(1)
        return hull.flatMap { listOf(it.first, it.second) }
    }
}
