package de.creaflect.sketch

import kotlin.math.PI
import kotlin.math.abs

/**
 * One reading from whatever holds the pen: a position on the page in pixels, pressure in 0..1
 * (a mouse reports 1), tilt in degrees if the device knows it, and when it happened. The engine
 * takes these and nothing else — where they come from (Windows Ink, WinTab, a mouse) is the
 * app's business (docs/Pencil-Engine-Architecture.md §3).
 */
data class InputSample(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val tiltX: Float = 0f,
    val tiltY: Float = 0f,
    val timeNanos: Long,
    val source: Source = Source.PEN,
) {
    enum class Source { PEN, MOUSE }
}

/**
 * The One-Euro filter (Casiez, Roussel, Vogel 2012): a low-pass whose cutoff rises with speed,
 * so a resting hand stops jittering and a fast hand is not dragged behind. [minCutoff] sets how
 * still the still hand is, [beta] how quickly speed opens the filter; the paper's 1.0 / 0.007 are
 * the starting point, to be tuned with the pen in hand (LEARNINGS L4).
 */
class OneEuroFilter(
    private val minCutoff: Float = 1f,
    private val beta: Float = 0.007f,
    private val dCutoff: Float = 1f,
) {
    private var previous: Float? = null
    private var previousDerivative = 0f
    private var previousTimeNanos = 0L

    fun filter(value: Float, timeNanos: Long): Float {
        val prev = previous
        if (prev == null) {
            previous = value
            previousTimeNanos = timeNanos
            return value
        }
        val dt = ((timeNanos - previousTimeNanos) / 1e9f).coerceIn(1e-4f, 1f)
        val derivative = (value - prev) / dt
        val smoothedDerivative = lowPass(derivative, previousDerivative, alpha(dCutoff, dt))
        val cutoff = minCutoff + beta * abs(smoothedDerivative)
        val smoothed = lowPass(value, prev, alpha(cutoff, dt))
        previous = smoothed
        previousDerivative = smoothedDerivative
        previousTimeNanos = timeNanos
        return smoothed
    }

    fun reset() {
        previous = null
        previousDerivative = 0f
    }

    private fun alpha(cutoff: Float, dt: Float): Float {
        val tau = 1f / (2f * PI.toFloat() * cutoff)
        return 1f / (1f + tau / dt)
    }

    private fun lowPass(value: Float, prev: Float, a: Float): Float = a * value + (1f - a) * prev
}
