package de.creaflect.actiondraw.board.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
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
 * carousel — drag sideways, use the chevrons, the wheel, the arrow keys, or click a thumbnail in
 * the filmstrip. Both neighbours are laid out just off-screen, so a drag actually reveals them.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BoardViewer(state: BoardState, thumbs: ThumbCache) {
    val index = state.viewerIndex
    val count = state.viewerIds.size
    var viewWidth by remember { mutableStateOf(1) }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    /** Slides one step: the row glides by a full width, then the index takes over. */
    fun glide(direction: Int) {
        if (count < 2) return
        scope.launch {
            offset.animateTo(-direction * viewWidth.toFloat(), tween(180))
            state.viewerStep(direction)
            offset.snapTo(0f)
        }
    }

    // A step from elsewhere (keyboard, filmstrip) must not leave a stale drag offset behind.
    LaunchedEffect(index) { if (offset.value != 0f && !offset.isRunning) offset.snapTo(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF2000000))
            .onSizeChanged { viewWidth = it.width.coerceAtLeast(1) }
            .pointerInput(count, viewWidth) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, delta ->
                        change.consume()
                        if (count > 1) scope.launch { offset.snapTo(offset.value + delta) }
                    },
                    onDragEnd = {
                        val threshold = viewWidth * 0.18f
                        when {
                            offset.value <= -threshold -> glide(1)
                            offset.value >= threshold -> glide(-1)
                            else -> scope.launch { offset.animateTo(0f, tween(150)) }
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f, tween(150)) } },
                )
            }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (dy != 0f) glide(if (dy > 0) 1 else -1)
            },
    ) {
        // The current slide plus both neighbours, in a row that the drag offset moves.
        for (slot in -1..1) {
            if (slot != 0 && count < 2) continue
            val slideIndex = ((index + slot) % count + count) % count
            val item = state.viewerItemAt(slideIndex) ?: continue
            Slide(
                state = state,
                thumbs = thumbs,
                item = item,
                current = slot == 0,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = slot * viewWidth.toFloat() + offset.value },
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
                "Drag or ‹ › to flip · wheel · ←/→ · Esc closes",
                color = Color.White.copy(alpha = 0.35f),
                style = MaterialTheme.typography.caption,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

/** One picture, fitted into the whole overlay. */
@Composable
private fun Slide(
    state: BoardState,
    thumbs: ThumbCache,
    item: ImageItem,
    current: Boolean,
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
    Box(modifier.padding(horizontal = 48.dp, vertical = 56.dp), contentAlignment = Alignment.Center) {
        val bmp = bitmap
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
