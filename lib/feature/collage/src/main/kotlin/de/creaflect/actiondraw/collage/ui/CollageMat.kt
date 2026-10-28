package de.creaflect.actiondraw.collage.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
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
import de.creaflect.actiondraw.collage.Tool
import de.creaflect.actiondraw.collage.bounds
import de.creaflect.actiondraw.collage.middle
import de.creaflect.actiondraw.collage.onMat
import de.creaflect.actiondraw.collage.toMat
import de.creaflect.actiondraw.image.Pictures
import de.creaflect.actiondraw.ui.Design
import de.creaflect.actiondraw.ui.DesignSystem
import de.creaflect.actiondraw.ui.LocalDesign
import de.creaflect.actiondraw.ui.LocalReducedMotion
import kotlinx.coroutines.CancellationException
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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import org.jetbrains.skia.Rect as SkRect

/** The cutting mat's green, its fine grid and its ruled one. */
private val MatGreen = Color(0xFF2C5A4B)
private val MatLine = Color(0x1FE8F0E4)
private val MatRule = Color(0x47E8F0E4)
private val MatFigure = Color(0x99E8F0E4)

/** How far a piece lies off the mat, in mat units: its lamp shadow; tape lies almost flat. */
private const val LIFT = 14f
private const val TAPE_LIFT = 2.5f

/** What the mat says the tool in hand does, in the hand of the design — Move says nothing. */
private fun hintOf(tool: Tool): String? = when (tool) {
    Tool.MOVE -> null
    Tool.SCISSORS -> "click corners · drag freehand · shift to cut straight"
    Tool.TEAR -> "drag across a piece to tear it · shift tears straight"
    Tool.TAPE -> "drag to lay a strip · shift lays it level"
}

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
    val sources = state.collage.pieces.mapNotNull { it.source }.toSet()
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

/** What a press on the mat begins, by the tool in hand: followed as the pointer drags, ended as it lets go. */
private interface Gesture {
    fun drag(view: MatView, at: Offset, straight: Boolean)

    fun release(view: MatView, at: Offset, dragged: Boolean, straight: Boolean)

    fun cancel()
}

/** Move: the piece or handle [grab] took moved by the drag, as one change. */
private class Take(private val state: CollageState, private val grab: Grab?) : Gesture {
    private var began = false

    override fun drag(view: MatView, at: Offset, straight: Boolean) {
        if (!began) {
            state.beginDrag()
            began = true
        }
        grab?.drag(state, view, at, straight)
    }

    override fun release(view: MatView, at: Offset, dragged: Boolean, straight: Boolean) = cancel()

    override fun cancel() {
        if (began) state.endDrag()
        began = false
    }
}

/**
 * The scissors, or — [tearing] — the tear. With the scissors a press lays a corner, straight on from
 * the last one, and a drag cuts on from it freehand (Shift: straight); a drag on its own is a cut as
 * it lets go. A click on the first corner cuts all the way round, a click on the last one ends the
 * cut there. The tear is only dragged, and torn as it lets go. [reach] is how near a click is to a
 * corner, in pixels.
 */
private class Snip(private val state: CollageState, view: MatView, press: Offset, private val reach: Float, private val tearing: Boolean) : Gesture {
    private val trail = ArrayList<Spot>()
    private var base = emptyList<Spot>()
    private val fresh = tearing || state.path.isEmpty()
    private var done = false

    init {
        val at = view.toMat(press)
        val path = state.path
        val near = reach / view.scale
        when {
            !tearing && path.size >= 3 && close(at, path.first(), near) -> finish(closed = true)
            !tearing && path.size >= 2 && close(at, path.last(), near) -> finish(closed = false)
            else -> {
                base = (if (tearing) emptyList() else path) + at
                state.trace(base)
            }
        }
    }

    private fun finish(closed: Boolean) {
        state.cutAlongPath(closed)
        done = true
    }

    override fun drag(view: MatView, at: Offset, straight: Boolean) {
        if (done) return
        val m = view.toMat(at)
        // A place every couple of pixels the pointer goes.
        if (trail.isEmpty() || hypot(m.x - trail.last().x, m.y - trail.last().y) * view.scale >= 2f) trail += m
        state.trace(if (straight) base + m else base + trail)
    }

    override fun release(view: MatView, at: Offset, dragged: Boolean, straight: Boolean) {
        if (done) return
        if (!dragged) {
            // A click lays a corner — the tear has none.
            if (tearing) state.trace(emptyList())
            return
        }
        drag(view, at, straight)
        val path = state.path
        val near = reach / view.scale
        // Back where it began, all the way round — and not a twitch on the spot.
        val wide = path.maxOf { it.x } - path.minOf { it.x } + path.maxOf { it.y } - path.minOf { it.y } > 4 * near
        val round = path.size >= 3 && wide && close(path.last(), path.first(), near)
        if (fresh || round) finish(round)
    }

    override fun cancel() {
        if (!done && fresh) state.trace(emptyList())
    }

    private fun close(a: Spot, b: Spot, near: Float) = hypot(a.x - b.x, a.y - b.y) <= near
}

/** The tape: a strip from where the press was to where the pointer lets go — Shift: level, or in steps of 15° — shown to [laying] as it is dragged. */
private class Lay(private val state: CollageState, view: MatView, press: Offset, private val laying: (Pair<Spot, Spot>?) -> Unit) : Gesture {
    private val from = view.toMat(press)

    private fun to(view: MatView, at: Offset, straight: Boolean): Spot {
        val m = view.toMat(at)
        if (!straight) return m
        val long = hypot(m.x - from.x, m.y - from.y)
        val a = Math.toRadians((angle(Offset(from.x, from.y), Offset(m.x, m.y)) / 15f).roundToInt() * 15.0)
        return Spot(from.x + long * cos(a).toFloat(), from.y + long * sin(a).toFloat())
    }

    override fun drag(view: MatView, at: Offset, straight: Boolean) = laying(from to to(view, at, straight))

    override fun release(view: MatView, at: Offset, dragged: Boolean, straight: Boolean) {
        laying(null)
        state.layTape(from, if (dragged) to(view, at, straight) else from)
    }

    override fun cancel() = laying(null)
}

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
 * outlined — with its handles, while Move is in hand. What a press does is the tool's: with Move it
 * takes the chosen piece's handle, or the topmost piece under it — or lets go of the chosen one on
 * the bare mat — and a drag moves it, turns it (Shift: in steps of 15°) or grows it, as one change.
 * The scissors and the tear draw their path in Naples yellow, dashed and marching; the tape lays a
 * strip.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun CollageMat(state: CollageState, images: PieceImages, modifier: Modifier = Modifier) {
    val design = LocalDesign.current
    val hand = Design.hand
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val pad = with(density) { 14.dp.toPx() }
    val reach = with(density) { 12.dp.toPx() }
    val out = with(density) { 26.dp.toPx() }
    var view by remember { mutableStateOf<MatView?>(null) }
    var hover by remember { mutableStateOf<Offset?>(null) }
    var laying by remember { mutableStateOf<Pair<Spot, Spot>?>(null) }
    val current by rememberUpdatedState(view)
    // The path being cut moves like marching ants — unless motion is reduced. Read only while drawing.
    val ants = if (LocalReducedMotion.current || state.path.isEmpty()) {
        null
    } else {
        rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)))
    }
    Canvas(
        modifier
            .onSizeChanged { view = MatView.fit(it.toSize(), pad) }
            .pointerHoverIcon(if (state.tool == Tool.MOVE) PointerIcon.Default else PointerIcon.Crosshair)
            .onPointerEvent(PointerEventType.Move) { hover = it.changes.first().position }
            .onPointerEvent(PointerEventType.Exit) { hover = null }
            .pointerInput(state) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (currentEvent.buttons.isSecondaryPressed) return@awaitEachGesture
                    val v = current ?: return@awaitEachGesture
                    val gesture = begin(state, v, down.position, reach, out) { laying = it }
                    var dragged = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                gesture.cancel()
                                return@awaitEachGesture
                            }
                            val straight = event.keyboardModifiers.isShiftPressed
                            if (!change.pressed) {
                                gesture.release(current ?: v, change.position, dragged, straight)
                                return@awaitEachGesture
                            }
                            if (!dragged && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) dragged = true
                            if (dragged && change.positionChanged()) {
                                change.consume()
                                gesture.drag(current ?: v, change.position, straight)
                            }
                        }
                    } catch (e: CancellationException) {
                        gesture.cancel()
                        throw e
                    }
                }
            }
            .testTag("collage-mat"),
    ) {
        val v = view ?: MatView.fit(size, pad)
        drawMat(v, measurer)
        state.collage.pieces.forEach { drawPiece(it, it.source?.let(images::get), v, design, lifted = true) }
        state.selectedPiece?.let { drawChosen(it, v, out, design, handles = state.tool == Tool.MOVE) }
        laying?.let { (from, to) -> drawPiece(state.strip(from, to), null, v, design, lifted = true) }
        if (state.path.isNotEmpty()) {
            val toward = hover.takeIf { state.tool == Tool.SCISSORS }
            drawCut(state.path, toward, v, design.pigmentOf(Room.COLLAGE).glow, ants?.value ?: 0f)
        }
        if (state.tool == Tool.SCISSORS) hover?.let { drawScissors(it + Offset(8.dp.toPx(), -16.dp.toPx()), design.colors.paper) }
        hintOf(state.tool)?.let { hint ->
            val text = measurer.measure(hint, hand.copy(color = design.colors.paper.copy(alpha = 0.85f), fontSize = 20.sp))
            val corner = v.toScreen(Spot(Collage.MAT_WIDTH, Collage.MAT_HEIGHT))
            drawText(text, topLeft = corner - Offset(text.size.width + 18.dp.toPx(), text.size.height + 10.dp.toPx()))
        }
    }
}

/** The gesture a press at [press] begins, by the tool in hand. */
private fun begin(state: CollageState, view: MatView, press: Offset, reach: Float, out: Float, laying: (Pair<Spot, Spot>?) -> Unit): Gesture = when (state.tool) {
    Tool.MOVE -> {
        val grab = grabAt(state, view, press, reach, out)
        state.select(grab?.id)
        Take(state, grab)
    }
    Tool.SCISSORS -> Snip(state, view, press, reach, tearing = false)
    Tool.TEAR -> Snip(state, view, press, reach, tearing = true)
    Tool.TAPE -> Lay(state, view, press, laying)
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
 * piece is turned. A strip of tape is the design's masking tape, lying almost flat. The outline
 * drawn is the outline a click hits.
 */
internal fun DrawScope.drawPiece(piece: Piece, image: PieceImage?, view: MatView, design: DesignSystem, lifted: Boolean) {
    val outline = outlinePath(piece, view)
    if (piece.isTape) {
        with(design.materials) {
            if (lifted) drawLampShadow(outline, TAPE_LIFT * view.scale, alpha = 0.3f)
            drawPath(outline, tape)
        }
        // A warm shade over the tape's paper, so it reads on a light picture as well as on the mat.
        drawPath(outline, Color(0x1A6B5A3A))
        drawPath(outline, Color(0x30000000), style = Stroke(width = 0.8f))
        return
    }
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

/** The chosen piece: outlined in Naples yellow — with [handles], the one that turns it out from its top edge and the one that grows it at its corner. */
private fun DrawScope.drawChosen(piece: Piece, view: MatView, out: Float, design: DesignSystem, handles: Boolean) {
    val glow = design.pigmentOf(Room.COLLAGE).glow
    drawPath(outlinePath(piece, view), glow, style = Stroke(2.dp.toPx()))
    if (!handles) return
    val h = handlesOf(piece, view, out)
    drawLine(glow, h.edge, h.turn, 1.5.dp.toPx())
    drawCircle(Color(0xFF1C1A17), 7.dp.toPx(), h.turn)
    drawCircle(glow, 5.5.dp.toPx(), h.turn)
    val half = 5.dp.toPx()
    drawRect(Color(0xFF1C1A17), h.grow - Offset(half + 1.5f, half + 1.5f), Size(2 * half + 3f, 2 * half + 3f))
    drawRect(glow, h.grow - Offset(half, half), Size(2 * half, 2 * half))
}

/**
 * The path being cut, through [view]: dashed in Naples yellow over a shadow line, the dashes marching
 * on by [phase]; a fainter line on [toward] the pointer, where the next corner would go; and a ring
 * at its first corner, where a click cuts all the way round.
 */
private fun DrawScope.drawCut(path: List<Spot>, toward: Offset?, view: MatView, glow: Color, phase: Float) {
    val dash = 8.dp.toPx()
    val gap = 6.dp.toPx()
    val marching = PathEffect.dashPathEffect(floatArrayOf(dash, gap), -phase * (dash + gap))
    val line = Path().apply {
        path.forEachIndexed { i, m ->
            val p = view.toScreen(m)
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
    }
    val under = Color(0x8C000000)
    drawPath(line, under, style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(line, glow, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = marching))
    val last = view.toScreen(path.last())
    toward?.let { drawLine(glow.copy(alpha = 0.55f), last, it, 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash / 2, gap / 2))) }
    if (path.size >= 2) {
        val first = view.toScreen(path.first())
        drawCircle(under, 5.dp.toPx(), first)
        drawCircle(glow, 5.dp.toPx(), first, style = Stroke(1.5.dp.toPx()))
    }
}

/** A pair of open scissors, their points at [at], drawn in [ink] over a shadow line — what the pointer cuts with. */
private fun DrawScope.drawScissors(at: Offset, ink: Color) {
    val s = 1.dp.toPx()
    val glyph = Path().apply {
        moveTo(0f, -3f * s)
        lineTo(12f * s, 3.5f * s)
        moveTo(0f, 3f * s)
        lineTo(12f * s, -3.5f * s)
        addOval(Rect(Offset(15.5f * s, 5.5f * s), 3.5f * s))
        addOval(Rect(Offset(15.5f * s, -5.5f * s), 3.5f * s))
    }
    translate(at.x, at.y) {
        rotate(-35f, Offset.Zero) {
            drawPath(glyph, Color(0x99000000), style = Stroke(3f * s, cap = StrokeCap.Round))
            drawPath(glyph, ink, style = Stroke(1.5f * s, cap = StrokeCap.Round))
        }
    }
}
