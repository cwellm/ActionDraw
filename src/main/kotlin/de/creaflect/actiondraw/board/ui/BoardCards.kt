package de.creaflect.actiondraw.board.ui

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.board.BoardEditor
import de.creaflect.actiondraw.board.BoardGroup
import de.creaflect.actiondraw.board.BoardItem
import de.creaflect.actiondraw.board.BoardState
import de.creaflect.actiondraw.board.ImageItem
import de.creaflect.actiondraw.board.LinkItem
import de.creaflect.actiondraw.board.NoteColors
import de.creaflect.actiondraw.board.NoteItem
import de.creaflect.actiondraw.image.ThumbCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One board cell (image or note) with its right-click menu; [groupId] is the section it sits in.
 * With a [reorder] handle the card can also be dragged to a new place within its section.
 */
@Composable
fun BoardCard(
    state: BoardState,
    thumbs: ThumbCache,
    item: BoardItem,
    textured: Boolean,
    groupId: String?,
    reorder: GridReorder? = null,
    cellKey: String? = null,
) {
    val dragging = reorder != null && cellKey != null && reorder.draggingKey == cellKey
    val isTarget = reorder != null && cellKey != null && reorder.targetKey == cellKey
    val modifier = if (reorder != null && cellKey != null) {
        Modifier
            .alpha(if (dragging) 0.4f else 1f)
            .border(
                2.dp,
                if (isTarget) MaterialTheme.colors.primary else Color.Transparent,
                RoundedCornerShape(6.dp),
            )
            .pointerInput(cellKey, groupId) {
                detectDragGestures(
                    onDragStart = { reorder.start(cellKey) },
                    onDrag = { change, _ -> change.consume(); reorder.drag(cellKey, change.position) },
                    onDragEnd = { reorder.drop() },
                    onDragCancel = { reorder.cancel() },
                )
            }
    } else {
        Modifier
    }
    Box(modifier) {
        ContextMenuArea(items = { gridOrderMenuItems(state, item, groupId) + cardMenuItems(state, item) }) {
            when (item) {
                is ImageItem -> ImageCard(state, thumbs, item, textured)
                is NoteItem -> NoteCard(state, item, textured)
                is LinkItem -> LinkCard(state, thumbs, item, textured)
            }
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
            menu += ContextMenuItem("Palette…") { state.openEditor(BoardEditor.ShowPalette(ids)) }
            menu += ContextMenuItem("Caption…") { state.openEditor(BoardEditor.EditCaption(item.id)) }
            menu += ContextMenuItem("Tags…") { state.openEditor(BoardEditor.EditTags(ids)) }
            menu += ContextMenuItem(if (item.starred) "Unstar" else "Star") { state.toggleStar(ids) }
        }

        is NoteItem -> {
            menu += ContextMenuItem("Edit note…") { state.openEditor(BoardEditor.EditNote(item.id)) }
            menu += ContextMenuItem(if (item.heading) "Normal size" else "Make heading") {
                state.toggleNoteHeading(item.id)
            }
            menu += ContextMenuItem("Next paper colour") {
                val next = NoteColors.ALL[(NoteColors.ALL.indexOf(item.color) + 1) % NoteColors.ALL.size]
                state.setNoteColor(item.id, next)
            }
        }

        is LinkItem -> {
            menu += ContextMenuItem("Open in browser") { state.openLink(item) }
            menu += ContextMenuItem("Edit link…") { state.openEditor(BoardEditor.EditLink(item.id)) }
            menu += ContextMenuItem(
                if (item.preview == null) "Fetch preview (goes online)" else "Fetch preview again",
            ) { state.openEditor(BoardEditor.FetchPreview(item.id)) }
            if (item.preview != null) {
                menu += ContextMenuItem("Remove preview") { state.clearLinkPreview(item.id) }
            }
        }
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
            PracticeBadge(state.practiceOf(item), Modifier.align(Alignment.BottomStart).padding(4.dp))
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
            .background(notePaper(item, textured))
            .border(2.dp, selectionBorder(state, item.id), shape)
            .cardClicks(state, item.id)
            .aspectRatio(1f),
    ) {
        Text(
            NoteText.format(item.text),
            style = if (item.heading) MaterialTheme.typography.h6 else MaterialTheme.typography.body2,
            color = noteInk(item, textured),
            maxLines = if (item.heading) 4 else 9,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(10.dp),
        )
    }
}

/** A link card: the title (or the bare url) plus its host, opened on double-click. */
@Composable
private fun LinkCard(state: BoardState, thumbs: ThumbCache, item: LinkItem, textured: Boolean) {
    val shape = RoundedCornerShape(4.dp)
    val preview = state.previewFileOf(item)
    val previewThumb: ImageBitmap? by produceState<ImageBitmap?>(null, preview) {
        value = preview?.let { withContext(Dispatchers.IO) { thumbs.load(it) } }
    }
    Column(
        Modifier
            .shadow(if (textured) 3.dp else 0.dp, shape)
            .clip(shape)
            .background(if (textured) Themes.cardBacking else Color(0xFF1C1C1E))
            .border(2.dp, selectionBorder(state, item.id), shape)
            .cardClicks(state, item.id)
            .aspectRatio(1f)
            .padding(10.dp),
    ) {
        val bmp = previewThumb
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = item.title.ifBlank { item.url },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(3.dp)),
            )
            Spacer(Modifier.height(4.dp))
        } else {
            Text("🔗", style = MaterialTheme.typography.h6)
        }
        Text(
            item.title.ifBlank { item.url },
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        Text(
            host(item.url),
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { state.openLink(item) },
        )
    }
}

internal fun host(url: String): String =
    url.substringAfter("://").substringBefore('/').ifBlank { url }

internal fun notePaper(item: NoteItem, textured: Boolean): Color =
    Themes.parseColor(item.color)?.let { if (textured) it else it.copy(alpha = 0.35f) }
        ?: if (textured) Themes.noteBacking else Themes.noteBackingDark

internal fun noteInk(item: NoteItem, textured: Boolean): Color =
    if (textured || item.color != null) Themes.noteInk else Themes.noteInkDark

/** Section header: collapse toggle, colour dot, name, count and the group's Draw button. */
@Composable
fun GroupHeader(state: BoardState, group: BoardGroup?, count: Int, dropTarget: Boolean = false) {
    val accent = accentOf(state, group)
    val row: @Composable () -> Unit = {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 2.dp)
                    .background(
                        if (dropTarget) MaterialTheme.colors.primary.copy(alpha = 0.18f) else Color.Transparent,
                        RoundedCornerShape(4.dp),
                    ),
            ) {
                if (group != null) {
                    Text(
                        if (group.collapsed) "▸" else "▾",
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier
                            .clickable { state.toggleCollapsed(group.id) }
                            .padding(horizontal = 4.dp),
                    )
                    // Every group gets a colour, whether or not one was picked for it, so the
                    // sections are told apart at a glance.
                    accent?.let {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(it))
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Text(
                    "${group?.name ?: "Inbox"} ($count)",
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.Bold,
                    color = accent ?: MaterialTheme.colors.onBackground,
                )
                Spacer(Modifier.weight(1f))
                if (count > 0) {
                    OutlinedButton(onClick = { state.drawGroup(group?.id) }) { Text("Draw $count") }
                }
            }
            // A hairline in the group's colour ties its cards to the header above them.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background((accent ?: MaterialTheme.colors.onBackground).copy(alpha = 0.35f)),
            )
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

/** Shows how a picture stands with the practice side: flagged to redo, drawn, or never drawn. */
@Composable
internal fun PracticeBadge(practice: BoardState.Practice, modifier: Modifier) {
    val (glyph, color) = when (practice) {
        BoardState.Practice.REDO -> "⟳" to Color(0xFFEF9A9A)
        BoardState.Practice.SEEN -> "✓" to Color(0xFF80CBC4)
        BoardState.Practice.UNSEEN -> return
    }
    Surface(color = Color(0x99000000), shape = RoundedCornerShape(3.dp), modifier = modifier) {
        Text(
            glyph,
            color = color,
            style = MaterialTheme.typography.caption,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

/** The colour a section is drawn in — the Inbox has none. */
@Composable
private fun accentOf(state: BoardState, group: BoardGroup?): Color? =
    group?.let { Themes.parseColor(state.accentOfGroup(it)) }
