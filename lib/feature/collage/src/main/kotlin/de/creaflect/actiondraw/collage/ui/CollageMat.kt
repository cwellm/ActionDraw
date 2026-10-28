package de.creaflect.actiondraw.collage.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import de.creaflect.actiondraw.Room
import de.creaflect.actiondraw.collage.Collage
import de.creaflect.actiondraw.collage.CollageState
import de.creaflect.actiondraw.collage.Piece
import de.creaflect.actiondraw.collage.Spot
import de.creaflect.actiondraw.collage.bounds
import de.creaflect.actiondraw.collage.middle
import de.creaflect.actiondraw.collage.onMat
import de.creaflect.actiondraw.collage.toMat
import de.creaflect.actiondraw.image.Pictures
import de.creaflect.actiondraw.ui.DesignSystem
import de.creaflect.actiondraw.ui.LocalDesign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import java.io.File
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.jetbrains.skia.Rect as SkRect

/** The cutting mat's green, its fine grid and its ruled one. */
private val MatGreen = Color(0xFF2C5A4B)
private val MatLine = Color(0x1FE8F0E4)
private val MatRule = Color(0x47E8F0E4)
private val MatFigure = Color(0x99E8F0E4)

/** How far a piece lies off the mat, in mat units: its lamp shadow. */
private const val LIFT = 14f

/** How large a picture is kept to draw pieces from, on its long side: enough for the mat, few enough pixels to hold several. */
private const val DRAWN_SIZE = 2048

private val Sampling: SamplingMode = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)

/** How the mat lies in the view: its top left corner at ([left], [top]), [scale] pixels to a mat unit. */
internal class MatView(val left: Float, val top: Float, val scale: Float) {
    fun toScreen(m: Spot): Offset = Offset(left + m.x * scale, top + m.y * scale)

    fun toMat(o: Offset): Spot = Spot((o.x - left) / scale, (o.y - top) / scale)

    companion object {
        /** The mat as large as fits [size] with [pad] pixels round it, in its middle. */
        fun fit(size: Size, pad: Float): MatView {
            val scale = min((size.width - 2 * pad) / Collage.MAT_WIDTH, (size.height - 2 * pad) / Collage.MAT_HEIGHT).coerceAtLeast(0.01f)
            return MatView((size.width - Collage.MAT_WIDTH * scale) / 2f, (size.height - Collage.MAT_HEIGHT * scale) / 2f, scale)
        }

        /** [piece] filling [size] as it lies: what the pieces list shows. */
        fun around(piece: Piece, size: Size): MatView {
            val corners = piece.onMat()
            val l = corners.minOf { it.x }
            val t = corners.minOf { it.y }
            val w = (corners.maxOf { it.x } - l).coerceAtLeast(1f)
            val h = (corners.maxOf { it.y } - t).coerceAtLeast(1f)
            val scale = min(size.width / w, size.height / h)
            return MatView((size.width - w * scale) / 2f - l * scale, (size.height - h * scale) / 2f - t * scale, scale)
        }
    }
}

/** A piece's picture, kept to draw it from — or none, where it cannot be read — and the picture's own size in pixels. */
internal class PieceImage(val image: Image?, val width: Float, val height: Float)

/**
 * The pieces' pictures, decoded off the UI thread as they first come onto the mat and kept while
 * the room is on screen: large ones no larger than the mat needs. Each piece is drawn in its
 * picture's own pixels, whatever size the picture is kept at.
 */
internal class PieceImages {
    private val loaded = mutableStateMapOf<File, PieceImage>()

    operator fun get(file: File): PieceImage? = loaded[file]

    suspend fun load(files: Collection<File>, pictures: Pictures) {
        for (file in files) {
            if (file in loaded) continue
            loaded[file] = withContext(Dispatchers.IO) {
                val whole = pictures.decode(file) ?: return@withContext PieceImage(null, 1f, 1f)
                PieceImage(shrunk(whole), whole.width.toFloat(), whole.height.toFloat())
            }
        }
    }

    fun close() {
        loaded.values.forEach { it.image?.close() }
        loaded.clear()
    }

    private fun shrunk(whole: Image): Image {
        val k = DRAWN_SIZE.toFloat() / max(whole.width, whole.height)
        if (k >= 1f) return whole
        val w = max(1, (whole.width * k).roundToInt())
        val h = max(1, (whole.height * k).roundToInt())
        return Surface.makeRasterN32Premul(w, h).use { surface ->
            surface.canvas.drawImageRect(whole, SkRect.makeWH(whole.width.toFloat(), whole.height.toFloat()), SkRect.makeWH(w.toFloat(), h.toFloat()), Sampling, null, true)
            surface.makeImageSnapshot()
        }.also { whole.close() }
    }
}

/** The pieces' pictures for the room on screen, loaded as pieces come in and let go of when it goes. */
@Composable
internal fun rememberPieceImages(state: CollageState): PieceImages {
    val images = remember { PieceImages() }
    DisposableEffect(images) { onDispose { images.close() } }
    val sources = state.collage.pieces.map { it.source }.toSet()
    LaunchedEffect(sources) { images.load(sources, state.pictures) }
    return images
}

/**
 * What a press on the mat took hold of, and what the drag after it does: the chosen piece by its
 * handle to turn or to grow, or a piece by itself to move — each from where it was as it was taken.
 */
private sealed class Grab(val id: String) {
    abstract fun drag(state: CollageState, view: MatView, pointer: Offset, straight: Boolean)

    class Move(id: String, val from: Spot, val x: Float, val y: Float) : Grab(id) {
        override fun drag(state: CollageState, view: MatView, pointer: Offset, straight: Boolean) {
            val at = view.toMat(pointer)
            state.moveTo(id, x + at.x - from.x, y + at.y - from.y)
        }
    }

    class Turn(id: String, val centre: Offset, val from: Float, val turn: Float) : Grab(id) {
        override fun drag(state: CollageState, view: MatView, pointer: Offset, straight: Boolean) {
            val degrees = turn + angle(centre, pointer) - from
            // Shift turns in steps of 15°, to set a piece square again.
            state.turnTo(id, if (straight) (degrees / 15f).roundToInt() * 15f else degrees)
        }
    }

    class Grow(id: String, val centre: Offset, val from: Float, val scale: Float) : Grab(id) {
        override fun drag(state: CollageState, view: MatView, pointer: Offset, straight: Boolean) {
            state.scaleTo(id, scale * (pointer - centre).getDistance() / from)
        }
    }
}

private fun angle(centre: Offset, p: Offset): Float = Math.toDegrees(atan2((p.y - centre.y).toDouble(), (p.x - centre.x).toDouble())).toFloat()

/** Where the chosen piece's handles are on screen: the one that turns it, out from its top edge, and the one that grows it, at its corner. */
private class Handles(val centre: Offset, val edge: Offset, val turn: Offset, val grow: Offset)

private fun handlesOf(piece: Piece, view: MatView, out: Float): Handles {
    val b = piece.bounds()
    val centre = view.toScreen(piece.toMat(piece.middle()))
    val edge = view.toScreen(piece.toMat(Spot((b[0] + b[2]) / 2f, b[1])))
    val up = (edge - centre).let { d -> d / d.getDistance().coerceAtLeast(0.001f) }
    return Handles(centre, edge, edge + up * out, view.toScreen(piece.toMat(Spot(b[2], b[3]))))
}

/**
 * The cutting mat (F10.6): green, with a fine grid, a ruled one and a ruler along its top, the
 * pieces on it from the bottom of the stack up, each lifted a little off it, and the chosen one
 * outlined with its handles. A press takes the chosen piece's handle, or the topmost piece under
 * it — or lets go of the chosen one on the bare mat; a drag then moves it, turns it (Shift: in
 * steps of 15°) or grows it, and lets go as one change.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun CollageMat(state: CollageState, images: PieceImages, modifier: Modifier = Modifier) {
    val design = LocalDesign.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val pad = with(density) { 14.dp.toPx() }
    val reach = with(density) { 12.dp.toPx() }
    val out = with(density) { 26.dp.toPx() }
    var view by remember { mutableStateOf<MatView?>(null) }
    var grab by remember { mutableStateOf<Grab?>(null) }
    var straight by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(view)
    Canvas(
        modifier
            .onSizeChanged { view = MatView.fit(it.toSize(), pad) }
            .onPointerEvent(PointerEventType.Move) { straight = it.keyboardModifiers.isShiftPressed }
            .onPointerEvent(PointerEventType.Press) { event ->
                val v = current ?: return@onPointerEvent
                val at = event.changes.first().position
                straight = event.keyboardModifiers.isShiftPressed
                grab = grabAt(state, v, at, reach, out)
                state.select(grab?.id)
            }
            .pointerInput(state) {
                detectDragGestures(
                    onDragStart = { state.beginDrag() },
                    onDragEnd = { state.endDrag() },
                    onDragCancel = { state.endDrag() },
                ) { change, _ ->
                    change.consume()
                    val v = current ?: return@detectDragGestures
                    grab?.drag(state, v, change.position, straight)
                }
            }
            .testTag("collage-mat"),
    ) {
        val v = view ?: MatView.fit(size, pad)
        drawMat(v, measurer)
        state.collage.pieces.forEach { drawPiece(it, images[it.source], v, design, lifted = true) }
        state.selectedPiece?.let { drawChosen(it, v, out, design) }
    }
}

private fun grabAt(state: CollageState, view: MatView, at: Offset, reach: Float, out: Float): Grab? {
    state.selectedPiece?.let { piece ->
        val h = handlesOf(piece, view, out)
        if ((at - h.turn).getDistance() <= reach) return Grab.Turn(piece.id, h.centre, angle(h.centre, at), piece.turn)
        if ((at - h.grow).getDistance() <= reach) return Grab.Grow(piece.id, h.centre, (at - h.centre).getDistance().coerceAtLeast(1f), piece.scale)
    }
    val spot = view.toMat(at)
    val piece = state.pieceAt(spot) ?: return null
    return Grab.Move(piece.id, spot, piece.x, piece.y)
}

/** The mat itself: green, its grid every quarter of a mark, a heavier line every mark, and the ruler's figures along its top. */
private fun DrawScope.drawMat(view: MatView, measurer: androidx.compose.ui.text.TextMeasurer) {
    val topLeft = view.toScreen(Spot(0f, 0f))
    val size = Size(Collage.MAT_WIDTH * view.scale, Collage.MAT_HEIGHT * view.scale)
    drawRoundRect(MatGreen, topLeft, size, CornerRadius(10.dp.toPx()))
    val step = Collage.RULER / 4f
    var x = step
    while (x < Collage.MAT_WIDTH) {
        val major = (x % Collage.RULER) == 0f
        drawLine(if (major) MatRule else MatLine, view.toScreen(Spot(x, 0f)), view.toScreen(Spot(x, Collage.MAT_HEIGHT)), if (major) 1.2f else 1f)
        x += step
    }
    var y = step
    while (y < Collage.MAT_HEIGHT) {
        val major = (y % Collage.RULER) == 0f
        drawLine(if (major) MatRule else MatLine, view.toScreen(Spot(0f, y)), view.toScreen(Spot(Collage.MAT_WIDTH, y)), if (major) 1.2f else 1f)
        y += step
    }
    val figure = TextStyle(color = MatFigure, fontSize = 9.sp)
    var mark = 1
    while (mark * Collage.RULER < Collage.MAT_WIDTH) {
        val at = view.toScreen(Spot(mark * Collage.RULER, 0f))
        drawLine(MatFigure, at, at + Offset(0f, 7.dp.toPx()), 1.2f)
        val text = measurer.measure(mark.toString(), figure)
        drawText(text, topLeft = at + Offset(-text.size.width / 2f, 8.dp.toPx()))
        mark++
    }
}

/**
 * [piece] as it lies, through [view]: its picture clipped to its outline — paper where the picture
 * cannot be read — and, [lifted], the lamp's shadow under it, cast to the bottom right however the
 * piece is turned. The outline drawn is the outline a click hits.
 */
internal fun DrawScope.drawPiece(piece: Piece, image: PieceImage?, view: MatView, design: DesignSystem, lifted: Boolean) {
    val outline = outlinePath(piece, view)
    if (lifted) with(design.materials) { drawLampShadow(outline, LIFT * view.scale) }
    clipPath(outline) {
        val picture = image?.image
        if (picture == null) {
            drawPath(outline, design.colors.paper)
            return@clipPath
        }
        val c = piece.middle()
        val s = piece.scale * view.scale
        val at = view.toScreen(Spot(piece.x, piece.y))
        withTransform({
            translate(at.x, at.y)
            rotate(piece.turn, Offset.Zero)
            scale(if (piece.flipped) -s else s, s, Offset.Zero)
            translate(-c.x, -c.y)
        }) {
            drawIntoCanvas {
                Paint().use { paint ->
                    it.nativeCanvas.drawImageRect(
                        picture,
                        SkRect.makeWH(picture.width.toFloat(), picture.height.toFloat()),
                        SkRect.makeWH(image.width, image.height),
                        Sampling,
                        paint,
                        true,
                    )
                }
            }
        }
    }
}

/** The piece's outline on screen, through [view]. */
internal fun outlinePath(piece: Piece, view: MatView): Path = Path().apply {
    piece.onMat().forEachIndexed { i, m ->
        val p = view.toScreen(m)
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

/** The chosen piece: outlined in Naples yellow, with the handle that turns it out from its top edge and the one that grows it at its corner. */
private fun DrawScope.drawChosen(piece: Piece, view: MatView, out: Float, design: DesignSystem) {
    val glow = design.pigmentOf(Room.COLLAGE).glow
    val h = handlesOf(piece, view, out)
    drawPath(outlinePath(piece, view), glow, style = Stroke(2.dp.toPx()))
    drawLine(glow, h.edge, h.turn, 1.5.dp.toPx())
    drawCircle(Color(0xFF1C1A17), 7.dp.toPx(), h.turn)
    drawCircle(glow, 5.5.dp.toPx(), h.turn)
    val half = 5.dp.toPx()
    drawRect(Color(0xFF1C1A17), h.grow - Offset(half + 1.5f, half + 1.5f), Size(2 * half + 3f, 2 * half + 3f))
    drawRect(glow, h.grow - Offset(half, half), Size(2 * half, 2 * half))
}
