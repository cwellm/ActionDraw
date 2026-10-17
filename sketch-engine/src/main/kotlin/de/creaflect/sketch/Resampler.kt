package de.creaflect.sketch

import kotlin.math.ceil
import kotlin.math.hypot

/**
 * Re-spaces a stroke's points evenly along its path, so dab density does not depend on how fast
 * the pen moved (LEARNINGS L4). Each new point closes a segment from the previous one; the
 * segment is a Catmull-Rom curve through the neighbours (the last point doubled as the trailing
 * control, so a segment is placed the moment it exists and a replay places it the same way),
 * walked in small steps, and a dab is emitted every [spacing] of arc length — width, alpha,
 * pressure, speed and tilt interpolated between the two points, and the dab's heading the
 * direction it was walked in, which the spacing of a dab on its side depends on.
 */
class Resampler(private val spacing: (StrokePoint) -> Float) {
    private val points = ArrayList<StrokePoint>()
    private var sinceLastDab = 0f

    /** Feeds the next point; every dab it produces goes to [out], in order. */
    fun add(point: StrokePoint, out: (StrokePoint) -> Unit) {
        points += point
        val n = points.size
        if (n == 1) {
            out(point)
            sinceLastDab = 0f
            return
        }
        val p1 = points[n - 2]
        val p2 = points[n - 1]
        val p0 = if (n >= 3) points[n - 3] else p1
        val p3 = p2
        val length = hypot(p2.x - p1.x, p2.y - p1.y)
        if (length <= 0f) return
        val steps = ceil(length / STEP).toInt().coerceAtLeast(1)
        var last = p1
        for (i in 1..steps) {
            val t = i / steps.toFloat()
            val x = catmullRom(p0.x, p1.x, p2.x, p3.x, t)
            val y = catmullRom(p0.y, p1.y, p2.y, p3.y, t)
            val dx = x - last.x
            val dy = y - last.y
            val d = hypot(dx, dy)
            val here = StrokePoint(
                x = x,
                y = y,
                width = lerp(p1.width, p2.width, t),
                alpha = lerp(p1.alpha, p2.alpha, t),
                pressure = lerp(p1.pressure, p2.pressure, t),
                speed = lerp(p1.speed, p2.speed, t),
                timeNanos = p1.timeNanos + ((p2.timeNanos - p1.timeNanos) * t).toLong(),
                tiltX = lerp(p1.tiltX, p2.tiltX, t),
                tiltY = lerp(p1.tiltY, p2.tiltY, t),
                headX = if (d > 0f) dx / d else p2.headX,
                headY = if (d > 0f) dy / d else p2.headY,
            )
            sinceLastDab += d
            if (sinceLastDab >= spacing(here)) {
                out(here)
                sinceLastDab = 0f
            }
            last = here
        }
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun catmullRom(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val t2 = t * t
        val t3 = t2 * t
        return 0.5f * (
            2f * p1 +
                (-p0 + p2) * t +
                (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 +
                (-p0 + 3f * p1 - 3f * p2 + p3) * t3
            )
    }

    private companion object {
        /** The curve is walked in steps this long; dabs land within this of their exact place. */
        const val STEP = 0.75f
    }
}
