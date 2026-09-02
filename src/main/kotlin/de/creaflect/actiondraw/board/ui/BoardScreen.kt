package de.creaflect.actiondraw.board.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.board.BoardEditor
import de.creaflect.actiondraw.board.BoardLayouts
import de.creaflect.actiondraw.board.BoardState
import de.creaflect.actiondraw.board.BoardThemes
import de.creaflect.actiondraw.image.ThumbCache
import de.creaflect.actiondraw.ui.SelectChip
import de.creaflect.actiondraw.ui.chooseImages
import java.io.File
import java.net.URI

/**
 * The Idea Board: grouped grid of image and note cards on a cork/papyrus/plain surface.
 * Fullscreen ("immersive") hides all chrome; the grid itself keeps working.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun BoardScreen(state: BoardState, thumbs: ThumbCache, isFullscreen: Boolean, setFullscreen: (Boolean) -> Unit) {
    val board = state.board ?: return
    val textured = Themes.isTextured(board.theme)
    val colors = if (textured) Themes.paperColors else MaterialTheme.colors

    // Chrome disappears only in explicit immersive mode; leaving fullscreen by any means
    // (Esc, OS controls) always brings every menu back.
    LaunchedEffect(isFullscreen) { if (!isFullscreen) state.immersive = false }
    val hideChrome = state.immersive && isFullscreen

    // Explorer drops land on the board as imported cards (Inbox).
    val dropTarget = remember(state) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val data = event.dragData()
                if (data !is DragData.FilesList) return false
                val files = data.readFiles().mapNotNull { runCatching { File(URI(it)) }.getOrNull() }
                if (files.isEmpty()) return false
                state.importExternal(files)
                return true
            }
        }
    }

    MaterialTheme(colors = colors) {
        val tile = remember(board.theme) { Themes.tile(board.theme) }
        val background =
            if (tile != null) Modifier.background(ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated)))
            else Modifier.background(colors.background)

        Box(
            Modifier
                .fillMaxSize()
                .then(background)
                .dragAndDropTarget(shouldStartDragAndDrop = { true }, target = dropTarget),
        ) {
            Column(Modifier.fillMaxSize()) {
                if (!hideChrome) {
                    BoardHeader(state, board.name, board.theme, onImmersive = {
                        state.immersive = true
                        setFullscreen(true)
                    })
                    if (state.openedFromBackup) {
                        Text(
                            "Board file was unreadable — restored from its backup.",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.error,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    if (state.allTags.isNotEmpty()) TagFilterBar(state)
                }
                if (state.layout == BoardLayouts.FREE) {
                    BoardCanvas(state, thumbs, textured, Modifier.weight(1f).fillMaxWidth())
                } else {
                    BoardGrid(state, thumbs, textured, Modifier.weight(1f).fillMaxWidth())
                }
                if (!hideChrome) BoardActionBar(state)
            }
            if (state.viewerOpen) BoardViewer(state, thumbs)
        }
    }
}

@Composable
private fun BoardHeader(state: BoardState, name: String, theme: String, onImmersive: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
    ) {
        Text(name, style = MaterialTheme.typography.h5, color = MaterialTheme.colors.primary)
        Spacer(Modifier.weight(1f))
        SelectChip("Grid", state.layout == BoardLayouts.GRID) { state.setLayout(BoardLayouts.GRID) }
        SelectChip("Free", state.layout == BoardLayouts.FREE) { state.setLayout(BoardLayouts.FREE) }
        Spacer(Modifier.width(8.dp))
        BoardThemes.ALL.forEach { id ->
            SelectChip(id.replaceFirstChar { it.uppercase() }, theme == id) { state.setTheme(id) }
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onImmersive) { Text("⛶ Immersive") }
        OutlinedButton(onClick = { state.closeBoard() }) { Text("Close") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagFilterBar(state: BoardState) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        state.allTags.forEach { tag ->
            SelectChip("#$tag (${state.tagCount(tag)})", tag in state.filterTags) { state.toggleFilterTag(tag) }
        }
        if (state.filterTags.isNotEmpty()) {
            OutlinedButton(onClick = { state.clearFilter() }) { Text("Clear filter") }
        }
    }
}

@Composable
private fun BoardGrid(state: BoardState, thumbs: ThumbCache, textured: Boolean, modifier: Modifier) {
    val empty = state.board?.items.orEmpty().isEmpty()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        modifier = modifier,
    ) {
        if (empty) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "An empty board. Drop pictures from Explorer, paste one (Ctrl+V — a copied " +
                        "web image works too), or use Import…",
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                )
            }
        }
        state.sections.forEach { (group, itemsInGroup) ->
            if (group != null || itemsInGroup.isNotEmpty()) {
                item(key = "header-${group?.id ?: "inbox"}", span = { GridItemSpan(maxLineSpan) }) {
                    GroupHeader(state, group, itemsInGroup.size)
                }
            }
            if (group?.collapsed != true) {
                items(itemsInGroup, key = { "${group?.id ?: "inbox"}/${it.id}" }) { item ->
                    BoardCard(state, thumbs, item, textured, groupId = group?.id)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardActionBar(state: BoardState) {
    Surface(elevation = 8.dp) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val drawable = state.selectedImageFiles.size
                Button(onClick = { state.drawSelection() }, enabled = drawable > 0) {
                    Text("Draw selection ($drawable)")
                }
                val viewable = state.viewableIds.size
                OutlinedButton(onClick = { state.openViewer() }, enabled = viewable > 0) {
                    Text(if (state.selection.isEmpty()) "View all ($viewable)" else "View ($viewable)")
                }
                OutlinedButton(onClick = { state.openEditor(BoardEditor.NewGroup) }) { Text("New group") }
                OutlinedButton(onClick = { state.openEditor(BoardEditor.EditNote(null)) }) { Text("New note") }
                OutlinedButton(onClick = {
                    chooseImages(state.root).takeIf { it.isNotEmpty() }?.let { state.importExternal(it) }
                }) { Text("Import…") }
                OutlinedButton(onClick = { state.importPasted() }) { Text("Paste") }
                OutlinedButton(onClick = { state.copySelection() }, enabled = state.selection.isNotEmpty()) {
                    Text("Copy")
                }
                if (state.selection.isNotEmpty()) {
                    var moveOpen by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { moveOpen = true }) { Text("Move to ▾") }
                        DropdownMenu(expanded = moveOpen, onDismissRequest = { moveOpen = false }) {
                            DropdownMenuItem(onClick = {
                                state.moveToGroup(state.selection, null)
                                moveOpen = false
                            }) { Text("Inbox") }
                            state.sortedGroups.forEach { group ->
                                DropdownMenuItem(onClick = {
                                    state.moveToGroup(state.selection, group.id)
                                    moveOpen = false
                                }) { Text(group.name) }
                            }
                        }
                    }
                }
            }
            Text(
                "Click select · Ctrl/Shift multi · right-click menu · Ctrl+C/V copy/paste · Ctrl+↑/↓ reorder " +
                    "(+Shift: all the way) · Space view large · Enter draw · N note · G group · S star · T tags · " +
                    "F2 caption · Del remove · F immersive",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}
