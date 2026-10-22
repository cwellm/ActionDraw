package de.creaflect.actiondraw.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.File

// The lib's pictures, ready for a Compose `Image`. Both decode: call them off the UI thread.

/** [file] at most [maxSize] on its long edge — the lib keeps it on disk for next time. */
fun Pictures.thumbnailBitmap(file: File, maxSize: Int = 192): ImageBitmap? = thumbnail(file, maxSize)?.toComposeImageBitmap()

/** [file] whole, or null when it cannot be decoded. */
fun Pictures.bitmap(file: File): ImageBitmap? = runCatching { decode(file)?.toComposeImageBitmap() }.getOrNull()
