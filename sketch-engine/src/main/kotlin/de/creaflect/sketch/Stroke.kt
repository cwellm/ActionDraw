package de.creaflect.sketch

import kotlin.math.hypot

/** One point of a stroke as the lead left it: place, the width and darkness it earned, and why. */
data class StrokePoint(
    val x: Float,
    val y: Float,
    val width: Float,
    val alpha: Float,
    val pressure: Float,
    val speed: Float,
    val timeNanos: Long,
)

/** A finished stroke: the brush it was made with and its points, in order. */
class Stroke(val brush: Brush, val points: List<StrokePoint>) {
    val isEmpty: Boolean get() = points.isEmpty()
}

/**
 * Turns input samples into stroke points, one at a time, so a stroke can be drawn while it is
 * being made: the position goes through the One-Euro filter, the pressure through a lighter one
 * (pressure edges are expression, not noise), speed comes from the timestamps and is smoothed
 * over a short window, and the brush's lead turns pressure and speed into width and alpha.
 */
class StrokeBuilder(
    val brush: Brush,
    smoothing: Boolean = true,
    /** The lead's model, or another — the eraser uses the rubber's, whatever brush it is held as. */
    private val model: PencilModel = brush.model,
) {
    // The paper's beta (0.007) is tuned for a cursor; a pen at 800 px/s lagged twenty pixels
    // behind it. Opening the filter faster with speed keeps a still hand still and a moving
    // hand within a few pixels of the tip (LEARNINGS L4, to be tuned with the pen in hand).
    private val filterX = if (smoothing) OneEuroFilter(minCutoff = 1f, beta = POSITION_BETA) else null
    private val filterY = if (smoothing) OneEuroFilter(minCutoff = 1f, beta = POSITION_BETA) else null
    private val filterPressure = if (smoothing) OneEuroFilter(minCutoff = 4f, beta = 0.02f) else null
    private val points = mutableListOf<StrokePoint>()
    private var smoothedSpeed = 0f

    val current: List<StrokePoint> get() = points

    fun add(sample: InputSample): StrokePoint {
        val x = filterX?.filter(sample.x, sample.timeNanos) ?: sample.x
        val y = filterY?.filter(sample.y, sample.timeNanos) ?: sample.y
        val pressure = (filterPressure?.filter(sample.pressure, sample.timeNanos) ?: sample.pressure).coerceIn(0f, 1f)
        val last = points.lastOrNull()
        if (last != null) {
            val dt = (sample.timeNanos - last.timeNanos) / 1e9f
            val speed = if (dt > 0f) hypot(x - last.x, y - last.y) / dt else smoothedSpeed
            smoothedSpeed = SPEED_MEMORY * smoothedSpeed + (1f - SPEED_MEMORY) * speed
        }
        val point = StrokePoint(
            x = x,
            y = y,
            width = model.width(pressure, brush.size),
            alpha = model.alpha(pressure, smoothedSpeed),
            pressure = pressure,
            speed = smoothedSpeed,
            timeNanos = sample.timeNanos,
        )
        points += point
        return point
    }

    fun build(): Stroke = Stroke(brush, points.toList())

    companion object {
        /** How much of the previous speed survives into the next point's: a short window. */
        const val SPEED_MEMORY = 0.7f

        /** How fast the position filter opens with speed; see the note above. */
        const val POSITION_BETA = 0.05f
    }
}
