package de.creaflect.actiondraw.collage

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Where a piece is, worked out from where it lies (F10.6). A piece is its outline in its picture's
 * pixels, laid on the mat: the middle of the outline's bounds at the piece's place, turned, scaled
 * and maybe flipped about it. The outline is what is drawn and what a click hits — one shape for
 * both, as on the board.
 */

/** The outline's bounds in the picture's pixels: left, top, right, bottom. */
fun Piece.bounds(): FloatArray {
    var l = Float.MAX_VALUE
    var t = Float.MAX_VALUE
    var r = -Float.MAX_VALUE
    var b = -Float.MAX_VALUE
    for (p in outline) {
        l = min(l, p.x)
        t = min(t, p.y)
        r = max(r, p.x)
        b = max(b, p.y)
    }
    return floatArrayOf(l, t, r, b)
}

/** The middle of the outline's bounds: what the piece lies at, turns and grows about. */
fun Piece.middle(): Spot = bounds().let { Spot((it[0] + it[2]) / 2f, (it[1] + it[3]) / 2f) }

/** Where [p], a place on the piece's picture, lies on the mat. */
fun Piece.toMat(p: Spot): Spot {
    val c = middle()
    val dx = (p.x - c.x) * scale * (if (flipped) -1f else 1f)
    val dy = (p.y - c.y) * scale
    val a = Math.toRadians(turn.toDouble())
    val cs = cos(a).toFloat()
    val sn = sin(a).toFloat()
    return Spot(x + dx * cs - dy * sn, y + dx * sn + dy * cs)
}

/** Where [m], a place on the mat, lies on the piece's picture. */
fun Piece.toPicture(m: Spot): Spot {
    val c = middle()
    val a = Math.toRadians(turn.toDouble())
    val cs = cos(a).toFloat()
    val sn = sin(a).toFloat()
    val ux = m.x - x
    val uy = m.y - y
    val dx = (ux * cs + uy * sn) / scale * (if (flipped) -1f else 1f)
    val dy = (-ux * sn + uy * cs) / scale
    return Spot(c.x + dx, c.y + dy)
}

/** Whether [m], a place on the mat, is on the piece: inside its outline. */
operator fun Piece.contains(m: Spot): Boolean = insideOf(outline, toPicture(m))

/** The outline's corners as they lie on the mat. */
fun Piece.onMat(): List<Spot> = outline.map(::toMat)

/** Whether [p] is inside the polygon [corners] — an odd number of its edges crossed on the way out to the right. */
fun insideOf(corners: List<Spot>, p: Spot): Boolean {
    var inside = false
    var j = corners.lastIndex
    for (i in corners.indices) {
        val a = corners[i]
        val b = corners[j]
        if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
        j = i
    }
    return inside
}
