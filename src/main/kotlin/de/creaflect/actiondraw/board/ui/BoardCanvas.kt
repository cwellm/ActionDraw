package de.creaflect.actiondraw.board.ui

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.board.BoardEditor
import de.creaflect.actiondraw.board.BoardItem
import de.creaflect.actiondraw.board.BoardState
import de.creaflect.actiondraw.board.FrameShape
import de.creaflect.actiondraw.board.ImageItem
import de.creaflect.actiondraw.board.LinkItem
import de.creaflect.actiondraw.board.NoteItem
import de.creaflect.actiondraw.image.ThumbCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import de.creaflect.actiondraw.board.NoteKind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.foundation.shape.GenericShape

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
            .testTag("canvas")
            .pointerLogging("root")
            .onSizeChanged { viewSize = it }
            .pointerInput(state) {
                // Plain drag pans the board; Shift+drag pulls a rubber band over the cards.
                var marqueeing = false
                detectDragGestures(
                    onDragStart = { start ->
                        PointerLog.log("root: drag start at $start (marquee=$shiftHeld)")
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
        // Empty board underneath everything: tapping it clears the selection. It must stay below
        // the cards, or it would swallow their clicks (and it used to, which made it impossible
        // to select more than one card at a time).
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(state) { detectTapGestures { state.clearSelection() } },
        )

        // Group areas next, so cards sit on top of their own group's tint.
        state.groupHulls.forEach { hull ->
            key("hull-" + hull.group.id) { GroupArea(state, hull, viewSize) }
        }

        state.freeItems.forEach { item ->
            key(item.id) { CanvasItem(state, thumbs, item, textured, viewSize) }
        }

        // Group labels last of all: they are the handle for picking a group up, so nothing may
        // end up lying over them.
        state.groupHulls.forEach { hull ->
            key("label-" + hull.group.id) { GroupLabel(state, hull, viewSize) }
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
    val aspect = state.aspectOf(item)
    val wPx = BoardState.BASE_SIZE * pos.scale * zoom
    val hPx = wPx / aspect
    val cx = (pos.x - state.camX) * zoom + viewSize.width / 2f
    val cy = (pos.y - state.camY) * zoom + viewSize.height / 2f
    val density = LocalDensity.current
    val singleSelected = state.selection.size == 1 && item.id in state.selection

    // Size and place go on a box *around* the menu area, so its hit box — and the card's — sit
    // where the card is drawn. With the transform on the inner box, the menu area stayed at the
    // canvas' top-left and swallowed presses meant for whatever was drawn there; the board then
    // panned under the pointer, every card moving with the one the user had meant to drag.
    Box(
        Modifier
            .requiredSize(with(density) { wPx.toDp() }, with(density) { hPx.toDp() })
            .graphicsLayer {
                translationX = cx - wPx / 2
                translationY = cy - hPx / 2
                rotationZ = pos.rotation
            },
    ) {
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
                    .fillMaxSize()
                    .testTag("card-" + item.id)
                    .cardClicks(state, item.id)
                    .pointerInput(item.id) {
                        detectDragGestures(
                            onDragStart = {
                                PointerLog.log("card ${item.id.take(8)}: drag start")
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
                                state.trackDropTarget(item.id)
                                // Line the card up with its neighbours while it moves.
                                state.item(item.id)?.pos?.let { moved ->
                                    val (sx, sy) = state.snapPosition(item.id, moved.x, moved.y, 10f / state.zoom)
                                    if (sx != moved.x || sy != moved.y) {
                                        state.dragBy(item.id, sx - moved.x, sy - moved.y)
                                    }
                                }
                            },
                            onDragEnd = {
                                PointerLog.log("card ${item.id.take(8)}: drag end")
                                state.clearSnapGuides()
                                // Let go over a group's frame: the card (or its selection) joins it.
                                state.dropIntoGroupAt(item.id)
                                state.commitLayout()
                            },
                            onDragCancel = {
                                PointerLog.log("card ${item.id.take(8)}: drag cancelled")
                                state.clearSnapGuides()
                            },
                        )
                    },
            ) {
                when (item) {
                    is ImageItem -> CanvasImage(state, thumbs, item, textured)
                    is NoteItem -> CanvasNote(state, item, textured)
                    is LinkItem -> CanvasLink(state, item, textured)
                }
                // A grouped card carries its group's colour, so it is recognisable even when
                // dragged out of the group area.
                state.accentOf(item)?.let { hex ->
                    Themes.parseColor(hex)?.let { accent ->
                        Box(
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(3.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(accent),
                        )
                    }
                }
                if (singleSelected) {
                    RotateHandle(state, item.id, Modifier.align(Alignment.TopCenter))
                    ScaleHandle(state, item.id, Modifier.align(Alignment.BottomEnd))
                }
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
            .border(3.dp, selectionBorder(state, item.id), shape)
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
    val paper = notePaper(item, textured)
    val ink = if (textured) Themes.noteInk else Themes.noteInkDark
    if (item.kind == NoteKind.POSTIT) {
        // A post-it: all of the text, as typed, in a written hand, on paper that fits it.
        Box(
            Modifier
                .fillMaxSize()
                .shadow(if (textured) 4.dp else 1.dp, shape)
                .clip(shape)
                .background(paper)
                .border(2.dp, selectionBorder(state, item.id), shape),
        ) {
            Text(
                item.text,
                style = MaterialTheme.typography.body1.copy(fontFamily = FontFamily.Cursive, lineHeight = 22.sp),
                color = ink,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(10.dp).testTag("postit-" + item.id),
            )
        }
    } else {
        // A document note: only its title on the board; a tap opens the whole note to read.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .shadow(if (textured) 4.dp else 1.dp, shape)
                .clip(shape)
                .background(paper)
                .border(2.dp, selectionBorder(state, item.id), shape)
                .pointerInput(item.id) { detectTapGestures { state.openEditor(BoardEditor.ShowNote(item.id)) } }
                .padding(horizontal = 10.dp)
                .testTag("note-" + item.id),
        ) {
            Text("▤", style = MaterialTheme.typography.body2, color = ink.copy(alpha = 0.6f))
            Spacer(Modifier.width(8.dp))
            Text(
                item.title,
                style = if (item.heading) MaterialTheme.typography.subtitle1 else MaterialTheme.typography.body2,
                fontWeight = FontWeight.Bold,
                color = ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxSize()
            .shadow(if (textured) 4.dp else 1.dp, shape)
            .clip(shape)
            .background(if (textured) Themes.cardBacking else Color(0xFF1C1C1E))
            .border(2.dp, selectionBorder(state, item.id), shape)
            // A plain tap opens the page; selecting is still Ctrl/Shift+click, drag, or right-click.
            .pointerInput(item.id) { detectTapGestures { state.openLink(item) } }
            .padding(horizontal = 10.dp)
            .testTag("link-" + item.id),
    ) {
        Text("\uD83D\uDD17", style = MaterialTheme.typography.body2)
        Spacer(Modifier.width(8.dp))
        Text(
            item.title.ifBlank { item.url },
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.primary,
            textDecoration = TextDecoration.Underline,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A group's territory on the canvas: a tinted, outlined area behind its cards with the group name
 * in the corner. Dragging the area (or its label) moves the whole group; the cards on top keep
 * their own drag, so a single card can still be moved out of place inside it.
 */
@Composable
private fun GroupArea(state: BoardState, hull: BoardState.GroupHull, viewSize: IntSize) {
    val zoom = state.zoom
    val density = LocalDensity.current
    val accent = Themes.parseColor(hull.color) ?: MaterialTheme.colors.secondary
    val x = (hull.left - state.camX) * zoom + viewSize.width / 2f
    val y = (hull.top - state.camY) * zoom + viewSize.height / 2f
    val w = (hull.right - hull.left) * zoom
    val h = (hull.bottom - hull.top) * zoom
    val nested = hull.group.parentId != null

    // The frame's shape in the area's own pixels: the union of the padded boxes and bridges,
    // each a rounded rectangle. Built once per hull geometry and zoom, then both drawn and
    // hit-tested, so what shows is exactly what answers a click.
    val shape = remember(hull.boxes, hull.connectors, zoom) {
        frameShape(hull.boxes, hull.connectors, originX = hull.left, originY = hull.top, zoom = zoom)
    }
    val composePath = remember(shape) { shape.asComposePath() }
    // The gesture below reads the shape through this, and is keyed on the group alone: keyed on
    // the shape, it restarted the moment the group moved under it and lost the rest of the drag.
    val currentShape by rememberUpdatedState(shape)
    // The frame's layer clips to its own shape, and Compose hit-tests a clipping layer by its
    // outline: a press outside the shape is not a hit here at all and goes on to whatever is
    // drawn underneath. Without this, a frame's rectangle swallowed presses meant for another
    // group's frame lying inside it (and the canvas panned instead).
    val frameClip = remember(composePath) { GenericShape { _, _ -> addPath(composePath) } }
    val receiving = state.dropTargetGroup == hull.group.id
    val fill = accent.copy(alpha = if (receiving) 0.28f else if (nested) 0.10f else 0.14f)
    // Twice the width it shows: the clip takes the outer half of a stroke centred on the edge.
    val stroke = with(density) { (if (receiving) 4.dp else if (nested) 1.dp else 2.dp).toPx() } * 2f
    // A concept's group is outlined in dashes: borrowed, not the board's own.
    val borrowedDash = if (hull.group.isConcept) PathEffect.dashPathEffect(floatArrayOf(12f, 8f)) else null

    // As for a card: the frame's size and place on a box around the menu area (see CanvasItem),
    // clipped to the frame's shape so that only the shape is a hit.
    Box(
        Modifier
            .requiredSize(with(density) { w.toDp() }, with(density) { h.toDp() })
            .graphicsLayer {
                translationX = x
                translationY = y
                clip = true
                this.shape = frameClip // `shape` alone is the Skia path above
            },
    ) {
        ContextMenuArea(items = {
            val group = hull.group
            if (group.isConcept) listOf(
                ContextMenuItem("Draw " + hull.count) { state.drawGroup(group.id) },
                ContextMenuItem("Select group") { state.selectGroup(group.id) },
                ContextMenuItem("Open concept") { group.conceptId?.let(state::showConcept) },
                ContextMenuItem("Unlink from this board") { group.conceptId?.let(state::unlinkConcept) },
            ) else listOf(
                ContextMenuItem("Draw " + hull.count) { state.drawGroup(group.id) },
                ContextMenuItem("Select group") { state.selectGroup(group.id) },
                ContextMenuItem("Rename group…") { state.openEditor(BoardEditor.RenameGroup(group.id)) },
                ContextMenuItem("Cycle colour") { state.cycleGroupColor(group.id) },
                ContextMenuItem("Delete group (cards stay)") { state.deleteGroup(group.id) },
            )
        }) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawPath(composePath, fill)
                        drawPath(composePath, accent.copy(alpha = 0.7f), style = Stroke(width = stroke, join = StrokeJoin.Round, cap = StrokeCap.Round, pathEffect = borrowedDash))
                    }
                    // Clicking the frame picks the group up; dragging it moves the group as one. A
                    // press outside the shape -- in the empty notch of an L, say -- is not the
                    // group's business and falls through to whatever is under it.
                    .pointerInput(hull.group.id) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (!currentShape.contains(down.position.x, down.position.y)) {
                                PointerLog.log("frame ${hull.group.name}: down outside the shape at ${down.position}")
                                return@awaitEachGesture
                            }
                            PointerLog.log("frame ${hull.group.name}: down id=${down.id.value} at ${down.position} consumed=${down.isConsumed}")
                            down.consume()
                            var moved = false
                            val slop = awaitTouchSlopOrCancellation(down.id) { change, over ->
                                PointerLog.log("frame ${hull.group.name}: slop reached, over=$over")
                                change.consume()
                                moved = true
                                state.dragGroupBy(hull.group.id, over.x / state.zoom, over.y / state.zoom)
                            }
                            if (slop != null) {
                                drag(slop.id) { change ->
                                    val delta = change.positionChange()
                                    change.consume()
                                    state.dragGroupBy(hull.group.id, delta.x / state.zoom, delta.y / state.zoom)
                                }
                                PointerLog.log("frame ${hull.group.name}: drag ended")
                                state.commitLayout()
                            } else if (!moved) {
                                PointerLog.log("frame ${hull.group.name}: cancelled before the slop -> select")
                                state.selectGroup(hull.group.id)
                            } else {
                                PointerLog.log("frame ${hull.group.name}: cancelled after the slop")
                            }
                        }
                    },
            )
        }
    }
}

/**
 * One Skia path for a group's frame: the union of [boxes] (rounded rectangles) and [connectors]
 * (convex polygons filling the space between pieces), translated so that ([originX], [originY])
 * is the path's origin and scaled by [zoom] to pixels. The union is then thickened with a round
 * stroke and united back, which rounds off every corner the union left sharp — the frame reads
 * as one soft shape rather than a stack of rectangles.
 */
internal fun frameShape(
    boxes: List<List<Float>>,
    connectors: List<List<Float>>,
    originX: Float,
    originY: Float,
    zoom: Float,
): org.jetbrains.skia.Path {
    fun x(v: Float) = (v - originX) * zoom
    fun y(v: Float) = (v - originY) * zoom
    val radius = FrameShape.RADIUS * zoom
    var union: org.jetbrains.skia.Path? = null
    fun add(piece: org.jetbrains.skia.Path) {
        union = if (union == null) piece else org.jetbrains.skia.Path.makeCombining(union!!, piece, org.jetbrains.skia.PathOp.UNION) ?: union
    }
    for (box in boxes) {
        add(org.jetbrains.skia.Path().addRRect(org.jetbrains.skia.RRect.makeLTRB(x(box[0]), y(box[1]), x(box[2]), y(box[3]), radius)))
    }
    for (polygon in connectors) {
        if (polygon.size < 6) continue
        val points = Array(polygon.size / 2) { i -> org.jetbrains.skia.Point(x(polygon[2 * i]), y(polygon[2 * i + 1])) }
        add(org.jetbrains.skia.Path().addPoly(points, true))
    }
    val core = union ?: return org.jetbrains.skia.Path()
    // Dilate: stroke the outline with round joins and unite it with the fill.
    val paint = org.jetbrains.skia.Paint().apply {
        mode = org.jetbrains.skia.PaintMode.STROKE
        strokeWidth = radius * 0.6f
        strokeJoin = org.jetbrains.skia.PaintStrokeJoin.ROUND
        strokeCap = org.jetbrains.skia.PaintStrokeCap.ROUND
    }
    val rim = org.jetbrains.skia.PathUtils.fillPathWithPaint(core, paint)
    paint.close()
    return org.jetbrains.skia.Path.makeCombining(core, rim, org.jetbrains.skia.PathOp.UNION) ?: core
}

/**
 * A group's name, drawn over everything else so no card can cover it, and kept in sight while any
 * part of its group is: a hull is often far wider than the view, and pinning the label to the
 * hull's top-left corner meant the handle disappeared with the corner.
 */
@Composable
private fun GroupLabel(state: BoardState, hull: BoardState.GroupHull, viewSize: IntSize) {
    val zoom = state.zoom
    val accent = Themes.parseColor(hull.color) ?: MaterialTheme.colors.secondary
    val left = (hull.left - state.camX) * zoom + viewSize.width / 2f
    val top = (hull.top - state.camY) * zoom + viewSize.height / 2f
    val right = (hull.right - state.camX) * zoom + viewSize.width / 2f
    val bottom = (hull.bottom - state.camY) * zoom + viewSize.height / 2f
    // Off screen entirely: there is nothing to label.
    if (right <= 0f || bottom <= 0f || left >= viewSize.width || top >= viewSize.height) return

    var size by remember { mutableStateOf(IntSize.Zero) }
    // Slide along the edge to stay visible, but never outside the group being named.
    val x = left.coerceAtLeast(0f).coerceAtMost((right - size.width).coerceAtLeast(0f))
    val y = top.coerceAtLeast(0f).coerceAtMost((bottom - size.height).coerceAtLeast(0f))

    Surface(
        color = accent.copy(alpha = 0.85f),
        shape = RoundedCornerShape(bottomEnd = 8.dp),
        modifier = Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .onSizeChanged { size = it }
            .testTag("group-label-" + hull.group.id)
            // The label is the group's handle: a click picks the group up, a drag moves it — the
            // same as the frame. With only a click here, a drag from the label fell through to
            // the canvas and panned the whole board, which read as "everything moves with it".
            .pointerInput(hull.group.id) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    PointerLog.log("label ${hull.group.name}: down id=${down.id.value} at ${down.position} consumed=${down.isConsumed}")
                    down.consume()
                    var moved = false
                    val slop = awaitTouchSlopOrCancellation(down.id) { change, over ->
                        PointerLog.log("label ${hull.group.name}: slop reached, over=$over")
                        change.consume()
                        moved = true
                        state.dragGroupBy(hull.group.id, over.x / state.zoom, over.y / state.zoom)
                    }
                    if (slop != null) {
                        drag(slop.id) { change ->
                            val delta = change.positionChange()
                            change.consume()
                            state.dragGroupBy(hull.group.id, delta.x / state.zoom, delta.y / state.zoom)
                        }
                        PointerLog.log("label ${hull.group.name}: drag ended")
                        state.commitLayout()
                    } else if (!moved) {
                        PointerLog.log("label ${hull.group.name}: cancelled before the slop -> select")
                        state.selectGroup(hull.group.id)
                    } else {
                        PointerLog.log("label ${hull.group.name}: cancelled after the slop")
                    }
                }
            },
    ) {
        Text(
            (if (hull.group.isConcept) "⧉ " else "") + hull.group.name + "  ·  " + hull.count,
            style = MaterialTheme.typography.caption,
            color = Color(0xFF1A1A1A),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
