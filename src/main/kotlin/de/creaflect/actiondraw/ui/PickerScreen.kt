package de.creaflect.actiondraw.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.AppState
import de.creaflect.actiondraw.image.Thumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Thumbnail grid for choosing which pictures of the folder take part in sessions. */
@Composable
fun PickerScreen(state: AppState) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Choose pictures — ${state.selectedCount} of ${state.totalCount} selected",
                style = MaterialTheme.typography.h6,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { state.selectAllImages() }) { Text("All") }
            OutlinedButton(onClick = { state.selectNoImages() }) { Text("None") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { state.closePicker() }, enabled = state.selectedCount > 0) { Text("Done") }
        }
        Text(
            "Click a picture to include or exclude it · sessions draw only from the selection · Esc/Enter done",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
        )
        Spacer(Modifier.padding(4.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 132.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.images, key = { it.name }) { file ->
                PickerCell(
                    file = file,
                    selected = state.isSelected(file.name),
                    onToggle = { state.toggleSelected(file.name) },
                )
            }
        }
    }
}

@Composable
private fun PickerCell(file: File, selected: Boolean, onToggle: () -> Unit) {
    val thumb: ImageBitmap? by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { Thumbnails.load(file) }
    }
    val shape = RoundedCornerShape(6.dp)
    val border = if (selected) MaterialTheme.colors.primary else Color.Transparent
    Column {
        Box(
            Modifier
                .aspectRatio(1f)
                .clip(shape)
                .border(2.dp, border, shape)
                .background(Color(0xFF0D0D0D))
                .clickable { onToggle() }
                .alpha(if (selected) 1f else 0.35f),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = thumb
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = file.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text("…", color = Color.White.copy(alpha = 0.4f))
            }
            if (selected) {
                Surface(
                    color = MaterialTheme.colors.primary,
                    shape = RoundedCornerShape(bottomStart = 6.dp),
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Text(
                        "✓",
                        color = MaterialTheme.colors.onPrimary,
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Text(
            file.name,
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = if (selected) 0.7f else 0.35f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
