package de.creaflect.actiondraw.collage

import java.util.Random
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max

/*
 * Cutting a piece (F10.6): its outline divided along a path — the scissors', or a tear's — into the
 * parts it falls into, as paper does. A cut from edge to edge parts it, as often as the path goes
 * through; a cut all the way round a part of it cuts that part out — and where the path stays inside
 * the piece, the scissors come in from the nearest edge, so what is left is still one outline. The
 * work is done in doubles, so a cut's own corners lie exactly on the parts' new edges.
 */

/** How far along a path two crossings may be and still be one: a segment's fraction. */
private const val T_EPS = 1e-9

/** The smallest part a cut leaves, in square pixels: anything thinner falls away. */
private const val MIN_AREA = 2.0

/** How far a tear wanders off its path, at most, and how often it is followed: in mat units. */
const val TEAR_REACH = 7f
const val TEAR_STEP = 3f

/** A place while a cut is worked out. */
private class P(val x: Double, val y: Double)

/** Where a path crosses an edge of an outline: [t] along the path (its segment, and how far into it), on edge [edge] at [u] along it, at [at]. */
private class Crossing(val t: Double, val edge: Int, val u: Double, val at: P)

/** A stretch of the path inside the outline [part], from where it comes in to where it goes out. */
private class Run(val part: Int, val from: Crossing, val to: Crossing)

/**
 * The parts [outline] falls into, cut along [path] (both in the same coordinates): divided wherever
 * the path goes through it from edge to edge; [closed], whatever the path goes round is cut out.
 * Just [outline] itself where the path does not go through it. Slivers too thin to hold fall away.
 */
fun divide(outline: List<Spot>, path: List<Spot>, closed: Boolean): List<List<Spot>> {
    val shape = outline.map { P(it.x.toDouble(), it.y.toDouble()) }.cleaned()
    var cut = path.map { P(it.x.toDouble(), it.y.toDouble()) }.distinctRuns()
    if (closed && cut.size > 2 && same(cut.first(), cut.last())) cut = cut.dropLast(1)
    if (shape.size < 3 || cut.size < 2) return listOf(outline)
    val parts = if (closed && cut.size >= 3) round(shape, cut) else across(listOf(shape), cut)
    if (parts.size < 2) return listOf(outline)
    return parts.map { part -> part.map { Spot(it.x.toFloat(), it.y.toFloat()) } }
}

/** A cut all the way round [ring]: from edge to edge wherever it crosses [shape], else — inside it — cut out, the scissors coming in from the nearest edge. */
private fun round(shape: List<P>, ring: List<P>): List<List<P>> {
    val loop = ring + ring.first()
    val groups = crossings(shape, loop, 0.0)
    if (groups.isEmpty()) {
        // No edge crossed: the ring is inside the piece, or round all of it, or beside it.
        if (!inside(shape, ring.first())) return listOf(shape)
        return keyhole(shape, ring)
    }
    // Started where the ring is outside, each stretch of it inside is a cut from edge to edge.
    val start = outsideStart(shape, loop, groups) ?: return listOf(shape)
    return across(listOf(shape), start)
}

/** [parts] cut along [cut] from edge to edge, each stretch of it inside one of them in turn, from its start to its end. */
private fun across(start: List<List<P>>, cut: List<P>): List<List<P>> {
    val least = max(MIN_AREA, start.sumOf { abs(area(it)) } * 1e-7)
    val parts = start.toMutableList()
    var t = 0.0
    repeat(10_000) {
        val run = parts.indices.mapNotNull { firstRun(it, parts[it], cut, t) }.minByOrNull { it.from.t } ?: return parts
        val halves = split(parts[run.part], cut, run).filter { abs(area(it)) > least }
        parts.removeAt(run.part)
        parts.addAll(run.part, halves)
        t = run.to.t
    }
    return parts
}

/** The first stretch of [cut], from [from] on, inside [shape] from one crossing of its edges to the next. */
private fun firstRun(index: Int, shape: List<P>, cut: List<P>, from: Double): Run? {
    val groups = crossings(shape, cut, from)
    for (i in 0 until groups.size - 1) {
        val t0 = groups[i].first().t
        val t1 = groups[i + 1].first().t
        if (t1 - t0 <= T_EPS || !inside(shape, pointAt(cut, (t0 + t1) / 2))) continue
        val turning = area(shape)
        return Run(index, pick(groups[i], shape, cut, turning, inward = true), pick(groups[i + 1], shape, cut, turning, inward = false))
    }
    return null
}

/**
 * Of crossings at one place — a corner, or the two edges of the way the scissors came in before — the
 * one whose edge has the inside on the side the cut is: ahead where it comes in, behind where it goes out.
 */
private fun pick(group: List<Crossing>, shape: List<P>, cut: List<P>, turning: Double, inward: Boolean): Crossing {
    if (group.size == 1) return group[0]
    val t = group[0].t
    val toward = if (inward) {
        val k = floor(t + T_EPS).toInt().coerceIn(0, cut.size - 2)
        P(cut[k + 1].x - cut[k].x, cut[k + 1].y - cut[k].y)
    } else {
        val k = (ceil(t - T_EPS).toInt() - 1).coerceIn(0, cut.size - 2)
        P(cut[k].x - cut[k + 1].x, cut[k].y - cut[k + 1].y)
    }
    val n = shape.size
    return group.firstOrNull { c ->
        val a = shape[c.edge]
        val b = shape[(c.edge + 1) % n]
        ((b.x - a.x) * toward.y - (b.y - a.y) * toward.x) * turning > 0
    } ?: group[0]
}

/** [shape] parted by the stretch of [cut] in [run]: the side ahead of where it goes out, and the side ahead of where it came in. */
private fun split(shape: List<P>, cut: List<P>, run: Run): List<List<P>> {
    val chord = ArrayList<P>()
    chord += run.from.at
    for (k in floor(run.from.t).toInt() + 1..ceil(run.to.t).toInt() - 1) {
        if (k > run.from.t + T_EPS && k < run.to.t - T_EPS) chord += cut[k]
    }
    chord += run.to.at
    return listOf(
        (chord + onward(shape, run.to, run.from)).cleaned(),
        (chord.asReversed() + onward(shape, run.from, run.to)).cleaned(),
    )
}

/** The corners of [shape] met going on along its edges from [from] to [to]. */
private fun onward(shape: List<P>, from: Crossing, to: Crossing): List<P> {
    if (from.edge == to.edge && from.u <= to.u) return emptyList()
    val n = shape.size
    val met = ArrayList<P>()
    var i = (from.edge + 1) % n
    while (true) {
        met += shape[i]
        if (i == to.edge) return met
        i = (i + 1) % n
    }
}

/** Every place [cut], from [from] on, crosses an edge of [shape], in order along it; crossings at one place together. */
private fun crossings(shape: List<P>, cut: List<P>, from: Double): List<List<Crossing>> {
    val found = ArrayList<Crossing>()
    val n = shape.size
    for (k in 0 until cut.size - 1) {
        if (k + 1 < from - T_EPS) continue
        val a = cut[k]
        val rx = cut[k + 1].x - a.x
        val ry = cut[k + 1].y - a.y
        for (e in 0 until n) {
            val c = shape[e]
            val d = shape[(e + 1) % n]
            val sx = d.x - c.x
            val sy = d.y - c.y
            val den = rx * sy - ry * sx
            // Along an edge, not across it: no crossing.
            if (abs(den) <= 1e-12 * hypot(rx, ry) * hypot(sx, sy)) continue
            val qx = c.x - a.x
            val qy = c.y - a.y
            val s = (qx * sy - qy * sx) / den
            val u = (qx * ry - qy * rx) / den
            if (s < -T_EPS || s > 1 + T_EPS || u < -T_EPS || u > 1 + T_EPS) continue
            val along = s.coerceIn(0.0, 1.0)
            val t = k + along
            if (t < from - T_EPS) continue
            found += Crossing(t, e, u.coerceIn(0.0, 1.0), P(a.x + rx * along, a.y + ry * along))
        }
    }
    found.sortBy { it.t }
    val groups = ArrayList<MutableList<Crossing>>()
    for (c in found) {
        if (groups.isNotEmpty() && c.t - groups.last().last().t <= T_EPS) groups.last() += c else groups += mutableListOf(c)
    }
    return groups
}

/** The closed [loop] begun again halfway along a stretch of it outside [shape]; null when there is none. */
private fun outsideStart(shape: List<P>, loop: List<P>, groups: List<List<Crossing>>): List<P>? {
    val n = loop.size - 1
    val ts = groups.map { it.first().t }
    for (i in ts.indices) {
        val t0 = ts[i]
        val t1 = if (i + 1 < ts.size) ts[i + 1] else ts[0] + n
        if (t1 - t0 <= T_EPS) continue
        val m = ((t0 + t1) / 2) % n
        val start = pointAt(loop, m)
        if (inside(shape, start)) continue
        val k = floor(m).toInt()
        return (listOf(start) + loop.subList(k + 1, n + 1) + loop.subList(1, k + 1) + listOf(start)).distinctRuns()
    }
    return null
}

/**
 * [ring], inside [shape], cut out: the ring itself, and what is left — the shape with the way the
 * scissors came in from its nearest edge, round the ring the other way, and back out the same way.
 */
private fun keyhole(shape: List<P>, ring: List<P>): List<List<P>> {
    val n = shape.size
    var best = Double.MAX_VALUE
    var corner = 0
    var edge = 0
    var entry = shape[0]
    for (k in ring.indices) {
        for (e in 0 until n) {
            val q = nearestOn(shape[e], shape[(e + 1) % n], ring[k])
            val d = (q.x - ring[k].x) * (q.x - ring[k].x) + (q.y - ring[k].y) * (q.y - ring[k].y)
            if (d < best) {
                best = d
                corner = k
                edge = e
                entry = q
            }
        }
    }
    // Round the hole the other way from the outline, so what is left is one outline of the right area.
    val hole = if (area(ring) * area(shape) > 0) ring.asReversed() else ring
    val at = hole.indexOf(ring[corner])
    val roundIt = hole.subList(at, hole.size) + hole.subList(0, at) + hole[at]
    val left = shape.subList(0, edge + 1) + entry + roundIt + entry + shape.subList(edge + 1, n)
    return listOf(left.cleaned(), ring)
}

private fun nearestOn(a: P, b: P, p: P): P {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len = dx * dx + dy * dy
    if (len == 0.0) return a
    val f = (((p.x - a.x) * dx + (p.y - a.y) * dy) / len).coerceIn(0.0, 1.0)
    return P(a.x + dx * f, a.y + dy * f)
}

private fun pointAt(path: List<P>, t: Double): P {
    val k = floor(t).toInt().coerceIn(0, path.size - 2)
    val f = t - k
    return P(path[k].x + (path[k + 1].x - path[k].x) * f, path[k].y + (path[k + 1].y - path[k].y) * f)
}

private fun inside(shape: List<P>, p: P): Boolean {
    var inside = false
    var j = shape.lastIndex
    for (i in shape.indices) {
        val a = shape[i]
        val b = shape[j]
        if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
        j = i
    }
    return inside
}

/** The signed area: its sign tells which way round the outline goes. */
private fun area(shape: List<P>): Double {
    var sum = 0.0
    for (i in shape.indices) {
        val a = shape[i]
        val b = shape[(i + 1) % shape.size]
        sum += a.x * b.y - b.x * a.y
    }
    return sum / 2
}

private fun same(a: P, b: P) = abs(a.x - b.x) < 1e-9 && abs(a.y - b.y) < 1e-9

/** Without a place given twice in a row. */
private fun List<P>.distinctRuns(): List<P> = fold(ArrayList<P>()) { out, p -> if (out.isEmpty() || !same(out.last(), p)) out += p; out }

/** An outline without a corner given twice in a row, nor its last the same as its first. */
private fun List<P>.cleaned(): List<P> = distinctRuns().let { if (it.size > 1 && same(it.first(), it.last())) it.dropLast(1) else it }

/** How large an outline is, in its own square units. */
fun areaOf(outline: List<Spot>): Float {
    var sum = 0f
    for (i in outline.indices) {
        val a = outline[i]
        val b = outline[(i + 1) % outline.size]
        sum += a.x * b.y - b.x * a.y
    }
    return abs(sum / 2)
}

/** A place surely inside [outline]: the middle of the widest stretch of a line across it halfway down. */
fun insidePoint(outline: List<Spot>): Spot {
    val y = (outline.minOf { it.y } + outline.maxOf { it.y }) / 2f
    val xs = ArrayList<Float>()
    var j = outline.lastIndex
    for (i in outline.indices) {
        val a = outline[i]
        val b = outline[j]
        if ((a.y > y) != (b.y > y)) xs += (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x
        j = i
    }
    xs.sort()
    var best = Spot(outline[0].x, outline[0].y)
    var widest = -1f
    for (i in 0 until xs.size - 1 step 2) {
        if (xs[i + 1] - xs[i] > widest) {
            widest = xs[i + 1] - xs[i]
            best = Spot((xs[i] + xs[i + 1]) / 2f, y)
        }
    }
    return best
}

/** Whether the outlines [a] and [b], in the same coordinates, overlap at all. */
fun overlaps(a: List<Spot>, b: List<Spot>): Boolean {
    if (a.any { insideOf(b, it) } || b.any { insideOf(a, it) }) return true
    for (i in a.indices) {
        val p = a[i]
        val q = a[(i + 1) % a.size]
        for (j in b.indices) if (segmentsCross(p, q, b[j], b[(j + 1) % b.size])) return true
    }
    return false
}

private fun segmentsCross(a: Spot, b: Spot, c: Spot, d: Spot): Boolean {
    fun side(p: Spot, q: Spot, r: Spot) = (q.x - p.x) * (r.y - p.y) - (q.y - p.y) * (r.x - p.x)
    val d1 = side(c, d, a)
    val d2 = side(c, d, b)
    val d3 = side(a, b, c)
    val d4 = side(a, b, d)
    return ((d1 > 0) != (d2 > 0)) && ((d3 > 0) != (d4 > 0))
}

/**
 * [path] as paper tears along it: followed every [step] units, each place pushed off to one side or
 * the other — a long wander, a shorter one and a fine fray — by up to [reach]. The same [seed], the
 * same tear.
 */
fun torn(path: List<Spot>, seed: Long, reach: Float = TEAR_REACH, step: Float = TEAR_STEP): List<Spot> {
    val even = resampled(path, step)
    if (even.size < 2) return path
    val rnd = Random(seed)
    val octaves = listOf(60f to 0.55f, 18f to 0.3f, 5f to 0.15f).map { (wave, weight) ->
        Triple(wave, weight, FloatArray((even.size * step / wave).toInt() + 3) { rnd.nextFloat() * 2f - 1f })
    }
    return even.mapIndexed { i, p ->
        val before = even[max(i - 1, 0)]
        val after = even[minOf(i + 1, even.lastIndex)]
        val dx = after.x - before.x
        val dy = after.y - before.y
        val len = hypot(dx, dy).coerceAtLeast(1e-6f)
        val s = i * step
        val off = reach * octaves.sumOf { (wave, weight, lattice) -> (weight * smoothNoise(lattice, s / wave)).toDouble() }.toFloat()
        Spot(p.x - dy / len * off, p.y + dx / len * off)
    }
}

private fun smoothNoise(lattice: FloatArray, x: Float): Float {
    val i = floor(x).toInt().coerceIn(0, lattice.size - 2)
    val f = (x - i).coerceIn(0f, 1f)
    val s = f * f * (3 - 2 * f)
    return lattice[i] + (lattice[i + 1] - lattice[i]) * s
}

/** [path] followed at even steps of [step], from its first place to its last. */
private fun resampled(path: List<Spot>, step: Float): List<Spot> {
    if (path.size < 2) return path
    val out = arrayListOf(path.first())
    var carried = 0f
    for (k in 0 until path.size - 1) {
        val a = path[k]
        val b = path[k + 1]
        val len = hypot(b.x - a.x, b.y - a.y)
        var d = step - carried
        while (d <= len) {
            out += Spot(a.x + (b.x - a.x) * d / len, a.y + (b.y - a.y) * d / len)
            d += step
        }
        carried = (carried + len) % step
    }
    if (out.last() != path.last()) out += path.last()
    return out
}

/**
 * A strip of masking tape [length] long and [width] wide, in its own units: straight along its sides,
 * torn across its ends, as the design's tape is. The same [seed], the same strip.
 */
fun tapeStrip(length: Float, width: Float, seed: Long): List<Spot> {
    val rnd = Random(seed)
    val teeth = 5
    val bite = width * 0.18f
    val out = arrayListOf(Spot(0f, 0f), Spot(length, 0f))
    for (i in 1 until teeth) out += Spot(length - rnd.nextFloat() * bite, width * i / teeth)
    out += Spot(length, width)
    out += Spot(0f, width)
    for (i in teeth - 1 downTo 1) out += Spot(rnd.nextFloat() * bite, width * i / teeth)
    return out
}
