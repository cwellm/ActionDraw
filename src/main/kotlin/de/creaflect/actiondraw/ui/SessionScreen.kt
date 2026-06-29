package de.creaflect.actiondraw.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.AppState
import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.image.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

private val LowTimeColor = Color(0xFFEF5350)

@Composable
fun SessionScreen(state: AppState, onToggleFullscreen: () -> Unit, isFullscreen: Boolean) {
    // Per-second countdown. Restarts on navigation (index/pose) and suspends while paused.
    LaunchedEffect(state.index, state.rampPose, state.isPaused) {
        while (!state.isPaused) {
            delay(1000)
            state.tick()
        }
    }

    val current = state.currentImage
    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(null, current) {
        value = current?.let { f ->
            withContext(Dispatchers.IO) { runCatching { ImageLoader.load(f) }.getOrNull() }
        }
    }

    if (isFullscreen) {
        // Image fills the whole screen; only the remaining time floats in the bottom corner.
        Box(Modifier.fillMaxSize()) {
            ImageArea(state, bitmap, current, Modifier.fillMaxSize())
            Surface(
                color = Color(0xAA000000),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            ) {
                Text(
                    formatTime(state.remainingSeconds),
                    color = if (state.remainingSeconds <= 5) LowTimeColor else Color.White,
                    style = MaterialTheme.typography.h5,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            ImageArea(state, bitmap, current, Modifier.weight(1f).fillMaxWidth())
            ControlBar(state, onToggleFullscreen)
        }
    }
}

@Composable
private fun ImageArea(state: AppState, bitmap: ImageBitmap?, current: File?, modifier: Modifier) {
    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        val bmp = bitmap
        if (bmp != null) {
            val colorFilter = when (state.viewMode) {
                ViewMode.GRAYSCALE -> grayscaleFilter()
                ViewMode.SQUINT -> squintFilter()
                ViewMode.SEPIA -> sepiaFilter()
                ViewMode.WARM -> warmFilter()
                ViewMode.COOL -> coolFilter()
                else -> null
            }
            val renderEffect = when (state.viewMode) {
                ViewMode.EDGE -> edgeRenderEffect()
                ViewMode.SILHOUETTE -> silhouetteRenderEffect()
                ViewMode.POSTERIZE -> posterizeRenderEffect()
                ViewMode.PIXELATE -> pixelateRenderEffect()
                else -> null
            }
            Image(
                bitmap = bmp,
                contentDescription = current?.name,
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { // outermost: orientation
                        if (state.upsideDown) rotationZ = 180f
                        if (state.mirror) scaleX = -1f
                    }
                    .blur(if (state.blur) state.blurRadius else 0.dp)
                    .graphicsLayer { this.renderEffect = renderEffect }, // innermost: on the raw image
            )
            // Proportion overlay sits above the image and is unaffected by its filters/rotation.
            if (state.gridMode != GridMode.OFF) {
                ProportionOverlay(bmp, state.gridMode, Modifier.fillMaxSize())
            }
        } else {
            Text(if (current == null) "No image" else "Loading…", color = Color.White)
        }
    }
}

/** Proportion overlay (thirds / phi / diagonal) + a stronger centre cross, within the fitted image rect. */
@Composable
private fun ProportionOverlay(bitmap: ImageBitmap, mode: GridMode, modifier: Modifier) {
    val lineColor = Color.White.copy(alpha = 0.45f)
    val centerColor = Color.White.copy(alpha = 0.8f)
    val lines = gridLines(mode)
    Canvas(modifier) {
        val scale = min(size.width / bitmap.width, size.height / bitmap.height)
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        val left = (size.width - w) / 2f
        val top = (size.height - h) / 2f
        val stroke = 1.5.dp.toPx()
        fun at(nx: Float, ny: Float) = Offset(left + w * nx, top + h * ny)

        lines.forEach { drawLine(lineColor, at(it.x1, it.y1), at(it.x2, it.y2), stroke) }
        // Shared centre cross + outer border for every active mode.
        drawLine(centerColor, at(0.5f, 0f), at(0.5f, 1f), stroke)
        drawLine(centerColor, at(0f, 0.5f), at(1f, 0.5f), stroke)
        drawRect(lineColor, topLeft = Offset(left, top), size = Size(w, h), style = Stroke(stroke))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlBar(state: AppState, onToggleFullscreen: () -> Unit) {
    Surface(elevation = 8.dp) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            val low = state.remainingSeconds <= 5
            val progress =
                if (state.currentIntervalSeconds > 0)
                    state.elapsedSeconds.toFloat() / state.currentIntervalSeconds
                else 0f
            LinearProgressIndicator(
                progress = progress.coerceIn(0f, 1f),
                color = if (low) LowTimeColor else MaterialTheme.colors.primary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))

            // Stats / ramp progress line.
            Text(
                buildProgressText(state),
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatTime(state.remainingSeconds),
                    style = MaterialTheme.typography.h6,
                    color = if (low) LowTimeColor else MaterialTheme.colors.onSurface,
                )
                Spacer(Modifier.width(16.dp))
                Button(onClick = { state.previous() }) { Text("◀ Prev") }
                Button(onClick = { state.play() }) { Text("Play") }
                Button(onClick = { state.togglePause() }) {
                    Text(if (state.isPaused) "Resume" else "Pause")
                }
                Button(onClick = { state.stop() }) { Text("■ Stop") }
                Button(onClick = { state.next() }) { Text("Next ▶") }
                Spacer(Modifier.width(16.dp))
                Button(onClick = onToggleFullscreen) { Text("Fullscreen") }
            }

            Spacer(Modifier.height(8.dp))

            // View mode: mutually-exclusive value / colour / structure studies (wraps on narrow windows).
            Text("VIEW", style = MaterialTheme.typography.overline, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ViewChip("None", state, ViewMode.NONE)
                ViewChip("B&W", state, ViewMode.GRAYSCALE)
                ViewChip("Squint", state, ViewMode.SQUINT)
                ViewChip("Sepia", state, ViewMode.SEPIA)
                ViewChip("Posterize", state, ViewMode.POSTERIZE)
                ViewChip("Pixelate", state, ViewMode.PIXELATE)
                ViewChip("Warm", state, ViewMode.WARM)
                ViewChip("Cool", state, ViewMode.COOL)
                ViewChip("Edge", state, ViewMode.EDGE)
                ViewChip("Silhouette", state, ViewMode.SILHOUETTE)
            }

            Spacer(Modifier.height(8.dp))

            // Grid mode + independent toggles + per-image redo flag.
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Grid:", modifier = Modifier.align(Alignment.CenterVertically))
                SelectChip("Off", state.gridMode == GridMode.OFF) { state.gridMode = GridMode.OFF }
                SelectChip("Thirds", state.gridMode == GridMode.THIRDS) { state.gridMode = GridMode.THIRDS }
                SelectChip("Phi", state.gridMode == GridMode.PHI) { state.gridMode = GridMode.PHI }
                SelectChip("Diagonal", state.gridMode == GridMode.DIAGONAL) { state.gridMode = GridMode.DIAGONAL }
                Spacer(Modifier.width(8.dp))
                FilterToggle("Blur", state.blur) { state.blur = it }
                FilterToggle("Mirror", state.mirror) { state.mirror = it }
                FilterToggle("Upside down", state.upsideDown) { state.upsideDown = it }
                Spacer(Modifier.width(8.dp))
                SelectChip("⟳ Redo", state.isCurrentRedo) { state.toggleRedoCurrent() }
            }

            // Adjusting the time is only allowed while paused; elapsed time is left untouched.
            // (In a ramp the durations are fixed by the plan, so the slider only shows in fixed mode.)
            if (state.isPaused && !state.isRamp) {
                Spacer(Modifier.height(8.dp))
                IntervalSelector(
                    seconds = state.intervalSeconds,
                    onChange = { state.intervalSeconds = it },
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "Space pause · ←/→ prev/next · 1-0 view · B blur · M mirror · U flip · G grid · R redo · F fullscreen · Esc stop",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun buildProgressText(state: AppState): String = buildString {
    val plan = state.rampPlan
    if (plan != null) {
        append("Step ${state.rampStepIndex + 1}/${plan.steps.size}")
        append(" · pose ${state.rampPose + 1}/${state.rampTotalPoses}")
        append("  ·  ")
    }
    append("${state.sessionPoses} drawn · ${formatDuration(state.sessionSeconds)}")
}

@Composable
private fun ViewChip(label: String, state: AppState, mode: ViewMode) {
    SelectChip(label, state.viewMode == mode) { state.viewMode = mode }
}

@Composable
private fun FilterToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}
