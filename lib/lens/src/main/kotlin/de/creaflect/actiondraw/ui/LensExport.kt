package de.creaflect.actiondraw.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Density
import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.lens.LensState
import org.jetbrains.skia.EncodedImageFormat

/**
 * [bitmap] seen through [lens], whole and at its own size, as PNG bytes — as it looks on screen,
 * where it is shown [shownScale] times its size at [density]: the lens's sizes in pixels and its
 * blur grow with the picture, so a pixelate's blocks and a blur look as they did. Drawn by the same
 * [LensView] the screen draws with, in [design]; the grid and the glass stay behind. Null when it
 * could not be drawn. A large picture takes a while: call it off the UI thread.
 */
fun lensedPng(bitmap: ImageBitmap, lens: LensState, shownScale: Float, density: Float, design: DesignSystem): ByteArray? = runCatching {
    val scale = if (shownScale > 0f) 1f / shownScale else 1f
    val seen = lens.copy(scale).apply { gridMode = GridMode.OFF }
    val scene = ImageComposeScene(bitmap.width, bitmap.height, Density(density * scale)) {
        CompositionLocalProvider(LocalDesign provides design) {
            LensView(seen, bitmap, picture = null, modifier = Modifier.fillMaxSize())
        }
    }
    try {
        scene.render().use { image -> image.encodeToData(EncodedImageFormat.PNG)?.use { it.bytes } }
    } finally {
        scene.close()
    }
}.getOrNull()
