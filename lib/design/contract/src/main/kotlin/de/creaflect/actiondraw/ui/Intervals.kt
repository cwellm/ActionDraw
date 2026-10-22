package de.creaflect.actiondraw.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Allowed per-image durations: 30s–10min in 30s steps, 10–30min in 1-min steps,
 * 30–60min in 5-min steps (46 discrete options).
 */
val INTERVAL_OPTIONS: List<Int> = buildList {
    for (s in 30..600 step 30) add(s)
    for (s in 660..1800 step 60) add(s)
    for (s in 2100..3600 step 300) add(s)
}

/** Index of [seconds] in [INTERVAL_OPTIONS], or the nearest option at/above it. */
fun intervalIndexOf(seconds: Int): Int =
    INTERVAL_OPTIONS.indexOf(seconds).takeIf { it >= 0 }
        ?: INTERVAL_OPTIONS.indexOfFirst { it >= seconds }.coerceAtLeast(0)

@Composable
fun IntervalSelector(seconds: Int, onChange: (Int) -> Unit, enabled: Boolean = true) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Time per image: ${formatTime(seconds)}")
        val maxIndex = INTERVAL_OPTIONS.lastIndex
        Slider(
            value = intervalIndexOf(seconds).toFloat(),
            onValueChange = { onChange(INTERVAL_OPTIONS[it.roundToInt().coerceIn(0, maxIndex)]) },
            valueRange = 0f..maxIndex.toFloat(),
            steps = (maxIndex - 1).coerceAtLeast(0),
            enabled = enabled,
            modifier = Modifier.width(420.dp),
        )
    }
}

/** Short mm:ss form for per-image times. */
fun formatTime(totalSeconds: Int): String =
    "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)

/** Longer form that adds an hours field when needed, for session totals. */
fun formatDuration(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
