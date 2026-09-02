package de.creaflect.actiondraw.board.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.board.BoardEditor
import de.creaflect.actiondraw.board.BoardState
import de.creaflect.actiondraw.board.BoardStore
import de.creaflect.actiondraw.board.ImageItem
import de.creaflect.actiondraw.image.ThumbCache
import de.creaflect.actiondraw.ui.chooseFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** One entry of the board list: what can be shown without opening the board. */
private data class BoardSummary(
    val name: String,
    val dir: File,
    val pictures: Int,
    val notes: Int,
    val cover: File?,
)

/**
 * The list of Idea Boards: every board under the boards home plus the recently opened ones, each
 * with a cover picture and its counts. Clicking one opens it.
 */
@Composable
fun BoardListScreen(state: BoardState, thumbs: ThumbCache) {
    val boards by produceState(initialValue = emptyList<BoardSummary>(), state.boardsHomeTick, state.recent) {
        value = withContext(Dispatchers.IO) {
            state.availableBoards().map { (name, dir) ->
                val file = BoardStore.peek(dir)
                val images = file?.items.orEmpty().filterIsInstance<ImageItem>()
                BoardSummary(
                    name = name,
                    dir = dir,
                    pictures = images.size,
                    notes = file?.items.orEmpty().size - images.size,
                    cover = images.firstOrNull()?.let { File(dir, it.path) }?.takeIf { it.isFile },
                )
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Idea Boards", style = MaterialTheme.typography.h4, color = MaterialTheme.colors.primary)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { state.leaveList() }) { Text("Back") }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
        ) {
            Text(
                "Home: ${state.boardsHome().path}",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = {
                chooseFolder(state.boardsHome().takeIf { it.isDirectory }, "Boards home")
                    ?.let { state.setBoardsHomeDir(it) }
            }) { Text("Change home…") }
            OutlinedButton(onClick = {
                chooseFolder(state.boardsHome().takeIf { it.isDirectory }, "Open board folder")
                    ?.let(state::openBoard)
            }) { Text("Explore…") }
            Button(onClick = { state.openEditor(BoardEditor.NewBoard) }) { Text("New board…") }
        }

        if (state.openFailed) {
            Text(
                "Couldn't read that board file (and no usable backup).",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.error,
            )
        }

        if (boards.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No boards yet — create one to start collecting.",
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 200.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(boards, key = { it.dir.absolutePath }) { board ->
                    BoardTile(board, thumbs) { state.openBoard(board.dir) }
                }
            }
        }
    }
}

@Composable
private fun BoardTile(board: BoardSummary, thumbs: ThumbCache, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    val cover: ImageBitmap? by produceState<ImageBitmap?>(null, board.cover) {
        value = board.cover?.let { withContext(Dispatchers.IO) { thumbs.load(it, maxSize = 320) } }
    }
    Column(
        Modifier
            .clip(shape)
            .background(MaterialTheme.colors.surface)
            .clickable { onOpen() }
            .padding(bottom = 8.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.4f).background(Color(0xFF0D0D0D)),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = cover
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = board.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text("empty", color = Color.White.copy(alpha = 0.35f), style = MaterialTheme.typography.caption)
            }
        }
        Text(
            board.name,
            style = MaterialTheme.typography.subtitle1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp),
        )
        Text(
            buildString {
                append(board.pictures)
                append(if (board.pictures == 1) " picture" else " pictures")
                if (board.notes > 0) append(" · ${board.notes} note${if (board.notes == 1) "" else "s"}")
            },
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.secondary,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Text(
            board.dir.path,
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.45f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}
