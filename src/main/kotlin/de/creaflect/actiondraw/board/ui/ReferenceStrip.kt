package de.creaflect.actiondraw.board.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.board.BoardState
import de.creaflect.actiondraw.board.ImageItem
import de.creaflect.actiondraw.image.ImageLoader
import de.creaflect.actiondraw.image.ThumbCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The contents of the always-on-top window: one picture big, the rest as a strip underneath.
 * Made to sit in a corner of the screen while you paint in Krita — it floats above other windows,
 * so the reference stays visible without alt-tabbing.
 */
@Composable
fun ReferenceStrip(state: BoardState, thumbs: ThumbCache) {
    val ids = state.stripIds
    val current = state.stripItem

    Column(Modifier.fillMaxSize().background(Color(0xFF101010))) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (current == null) {
                Text(
                    "Select pictures on the board to fill the strip.",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                val file = state.fileOf(current)
                val bitmap: ImageBitmap? by produceState<ImageBitmap?>(null, file) {
                    value = file?.let {
                        withContext(Dispatchers.IO) { runCatching { ImageLoader.load(it) }.getOrNull() }
                    }
                }
                bitmap?.let {
                    Image(
                        bitmap = it,
                        contentDescription = current.caption ?: current.path,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(4.dp),
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
                                if (id == current?.id) MaterialTheme.colors.primary else Color.Transparent,
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
            Text("‹", color = Color.White, modifier = Modifier.clickable { state.stripStep(-1) })
            Text("›", color = Color.White, modifier = Modifier.clickable { state.stripStep(1) })
            Text(
                if (ids.isEmpty()) "" else "${state.stripIndex + 1} / ${ids.size}",
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.caption,
            )
        }
    }
}
