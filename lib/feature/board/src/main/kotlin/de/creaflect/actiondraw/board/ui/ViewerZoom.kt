package de.creaflect.actiondraw.board.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.min

/**
 * The arithmetic behind zooming in the large viewer, kept out of the composable so it can be
 * tested: the picture is fitted to the screen at zoom 1, scaled about its centre beyond that, and
 * a pan moves it — never so far that empty space shows where picture could be.
 */
internal object ViewerZoom {
    /** The size a picture takes when fitted into [box] — [ContentScale.Fit], scaling up too. */
    fun fitted(picture: Size, box: Size): Size {
        if (picture.width <= 0f || picture.height <= 0f) return Size.Zero
        val scale = min(box.width / picture.width, box.height / picture.height)
        return Size(picture.width * scale, picture.height * scale)
    }

    /** Keeps the scaled picture over the view: an axis that still fits cannot be panned at all. */
    fun clampPan(pan: Offset, fitted: Size, zoom: Float, view: Size): Offset {
        val maxX = ((fitted.width * zoom - view.width) / 2f).coerceAtLeast(0f)
        val maxY = ((fitted.height * zoom - view.height) / 2f).coerceAtLeast(0f)
        return Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
    }

    /**
     * The pan that leaves whatever is under [cursor] (measured from the view centre) exactly
     * where it is while the zoom goes from [from] to [to] — so the wheel zooms *into* the spot
     * the pointer is on rather than into the middle of the screen.
     */
    fun panKeepingPointStill(pan: Offset, cursor: Offset, from: Float, to: Float): Offset =
        cursor - (cursor - pan) * (to / from)
}
