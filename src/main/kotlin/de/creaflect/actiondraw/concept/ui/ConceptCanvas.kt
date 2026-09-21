package de.creaflect.actiondraw.concept.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import de.creaflect.actiondraw.board.BoardItem
import de.creaflect.actiondraw.board.BoardState
import de.creaflect.actiondraw.board.ImageItem
import de.creaflect.actiondraw.board.LinkItem
import de.creaflect.actiondraw.board.NoteItem
import de.creaflect.actiondraw.concept.ConceptState
import de.creaflect.actiondraw.image.ThumbCache

/**
 * The concept's cards placed freely, as a board's Free layout places them: drag a card to move
 * it, drag empty space to pan, the wheel zooms about the cursor, Ctrl+wheel resizes the selected
 * cards. The arrangement is the concept's own — it never reaches the boards that link it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ConceptCanvas(state: ConceptState, thumbs: ThumbCache, modifier: Modifier = Modifier) {
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier
            .clipToBounds()
            .testTag("concept-canvas")
            .onSizeChanged { viewSize = it }
            .pointerInput(state) {
                detectDragGestures(
                    onDrag = { change, drag ->
                        change.consume()
                        state.pan(-drag.x / state.zoom, -drag.y / state.zoom)
                    },
                    onDragEnd = { state.commitCamera() },
                    onDragCancel = { state.commitCamera() },
                )
            }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val change = event.changes.firstOrNull() ?: return@onPointerEvent
                val delta = change.scrollDelta.y
                if (delta == 0f) return@onPointerEvent
                if (event.keyboardModifiers.isCtrlPressed && state.selection.isNotEmpty()) {
                    state.selection.forEach { state.resizeBy(it, if (delta < 0) 1.1f else 1 / 1.1f) }
                    state.commitLayout()
                } else {
                    // Zoom about the cursor: the point underneath stays where it is.
                    val factor = if (delta < 0) 1.15f else 1 / 1.15f
                    val newZoom = (state.zoom * factor).coerceIn(0.1f, 5f)
                    val halfW = viewSize.width / 2f
                    val halfH = viewSize.height / 2f
                    val px = (change.position.x - halfW) / state.zoom + state.camX
                    val py = (change.position.y - halfH) / state.zoom + state.camY
                    state.setZoom(newZoom, px - (change.position.x - halfW) / newZoom, py - (change.position.y - halfH) / newZoom)
                }
            },
    ) {
        // Empty space under everything: a tap clears the selection. It stays below the cards, or
        // it would swallow their clicks.
        Box(Modifier.fillMaxSize().pointerInput(state) { detectTapGestures { state.clearSelection() } })
        state.items.forEach { item ->
            key(item.id) { ConceptCanvasItem(state, thumbs, item, viewSize) }
        }
    }
}

@Composable
private fun ConceptCanvasItem(state: ConceptState, thumbs: ThumbCache, item: BoardItem, viewSize: IntSize) {
    val pos = item.pos ?: return
    val zoom = state.zoom
    val wPx = BoardState.BASE_SIZE * pos.scale * zoom
    val hPx = wPx / state.aspectOf(item)
    val cx = (pos.x - state.camX) * zoom + viewSize.width / 2f
    val cy = (pos.y - state.camY) * zoom + viewSize.height / 2f
    val density = LocalDensity.current
    Box(
        Modifier
            .requiredSize(with(density) { wPx.toDp() }, with(density) { hPx.toDp() })
            .graphicsLayer {
                translationX = cx - wPx / 2
                translationY = cy - hPx / 2
            }
            .testTag("concept-card-" + item.id)
            .pointerInput(item.id) {
                detectDragGestures(
                    onDrag = { change, drag ->
                        change.consume()
                        state.dragBy(item.id, drag.x / state.zoom, drag.y / state.zoom)
                    },
                    onDragEnd = { state.commitLayout() },
                    onDragCancel = { state.commitLayout() },
                )
            },
    ) {
        // The same tiles as the grid shows, filling the card's own size.
        when (item) {
            is ImageItem -> PictureCard(state, thumbs, item, Modifier.fillMaxSize())
            is NoteItem -> NoteCard(state, item, Modifier.fillMaxSize())
            is LinkItem -> LinkCard(state, item, Modifier.fillMaxSize())
        }
    }
}
