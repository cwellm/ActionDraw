package de.creaflect.actiondraw.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Density
import de.creaflect.actiondraw.lens.LensState
import org.jetbrains.skia.EncodedImageFormat
import kotlin.math.roundToInt

/** The long side a view of a picture on a form is saved at, at most. */
const val FORM_SAVED = 1600f

/**
 * [bitmap] seen through [lens], whole and at its own size, as PNG bytes — as it looks on screen,
 * where it is shown [shownScale] times its size at [density]: the lens's sizes in pixels and its
 * blur grow with the picture, so a pixelate's blocks and a blur look as they did. Drawn by the same
 * [LensView] the screen draws with, in [design], without its marks: the grid, the glass in hand and
 * the centre's mark stay behind; the glass laid over the whole picture comes along. Null when it
 * could not be drawn. A large picture takes a while: call it off the UI thread.
 */
fun lensedPng(bitmap: ImageBitmap, lens: LensState, shownScale: Float, density: Float, design: DesignSystem): ByteArray? = runCatching {
    // A form is drawn — four rays a pixel, on the processor — not resampled: a large picture on one
    // is saved no larger than FORM_SAVED on its long side.
    val out = if (lens.form.form != null) minOf(1f, FORM_SAVED / maxOf(bitmap.width, bitmap.height)) else 1f
    val scale = (if (shownScale > 0f) 1f / shownScale else 1f) * out
    val seen = lens.copy(scale)
    val width = (bitmap.width * out).roundToInt().coerceAtLeast(1)
    val height = (bitmap.height * out).roundToInt().coerceAtLeast(1)
    val scene = ImageComposeScene(width, height, Density(density * scale)) {
        CompositionLocalProvider(LocalDesign provides design) {
            LensView(seen, bitmap, picture = null, modifier = Modifier.fillMaxSize(), marks = false)
        }
    }
    try {
        scene.render().use { image -> image.encodeToData(EncodedImageFormat.PNG)?.use { it.bytes } }
    } finally {
        scene.close()
    }
}.getOrNull()
