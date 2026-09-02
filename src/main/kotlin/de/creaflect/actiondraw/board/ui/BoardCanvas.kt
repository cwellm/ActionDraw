package de.creaflect.actiondraw.board.ui

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.board.BoardItem
import de.creaflect.actiondraw.board.BoardState
import de.creaflect.actiondraw.board.ImageItem
import de.creaflect.actiondraw.board.LinkItem
import de.creaflect.actiondraw.board.NoteItem
import de.creaflect.actiondraw.image.ThumbCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

/**
 * The freeform board: an infinite pan/zoom surface where every card sits at its own position,
 * scale and rotation. Drag empty space to pan · wheel zooms about the cursor · drag a card to
 * move it (its selection moves along) · handles on a single-selected card resize (corner) and
 * rotate (top) · Ctrl+wheel resizes and Shift+wheel rotates the selected card.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BoardCanvas(state: BoardState, thumbs: ThumbCache, textured: Boolean, modifier: Modifier) {
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var shiftHeld by remember { mutableStateOf(false) }

    Box(
        modifier
            .clipToBounds()
            .onSizeChanged { viewSize = it }
            .pointerInput(state) {
                // Plain drag pans the board; Shift+drag pulls a rubber band over the cards.
                var marqueeing = false
                detectDragGestures(
                    onDragStart = { start ->
                        marqueeing = shiftHeld
                        if (marqueeing) {
                            val (bx, by) = boardPoint(start, viewSize, state)
                            state.startMarquee(bx, by)
                        }
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        if (marqueeing) {
                            val (bx, by) = boardPoint(change.position, viewSize, state)
                            state.updateMarquee(bx, by)
                        } else {
                            state.pan(-drag.x / state.zoom, -drag.y / state.zoom)
                        }
                    },
                    onDragEnd = {
                        if (marqueeing) state.commitMarquee() else state.commitCamera()
                        marqueeing = false
                    },
                    onDragCancel = {
                        if (marqueeing) state.cancelMarquee() else state.commitCamera()
                        marqueeing = false
                    },
                )
            }
            .pointerInput(state) { detectTapGestures(onTap = { state.clearSelection() }) }
            .onPointerEvent(PointerEventType.Move) { event ->
                shiftHeld = event.keyboardModifiers.isShiftPressed
            }
            .onPointerEvent(PointerEventType.Press) { event ->
                shiftHeld = event.keyboardModifiers.isShiftPressed
            }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val change = event.changes.firstOrNull() ?: return@onPointerEvent
                val delta = change.scrollDelta.y
                if (delta == 0f) return@onPointerEvent
                val target = state.selection.firstOrNull() ?: state.focusId
                when {
                    event.keyboardModifiers.isCtrlPressed && target != null -> {
                        state.resizeBy(target, if (delta < 0) 1.1f else 1 / 1.1f)
                        state.commitLayout()
                    }

                    event.keyboardModifiers.isShiftPressed && target != null -> {
                        state.rotateBy(target, if (delta < 0) -5f else 5f)
                        state.commitLayout()
                    }

                    else -> {
                        // Zoom about the cursor: the board point underneath stays fixed.
                        val factor = if (delta < 0) 1.15f else 1 / 1.15f
                        val newZoom = (state.zoom * factor).coerceIn(0.1f, 5f)
                        val halfW = viewSize.width / 2f
                        val halfH = viewSize.height / 2f
                        val bx = (change.position.x - halfW) / state.zoom + state.camX
                        val by = (change.position.y - halfH) / state.zoom + state.camY
                        state.setZoom(
                            newZoom,
                            bx - (change.position.x - halfW) / newZoom,
                            by - (change.position.y - halfH) / newZoom,
                        )
                    }
                }
            },
    ) {
        state.freeItems.forEach { item ->
            key(item.id) { CanvasItem(state, thumbs, item, textured, viewSize) }
        }

        // Alignment guides and the rubber band, drawn over the cards.
        Canvas(Modifier.fillMaxSize()) {
            val guideColor = Color(0x99FFB74D)
            state.snapGuideX?.let { gx ->
                val x = (gx - state.camX) * state.zoom + size.width / 2
                drawLine(guideColor, Offset(x, 0f), Offset(x, size.height), 1f)
            }
            state.snapGuideY?.let { gy ->
                val y = (gy - state.camY) * state.zoom + size.height / 2
                drawLine(guideColor, Offset(0f, y), Offset(size.width, y), 1f)
            }
            state.marquee?.let { rect ->
                val x1 = (minOf(rect[0], rect[2]) - state.camX) * state.zoom + size.width / 2
                val x2 = (maxOf(rect[0], rect[2]) - state.camX) * state.zoom + size.width / 2
                val y1 = (minOf(rect[1], rect[3]) - state.camY) * state.zoom + size.height / 2
                val y2 = (maxOf(rect[1], rect[3]) - state.camY) * state.zoom + size.height / 2
                drawRect(Color(0x2280CBC4), topLeft = Offset(x1, y1), size = Size(x2 - x1, y2 - y1))
                drawRect(
                    Color(0xCC80CBC4),
                    topLeft = Offset(x1, y1),
                    size = Size(x2 - x1, y2 - y1),
                    style = Stroke(1f),
                )
            }
        }

        if (state.freeItems.isEmpty()) {
            Text(
                "An empty canvas. Drop pictures from Explorer, paste one (Ctrl+V), or use Import…",
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
        }

        OutlinedButton(
            onClick = { state.fitAll(viewSize.width.toFloat(), viewSize.height.toFloat()) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
        ) { Text("Fit · ${(state.zoom * 100).toInt()}%") }
    }
}

@Composable
private fun CanvasItem(
    state: BoardState,
    thumbs: ThumbCache,
    item: BoardItem,
    textured: Boolean,
    viewSize: IntSize,
) {
    val pos = item.pos ?: return
    val zoom = state.zoom
    val aspect = ((item as? ImageItem)?.aspect ?: 1f).coerceIn(0.2f, 5f)
    val wPx = BoardState.BASE_SIZE * pos.scale * zoom
    val hPx = wPx / aspect
    val cx = (pos.x - state.camX) * zoom + viewSize.width / 2f
    val cy = (pos.y - state.camY) * zoom + viewSize.height / 2f
    val density = LocalDensity.current
    val singleSelected = state.selection.size == 1 && item.id in state.selection

    ContextMenuArea(items = {
        listOf(
            ContextMenuItem("Bring forward") { state.stepZ(item.id, forward = true) },
            ContextMenuItem("Send backward") { state.stepZ(item.id, forward = false) },
            ContextMenuItem("Bring to front") { state.bringToFront(item.id) },
            ContextMenuItem("Send to back") { state.sendToBack(item.id) },
        ) + cardMenuItems(state, item)
    }) {
        Box(
            Modifier
                .size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() })
                .graphicsLayer {
                    translationX = cx - wPx / 2
                    translationY = cy - hPx / 2
                    rotationZ = pos.rotation
                }
                .cardClicks(state, item.id)
                .pointerInput(item.id) {
                    detectDragGestures(
                        onDragStart = {
                            if (item.id !in state.selection) state.clickItem(item.id, ctrl = false, shift = false)
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            // The pointer delta arrives in the card's rotated space; rotate it
                            // back so the card follows the cursor on screen.
                            val rotation = state.item(item.id)?.pos?.rotation ?: 0f
                            val rad = Math.toRadians(rotation.toDouble())
                            val wx = drag.x * cos(rad).toFloat() - drag.y * sin(rad).toFloat()
                            val wy = drag.x * sin(rad).toFloat() + drag.y * cos(rad).toFloat()
                            state.dragBy(item.id, wx / state.zoom, wy / state.zoom)
                            // Line the card up with its neighbours while it moves.
                            state.item(item.id)?.pos?.let { moved ->
                                val (sx, sy) = state.snapPosition(item.id, moved.x, moved.y, 10f / state.zoom)
                                if (sx != moved.x || sy != moved.y) {
                                    state.dragBy(item.id, sx - moved.x, sy - moved.y)
                                }
                            }
                        },
                        onDragEnd = { state.clearSnapGuides(); state.commitLayout() },
                        onDragCancel = { state.clearSnapGuides() },
                    )
                },
        ) {
            when (item) {
                is ImageItem -> CanvasImage(state, thumbs, item, textured)
                is NoteItem -> CanvasNote(state, item, textured)
                is LinkItem -> CanvasLink(state, item, textured)
            }
            if (singleSelected) {
                RotateHandle(state, item.id, Modifier.align(Alignment.TopCenter))
                ScaleHandle(state, item.id, Modifier.align(Alignment.BottomEnd))
            }
        }
    }
}

@Composable
private fun CanvasImage(state: BoardState, thumbs: ThumbCache, item: ImageItem, textured: Boolean) {
    val file = state.fileOf(item)
    val thumb: ImageBitmap? by produceState<ImageBitmap?>(null, file) {
        value = file?.let { withContext(Dispatchers.IO) { thumbs.load(it, maxSize = 384) } }
    }
    // Remember the aspect once, so the card keeps its shape on every future open.
    LaunchedEffect(thumb) {
        val bmp = thumb
        if (bmp != null && item.aspect == null && bmp.height > 0) {
            state.recordAspect(item.id, bmp.width.toFloat() / bmp.height)
        }
    }
    val shape = RoundedCornerShape(3.dp)
    Box(
        Modifier
            .fillMaxSize()
            .shadow(if (textured) 4.dp else 1.dp, shape)
            .clip(shape)
            .background(if (textured) Themes.cardBacking else Color(0xFF0D0D0D))
            .border(2.dp, selectionBorder(state, item.id), shape)
            .padding(if (textured) 5.dp else 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = thumb
        if (bmp != null) {
            Image(bitmap = bmp, contentDescription = item.path, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        } else {
            Text("…", color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f))
        }
        if (item.starred) {
            Text("★", color = Color(0xFFFFB300), modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
        }
    }
}

@Composable
private fun CanvasNote(state: BoardState, item: NoteItem, textured: Boolean) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        Modifier
            .fillMaxSize()
            .shadow(if (textured) 4.dp else 1.dp, shape)
            .clip(shape)
            .background(if (textured) Themes.noteBacking else Themes.noteBackingDark)
            .border(2.dp, selectionBorder(state, item.id), shape),
    ) {
        Text(
            item.text,
            style = MaterialTheme.typography.body2,
            color = if (textured) Themes.noteInk else Themes.noteInkDark,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(10.dp),
        )
    }
}

/** Drag horizontally to rotate the card (0.4° per pixel). */
@Composable
private fun RotateHandle(state: BoardState, id: String, modifier: Modifier) {
    Box(
        modifier
            .padding(2.dp)
            .size(14.dp)
            .background(MaterialTheme.colors.primary, CircleShape)
            .pointerInput(id) {
                detectDragGestures(
                    onDrag = { change, drag ->
                        change.consume()
                        state.rotateBy(id, drag.x * 0.4f)
                    },
                    onDragEnd = { state.commitLayout() },
                )
            },
    )
}

/** Drag outward/inward to resize the card. */
@Composable
private fun ScaleHandle(state: BoardState, id: String, modifier: Modifier) {
    Box(
        modifier
            .padding(2.dp)
            .size(14.dp)
            .background(MaterialTheme.colors.secondary, CircleShape)
            .pointerInput(id) {
                detectDragGestures(
                    onDrag = { change, drag ->
                        change.consume()
                        state.resizeBy(id, 1f + (drag.x + drag.y) / 300f)
                    },
                    onDragEnd = { state.commitLayout() },
                )
            },
    )
}

/** Screen point -> board point, for the marquee. */
private fun boardPoint(point: Offset, viewSize: IntSize, state: BoardState): Pair<Float, Float> =
    ((point.x - viewSize.width / 2f) / state.zoom + state.camX) to
        ((point.y - viewSize.height / 2f) / state.zoom + state.camY)

/** A link card on the canvas — the same look as in the grid, sized to its cell. */
@Composable
private fun CanvasLink(state: BoardState, item: LinkItem, textured: Boolean) {
    val shape = RoundedCornerShape(3.dp)
    Column(
        Modifier
            .fillMaxSize()
            .shadow(if (textured) 4.dp else 1.dp, shape)
            .clip(shape)
            .background(if (textured) Themes.cardBacking else Color(0xFF1C1C1E))
            .border(2.dp, selectionBorder(state, item.id), shape)
            .padding(8.dp),
    ) {
        Text("\uD83D\uDD17", style = MaterialTheme.typography.body1)
        Text(
            item.title.ifBlank { item.url },
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
