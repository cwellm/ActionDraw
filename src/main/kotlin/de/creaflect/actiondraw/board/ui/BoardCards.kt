package de.creaflect.actiondraw.board.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.creaflect.actiondraw.board.BoardEditor
import de.creaflect.actiondraw.board.BoardGroup
import de.creaflect.actiondraw.board.BoardItem
import de.creaflect.actiondraw.board.BoardState
import de.creaflect.actiondraw.board.ImageItem
import de.creaflect.actiondraw.board.LinkItem
import de.creaflect.actiondraw.board.NoteColors
import de.creaflect.actiondraw.board.NoteItem
import de.creaflect.actiondraw.board.NoteKind
import de.creaflect.actiondraw.board.paletteCaption
import de.creaflect.actiondraw.board.paletteColours
import de.creaflect.actiondraw.image.ThumbCache
import de.creaflect.actiondraw.ui.Atelier
import de.creaflect.actiondraw.ui.AtelierType
import de.creaflect.actiondraw.ui.CrossRoomButton
import de.creaflect.actiondraw.ui.Lift
import de.creaflect.actiondraw.ui.Room
import de.creaflect.actiondraw.ui.RuleRed
import de.creaflect.actiondraw.ui.drawPaperclip
import de.creaflect.actiondraw.ui.lampShadow
import de.creaflect.actiondraw.ui.paperTooth
import de.creaflect.actiondraw.ui.ruled
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
        CardMenuArea(state, items = { gridOrderMenuItems(state, item, groupId) + cardMenuItems(state, item) }) {
            when (item) {
                is ImageItem -> ImageCard(state, thumbs, item, textured)
                is NoteItem -> NoteCard(state, item, textured)
                is LinkItem -> LinkCard(state, thumbs, item, textured)
            }
        }
        if (state.isBorrowed(item)) ConceptTab(state, item, Modifier.align(Alignment.TopStart))
    }
}

/**
 * A card borrowed from a concept wears the concept's tab: viridian, the concept's name lettered on
 * it, half over the card's top edge like a folio's - it is the concept's, looked at here.
 */
@Composable
internal fun ConceptTab(state: BoardState, item: BoardItem, modifier: Modifier = Modifier) {
    val name = state.groupById(item.groups.firstOrNull())?.name ?: return
    val viridian = Room.CONCEPTS.pigment
    Text(
        name,
        style = AtelierType.Hand.copy(fontSize = 14.sp, lineHeight = 15.sp),
        color = viridian.onMass,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .offset(x = 10.dp, y = (-9).dp)
            .widthIn(max = 140.dp)
            .background(viridian.mass, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomEnd = 2.dp, bottomStart = 2.dp))
            .padding(horizontal = 7.dp)
            .testTag("concept-tab-" + item.id),
    )
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
    // A borrowed card is its concept's: looked at, drawn, starred and tagged here, edited there.
    val borrowed = state.isBorrowed(item)
    when (item) {
        is ImageItem -> {
            menu += ContextMenuItem("View large") { state.openViewer(item.id) }
            menu += ContextMenuItem("Palette…") { state.openEditor(BoardEditor.ShowPalette(ids)) }
            if (!borrowed) menu += ContextMenuItem("Caption…") { state.openEditor(BoardEditor.EditCaption(item.id)) }
            menu += ContextMenuItem("Tags…") { state.openEditor(BoardEditor.EditTags(ids)) }
            menu += ContextMenuItem(if (item.starred) "Unstar" else "Star") { state.toggleStar(ids) }
            menu += ContextMenuItem("Use as wallpaper") { state.fileOf(item)?.let { state.setWallpaper(it) } }
            // A picture Live Sketch saved can be taken up again where it was left.
            if (state.sketchOf(item) != null) menu += ContextMenuItem("Continue in Live Sketch") { state.openSketch(item) }
        }

        is NoteItem -> {
            if (!borrowed) menu += ContextMenuItem("Edit note…") { state.openEditor(BoardEditor.EditNote(item.id)) }
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
            if (!borrowed) menu += ContextMenuItem("Edit link…") { state.openEditor(BoardEditor.EditLink(item.id)) }
            menu += ContextMenuItem(
                if (item.preview == null) "Fetch preview (goes online)" else "Fetch preview again",
            ) { state.openEditor(BoardEditor.FetchPreview(item.id)) }
            if (item.preview != null) {
                menu += ContextMenuItem("Remove preview") { state.clearLinkPreview(item.id) }
            }
        }
    }
    menu += ContextMenuItem("Copy") { state.copySelection() }
    if (borrowed) {
        val group = state.groupById(item.groups.firstOrNull())
        menu += ContextMenuItem("Open concept") { group?.conceptId?.let(state::showConcept) }
        menu += ContextMenuItem("Unlink ${group?.name ?: "concept"} from this board") { group?.conceptId?.let(state::unlinkConcept) }
        return menu
    }
    state.groupById(item.groups.firstOrNull())?.let { group ->
        // Out of the group it is in: into the parent for a subgroup's card, else the Inbox.
        val lands = state.groupById(group.parentId)?.name ?: "Inbox"
        menu += ContextMenuItem("Remove from ${group.name} (→ $lands)") { state.removeFromGroup(ids) }
    }
    val verb = if (item.groups.isEmpty()) "Add to" else "Move to"
    state.sortedGroups.filterNot { it.id in item.groups }.forEach { group ->
        val label = (if (group.parentId != null) "  ↳ " else "") + (if (group.isConcept) "⧉ " else "") + group.name
        menu += ContextMenuItem("$verb $label") { state.moveToGroup(ids, group.id) }
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

/** What is selected on a board is marked in the Boards room's pigment, ultramarine. */
internal fun selectionBorder(state: BoardState, id: String): Color = when {
    id in state.selection -> Room.BOARDS.pigment.glow
    state.focusId == id -> Room.BOARDS.pigment.glow.copy(alpha = 0.45f)
    else -> Color.Transparent
}

@Composable
private fun ImageCard(state: BoardState, thumbs: ThumbCache, item: ImageItem, textured: Boolean) {
    val file = state.fileOf(item)
    val thumb: ImageBitmap? by produceState<ImageBitmap?>(null, file) {
        value = file?.let { withContext(Dispatchers.IO) { thumbs.load(it) } }
    }
    // A print: the picture on paper, its caption written under it — on any board surface.
    val shape = RoundedCornerShape(2.dp)
    Column(
        Modifier
            .lampShadow(Lift.RESTING, shape)
            .clip(shape)
            .background(Atelier.Paper)
            .paperTooth()
            .border(2.dp, selectionBorder(state, item.id), shape)
            .cardClicks(state, item.id)
            .padding(6.dp),
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
                Text("★", color = Room.COLLAGE.pigment.glow, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
            }
            PracticeBadge(state.practiceOf(item), Modifier.align(Alignment.BottomStart).padding(4.dp))
        }
        Text(
            item.caption ?: file?.name ?: item.path,
            style = MaterialTheme.typography.caption,
            color = Atelier.InkQuiet,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (item.tags.isNotEmpty()) {
            Text(
                item.tags.joinToString(" ") { "#$it" },
                style = MaterialTheme.typography.caption,
                color = Room.BOARDS.pigment.mass,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NoteCard(state: BoardState, item: NoteItem, textured: Boolean) {
    val shape = RoundedCornerShape(4.dp)
    val ink = Themes.noteInk
    if (item.kind == NoteKind.PALETTE) {
        PaletteCard(state, item, Modifier.fillMaxWidth().aspectRatio(BoardState.PALETTE_ASPECT))
    } else if (item.kind == NoteKind.POSTIT) {
        // A post-it: a yellow slip on any surface, the text as typed, in a written hand.
        Box(
            Modifier
                .lampShadow(Lift.RESTING, shape)
                .clip(shape)
                .background(notePaper(item))
                .paperTooth()
                .border(2.dp, selectionBorder(state, item.id), shape)
                .cardClicks(state, item.id)
                .fillMaxWidth(),
        ) {
            Text(
                item.text,
                style = AtelierType.Hand.copy(fontSize = 20.sp, lineHeight = 22.sp),
                color = ink,
                modifier = Modifier.padding(10.dp).testTag("postit-" + item.id),
            )
        }
    } else {
        // A document note: a ruled sheet with its title; a tap opens the note to read.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .lampShadow(Lift.RESTING, shape)
                .clip(shape)
                .background(notePaper(item))
                .paperTooth()
                .ruled(first = 10.dp, spacing = 9.dp)
                .border(2.dp, selectionBorder(state, item.id), shape)
                .cardClicks(state, item.id)
                .pointerInput(item.id) { detectTapGestures { state.openEditor(BoardEditor.ShowNote(item.id)) } }
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp)
                .testTag("note-" + item.id),
        ) {
            Text("▤", style = MaterialTheme.typography.body2, color = ink.copy(alpha = 0.6f))
            Spacer(Modifier.width(8.dp))
            Text(
                item.title,
                style = if (item.heading) MaterialTheme.typography.subtitle1 else MaterialTheme.typography.body2,
                fontWeight = FontWeight.Bold,
                color = ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A link card: its title, underlined, and a tap opens it. The preview picture, if fetched, sits beside. */
@Composable
private fun LinkCard(state: BoardState, thumbs: ThumbCache, item: LinkItem, textured: Boolean) {
    val shape = RoundedCornerShape(4.dp)
    val preview = state.previewFileOf(item)
    val previewThumb: ImageBitmap? by produceState<ImageBitmap?>(null, preview) {
        value = preview?.let { withContext(Dispatchers.IO) { thumbs.load(it) } }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .lampShadow(Lift.RESTING, shape)
            .clip(shape)
            .background(Atelier.Paper)
            .paperTooth()
            .ruled(first = 8.dp, spacing = 11.dp, header = RuleRed)
            .border(2.dp, selectionBorder(state, item.id), shape)
            .cardClicks(state, item.id)
            .pointerInput(item.id) { detectTapGestures { state.openLink(item) } }
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .testTag("link-" + item.id),
    ) {
        val bmp = previewThumb
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = item.title.ifBlank { item.url },
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(3.dp)),
            )
        } else {
            // No picture yet: a paperclip holds it to the board.
            Canvas(Modifier.size(14.dp, 30.dp)) { drawPaperclip(Offset(1f, 0f), size.height) }
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                item.title.ifBlank { item.url },
                style = MaterialTheme.typography.body2,
                color = Room.BOARDS.pigment.mass,
                textDecoration = TextDecoration.Underline,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                host(item.url),
                style = MaterialTheme.typography.caption,
                color = Atelier.InkQuiet,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun host(url: String): String =
    url.substringAfter("://").substringBefore('/').ifBlank { url }

/**
 * A note's paper, the same on every surface: the colour chosen for it, else a yellow slip for a
 * post-it and a white sheet for a document. (The plain board once drew them dark; on the
 * graphite table they are paper like everything else.) The ink is [Themes.noteInk] on all.
 */
internal fun notePaper(item: NoteItem): Color =
    Themes.parseColor(item.color) ?: if (item.kind == NoteKind.POSTIT) Themes.noteBacking else Atelier.Paper

/** Section header: collapse toggle, colour dot, name, count and the group's Draw button. */
@Composable
fun GroupHeader(state: BoardState, group: BoardGroup?, count: Int, dropTarget: Boolean = false) {
    val accent = accentOf(state, group)
    val nested = group?.parentId != null
    val row: @Composable () -> Unit = {
        Column(Modifier.padding(start = if (nested) 22.dp else 0.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (nested) 6.dp else 14.dp, bottom = 2.dp)
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
                    (if (nested) "↳ " else "") + (if (group?.isConcept == true) "⧉ " else "") + "${group?.name ?: "Inbox"} ($count)",
                    style = if (nested) MaterialTheme.typography.subtitle2 else MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.Bold,
                    // In the surface's own ink, readable on cork as on the table; the dot and the
                    // hairline carry the group's colour (teal lettering vanished into cork).
                    color = MaterialTheme.colors.onBackground,
                    // The name selects the section's cards, as a group's label does on the canvas.
                    modifier = if (group == null) Modifier else Modifier
                        .testTag("group-header-" + group.id)
                        .clickable { state.selectGroup(group.id) },
                )
                Spacer(Modifier.weight(1f))
                val drawable = if (group == null) count else state.itemsInTree(group.id).size
                if (drawable > 0) {
                    CrossRoomButton(Room.PRACTICE, "Draw $drawable") { state.drawGroup(group?.id) }
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

private fun groupMenuItems(state: BoardState, group: BoardGroup): List<ContextMenuItem> = buildList {
    if (group.isConcept) {
        // A linked concept: the board can look at it, draw from it and let it go — nothing more.
        add(ContextMenuItem("Draw ${state.itemsInTree(group.id).size}") { state.drawGroup(group.id) })
        add(ContextMenuItem("Select group") { state.selectGroup(group.id) })
        add(ContextMenuItem("Open concept") { group.conceptId?.let(state::showConcept) })
        add(ContextMenuItem("Move up") { state.moveGroup(group.id, -1) })
        add(ContextMenuItem("Move down") { state.moveGroup(group.id, +1) })
        add(ContextMenuItem("Unlink from this board") { group.conceptId?.let(state::unlinkConcept) })
        return@buildList
    }
    add(ContextMenuItem("Rename…") { state.openEditor(BoardEditor.RenameGroup(group.id)) })
    add(ContextMenuItem("Cycle colour") { state.cycleGroupColor(group.id) })
    add(ContextMenuItem("Move up") { state.moveGroup(group.id, -1) })
    add(ContextMenuItem("Move down") { state.moveGroup(group.id, +1) })
    // One level of nesting: a group can go inside a top-level group, or back out to the top.
    state.possibleParents(group.id).filter { it.id != group.parentId }.forEach { parent ->
        add(ContextMenuItem("Move into ${parent.name}") { state.setGroupParent(group.id, parent.id) })
    }
    if (group.parentId != null) {
        add(ContextMenuItem("Make top-level") { state.setGroupParent(group.id, null) })
        add(ContextMenuItem("New subgroup here…") { state.openEditor(BoardEditor.NewGroup(group.parentId)) })
    } else {
        add(ContextMenuItem("New subgroup inside…") { state.openEditor(BoardEditor.NewGroup(group.id)) })
    }
    add(
        ContextMenuItem(if (group.parentId == null) "Delete group (cards → Inbox)" else "Dissolve (cards → parent)") {
            state.ungroup(group.id)
        },
    )
}

/** Shows how a picture stands with the practice side: flagged to redo, drawn, or never drawn. */
@Composable
internal fun PracticeBadge(practice: BoardState.Practice, modifier: Modifier) {
    val (glyph, color) = when (practice) {
        // Practice's own pigment for what is to be drawn again; quiet ink for what was drawn.
        BoardState.Practice.REDO -> "⟳" to Room.PRACTICE.pigment.glow
        BoardState.Practice.SEEN -> "✓" to Atelier.TextSoft
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

/**
 * A palette pinned to the board: the colours read off a picture as a strip of swatches on a card
 * of paper, whose they are lettered under it - "colours of the cliff photo".
 */
@Composable
internal fun PaletteCard(state: BoardState, item: NoteItem, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(2.dp)
    val colours = remember(item.text) { item.paletteColours() }
    Column(
        modifier
            .lampShadow(Lift.RESTING, shape)
            .clip(shape)
            .background(Atelier.Paper)
            .paperTooth()
            .border(2.dp, selectionBorder(state, item.id), shape)
            .cardClicks(state, item.id)
            .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 4.dp)
            .testTag("palette-" + item.id),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
            colours.forEach { colour -> Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF000000.toInt() or colour))) }
        }
        Text(
            item.paletteCaption(),
            style = AtelierType.Hand.copy(fontSize = 16.sp, lineHeight = 17.sp),
            color = Atelier.InkQuiet,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
