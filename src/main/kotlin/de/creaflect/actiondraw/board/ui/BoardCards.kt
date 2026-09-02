package de.creaflect.actiondraw.board.ui

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.board.BoardEditor
import de.creaflect.actiondraw.board.BoardGroup
import de.creaflect.actiondraw.board.BoardItem
import de.creaflect.actiondraw.board.BoardState
import de.creaflect.actiondraw.board.ImageItem
import de.creaflect.actiondraw.board.NoteItem
import de.creaflect.actiondraw.image.ThumbCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One board cell (image or note) with its right-click menu; [groupId] is the section it sits in. */
@Composable
fun BoardCard(state: BoardState, thumbs: ThumbCache, item: BoardItem, textured: Boolean, groupId: String?) {
    ContextMenuArea(items = { gridOrderMenuItems(state, item, groupId) + cardMenuItems(state, item) }) {
        when (item) {
            is ImageItem -> ImageCard(state, thumbs, item, textured)
            is NoteItem -> NoteCard(state, item, textured)
        }
    }
}

/** Reordering within the group the card was clicked in (the array is the display order). */
private fun gridOrderMenuItems(state: BoardState, item: BoardItem, groupId: String?): List<ContextMenuItem> = listOf(
    ContextMenuItem("Move earlier") { state.stepInGroup(item.id, groupId, forward = false) },
    ContextMenuItem("Move later") { state.stepInGroup(item.id, groupId, forward = true) },
    ContextMenuItem("Move to group start") { state.toGroupEdge(item.id, groupId, toEnd = false) },
    ContextMenuItem("Move to group end") { state.toGroupEdge(item.id, groupId, toEnd = true) },
)

internal fun cardMenuItems(state: BoardState, item: BoardItem): List<ContextMenuItem> {
    // Right-clicking outside the selection retargets it (also done on press, belt and braces).
    val ids = if (item.id in state.selection) state.selection else setOf(item.id)
    val menu = mutableListOf<ContextMenuItem>()
    when (item) {
        is ImageItem -> {
            menu += ContextMenuItem("View large") { state.openViewer(item.id) }
            menu += ContextMenuItem("Caption…") { state.openEditor(BoardEditor.EditCaption(item.id)) }
            menu += ContextMenuItem("Tags…") { state.openEditor(BoardEditor.EditTags(ids)) }
            menu += ContextMenuItem(if (item.starred) "Unstar" else "Star") { state.toggleStar(ids) }
        }

        is NoteItem -> menu += ContextMenuItem("Edit note…") { state.openEditor(BoardEditor.EditNote(item.id)) }
    }
    menu += ContextMenuItem("Copy") { state.copySelection() }
    if (item.groups.isNotEmpty()) {
        menu += ContextMenuItem("Move to Inbox") { state.moveToGroup(ids, null) }
    }
    state.sortedGroups.filterNot { it.id in item.groups }.forEach { group ->
        menu += ContextMenuItem("Move to ${group.name}") { state.moveToGroup(ids, group.id) }
    }
    menu += ContextMenuItem("Remove from board") { state.removeItems(ids) }
    return menu
}

/** Explorer-style selection: click, Ctrl+click, Shift+click; right-click retargets. */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.cardClicks(state: BoardState, id: String): Modifier =
    onPointerEvent(PointerEventType.Press) { event ->
        when {
            event.buttons.isSecondaryPressed -> state.rightClickItem(id)
            event.buttons.isPrimaryPressed -> state.clickItem(
                id,
                ctrl = event.keyboardModifiers.isCtrlPressed,
                shift = event.keyboardModifiers.isShiftPressed,
            )
        }
    }

@Composable
internal fun selectionBorder(state: BoardState, id: String): Color = when {
    id in state.selection -> MaterialTheme.colors.secondary
    state.focusId == id -> MaterialTheme.colors.secondary.copy(alpha = 0.45f)
    else -> Color.Transparent
}

@Composable
private fun ImageCard(state: BoardState, thumbs: ThumbCache, item: ImageItem, textured: Boolean) {
    val file = state.fileOf(item)
    val thumb: ImageBitmap? by produceState<ImageBitmap?>(null, file) {
        value = file?.let { withContext(Dispatchers.IO) { thumbs.load(it) } }
    }
    val shape = RoundedCornerShape(4.dp)
    Column(
        Modifier
            .shadow(if (textured) 3.dp else 0.dp, shape)
            .clip(shape)
            .background(if (textured) Themes.cardBacking else Color(0xFF0D0D0D))
            .border(2.dp, selectionBorder(state, item.id), shape)
            .cardClicks(state, item.id)
            .padding(if (textured) 6.dp else 2.dp),
    ) {
        Box(
            Modifier.aspectRatio(1f).fillMaxWidth().background(Color(0x14000000)),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = thumb
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = item.path,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text("…", color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f))
            }
            if (item.starred) {
                Text("★", color = Color(0xFFFFB300), modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
            }
        }
        Text(
            item.caption ?: file?.name ?: item.path,
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (item.tags.isNotEmpty()) {
            Text(
                item.tags.joinToString(" ") { "#$it" },
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.secondary.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NoteCard(state: BoardState, item: NoteItem, textured: Boolean) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier
            .shadow(if (textured) 3.dp else 0.dp, shape)
            .clip(shape)
            .background(if (textured) Themes.noteBacking else Themes.noteBackingDark)
            .border(2.dp, selectionBorder(state, item.id), shape)
            .cardClicks(state, item.id)
            .aspectRatio(1f),
    ) {
        Text(
            item.text,
            style = MaterialTheme.typography.body2,
            color = if (textured) Themes.noteInk else Themes.noteInkDark,
            maxLines = 9,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(10.dp),
        )
    }
}

/** Section header: collapse toggle, colour dot, name, count and the group's Draw button. */
@Composable
fun GroupHeader(state: BoardState, group: BoardGroup?, count: Int) {
    val row: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp),
        ) {
            if (group != null) {
                Text(
                    if (group.collapsed) "▸" else "▾",
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.clickable { state.toggleCollapsed(group.id) }.padding(horizontal = 4.dp),
                )
                Themes.parseColor(group.color)?.let { accent ->
                    Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
                    Spacer(Modifier.width(6.dp))
                }
            }
            Text(
                "${group?.name ?: "Inbox"} ($count)",
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onBackground,
            )
            Spacer(Modifier.weight(1f))
            if (count > 0) {
                OutlinedButton(onClick = { state.drawGroup(group?.id) }) { Text("Draw $count") }
            }
        }
    }
    if (group != null) {
        ContextMenuArea(items = { groupMenuItems(state, group) }) { row() }
    } else {
        row()
    }
}

private fun groupMenuItems(state: BoardState, group: BoardGroup): List<ContextMenuItem> = listOf(
    ContextMenuItem("Rename…") { state.openEditor(BoardEditor.RenameGroup(group.id)) },
    ContextMenuItem("Cycle colour") { state.cycleGroupColor(group.id) },
    ContextMenuItem("Move up") { state.moveGroup(group.id, -1) },
    ContextMenuItem("Move down") { state.moveGroup(group.id, +1) },
    ContextMenuItem("Delete group (cards → Inbox)") { state.deleteGroup(group.id) },
)
