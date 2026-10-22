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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.creaflect.actiondraw.board.BoardEditor
import de.creaflect.actiondraw.board.BoardState
import de.creaflect.actiondraw.board.ImageItem
import de.creaflect.actiondraw.image.Pictures
import de.creaflect.actiondraw.image.thumbnailBitmap
import de.creaflect.actiondraw.ui.Design
import de.creaflect.actiondraw.ui.Lift
import de.creaflect.actiondraw.ui.Room
import de.creaflect.actiondraw.ui.RoomHeader
import de.creaflect.actiondraw.ui.chooseFolder
import de.creaflect.actiondraw.ui.lampShadow
import de.creaflect.actiondraw.ui.paperTooth
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
    /** The board this one is nested in, shown as a breadcrumb on the tile. */
    val parent: String? = null,
)

/**
 * The list of Idea Boards: every board under the boards home plus the recently opened ones, each
 * with a cover picture and its counts. Clicking one opens it.
 */
@Composable
fun BoardListScreen(state: BoardState, thumbs: Pictures, onHome: (() -> Unit)? = null) {
    val boards by produceState(initialValue = emptyList<BoardSummary>(), state.boardsHomeTick, state.recent) {
        value = withContext(Dispatchers.IO) {
            // Tree order, so a sub-board sits right after the board it belongs to.
            state.boardTree().map { node ->
                val file = state.peek(node.dir)
                val images = file?.items.orEmpty().filterIsInstance<ImageItem>()
                BoardSummary(
                    name = node.name,
                    dir = node.dir,
                    pictures = images.size,
                    notes = file?.items.orEmpty().size - images.size,
                    cover = images.firstOrNull()?.let { File(node.dir, it.path) }?.takeIf { it.isFile },
                    parent = node.parent,
                )
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        // The list is the room's top: its way back is the way home.
        if (onHome != null) RoomHeader(Room.BOARDS, onHome)
        else Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
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
            Button(onClick = { state.openEditor(BoardEditor.NewBoard()) }) { Text("New board…") }
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
                    BoardTile(
                        board = board,
                        thumbs = thumbs,
                        onOpen = { state.openBoard(board.dir) },
                        onMove = { state.openEditor(BoardEditor.MoveBoard(board.dir, board.name)) },
                        onDelete = {
                            state.openEditor(
                                BoardEditor.DeleteBoard(
                                    dir = board.dir,
                                    name = board.name,
                                    pictures = board.pictures,
                                    ownsFolder = state.entryFor(board.dir)?.ownsFolder ?: false,
                                    subBoards = state.subBoardCount(board.dir),
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BoardTile(
    board: BoardSummary,
    thumbs: Pictures,
    onOpen: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    val cover: ImageBitmap? by produceState<ImageBitmap?>(null, board.cover) {
        value = board.cover?.let { withContext(Dispatchers.IO) { thumbs.thumbnailBitmap(it, maxSize = 320) } }
    }
    // A board on the shelf: its cover as a print standing on a plank, its name lettered under it.
    // Not clipped, so the plank can reach into the gaps and a row of boards reads as one shelf.
    Column(
        Modifier
            .clickable { onOpen() }
            .padding(bottom = 8.dp),
    ) {
        Box(
            Modifier
                .padding(8.dp)
                .lampShadow(Lift.RESTING)
                .background(Design.colors.paper)
                .paperTooth()
                .padding(6.dp)
                .fillMaxWidth()
                .aspectRatio(1.4f)
                .background(Color(0xFF1A1715)),
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
        Shelf(Modifier.fillMaxWidth().offset(y = (-6).dp).testTag("shelf"))
        board.parent?.let {
            Text(
                "in $it",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp),
            )
        }
        Text(
            board.name,
            style = Design.hand.copy(fontSize = 24.sp),
            color = Design.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = if (board.parent == null) 8.dp else 0.dp),
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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp)) {
            Text(
                board.dir.path,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.45f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Move…",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.secondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .clickable { onMove() }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
            Text(
                "Delete…",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.error,
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .clickable { onDelete() }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * A stretch of the shelf the covers stand on: a wooden plank, lit along its front edge, its
 * shadow under it, reaching half the grid's gap to each side to meet the next one.
 */
@Composable
private fun Shelf(modifier: Modifier) {
    Box(
        modifier
            .height(9.dp)
            .drawBehind {
                val reach = 7.dp.toPx()
                val left = -reach
                val width = size.width + 2 * reach
                drawRect(Color.Black.copy(alpha = 0.35f), Offset(left, size.height), Size(width, 4.dp.toPx()))
                drawRect(
                    Brush.verticalGradient(listOf(Color(0xFF8A6446), Color(0xFF5E4130), Color(0xFF3B271A)), 0f, size.height),
                    Offset(left, 0f),
                    Size(width, size.height),
                )
                drawLine(Color.White.copy(alpha = 0.18f), Offset(left, 1f), Offset(left + width, 1f), 1.dp.toPx())
            },
    )
}
