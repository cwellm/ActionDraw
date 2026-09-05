package de.creaflect.actiondraw.board.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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
import de.creaflect.actiondraw.board.NoteItem
import de.creaflect.actiondraw.image.ThumbCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The side drawer: everything on the board as a list, grouped. On the freeform canvas this is the
 * only place where the whole collection — including groups that hold nothing yet — can be seen
 * and managed; clicking a row selects it and brings the camera to it.
 */
@Composable
fun BoardDrawer(state: BoardState, thumbs: ThumbCache) {
    Surface(
        color = MaterialTheme.colors.surface,
        elevation = 6.dp,
        modifier = Modifier.width(280.dp).fillMaxHeight(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 10.dp),
            ) {
                Text("Contents", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    "✕",
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.clickable { state.drawerOpen = false }.padding(6.dp),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                OutlinedButton(
                    onClick = { state.startGrouping() },
                    enabled = state.selection.isNotEmpty(),
                ) { Text("Group (${state.selection.size})") }
                OutlinedButton(
                    onClick = { state.ungroupItems(state.selection) },
                    enabled = state.selection.any { id -> state.item(id)?.groups?.isNotEmpty() == true },
                ) { Text("Ungroup") }
            }

            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
                state.sortedGroups.forEach { group ->
                    val items = state.itemsIn(group.id)
                    item(key = "g-${group.id}") { DrawerGroupRow(state, group, items.size) }
                    if (group.id !in state.drawerCollapsed) {
                        items(items.size, key = { i -> "gi-${group.id}-${items[i].id}" }) { i ->
                            DrawerItemRow(state, thumbs, items[i], indented = true)
                        }
                    }
                }

                val loose = state.itemsIn(null)
                item(key = "inbox") {
                    Text(
                        "Inbox (${loose.size})",
                        style = MaterialTheme.typography.caption,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.65f),
                        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                items(loose.size, key = { i -> "ii-${loose[i].id}" }) { i ->
                    DrawerItemRow(state, thumbs, loose[i], indented = false)
                }
            }
        }
    }
}

/** A group in the drawer: colour, name, count, and what can be done to it as a whole. */
@Composable
private fun DrawerGroupRow(state: BoardState, group: BoardGroup, count: Int) {
    val accent = Themes.parseColor(state.accentOfGroup(group)) ?: MaterialTheme.colors.secondary
    val collapsed = group.id in state.drawerCollapsed
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (collapsed) "▸" else "▾",
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.clickable { state.toggleDrawerGroup(group.id) }.padding(horizontal = 6.dp),
            )
            Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(6.dp))
            Text(
                "${group.name} ($count)",
                style = MaterialTheme.typography.body2,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clickable { state.revealGroup(group.id) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(start = 28.dp, top = 2.dp)) {
            DrawerAction("Select") { state.selectGroup(group.id) }
            DrawerAction("Draw") { state.drawGroup(group.id) }
            DrawerAction("Rename") { state.openEditor(BoardEditor.RenameGroup(group.id)) }
            DrawerAction("Ungroup") { state.ungroup(group.id) }
        }
    }
}

@Composable
private fun DrawerAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.caption,
        color = MaterialTheme.colors.secondary,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

/** One card in the drawer: a thumbnail (or a glyph) and its name. */
@Composable
private fun DrawerItemRow(state: BoardState, thumbs: ThumbCache, item: BoardItem, indented: Boolean) {
    val selected = item.id in state.selection
    val label = when (item) {
        is ImageItem -> item.caption ?: state.fileOf(item)?.name ?: item.path
        is NoteItem -> NoteText.plain(item.text).lineSequence().firstOrNull().orEmpty().ifBlank { "note" }
        is LinkItem -> item.title.ifBlank { item.url }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indented) 28.dp else 8.dp, end = 6.dp, top = 2.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (selected) MaterialTheme.colors.secondary.copy(alpha = 0.18f) else Color.Transparent,
            )
            .clickable { state.revealItem(item.id) }
            .padding(3.dp),
    ) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x22000000)),
            contentAlignment = Alignment.Center,
        ) {
            when (item) {
                is ImageItem -> {
                    val file = state.fileOf(item)
                    val thumb: ImageBitmap? by produceState<ImageBitmap?>(null, file) {
                        value = file?.let { withContext(Dispatchers.IO) { thumbs.load(it, maxSize = 64) } }
                    }
                    thumb?.let {
                        Image(
                            bitmap = it,
                            contentDescription = label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                is NoteItem -> Text("✎", style = MaterialTheme.typography.caption)
                is LinkItem -> Text("🔗", style = MaterialTheme.typography.caption)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
