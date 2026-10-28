package de.creaflect.actiondraw.collage.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import de.creaflect.actiondraw.collage.Collage
import de.creaflect.actiondraw.collage.onMat
import de.creaflect.actiondraw.image.Pictures
import de.creaflect.actiondraw.ui.DesignSystem
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** The most pixels a collage is drawn as, along its longer side. */
private const val MOST = 4000f

/** How much of the mat is left round the pieces, of the longer side they cover. */
private const val MARGIN = 0.03f

/**
 * [collage] drawn flat as one picture, PNG — what is saved, pinned and taken into the other rooms:
 * its pieces as they lie, glued down without their shadows, cut to the space they cover with a
 * little margin, on the design's paper. As sharp as its sharpest picture allows, no sharper than
 * [MOST] pixels along. Null when nothing is on the mat.
 */
internal fun drawCollage(collage: Collage, pictures: Pictures, design: DesignSystem): ByteArray? {
    val pieces = collage.pieces
    if (pieces.isEmpty()) return null
    val corners = pieces.flatMap { it.onMat() }
    val long = max(corners.maxOf { it.x } - corners.minOf { it.x }, corners.maxOf { it.y } - corners.minOf { it.y }).coerceAtLeast(1f)
    val left = corners.minOf { it.x } - long * MARGIN
    val top = corners.minOf { it.y } - long * MARGIN
    val across = corners.maxOf { it.x } + long * MARGIN - left
    val down = corners.maxOf { it.y } + long * MARGIN - top
    // Pixels to a mat unit: a picture pixel for a picture pixel where a piece is shrunk, never fewer than one.
    val sharpest = pieces.filter { !it.isTape }.maxOfOrNull { 1f / it.scale } ?: 1f
    val k = min(max(sharpest, 1f), MOST / max(across, down))
    val w = ceil(across * k).toInt().coerceAtLeast(1)
    val h = ceil(down * k).toInt().coerceAtLeast(1)
    val images = pieces.mapNotNull { it.source }.distinct().associateWith { file ->
        pictures.decode(file)?.let { PieceImage(it, it.width.toFloat(), it.height.toFloat()) }
    }
    try {
        return Surface.makeRasterN32Premul(w, h).use { surface ->
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, surface.canvas.asComposeCanvas(), Size(w.toFloat(), h.toFloat())) {
                drawRect(design.colors.paper)
                val view = MatView(-left * k, -top * k, k)
                pieces.forEach { drawPiece(it, it.source?.let(images::get), view, design, lifted = false) }
            }
            surface.makeImageSnapshot().use { it.encodeToData(EncodedImageFormat.PNG)?.bytes }
        }
    } finally {
        images.values.forEach { it?.image?.close() }
    }
}
