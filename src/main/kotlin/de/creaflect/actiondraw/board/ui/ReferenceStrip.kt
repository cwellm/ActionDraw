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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.board.BoardState
import de.creaflect.actiondraw.board.ImageItem
import de.creaflect.actiondraw.image.ImageLoader
import de.creaflect.actiondraw.image.ThumbCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The contents of the always-on-top window: one picture big, the rest as a strip underneath.
 * Made to sit in a corner of the screen while you paint in Krita — it floats above other windows,
 * so the reference stays visible without alt-tabbing.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ReferenceStrip(state: BoardState, thumbs: ThumbCache) {
    val ids = state.stripIds
    val index = state.stripIndex
    val count = ids.size
    var viewWidth by remember { mutableStateOf(1) }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    /** Same carousel as the large view: the row glides one width, then the index takes over. */
    fun glide(direction: Int) {
        if (count < 2) return
        scope.launch {
            offset.animateTo(-direction * viewWidth.toFloat(), tween(180))
            state.stripStep(direction)
            offset.snapTo(0f)
        }
    }

    LaunchedEffect(index) { if (offset.value != 0f && !offset.isRunning) offset.snapTo(0f) }

    Column(Modifier.fillMaxSize().background(Color(0xFF101010))) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
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
            contentAlignment = Alignment.Center,
        ) {
            if (count == 0) {
                Text(
                    "Select pictures on the board to fill the strip.",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                // The shown picture plus both neighbours, in a row the drag offset moves.
                for (slot in -1..1) {
                    if (slot != 0 && count < 2) continue
                    val slideIndex = ((index + slot) % count + count) % count
                    val item = state.item(ids[slideIndex]) as? ImageItem ?: continue
                    StripSlide(
                        state = state,
                        thumbs = thumbs,
                        item = item,
                        current = slot == 0,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationX = slot * viewWidth.toFloat() + offset.value },
                    )
                }
            }
        }

        if (ids.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                modifier = Modifier.fillMaxWidth().height(72.dp),
            ) {
                items(ids) { id ->
                    val item = state.item(id) as? ImageItem
                    val file = item?.let(state::fileOf)
                    val thumb: ImageBitmap? by produceState<ImageBitmap?>(null, file) {
                        value = file?.let { withContext(Dispatchers.IO) { thumbs.load(it, maxSize = 128) } }
                    }
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(
                                2.dp,
                                if (id == ids.getOrNull(index)) MaterialTheme.colors.primary else Color.Transparent,
                                RoundedCornerShape(3.dp),
                            )
                            .clickable { state.stripGoTo(id) },
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

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text("‹", color = Color.White, modifier = Modifier.clickable { glide(-1) })
            Text("›", color = Color.White, modifier = Modifier.clickable { glide(1) })
            Text(
                if (ids.isEmpty()) "" else "${index + 1} / $count",
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.caption,
            )
            Text(
                "drag or wheel to flip",
                color = Color.White.copy(alpha = 0.3f),
                style = MaterialTheme.typography.caption,
            )
        }
    }
}

/** One picture in the floating strip: full size for the centre slide, a preview for neighbours. */
@Composable
private fun StripSlide(
    state: BoardState,
    thumbs: ThumbCache,
    item: ImageItem,
    current: Boolean,
    modifier: Modifier,
) {
    val file = state.fileOf(item)
    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(null, file, current) {
        value = file?.let { f ->
            withContext(Dispatchers.IO) {
                if (current) runCatching { ImageLoader.load(f) }.getOrNull() else thumbs.load(f, maxSize = 480)
            }
        }
    }
    Box(modifier.padding(4.dp), contentAlignment = Alignment.Center) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = item.caption ?: item.path,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
