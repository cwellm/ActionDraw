package de.creaflect.actiondraw.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import java.io.File

/** Decodes an image file into a Compose [ImageBitmap] via Skia. Call off the main thread. */
object ImageLoader {
    fun load(file: File): ImageBitmap =
        Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
}
