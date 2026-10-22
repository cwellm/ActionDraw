package de.creaflect.actiondraw.board.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.board.BoardState
import de.creaflect.actiondraw.board.ImageItem
import de.creaflect.actiondraw.image.ImageLoader
import de.creaflect.actiondraw.image.ThumbCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The large view: the selected pictures shown big, one at a time. With more than one it becomes a
 * carousel — drag sideways, use the chevrons, the arrow keys, or click a thumbnail in the
 * filmstrip. Both neighbours are laid out just off-screen, so a drag actually reveals them.
 *
 * The wheel zooms — a tablet's dial is a wheel as far as the system is concerned — about the
 * spot under the pointer, as do `+` and `-` about the centre. Once zoomed, dragging pans instead
 * of flipping, and `0` fits the picture again.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BoardViewer(state: BoardState, thumbs: ThumbCache) {
    val index = state.viewerIndex
    val count = state.viewerIds.size
    val zoom = state.viewerZoom
    var viewSize by remember { mutableStateOf(IntSize(1, 1)) }
    val viewWidth = viewSize.width.coerceAtLeast(1)
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // The zoom lives on the state so the keys can reach it; the pan is screen geometry and stays
    // here, clamped against the fitted size the current slide reports.
    var pan by remember { mutableStateOf(Offset.Zero) }
    var fitted by remember { mutableStateOf(Size.Zero) }
    fun view() = Size(viewSize.width.toFloat(), viewSize.height.toFloat())

    /** Slides one step: the row glides by a full width, then the index takes over. */
    fun glide(direction: Int) {
        if (count < 2) return
        scope.launch {
            offset.animateTo(-direction * viewWidth.toFloat(), tween(180))
            state.viewerStep(direction)
            offset.snapTo(0f)
        }
    }

    /** After a flip drag: past the threshold it flips, short of it the row springs back. */
    fun settle() {
        val threshold = viewWidth * 0.18f
        when {
            offset.value <= -threshold -> glide(1)
            offset.value >= threshold -> glide(-1)
            else -> scope.launch { offset.animateTo(0f, tween(150)) }
        }
    }

    /** Zooms by [factor], keeping what is under [cursor] (from the view centre) where it is. */
    fun zoomAt(cursor: Offset, factor: Float) {
        val from = state.viewerZoom
        state.viewerZoomBy(factor)
        val to = state.viewerZoom
        if (to != from) {
            pan = ViewerZoom.clampPan(ViewerZoom.panKeepingPointStill(pan, cursor, from, to), fitted, to, view())
        }
    }

    // A step from elsewhere (keyboard, filmstrip) must not leave a stale drag offset behind.
    LaunchedEffect(index) { if (offset.value != 0f && !offset.isRunning) offset.snapTo(0f) }
    // Zoom changes from the keys, and a new picture, both arrive through the state.
    LaunchedEffect(zoom, fitted, viewSize) {
        pan = if (zoom == 1f) Offset.Zero else ViewerZoom.clampPan(pan, fitted, zoom, view())
    }

    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds() // an enlarged picture must not paint over the menu bar above
            .background(Color(0xF2000000))
            .onSizeChanged { viewSize = it }
            .pointerInput(count) {
                // One drag detector for both jobs: pan while zoomed, flip while fitted.
                detectDragGestures(
                    onDrag = { change, delta ->
                        change.consume()
                        val z = state.viewerZoom
                        if (z > 1f) {
                            pan = ViewerZoom.clampPan(pan + delta, fitted, z, view())
                        } else if (count > 1) {
                            scope.launch { offset.snapTo(offset.value + delta.x) }
                        }
                    },
                    onDragEnd = { if (state.viewerZoom == 1f) settle() },
                    onDragCancel = { if (state.viewerZoom == 1f) settle() },
                )
            }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val change = event.changes.firstOrNull() ?: return@onPointerEvent
                val dy = change.scrollDelta.y
                if (dy == 0f) return@onPointerEvent
                // Wheel up zooms in, as everywhere else; each event is one step, however the
                // device chooses to report its magnitude.
                val cursor = change.position - Offset(viewSize.width / 2f, viewSize.height / 2f)
                zoomAt(cursor, if (dy < 0) BoardState.VIEWER_ZOOM_STEP else 1f / BoardState.VIEWER_ZOOM_STEP)
            }
            .testTag("viewer"),
    ) {
        // The current slide plus both neighbours, in a row that the drag offset moves. Only the
        // current one is scaled and panned; the neighbours wait at fitted size.
        for (slot in -1..1) {
            if (slot != 0 && count < 2) continue
            val slideIndex = ((index + slot) % count + count) % count
            val item = state.viewerItemAt(slideIndex) ?: continue
            Slide(
                state = state,
                thumbs = thumbs,
                item = item,
                current = slot == 0,
                onFitted = { if (slot == 0) fitted = it },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val z = if (slot == 0) zoom else 1f
                        scaleX = z
                        scaleY = z
                        translationX = slot * viewWidth.toFloat() + offset.value + (if (slot == 0) pan.x else 0f)
                        translationY = if (slot == 0) pan.y else 0f
                    },
            )
        }

        if (count > 1) {
            Chevron("‹", Modifier.align(Alignment.CenterStart)) { glide(-1) }
            Chevron("›", Modifier.align(Alignment.CenterEnd)) { glide(1) }
        }

        Row(
            Modifier.align(Alignment.TopEnd).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (count > 1) {
                Text(
                    "${index + 1} / $count",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.body2,
                )
            }
            Surface(
                color = Color.White.copy(alpha = 0.12f),
                shape = CircleShape,
                modifier = Modifier.clickable { state.closeViewer() },
            ) {
                Text(
                    "✕",
                    color = Color.White,
                    style = MaterialTheme.typography.body1,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            state.viewerItem?.let { Caption(state, it) }
            if (count > 1) Filmstrip(state, thumbs)
            Text(
                "Drag or ‹ › to flip · ←/→ · wheel or +/− zooms · drag to pan · 0 fits · Esc closes",
                color = Color.White.copy(alpha = 0.35f),
                style = MaterialTheme.typography.caption,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

/** One picture, fitted into the whole overlay; the current one reports its fitted size for the zoom. */
@Composable
private fun Slide(
    state: BoardState,
    thumbs: ThumbCache,
    item: ImageItem,
    current: Boolean,
    onFitted: (Size) -> Unit,
    modifier: Modifier,
) {
    val file = state.fileOf(item)
    // The centre slide is decoded at full size; neighbours use a cached preview, so dragging
    // through a long selection never waits on a big decode.
    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(null, file, current) {
        value = file?.let { f ->
            withContext(Dispatchers.IO) {
                if (current) runCatching { ImageLoader.load(f) }.getOrNull() else thumbs.load(f, maxSize = 900)
            }
        }
    }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val bmp = bitmap
    LaunchedEffect(bmp, box, current) {
        if (current && bmp != null && box != IntSize.Zero) {
            onFitted(
                ViewerZoom.fitted(
                    Size(bmp.width.toFloat(), bmp.height.toFloat()),
                    Size(box.width.toFloat(), box.height.toFloat()),
                ),
            )
        }
    }
    Box(
        modifier.padding(horizontal = 48.dp, vertical = 56.dp).onSizeChanged { box = it },
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = item.caption ?: item.path,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text("Loading…", color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun Caption(state: BoardState, item: ImageItem) {
    val name = item.caption ?: state.fileOf(item)?.name ?: item.path
    val tags = item.tags.takeIf { it.isNotEmpty() }?.joinToString(" ") { "#$it" }
    Text(
        listOfNotNull(name, tags).joinToString("   "),
        color = Color.White.copy(alpha = 0.85f),
        style = MaterialTheme.typography.body2,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
    )
}

/** Thumbnails of everything in the carousel; click one to jump straight to it. */
@Composable
private fun Filmstrip(state: BoardState, thumbs: ThumbCache) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.viewerIndex) {
        runCatching { listState.animateScrollToItem(state.viewerIndex) }
    }
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.fillMaxWidth().height(74.dp).padding(vertical = 6.dp),
    ) {
        itemsIndexed(state.viewerIds) { i, id ->
            val item = state.viewerItemAt(i)
            val file = item?.let(state::fileOf)
            val thumb: ImageBitmap? by produceState<ImageBitmap?>(null, file) {
                value = file?.let { withContext(Dispatchers.IO) { thumbs.load(it, maxSize = 128) } }
            }
            val selected = i == state.viewerIndex
            Box(
                Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(
                        2.dp,
                        if (selected) MaterialTheme.colors.primary else Color.Transparent,
                        RoundedCornerShape(3.dp),
                    )
                    .clickable { state.viewerGoTo(i) },
                contentAlignment = Alignment.Center,
            ) {
                thumb?.let {
                    Image(
                        bitmap = it,
                        contentDescription = id,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun Chevron(glyph: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        color = Color.White.copy(alpha = 0.10f),
        shape = CircleShape,
        modifier = modifier.padding(12.dp).size(44.dp).clickable { onClick() },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(glyph, color = Color.White, style = MaterialTheme.typography.h5)
        }
    }
}
