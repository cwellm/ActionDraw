package de.creaflect.actiondraw.board.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.board.BoardEditor
import de.creaflect.actiondraw.board.BoardItem
import de.creaflect.actiondraw.board.ImageItem
import de.creaflect.actiondraw.board.BoardLayouts
import de.creaflect.actiondraw.board.BoardState
import de.creaflect.actiondraw.board.BoardThemes
import de.creaflect.actiondraw.board.ContactSheet
import de.creaflect.actiondraw.board.SessionRecipe
import de.creaflect.actiondraw.image.ThumbCache
import de.creaflect.actiondraw.ui.SelectChip
import de.creaflect.actiondraw.ui.formatTime
import de.creaflect.actiondraw.ui.chooseImages
import de.creaflect.actiondraw.ui.chooseSaveFile
import java.io.File
import java.net.URI
import androidx.compose.material.Divider
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.testTag
import de.creaflect.actiondraw.samePathAs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import de.creaflect.actiondraw.board.WallpaperFit
import de.creaflect.actiondraw.image.ImageLoader
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.material.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape

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
            // The wallpaper sits on the theme's texture and under everything else.
            WallpaperLayer(state)
            Row(Modifier.fillMaxSize()) {
              if (state.drawerOpen && !hideChrome) BoardDrawer(state, thumbs)
              Column(Modifier.weight(1f).fillMaxHeight()) {
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
                    state.importNotice?.let { notice ->
                        Text(
                            "$notice  (click to dismiss)",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.error,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 2.dp)
                                .clickable { state.dismissImportNotice() },
                        )
                    }
                    if (state.allTags.isNotEmpty()) FilterBar(state)
                }
                if (state.layout == BoardLayouts.FREE) {
                    BoardCanvas(state, thumbs, textured, Modifier.weight(1f).fillMaxWidth())
                } else {
                    BoardGrid(state, thumbs, textured, Modifier.weight(1f).fillMaxWidth())
                }
                // The empty board is just the board: the action bar exists for a selection.
                if (!hideChrome && state.selection.isNotEmpty()) BoardActionBar(state)
              }
            }
            if (state.viewerOpen) BoardViewer(state, thumbs)
        }
    }
}

/**
 * One line, most of the time: the board's name and where it sits, the layout, search, the
 * contents drawer, a menu for adding things, and an overflow for everything used once a session.
 * The header used to be a settings page sitting on top of the board; now it is a toolbar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardHeader(state: BoardState, name: String, theme: String, onImmersive: () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 2.dp),
    ) {
        Text(
            name,
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .align(Alignment.CenterVertically)
                .clickable { state.openEditor(BoardEditor.RenameBoard) }
                .padding(end = 4.dp),
        )
        BoardSwitcher(state)
        state.parentBoard?.let { parent ->
            FlatButton("↑ " + parent.name, Modifier.testTag("board-up")) { state.openBoard(parent.dir) }
        }
        Segmented(
            options = listOf(BoardLayouts.GRID to "Grid", BoardLayouts.FREE to "Free"),
            selected = state.layout,
            tag = "layout",
        ) { state.setLayout(it) }
        OutlinedTextField(
            value = state.query,
            onValueChange = state::search,
            singleLine = true,
            placeholder = { Text("Search", style = MaterialTheme.typography.caption) },
            textStyle = MaterialTheme.typography.body2,
            modifier = Modifier.width(200.dp).height(44.dp).testTag("board-search"),
        )
        FlatButton(if (state.drawerOpen) "Contents ✕" else "Contents") { state.drawerOpen = !state.drawerOpen }
        AddMenu(state)
        MoreMenu(state, theme, onImmersive)
    }
}

/** Text-style button: the header's default weight, so the board stays the loudest thing. */
@Composable
private fun FlatButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One control with the options side by side, the chosen one filled: Grid | Free. */
@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, tag: String, onPick: (T) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
            .testTag(tag),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Text(
                label,
                style = MaterialTheme.typography.body2,
                color = if (on) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface,
                modifier = Modifier
                    .background(if (on) MaterialTheme.colors.primary else Color.Transparent)
                    .clickable { onPick(value) }
                    .testTag("$tag-$label")
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

/** "+ ▾": the ways material gets onto the board. */
@Composable
private fun AddMenu(state: BoardState) {
    var open by remember { mutableStateOf(false) }
    Box {
        FlatButton("+ ▾", Modifier.testTag("board-add")) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(onClick = { open = false; state.openEditor(BoardEditor.EditNote(null)) }) { Text("New note") }
            DropdownMenuItem(onClick = { open = false; state.openEditor(BoardEditor.EditLink(null)) }) { Text("New link") }
            DropdownMenuItem(onClick = { open = false; state.startGrouping() }) {
                Text(if (state.selection.isEmpty()) "New group" else "Group the selection (${state.selection.size})")
            }
            Divider()
            DropdownMenuItem(onClick = {
                open = false
                chooseImages(state.root).takeIf { it.isNotEmpty() }?.let { state.importExternal(it) }
            }) { Text("Import pictures…") }
            DropdownMenuItem(onClick = { open = false; state.importPasted() }) { Text("Paste") }
        }
    }
}

/** "⋯": what is used once a session, out of the way until then. */
@Composable
private fun MoreMenu(state: BoardState, theme: String, onImmersive: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FlatButton("⋯", Modifier.testTag("board-more")) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.testTag("board-more-menu")) {
            Text(
                "Theme",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            BoardThemes.ALL.forEach { id ->
                DropdownMenuItem(onClick = { open = false; state.setTheme(id) }) {
                    Text((if (theme == id) "• " else "   ") + id.replaceFirstChar { it.uppercase() })
                }
            }
            Divider()
            if (state.layout == BoardLayouts.FREE) {
                DropdownMenuItem(onClick = { open = false; state.setSnappingPreference(!state.snapping) }, modifier = Modifier.testTag("snap-toggle")) {
                    Text((if (state.snapping) "• " else "   ") + "Snap to neighbours")
                }
            }
            DropdownMenuItem(onClick = { open = false; if (state.stripOpen) state.closeStrip() else state.openStrip() }) {
                Text(if (state.stripOpen) "Close float strip" else "Float strip")
            }
            DropdownMenuItem(onClick = { open = false; state.openEditor(BoardEditor.Wallpaper) }) { Text("Wallpaper…") }
            DropdownMenuItem(onClick = {
                open = false
                val items = state.sheetItems
                chooseSaveFile(
                    suggested = ContactSheet.suggestedName(state.board?.name ?: "board"),
                    start = state.root,
                )?.let { state.exportContactSheet(items, it) }
            }) { Text("Contact sheet…") }
            DropdownMenuItem(onClick = { open = false; state.openEditor(BoardEditor.EditSession) }) {
                Text(state.recipe?.let { "Session: ${recipeSummary(it)}" } ?: "Session…")
            }
            Divider()
            DropdownMenuItem(onClick = { open = false; state.openEditor(BoardEditor.Settings) }, modifier = Modifier.testTag("board-settings")) {
                Text("Settings…")
            }
            DropdownMenuItem(onClick = { open = false; state.openEditor(BoardEditor.Hotkeys) }, modifier = Modifier.testTag("board-hotkeys")) {
                Text("Hotkeys…")
            }
            DropdownMenuItem(onClick = { open = false; onImmersive() }) { Text("Immersive") }
            DropdownMenuItem(onClick = { open = false; state.closeBoard() }, modifier = Modifier.testTag("board-close")) {
                Text("Close board")
            }
        }
    }
}

/**
 * The board's background picture, if it has one: fitted as asked, softened and dimmed as asked,
 * and in free mode drifting at a third of the camera's pace so the board feels like a surface
 * the cards lie on rather than a photograph they float over. Drawn a little larger than the view
 * so the drift never shows an edge.
 */
@Composable
private fun WallpaperLayer(state: BoardState) {
    val paper = state.wallpaper ?: return
    val file = state.wallpaperFile ?: return
    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { runCatching { ImageLoader.load(file) }.getOrNull() }
    }
    val bmp = bitmap ?: return
    val free = state.layout == BoardLayouts.FREE
    val blurDp = (paper.blur * 24f).dp
    Box(Modifier.fillMaxSize().clipToBounds().testTag("wallpaper")) {
        val drift = Modifier.graphicsLayer {
            if (free) {
                translationX = -state.camX * state.zoom * 0.3f
                translationY = -state.camY * state.zoom * 0.3f
                scaleX = 1.3f
                scaleY = 1.3f
            }
        }
        when (paper.fit) {
            WallpaperFit.TILE -> Box(
                Modifier
                    .fillMaxSize()
                    .then(drift)
                    .blur(blurDp)
                    .background(ShaderBrush(ImageShader(bmp, TileMode.Repeated, TileMode.Repeated))),
            )
            WallpaperFit.CENTER -> Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.None,
                modifier = Modifier.fillMaxSize().then(drift).blur(blurDp),
            )
            else -> Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(drift).blur(blurDp),
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = paper.dim)))
    }
}

/**
 * Quick browse: every board, nested under the one it belongs to, without going back to the list.
 * Boards are a tree once they can sit inside each other, and the whole point of the tree is being
 * able to move around it from where you already are.
 */
@Composable
private fun BoardSwitcher(state: BoardState) {
    var open by remember { mutableStateOf(false) }
    val tree by produceState(emptyList<BoardState.BoardNode>(), open, state.boardsHomeTick, state.root) {
        if (open) value = withContext(Dispatchers.IO) { state.boardTree() }
    }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.testTag("board-switcher")) {
            Text("Boards ▾")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (tree.isEmpty()) {
                DropdownMenuItem(onClick = { open = false }) { Text("Looking…") }
            }
            tree.forEach { node ->
                val here = state.root?.samePathAs(node.dir) == true
                DropdownMenuItem(
                    onClick = {
                        open = false
                        if (!here) state.openBoard(node.dir)
                    },
                    modifier = Modifier.testTag("switch-to-" + node.name),
                ) {
                    Text(
                        "      ".repeat(node.depth) + (if (here) "• " else "") + node.name,
                        color = if (here) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Divider()
            DropdownMenuItem(onClick = {
                open = false
                state.openEditor(BoardEditor.NewBoard(state.root))
            }) {
                Text("New sub-board here…", color = MaterialTheme.colors.secondary)
            }
            state.root?.let { here ->
                DropdownMenuItem(onClick = {
                    open = false
                    state.openEditor(BoardEditor.MoveBoard(here, state.board?.name ?: here.name))
                }) {
                    Text("Move this board…", color = MaterialTheme.colors.secondary)
                }
            }
            DropdownMenuItem(onClick = { open = false; state.openBoardList() }) { Text("All boards…") }
        }
    }
}

/** Free-text search plus the tag chips — everything that narrows what the board shows. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterBar(state: BoardState) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
    ) {
        state.allTags.forEach { tag ->
            FlatChip("#$tag (${state.tagCount(tag)})", tag in state.filterTags) { state.toggleFilterTag(tag) }
        }
        if (state.filterTags.isNotEmpty()) {
            FlatButton("Clear filter") { state.clearFilter() }
        }
    }
}

/** A chip without an outline: quiet until chosen, then filled. */
@Composable
private fun FlatChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.caption,
        color = if (selected) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

@Composable
private fun BoardGrid(state: BoardState, thumbs: ThumbCache, textured: Boolean, modifier: Modifier) {
    val empty = state.board?.items.orEmpty().isEmpty()
    val gridState = rememberLazyGridState()
    val reorder = remember(state, gridState) { GridReorder(gridState, state) }
    LazyVerticalGrid(
        state = gridState,
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
        state.smartSections.forEach { (title, smartItems) ->
            item(key = "smart-$title", span = { GridItemSpan(maxLineSpan) }) {
                SmartHeader(state, title, smartItems)
            }
            items(smartItems, key = { "smart-$title/${it.id}" }) { item ->
                BoardCard(state, thumbs, item, textured, groupId = null)
            }
        }
        state.sections.forEach { (group, itemsInGroup) ->
            // A subgroup's header hides with its parent's cards when the parent is collapsed.
            if ((group != null || itemsInGroup.isNotEmpty()) && state.groupById(group?.parentId)?.collapsed != true) {
                val headerKey = GridReorder.headerKey(group?.id ?: "inbox")
                item(key = headerKey, span = { GridItemSpan(maxLineSpan) }) {
                    GroupHeader(state, group, itemsInGroup.size, dropTarget = reorder.targetKey == headerKey)
                }
            }
            if (!state.isFolded(group)) {
                val section = group?.id ?: "inbox"
                items(itemsInGroup, key = { GridReorder.cellKey(section, it.id) }) { item ->
                    BoardCard(
                        state, thumbs, item, textured,
                        groupId = group?.id,
                        reorder = reorder,
                        cellKey = GridReorder.cellKey(section, item.id),
                    )
                }
            }
        }
    }
}

/** What can be done with the selection — shown only while there is one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardActionBar(state: BoardState) {
    Surface(elevation = 8.dp, modifier = Modifier.testTag("action-bar")) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            val drawable = state.selectedImageFiles.size
            Button(onClick = { state.drawSelection() }, enabled = drawable > 0) { Text("Draw ($drawable)") }
            val viewable = state.viewableIds.size
            FlatButton("View ($viewable)") { state.openViewer() }
            FlatButton("Group (${state.selection.size})") { state.startGrouping() }
            if (state.selection.any { id -> state.item(id)?.groups?.isNotEmpty() == true }) {
                FlatButton("Ungroup") { state.ungroupItems(state.selection) }
            }
            if (state.selection.any { state.item(it) is ImageItem }) {
                FlatButton("Palette") { state.openEditor(BoardEditor.ShowPalette(state.selection)) }
            }
            FlatButton("Copy") { state.copySelection() }
            var moveOpen by remember { mutableStateOf(false) }
            Box {
                FlatButton("Move to ▾") { moveOpen = true }
                DropdownMenu(expanded = moveOpen, onDismissRequest = { moveOpen = false }) {
                    DropdownMenuItem(onClick = { state.moveToGroup(state.selection, null); moveOpen = false }) { Text("Inbox") }
                    state.sortedGroups.forEach { group ->
                        DropdownMenuItem(onClick = { state.moveToGroup(state.selection, group.id); moveOpen = false }) {
                            Text((if (group.parentId != null) "    " else "") + group.name)
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            FlatButton("Deselect") { state.clearSelection() }
        }
    }
}

/** Header of a rule-based section: what to redo, what has never been drawn. */
@Composable
private fun SmartHeader(state: BoardState, title: String, items: List<BoardItem>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp),
    ) {
        Text(
            "$title (${items.size})",
            style = MaterialTheme.typography.subtitle1,
            color = MaterialTheme.colors.secondary,
        )
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = { state.drawItems(items) }) { Text("Draw ${items.size}") }
    }
}

/** One-line form of a board's session recipe, for the action-bar button. */
private fun recipeSummary(recipe: SessionRecipe): String {
    val timing = recipe.plan ?: formatTime(recipe.intervalSeconds)
    val view = recipe.viewMode.lowercase().replaceFirstChar { it.uppercase() }
    return if (recipe.viewMode == "NONE") timing else "$timing · $view"
}
