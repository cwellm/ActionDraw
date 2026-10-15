package de.creaflect.actiondraw.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.asSkiaColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.creaflect.actiondraw.AppState
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import java.awt.Cursor
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import org.jetbrains.skia.Rect as SkRect

/** What the pointer is doing over the picture, in the picture area's own pixels. */
@Stable
class GlassPointer {
    /** Where the pointer is, or null when it is off the picture. */
    var at by mutableStateOf<Offset?>(null)

    /** Where a part of the picture is being dragged out from, while it is. */
    var dragFrom by mutableStateOf<Offset?>(null)

    /** The picture area's size, in pixels. */
    var area by mutableStateOf(Size.Zero)
}

/** Further than this, a press and release is a drag, not a click. */
private const val CLICK_SLOP = 6f

/**
 * The picture, drawn through the glass: whole and fitted, or the part the glass is down on, as
 * far as [progress] has come; and while the glass is in hand, a round of it larger under the
 * pointer. Drawn inside the picture's filter layers, so a lens and the knobs work on what the
 * glass shows as on the picture; the rim is [GlassOverlay]'s, over them.
 */
@Composable
fun GlassPicture(
    bitmap: ImageBitmap,
    state: AppState,
    picture: File?,
    pointer: GlassPointer,
    progress: Animatable<Float, *>,
    lastFocus: MutableState<GlassFocus?>,
    colorFilter: ColorFilter?,
    modifier: Modifier = Modifier,
) {
    val glass = state.glass
    val image = remember(bitmap) { Image.makeFromBitmap(bitmap.asSkiaBitmap()) }
    DisposableEffect(image) { onDispose { image.close() } }
    val filter = remember(colorFilter) { colorFilter?.asSkiaColorFilter() }
    Canvas(modifier) {
        val iw = image.width.toFloat()
        val ih = image.height.toFloat()
        val w = size.width
        val h = size.height
        val p = progress.value
        val focus = glass.focusOn(picture) ?: lastFocus.value
        val view = pictureView(iw, ih, w, h, focus, p)
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            Paint().use { paint ->
                paint.colorFilter = filter
                // The picture, whole or under the glass that fills the view.
                val shader = if (p > 0f && focus != null && glass.optic != Optic.PLAIN) {
                    opticShader(image, view.matrix(), glass.optic, w / 2f, h / 2f, hypot(w, h) / 2f, zoom = 1f, strength = p)
                } else {
                    image.makeShader(FilterTileMode.DECAL, FilterTileMode.DECAL, GlassSampling, view.matrix())
                }
                shader.use {
                    paint.shader = it
                    native.drawRect(SkRect.makeWH(w, h), paint)
                }
                // The glass in hand, larger under the pointer — not while a part is being dragged out.
                val at = pointer.at
                if (glass.inHand && p == 0f && at != null && pointer.dragFrom == null) {
                    val c = toLayer(at, w, h, state.mirror, state.upsideDown)
                    val r = glass.radius.dp.toPx()
                    native.save()
                    native.clipPath(org.jetbrains.skia.Path().addCircle(c.x, c.y, r), true)
                    paint.shader = null
                    paint.color = org.jetbrains.skia.Color.BLACK
                    native.drawCircle(c.x, c.y, r, paint)
                    opticShader(image, view.matrix(), glass.optic, c.x, c.y, r, zoom = 1f / glass.magnification, strength = 1f).use {
                        paint.shader = it
                        native.drawCircle(c.x, c.y, r, paint)
                    }
                    native.restore()
                }
            }
        }
    }
}

/** No cursor while the glass is the pointer; made once, and a plain cross where a custom cursor cannot be made. */
private val NoCursor: PointerIcon by lazy {
    runCatching {
        PointerIcon(Toolkit.getDefaultToolkit().createCustomCursor(BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), java.awt.Point(0, 0), "glass"))
    }.getOrDefault(PointerIcon.Crosshair)
}

private val MoveCursor = PointerIcon(Cursor(Cursor.MOVE_CURSOR))

/**
 * The glass, taken in hand over the picture area: a click sets it down on the point under it,
 * a drag sets it down on the part dragged out; while it is down, a drag slides the picture under
 * it and the wheel brings it nearer about the pointer; a right click lifts it off. In the hand, the
 * wheel magnifies and Shift+wheel makes the glass larger. Without the glass, nothing is taken.
 */
fun Modifier.glassInput(state: AppState, pointer: GlassPointer, picture: File?, iw: Float, ih: Float, focused: Boolean): Modifier =
    pointerHoverIcon(
        when {
            !state.glass.inHand -> PointerIcon.Default
            focused -> MoveCursor
            else -> NoCursor
        },
    ).pointerInput(picture, iw, ih) {
        awaitPointerEventScope {
            var downAt: Offset? = null
            var last: Offset? = null
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: continue
                val glass = state.glass
                val pos = change.position
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                fun layer(p: Offset) = toLayer(p, w, h, state.mirror, state.upsideDown)
                // A press or release something over the picture took — the print in the corner —
                // is not the glass's: lifted by the print, it must not be set down again under it.
                if (change.isConsumed && (event.type == PointerEventType.Press || event.type == PointerEventType.Release)) {
                    downAt = null
                    pointer.dragFrom = null
                    continue
                }
                when (event.type) {
                    PointerEventType.Enter, PointerEventType.Move -> {
                        pointer.at = pos
                        val from = downAt
                        if (from != null && change.pressed && glass.inHand) {
                            val focus = glass.focusOn(picture)
                            if (focus != null) {
                                // Down: the picture slides under the glass, as under a hand.
                                val a = layer(last ?: pos)
                                val b = layer(pos)
                                val scale = pictureView(iw, ih, w, h, focus, 1f).scale
                                glass.moveBy(-(b.x - a.x) / scale / iw, -(b.y - a.y) / scale / ih)
                            } else if ((pos - from).getDistance() >= CLICK_SLOP) {
                                pointer.dragFrom = from
                            }
                            change.consume()
                        }
                        last = pos
                    }
                    PointerEventType.Exit -> pointer.at = null
                    PointerEventType.Press -> if (glass.inHand) {
                        if (event.buttons.isSecondaryPressed) {
                            glass.wholePicture()
                        } else {
                            downAt = pos
                            last = pos
                        }
                        change.consume()
                    }
                    PointerEventType.Release -> {
                        val from = downAt
                        downAt = null
                        if (from != null && glass.inHand && glass.focusOn(picture) == null) {
                            val whole = pictureView(iw, ih, w, h, null, 0f)
                            if ((pos - from).getDistance() < CLICK_SLOP) {
                                val at = whole.toPicture(layer(pos))
                                if (at.x in 0f..iw && at.y in 0f..ih) glass.setDown(picture, at.x / iw, at.y / ih)
                            } else {
                                val a = whole.toPicture(layer(from))
                                val b = whole.toPicture(layer(pos))
                                val m = magnificationFor(iw, ih, w, h, abs(b.x - a.x), abs(b.y - a.y))
                                glass.setDown(picture, (a.x + b.x) / 2f / iw, (a.y + b.y) / 2f / ih, m)
                            }
                            change.consume()
                        }
                        pointer.dragFrom = null
                    }
                    PointerEventType.Scroll -> if (glass.inHand) {
                        // Shift turns a wheel sideways on some systems: take whichever way it went.
                        val delta = change.scrollDelta.y.takeIf { it != 0f } ?: change.scrollDelta.x
                        val steps = -delta
                        val focus = glass.focusOn(picture)
                        when {
                            focus != null -> {
                                // Nearer about the pointer: the point under it stays under it.
                                val local = layer(pos)
                                val under = pictureView(iw, ih, w, h, focus, 1f).toPicture(local)
                                glass.magnifyBy(steps)
                                val scale = fitScale(iw, ih, w, h) * glass.magnification
                                glass.setDown(picture, (under.x - (local.x - w / 2f) / scale) / iw, (under.y - (local.y - h / 2f) / scale) / ih)
                            }
                            event.keyboardModifiers.isShiftPressed -> glass.resizeBy(steps)
                            else -> glass.magnifyBy(steps)
                        }
                        change.consume()
                    }
                }
            }
        }
    }

/**
 * Over the picture and its filters, what is not the picture: the glass in the hand — rim, handle,
 * the light on it, its shadow — or the part being dragged out; and, while the glass is down, its
 * edge round the view and the whole picture in the corner, one click back.
 */
@Composable
fun BoxScope.GlassOverlay(
    bitmap: ImageBitmap,
    state: AppState,
    picture: File?,
    pointer: GlassPointer,
    progress: Animatable<Float, *>,
    focused: Boolean,
) {
    val glass = state.glass
    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        if (p > 0f) {
            // Looking through glass: its edge darkens the view's.
            drawRect(Brush.radialGradient(0.62f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.42f * p), center = center, radius = size.maxDimension * 0.62f))
        }
        val at = pointer.at
        if (!glass.inHand || p > 0f || at == null) return@Canvas
        val from = pointer.dragFrom
        if (from != null) {
            drawPart(Rect(from, at))
        } else {
            drawGlass(at, glass.radius.dp.toPx())
        }
    }
    if (focused) WholePicture(bitmap, state, picture, pointer, Modifier.align(Alignment.BottomStart).padding(16.dp))
}

/** A part being dragged out: a pencilled frame, the rest of the picture a shade darker. */
private fun DrawScope.drawPart(part: Rect) {
    val r = Rect(minOf(part.left, part.right), minOf(part.top, part.bottom), maxOf(part.left, part.right), maxOf(part.top, part.bottom))
    val outside = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(Rect(Offset.Zero, size))
        addRect(r)
    }
    drawPath(outside, Color.Black.copy(alpha = 0.35f))
    drawRect(Atelier.Paper.copy(alpha = 0.9f), r.topLeft, r.size, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))))
}

/**
 * A reading glass lying over the picture at [c], [r] across: its shadow on the picture, the
 * glass a little darker towards its edge, the lamp's light on it, a turned rim and a handle.
 */
private fun DrawScope.drawGlass(c: Offset, r: Float) {
    val rim = (r * 0.085f).coerceAtLeast(5.dp.toPx())
    val handleWidth = (r * 0.2f).coerceAtLeast(12.dp.toPx())
    val handleLength = r * 1.05f
    val ring = Path().apply {
        fillType = PathFillType.EvenOdd
        addOval(Rect(c, r + rim))
        addOval(Rect(c, r))
    }
    val handle = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(c.x + r + rim * 0.5f, c.y - handleWidth / 2f, c.x + r + rim + handleLength, c.y + handleWidth / 2f), androidx.compose.ui.geometry.CornerRadius(handleWidth / 2f))) }
    rotate(45f, c) {
        drawLampShadow(Path().apply { addPath(ring); addPath(handle) }, 10.dp.toPx(), alpha = 0.5f)
    }
    // The glass: darker towards its edge, where it is thicker, and the lamp's light on its top left.
    drawCircle(Brush.radialGradient(0.7f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.28f), center = c, radius = r), r, c)
    drawArc(
        Color.White.copy(alpha = 0.32f), 200f, 55f, false,
        topLeft = c - Offset(r * 0.8f, r * 0.8f), size = Size(r * 1.6f, r * 1.6f),
        style = Stroke(r * 0.05f, cap = StrokeCap.Round),
    )
    // The rim, turned metal in Payne's grey, catching the light round its top.
    drawCircle(
        Brush.sweepGradient(listOf(Color(0xFF8D99A6), Color(0xFF2A2F35), Color(0xFF46525F), Color(0xFFC9D1D9), Color(0xFF8D99A6)), center = c),
        radius = r + rim / 2f, center = c, style = Stroke(rim),
    )
    drawCircle(Color.White.copy(alpha = 0.22f), r, c, style = Stroke(1f))
    // The handle, turned wood with a metal collar, down to the right.
    rotate(45f, c) {
        val x0 = c.x + r + rim * 0.5f
        drawRect(Brush.verticalGradient(listOf(Color(0xFFC9D1D9), Color(0xFF46525F)), c.y - handleWidth * 0.42f, c.y + handleWidth * 0.42f), Offset(x0, c.y - handleWidth * 0.42f), Size(handleWidth * 0.7f, handleWidth * 0.84f))
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFF6B4A33), Color(0xFF3B271A), Color(0xFF24170F)), c.y - handleWidth / 2f, c.y + handleWidth / 2f),
            Offset(x0 + handleWidth * 0.7f, c.y - handleWidth / 2f),
            Size(handleLength - handleWidth * 0.2f, handleWidth),
            androidx.compose.ui.geometry.CornerRadius(handleWidth / 2f),
        )
        drawLine(Color.White.copy(alpha = 0.18f), Offset(x0 + handleWidth, c.y - handleWidth * 0.28f), Offset(x0 + handleLength, c.y - handleWidth * 0.28f), handleWidth * 0.12f, cap = StrokeCap.Round)
    }
}

/**
 * The whole picture as a small print in the corner while the glass is down, where it is down
 * ringed in pencil: a click on it, like Esc or a right click, lifts the glass off.
 */
@Composable
private fun WholePicture(bitmap: ImageBitmap, state: AppState, picture: File?, pointer: GlassPointer, modifier: Modifier) {
    val glass = state.glass
    val longest = 150.dp
    val aspect = bitmap.width.toFloat() / bitmap.height
    val (pw, ph) = if (aspect >= 1f) longest to longest / aspect else longest * aspect to longest
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .lampShadow(Lift.LIFTED)
                .background(Atelier.Paper)
                .paperTooth()
                .clickable(onClickLabel = "Back to the whole picture") { glass.wholePicture() }
                .padding(5.dp)
                .testTag("glass-whole"),
        ) {
            Box(
                Modifier.size(pw, ph).graphicsLayer {
                    if (state.upsideDown) rotationZ = 180f
                    if (state.mirror) scaleX = -1f
                },
            ) {
                Image(bitmap, contentDescription = "The whole picture", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                Canvas(Modifier.fillMaxSize().testTag("glass-whole-part")) {
                    val focus = glass.focusOn(picture) ?: return@Canvas
                    val area = pointer.area
                    if (area.width <= 0f || area.height <= 0f) return@Canvas
                    // What the glass shows: the view's corners taken back to the picture, in the print's measure.
                    val iw = bitmap.width.toFloat()
                    val ih = bitmap.height.toFloat()
                    val view = pictureView(iw, ih, area.width, area.height, focus, 1f)
                    val a = view.toPicture(Offset.Zero)
                    val b = view.toPicture(Offset(area.width, area.height))
                    val part = Rect(
                        (a.x / iw).coerceIn(0f, 1f) * size.width, (a.y / ih).coerceIn(0f, 1f) * size.height,
                        (b.x / iw).coerceIn(0f, 1f) * size.width, (b.y / ih).coerceIn(0f, 1f) * size.height,
                    )
                    drawPath(Path().apply { fillType = PathFillType.EvenOdd; addRect(Rect(Offset.Zero, size)); addRect(part) }, Color.Black.copy(alpha = 0.4f))
                    drawRect(Atelier.Paper, part.topLeft, part.size, style = Stroke(1.6.dp.toPx()))
                    drawRect(Room.LENS.pigment.mass, part.topLeft, part.size, style = Stroke(0.7.dp.toPx()))
                }
            }
        }
        Text(
            "×%.1f · %s · Esc for the whole".format(glass.magnification, glass.optic.label.lowercase()),
            fontSize = 11.sp,
            color = Atelier.TextSoft,
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

// ---------------- The glass among the controls ----------------

/**
 * The glass beside the knobs: the glass itself, taken up and put away (L), and its six optics
 * as cross-sections of their glass; choosing one takes the glass up. O turns to the next.
 */
@Composable
fun GlassTray(state: AppState) {
    val glass = state.glass
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.testTag("glass-tray")) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                .width(52.dp)
                .toggleable(value = glass.inHand, role = Role.Switch) { glass.toggle() }
                .testTag("glass"),
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(if (glass.inHand) Room.LENS.pigment.glow else Atelier.Raised),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(20.dp)) {
                    val ink = if (glass.inHand) Atelier.OnGlow else Atelier.TextSoft
                    val r = size.minDimension * 0.3f
                    val c = Offset(r + 1.5f, r + 1.5f)
                    drawCircle(ink, r, c, style = Stroke(1.6f))
                    drawLine(ink, c + Offset(r * 0.72f, r * 0.72f), Offset(size.width - 1f, size.height - 1f), 2.6f, cap = StrokeCap.Round)
                }
            }
            Text("Glass", fontSize = 11.sp, color = if (glass.inHand) Atelier.Text else Atelier.Muted)
            Text("L", fontSize = 10.sp, color = Atelier.InkQuiet)
        }
        Optic.entries.forEach { optic ->
            val chosen = glass.inHand && glass.optic == optic
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .width(54.dp)
                    .selectable(chosen, role = Role.RadioButton) { glass.choose(optic) }
                    .testTag("glass-optic-${optic.name.lowercase()}"),
            ) {
                Box(
                    Modifier.size(44.dp, 40.dp).clip(RoundedCornerShape(8.dp)).background(if (chosen) Atelier.Raised else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(34.dp, 26.dp)) { drawOpticSection(optic, chosen) }
                }
                Text(optic.label, fontSize = 11.sp, color = if (chosen) Atelier.Text else Atelier.Muted, maxLines = 1)
                Text(if (glass.optic == optic) "O" else " ", fontSize = 10.sp, color = Atelier.InkQuiet)
            }
        }
    }
}

/** An optic as a cross-section of its glass, light passing left to right, in a 34 × 26 box. */
private fun DrawScope.drawOpticSection(optic: Optic, chosen: Boolean) {
    val s = size.width / 34f
    val fill = Room.LENS.pigment.glow.copy(alpha = if (chosen) 0.85f else 0.45f)
    val edge = if (chosen) Atelier.Text else Atelier.TextSoft
    fun pt(x: Float, y: Float) = Offset(x * s, y * s)
    val path = Path().apply {
        when (optic) {
            Optic.PLAIN -> addRect(Rect(pt(14f, 2f), pt(20f, 24f)))
            Optic.CONVEX -> {
                moveTo(17f * s, 1f * s)
                quadraticTo(29f * s, 13f * s, 17f * s, 25f * s)
                quadraticTo(5f * s, 13f * s, 17f * s, 1f * s)
            }
            Optic.CONCAVE -> {
                moveTo(10f * s, 2f * s)
                lineTo(24f * s, 2f * s)
                quadraticTo(15f * s, 13f * s, 24f * s, 24f * s)
                lineTo(10f * s, 24f * s)
                quadraticTo(19f * s, 13f * s, 10f * s, 2f * s)
            }
            Optic.FISHEYE -> {
                moveTo(24f * s, 1f * s)
                cubicTo(4f * s, 1f * s, 4f * s, 25f * s, 24f * s, 25f * s)
                close()
            }
            Optic.CYLINDER -> addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(pt(3f, 8f), pt(31f, 18f)), androidx.compose.ui.geometry.CornerRadius(5f * s)))
            Optic.PRISM -> {
                moveTo(17f * s, 2f * s)
                lineTo(29f * s, 24f * s)
                lineTo(5f * s, 24f * s)
                close()
            }
        }
    }
    drawPath(path, fill)
    drawPath(path, edge, style = Stroke(1f))
    if (optic == Optic.PRISM) {
        // White light in, colours out.
        drawLine(Atelier.Paper.copy(alpha = 0.8f), pt(0f, 15f), pt(11f, 15f), 1.2f)
        listOf(Color(0xFFE0574A), Color(0xFF7CC46A), Color(0xFF5B7FE0)).forEachIndexed { i, colour ->
            drawLine(colour, pt(24f, 15f), pt(34f, 12f + i * 3.5f), 1.2f)
        }
    }
}
