package de.creaflect.actiondraw.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.OutlinedButton
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
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.image.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

@Composable
fun SessionScreen(state: AppState, onToggleFullscreen: () -> Unit, isFullscreen: Boolean) {
    // Per-second countdown. Restarts on navigation (index) and suspends while paused.
    LaunchedEffect(state.index, state.isPaused) {
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
                    color = Color.White,
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
                else -> null
            }
            val renderEffect = when (state.viewMode) {
                ViewMode.EDGE -> edgeRenderEffect()
                ViewMode.SILHOUETTE -> silhouetteRenderEffect()
                else -> null
            }
            Image(
                bitmap = bmp,
                contentDescription = current?.name,
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { if (state.upsideDown) rotationZ = 180f } // outermost: orientation
                    .blur(if (state.blur) state.blurRadius else 0.dp)
                    .graphicsLayer { this.renderEffect = renderEffect }, // innermost: on the raw image
            )
            // Proportion overlay sits above the image and is unaffected by its filters/rotation.
            if (state.showGrid) {
                ProportionOverlay(bmp, Modifier.fillMaxSize())
            }
        } else {
            Text(if (current == null) "No image" else "Loading…", color = Color.White)
        }
    }
}

/** Rule-of-thirds grid + a stronger center cross, drawn within the fitted image rect. */
@Composable
private fun ProportionOverlay(bitmap: ImageBitmap, modifier: Modifier) {
    val thirds = Color.White.copy(alpha = 0.45f)
    val center = Color.White.copy(alpha = 0.8f)
    Canvas(modifier) {
        val scale = min(size.width / bitmap.width, size.height / bitmap.height)
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        val left = (size.width - w) / 2f
        val top = (size.height - h) / 2f
        val stroke = 1.5.dp.toPx()

        for (i in 1..2) {
            val x = left + w * i / 3f
            drawLine(thirds, Offset(x, top), Offset(x, top + h), stroke)
            val y = top + h * i / 3f
            drawLine(thirds, Offset(left, y), Offset(left + w, y), stroke)
        }
        val cx = left + w / 2f
        val cy = top + h / 2f
        drawLine(center, Offset(cx, top), Offset(cx, top + h), stroke)
        drawLine(center, Offset(left, cy), Offset(left + w, cy), stroke)
        drawRect(thirds, topLeft = Offset(left, top), size = Size(w, h), style = Stroke(stroke))
    }
}

@Composable
private fun ControlBar(state: AppState, onToggleFullscreen: () -> Unit) {
    Surface(elevation = 8.dp) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            val progress =
                if (state.intervalSeconds > 0) state.elapsedSeconds.toFloat() / state.intervalSeconds else 0f
            LinearProgressIndicator(
                progress = progress.coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatTime(state.remainingSeconds), style = MaterialTheme.typography.h6)
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

            // View mode: mutually-exclusive value/structure studies.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("View:")
                ModeChip("None", state.viewMode == ViewMode.NONE) { state.viewMode = ViewMode.NONE }
                ModeChip("B&W", state.viewMode == ViewMode.GRAYSCALE) { state.viewMode = ViewMode.GRAYSCALE }
                ModeChip("Squint", state.viewMode == ViewMode.SQUINT) { state.viewMode = ViewMode.SQUINT }
                ModeChip("Edge", state.viewMode == ViewMode.EDGE) { state.viewMode = ViewMode.EDGE }
                ModeChip("Silhouette", state.viewMode == ViewMode.SILHOUETTE) { state.viewMode = ViewMode.SILHOUETTE }
            }

            Spacer(Modifier.height(8.dp))

            // Independent toggles.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterToggle("Blur", state.blur) { state.blur = it }
                FilterToggle("Upside down", state.upsideDown) { state.upsideDown = it }
                FilterToggle("Grid", state.showGrid) { state.showGrid = it }
            }

            // Adjusting the time is only allowed while paused; elapsed time is left untouched.
            if (state.isPaused) {
                Spacer(Modifier.height(8.dp))
                IntervalSelector(
                    seconds = state.intervalSeconds,
                    onChange = { state.intervalSeconds = it },
                )
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val padding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    if (selected) {
        Button(onClick = onClick, contentPadding = padding) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, contentPadding = padding) { Text(label) }
    }
}

@Composable
private fun FilterToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}
