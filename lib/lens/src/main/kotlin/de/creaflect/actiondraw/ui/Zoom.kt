package de.creaflect.actiondraw.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.IRect
import java.io.File
import kotlin.math.roundToInt

/** How much one step of the wheel, or of + and −, zooms in or out by. */
const val ZOOM_STEP = 1.25f

/** The furthest in a picture is zoomed: a sixty-fourth of it across. */
const val MOST_ZOOM = 64f

/**
 * A part of [picture] taken as the picture itself (the person's wish, 2026-11-13): zoomed into, it is
 * what every lens, knob and grid, the glass and the forms work on, as on a picture of its own, until
 * the whole picture is shown again. [left], [top], [width] and [height] are in the whole picture's
 * measure, 0 to 1. It is [picture]'s: another picture starts whole, as with the glass.
 */
data class Zoom(val picture: File?, val left: Float, val top: Float, val width: Float, val height: Float) {
    /** How many times nearer than the whole picture, the nearer way of the two. */
    val factor: Float get() = 1f / minOf(width, height)

    /** ([x], [y]) on the part shown, 0 to 1, on the whole picture. */
    fun onWhole(x: Float, y: Float): Pair<Float, Float> = (left + x * width) to (top + y * height)

    companion object {
        /** The whole of [picture], as a part: where zooming in starts from. */
        fun whole(picture: File?) = Zoom(picture, 0f, 0f, 1f, 1f)

        /**
         * The part of [picture] [width] by [height] from ([left], [top]): no nearer than [MOST_ZOOM]
         * nor larger than the picture, and kept on it. Null where that is the whole picture.
         */
        fun of(picture: File?, left: Float, top: Float, width: Float, height: Float): Zoom? {
            val w = width.coerceIn(1f / MOST_ZOOM, 1f)
            val h = height.coerceIn(1f / MOST_ZOOM, 1f)
            if (w >= 0.999f && h >= 0.999f) return null
            return Zoom(picture, left.coerceIn(0f, 1f - w), top.coerceIn(0f, 1f - h), w, h)
        }
    }
}

/**
 * [bitmap], or the part of it [zoom] takes, as a picture of its own: its pixels shared with the
 * whole, not copied, so a zoom follows the wheel at once. At least a couple of pixels either way.
 */
fun zoomedPart(bitmap: ImageBitmap, zoom: Zoom?): ImageBitmap {
    if (zoom == null) return bitmap
    val w = bitmap.width
    val h = bitmap.height
    val left = (zoom.left * w).roundToInt().coerceIn(0, w - 2)
    val top = (zoom.top * h).roundToInt().coerceIn(0, h - 2)
    val right = ((zoom.left + zoom.width) * w).roundToInt().coerceIn(left + 2, w)
    val bottom = ((zoom.top + zoom.height) * h).roundToInt().coerceIn(top + 2, h)
    val part = Bitmap()
    if (!bitmap.asSkiaBitmap().extractSubset(part, IRect.makeLTRB(left, top, right, bottom))) return bitmap
    part.setImmutable()
    return part.asComposeImageBitmap()
}
