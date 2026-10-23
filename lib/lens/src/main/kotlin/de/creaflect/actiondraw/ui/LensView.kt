package de.creaflect.actiondraw.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.lens.LensState
import java.io.File
import kotlin.math.min

/** The colour filter a lens applies, if it is one of the plain colour ones. */
internal fun lensColorFilter(mode: ViewMode): ColorFilter? = when (mode) {
    ViewMode.GRAYSCALE -> grayscaleFilter()
    ViewMode.SQUINT -> squintFilter()
    ViewMode.SEPIA -> sepiaFilter()
    else -> null
}

/** The render effect a lens applies, with the lens's own settings for it. */
@Composable
internal fun rememberLensEffect(mode: ViewMode, lens: LensState): RenderEffect? = when (mode) {
    ViewMode.EDGE -> remember { edgeRenderEffect() }
    ViewMode.SILHOUETTE -> remember(lens.silhouetteThreshold) { silhouetteRenderEffect(lens.silhouetteThreshold) }
    ViewMode.POSTERIZE -> remember(lens.posterizeLevels) { posterizeRenderEffect(lens.posterizeLevels) }
    ViewMode.PIXELATE -> remember(lens.pixelateBlock) { pixelateRenderEffect(lens.pixelateBlock) }
    ViewMode.NOTAN -> remember(lens.notanBands, lens.notanThreshold) { notanRenderEffect(lens.notanBands, lens.notanThreshold) }
    else -> null
}

/**
 * Keeps [lastFocus] at where the glass was last down on [current]. Its own composable, so the glass
 * moving recomposes this and nothing else; an effect rather than a snapshotFlow, for the reason
 * given at `PointerWatch.moves`.
 */
@Composable
private fun LastFocus(glass: GlassState, current: File?, lastFocus: MutableState<GlassFocus?>) {
    val focus = glass.focusOn(current)
    LaunchedEffect(focus) { if (focus != null) lastFocus.value = focus }
}

/**
 * The picture seen through [lens] — its lens, every knob, the grid — and the glass (F10.9) over
 * it: taken up, it shows the picture larger under the pointer; set down, the part it is on fills
 * the area — zooming there and back over [Motion.GLASS_MS] — and the whole picture waits in the
 * corner. While there is a [veil], it stands in the picture's place, and the glass takes nothing.
 */
@Composable
fun LensView(
    lens: LensState,
    bitmap: ImageBitmap?,
    picture: File?,
    modifier: Modifier = Modifier,
    veil: (@Composable () -> Unit)? = null,
) {
    val glass = lens.glass
    val pointer = remember { GlassPointer() }
    val focused by remember(picture) { derivedStateOf { glass.focusOn(picture) != null } }
    val progress = remember { Animatable(0f) }
    val reduced = LocalReducedMotion.current
    LaunchedEffect(focused) {
        progress.animateTo(if (focused) 1f else 0f, tween(if (reduced) Motion.REDUCED_MS else Motion.GLASS_MS, easing = Motion.GLASS_EASING))
    }
    // Where the glass was last down on this picture, so lifting it zooms back out from there.
    val lastFocus = remember(picture) { mutableStateOf<GlassFocus?>(null) }
    LastFocus(glass, picture, lastFocus)
    val bmp = bitmap
    val usable = bmp != null && veil == null
    Box(
        modifier = modifier
            .background(Color.Black)
            .onSizeChanged { pointer.area = Size(it.width.toFloat(), it.height.toFloat()) }
            .then(if (usable) Modifier.glassInput(lens, pointer, picture, bmp!!.width.toFloat(), bmp.height.toFloat(), focused) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (veil != null) {
            // Drawn nowhere, not merely covered: nothing of the picture should reach the screen.
            veil()
        } else if (bmp != null) {
            val colorFilter = lensColorFilter(lens.viewMode)
            val renderEffect = rememberLensEffect(lens.viewMode, lens)
            val invertEffect = remember { invertRenderEffect() }
            val temperatureEffect = remember(lens.temperature) { temperatureRenderEffect(lens.temperature) }
            val defractionEffect = remember(lens.defractionSeed, lens.defractionBlock, lens.defractionStrength) {
                defractionRenderEffect(lens.defractionSeed, lens.defractionBlock, lens.defractionStrength)
            }
            GlassPicture(
                bitmap = bmp,
                lens = lens,
                picture = picture,
                pointer = pointer,
                progress = progress,
                lastFocus = lastFocus,
                colorFilter = colorFilter,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("reference")
                    .semantics { contentDescription = picture?.name ?: "" }
                    .graphicsLayer { // outermost: colour inversion of the final result
                        this.renderEffect = if (lens.invert) invertEffect else null
                    }
                    .graphicsLayer { // white balance, over whatever the view mode produced
                        this.renderEffect = if (lens.temperature != 0f) temperatureEffect else null
                    }
                    .graphicsLayer { // orientation
                        if (lens.upsideDown) rotationZ = 180f
                        if (lens.mirror) scaleX = -1f
                    }
                    .blur(if (lens.blur) lens.blurRadius.dp else 0.dp)
                    .graphicsLayer { this.renderEffect = renderEffect } // view mode
                    .graphicsLayer { // innermost: shards cut from the raw image
                        this.renderEffect = if (lens.defraction) defractionEffect else null
                    },
            )
            // Proportion overlay sits above the image and is unaffected by its filters/rotation;
            // it measures the whole picture, so it waits while the glass is down on a part.
            if (lens.gridMode != GridMode.OFF && !focused) {
                ProportionOverlay(bmp, lens.gridMode, Modifier.fillMaxSize())
            }
            GlassOverlay(bmp, lens, picture, pointer, progress, focused)
        } else {
            Text(if (picture == null) "No image" else "Loading…", color = Color.White)
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
