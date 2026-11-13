package de.creaflect.actiondraw.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.creaflect.actiondraw.Room
import de.creaflect.actiondraw.lens.LensState
import java.io.File

/** How far a press moves before it is a drag, not a click. */
private const val ZOOM_SLOP = 6f

/** How much of the corner the glass's own print takes, with its line under it. */
private val GLASS_PRINT_ROOM = 200.dp

/**
 * Zooming by hand, while no glass is in hand (the person's wish, 2026-11-13): the wheel zooms in and
 * out about the pointer; a drag outlines a part to zoom into — further in, where it is zoomed into
 * already; Shift and a drag move the part zoomed into, as a hand slides the picture; a right click
 * shows the whole picture again. [shown] is the size of what is shown — the part, where it is
 * zoomed into — read as it is now, so a gesture is not broken off by the part it changes. [frame]
 * is the part being outlined, on screen, while it is.
 */
internal fun Modifier.zoomInput(lens: LensState, picture: File?, shown: State<Size>, frame: MutableState<Rect?>): Modifier =
    pointerInput(lens, picture) {
        awaitPointerEventScope {
            var downAt: Offset? = null
            var last: Offset? = null
            var moving = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: continue
                if (lens.glass.inHand) {
                    downAt = null
                    frame.value = null
                    continue
                }
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                val (iw, ih) = shown.value.let { it.width to it.height }

                // A place on screen on what is shown, 0 to 1 — mirrored or upside down as it is seen.
                fun onShown(p: Offset): Offset {
                    val at = pictureView(iw, ih, w, h, null, 0f).toPicture(toLayer(p, w, h, lens.mirror, lens.upsideDown))
                    return Offset(at.x / iw, at.y / ih)
                }
                val pos = change.position
                when (event.type) {
                    PointerEventType.Press -> when {
                        // The print in the corner took it: not the picture's.
                        change.isConsumed -> downAt = null
                        event.buttons.isSecondaryPressed -> if (lens.zoomOn(picture) != null) {
                            lens.showWhole()
                            change.consume()
                        }
                        else -> {
                            downAt = pos
                            last = pos
                            moving = event.keyboardModifiers.isShiftPressed
                        }
                    }
                    PointerEventType.Move -> {
                        val from = downAt
                        if (from != null && change.pressed) {
                            if (moving) {
                                val a = onShown(last ?: pos)
                                val b = onShown(pos)
                                lens.panBy(picture, -(b.x - a.x), -(b.y - a.y))
                                change.consume()
                            } else if ((pos - from).getDistance() >= ZOOM_SLOP) {
                                frame.value = Rect(from, pos)
                                change.consume()
                            }
                        }
                        last = pos
                    }
                    PointerEventType.Release -> {
                        val from = downAt
                        downAt = null
                        frame.value = null
                        if (from != null && !moving && (pos - from).getDistance() >= ZOOM_SLOP) {
                            val a = onShown(from)
                            val b = onShown(pos)
                            lens.zoomInto(picture, a.x, a.y, b.x, b.y)
                            change.consume()
                        }
                    }
                    PointerEventType.Scroll -> {
                        // Shift turns a wheel sideways on some systems: take whichever way it went.
                        val delta = change.scrollDelta.y.takeIf { it != 0f } ?: change.scrollDelta.x
                        val at = onShown(pos)
                        lens.zoomBy(picture, -delta, at.x.coerceIn(0f, 1f), at.y.coerceIn(0f, 1f))
                        change.consume()
                    }
                    else -> Unit
                }
            }
        }
    }

/**
 * The marks of a zoom, over the picture: the part being outlined, dashed; and, zoomed into, the
 * whole picture ([whole]) as a small print in the bottom left corner with the part shown ringed in
 * pencil — a click on it, like Z or a right click, shows the whole picture again. Where the glass
 * is down too ([glassDown]), its own print lies in that corner, and this one above it.
 */
@Composable
internal fun BoxScope.ZoomMarks(whole: ImageBitmap, lens: LensState, picture: File?, frame: Rect?, glassDown: Boolean) {
    val design = LocalDesign.current
    if (frame != null) {
        Canvas(Modifier.fillMaxSize().testTag("zoom-frame")) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
            drawRect(Color.Black.copy(alpha = 0.5f), frame.topLeft, frame.size, style = Stroke(3.dp.toPx()))
            drawRect(design.colors.paper, frame.topLeft, frame.size, style = Stroke(1.5.dp.toPx(), pathEffect = dash))
        }
    }
    val zoom = lens.zoomOn(picture) ?: return
    val longest = 150.dp
    val aspect = whole.width.toFloat() / whole.height
    val (pw, ph) = if (aspect >= 1f) longest to longest / aspect else longest * aspect to longest
    Column(
        Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = if (glassDown) 16.dp + GLASS_PRINT_ROOM else 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .lampShadow(Lift.LIFTED)
                .background(Design.colors.paper)
                .paperTooth()
                .clickable(onClickLabel = "Back to the whole picture") { lens.showWhole() }
                .padding(5.dp)
                .testTag("zoom-whole"),
        ) {
            Box(
                Modifier.size(pw, ph).graphicsLayer {
                    if (lens.upsideDown) rotationZ = 180f
                    if (lens.mirror) scaleX = -1f
                },
            ) {
                Image(whole, contentDescription = "The whole picture", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                Canvas(Modifier.fillMaxSize()) {
                    val part = Rect(
                        zoom.left * size.width,
                        zoom.top * size.height,
                        (zoom.left + zoom.width) * size.width,
                        (zoom.top + zoom.height) * size.height,
                    )
                    drawPath(Path().apply { fillType = PathFillType.EvenOdd; addRect(Rect(Offset.Zero, size)); addRect(part) }, Color.Black.copy(alpha = 0.4f))
                    drawRect(design.colors.paper, part.topLeft, part.size, style = Stroke(1.6.dp.toPx()))
                    drawRect(design.pigmentOf(Room.LENS).mass, part.topLeft, part.size, style = Stroke(0.7.dp.toPx()))
                }
            }
        }
        Text(
            "zoomed ×%.1f · Z for the whole".format(zoom.factor),
            fontSize = 11.sp,
            color = Design.colors.textSoft,
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}
