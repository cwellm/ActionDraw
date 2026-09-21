package de.creaflect.actiondraw.board.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.SessionPlans
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.board.BoardEditor
import de.creaflect.actiondraw.board.BoardState
import de.creaflect.actiondraw.board.BoardTemplate
import de.creaflect.actiondraw.board.ImageItem
import de.creaflect.actiondraw.board.LinkItem
import de.creaflect.actiondraw.board.NoteColors
import de.creaflect.actiondraw.board.NoteItem
import de.creaflect.actiondraw.board.Palette
import de.creaflect.actiondraw.board.SessionRecipe
import de.creaflect.actiondraw.ui.IntervalSelector
import de.creaflect.actiondraw.ui.SelectChip
import de.creaflect.actiondraw.ui.chooseFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.testTag
import de.creaflect.actiondraw.samePathAs
import androidx.compose.material.Slider
import de.creaflect.actiondraw.board.WallpaperFit
import de.creaflect.actiondraw.ui.chooseImages
import androidx.compose.ui.input.key.key
import de.creaflect.actiondraw.board.NoteKind
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import de.creaflect.actiondraw.ui.confirmOnEnter
import de.creaflect.actiondraw.ui.focusOnShow

/** Renders whichever board dialog is open — mounted once at app level, above every screen. */
@Composable
fun BoardDialogs(state: BoardState) {
    when (val editor = state.editor) {
        null -> {}

        is BoardEditor.NewBoard -> NewBoardDialog(state, editor.under)

        is BoardEditor.DeleteBoard -> DeleteBoardDialog(state, editor)

        BoardEditor.EditSession -> SessionRecipeDialog(state)

        is BoardEditor.GroupSelection -> GroupPlacementDialog(
            state = state,
            title = "Group ${state.selection.size} selected card(s)",
            confirm = "Group",
            initialParent = editor.parentId,
            onOk = { name, parent -> state.groupSelection(name, parent); state.closeEditor() },
        )

        is BoardEditor.NewGroup -> GroupPlacementDialog(
            state = state,
            title = "New group",
            confirm = "Create",
            initialParent = editor.parentId,
            onOk = { name, parent -> state.addGroup(name, parent); state.closeEditor() },
        )

        is BoardEditor.RenameGroup -> TextPromptDialog(
            title = "Rename group",
            initial = state.sortedGroups.find { it.id == editor.groupId }?.name ?: "",
            confirm = "Rename",
            onOk = { state.renameGroup(editor.groupId, it); state.closeEditor() },
            onCancel = state::closeEditor,
        )

        is BoardEditor.EditNote -> NoteDialog(state, editor.itemId, editor.kind)

        is BoardEditor.ShowNote -> ShowNoteDialog(state, editor.itemId)

        is BoardEditor.EditLink -> LinkDialog(state, editor.itemId)

        is BoardEditor.FetchPreview -> FetchPreviewDialog(state, editor.itemId)

        is BoardEditor.MoveBoard -> MoveBoardDialog(state, editor.dir, editor.name)

        BoardEditor.Wallpaper -> WallpaperDialog(state)

        BoardEditor.Hotkeys -> DialogScrim(onDismiss = state::closeEditor) {
            HotkeysSheet(sections = Hotkeys.SECTIONS.filter { it.first == Hotkeys.BOARD_TITLE }, onClose = state::closeEditor)
        }

        BoardEditor.Settings -> DialogScrim(onDismiss = state::closeEditor) {
            SettingsSheet(app = null, boards = state, onClose = state::closeEditor)
        }

        BoardEditor.RenameBoard -> TextPromptDialog(
            title = "Rename board",
            initial = state.board?.name ?: "",
            confirm = "Rename",
            onOk = { state.renameBoard(it); state.closeEditor() },
            onCancel = state::closeEditor,
        )

        is BoardEditor.ShowPalette -> PaletteDialog(state, editor.itemIds)

        is BoardEditor.EditCaption -> TextPromptDialog(
            title = "Caption",
            initial = (state.item(editor.itemId) as? ImageItem)?.caption ?: "",
            confirm = "Save",
            onOk = { state.setCaption(editor.itemId, it); state.closeEditor() },
            onCancel = state::closeEditor,
        )

        is BoardEditor.EditTags -> {
            val before = remember(editor) { state.commonTags(editor.itemIds) }
            TextPromptDialog(
                title = "Tags (comma-separated)",
                initial = before.sorted().joinToString(", "),
                confirm = "Apply",
                onOk = { text ->
                    state.applyTags(editor.itemIds, before, parseTags(text))
                    state.closeEditor()
                },
                onCancel = state::closeEditor,
            )
        }
    }
}

fun parseTags(text: String): Set<String> =
    text.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

/**
 * How this board wants to be drawn. Saved in the sidecar, so "Drachenbuch is always 60 s in
 * Notan" survives restarts and is applied to every session started from the board.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionRecipeDialog(state: BoardState) {
    val stored = state.recipe
    var plan by remember { mutableStateOf(stored?.plan) }
    var seconds by remember { mutableStateOf(stored?.intervalSeconds ?: 120) }
    var auto by remember { mutableStateOf(stored?.autoAdvance ?: true) }
    var view by remember { mutableStateOf(stored?.viewMode ?: ViewMode.NONE.name) }
    var grid by remember { mutableStateOf(stored?.grid ?: GridMode.OFF.name) }

    DialogScrim(onDismiss = state::closeEditor) {
        Text("Session for this board", style = MaterialTheme.typography.h6)
        Text(
            "Used whenever you draw from this board. Without one, the menu's settings apply.",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SelectChip("Fixed time", plan == null) { plan = null }
            SessionPlans.ALL.forEach { p -> SelectChip(p.name, plan == p.name) { plan = p.name } }
        }
        if (plan == null) IntervalSelector(seconds = seconds, onChange = { seconds = it })

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = auto, onCheckedChange = { auto = it })
            Text("Auto-advance", style = MaterialTheme.typography.body2)
        }

        Text("View", style = MaterialTheme.typography.overline)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ViewMode.entries.forEach { mode ->
                SelectChip(mode.label(), view == mode.name) { view = mode.name }
            }
        }

        Text("Grid", style = MaterialTheme.typography.overline)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GridMode.entries.forEach { mode ->
                SelectChip(mode.name.lowercase().replaceFirstChar { it.uppercase() }, grid == mode.name) {
                    grid = mode.name
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(onClick = { state.saveRecipe(null); state.closeEditor() }) { Text("Forget") }
            OutlinedButton(onClick = { state.rememberCurrentSetup(); state.closeEditor() }) {
                Text("Use current")
            }
            Button(onClick = {
                state.saveRecipe(SessionRecipe(plan, seconds, auto, view, grid))
                state.closeEditor()
            }) { Text("Save") }
        }
    }
}

/** Short label for a view mode chip — the enum names read badly in a row of chips. */
private fun ViewMode.label(): String = when (this) {
    ViewMode.NONE -> "None"
    ViewMode.GRAYSCALE -> "B&W"
    else -> name.lowercase().replaceFirstChar { it.uppercase() }
}

/** New or edited note: the text, its paper colour, and whether it reads as a heading. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteDialog(state: BoardState, itemId: String?, initialKind: String) {
    val existing = itemId?.let(state::item) as? NoteItem
    var text by remember(itemId) { mutableStateOf(existing?.text ?: "") }
    var kind by remember(itemId) { mutableStateOf(existing?.kind ?: initialKind) }
    DialogScrim(onDismiss = state::closeEditor) {
        Text(
            when {
                itemId != null -> "Edit note"
                kind == NoteKind.POSTIT -> "New post-it"
                else -> "New document note"
            },
            style = MaterialTheme.typography.h6,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SelectChip("Document — a title on the board, the text in a popup", kind == NoteKind.DOCUMENT) { kind = NoteKind.DOCUMENT }
            SelectChip("Post-it — all of it, as typed", kind == NoteKind.POSTIT) { kind = NoteKind.POSTIT }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().height(150.dp).focusOnShow(),
        )
        Text(
            if (kind == NoteKind.POSTIT) "Shown exactly as typed, in a written hand."
            else "# heading · **bold** · *italic* · `code` · [text](url) · - list · 1. list · --- ; " +
                "the first heading (or line) is the title on the board.",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
        )
        if (kind == NoteKind.DOCUMENT && Markdown.hasMarkup(text)) {
            // Only when there is markup to show: a plain note previewing itself is noise.
            Markdown.Rendered(
                text,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface,
                onLink = state::openUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
            )
        }
        if (existing != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Paper", style = MaterialTheme.typography.caption)
                NoteColors.ALL.forEach { color ->
                    val swatch = Themes.parseColor(color) ?: MaterialTheme.colors.surface
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .border(
                                2.dp,
                                if (existing.color == color) MaterialTheme.colors.primary else Color.Transparent,
                                CircleShape,
                            )
                            .clickable { state.setNoteColor(existing.id, color) },
                    )
                }
                Spacer(Modifier.width(8.dp))
                SelectChip("Heading", existing.heading) { state.toggleNoteHeading(existing.id) }
            }
        }
        DialogButtons(
            confirm = "Save",
            onOk = {
                state.saveNote(itemId, text, kind)
                if (itemId != null) state.setNoteKind(itemId, kind)
                state.closeEditor()
            },
            onCancel = state::closeEditor,
        )
    }
}

/** A document note opened to read: the whole text, rendered, with a way into editing it. */
@Composable
private fun ShowNoteDialog(state: BoardState, itemId: String) {
    val note = state.item(itemId) as? NoteItem
    DialogScrim(onDismiss = state::closeEditor) {
        Text(note?.title ?: "Note", style = MaterialTheme.typography.h6)
        Markdown.Rendered(
            note?.text.orEmpty(),
            style = MaterialTheme.typography.body1,
            color = MaterialTheme.colors.onSurface,
            onLink = state::openUrl,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .testTag("note-popup"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { state.openEditor(BoardEditor.EditNote(itemId)) }) { Text("Edit…") }
            Button(onClick = state::closeEditor) { Text("Close") }
        }
    }
}

/** A card that points at a page. The url is stored as typed and opened in the system browser. */
@Composable
private fun LinkDialog(state: BoardState, itemId: String?) {
    val existing = itemId?.let(state::item) as? LinkItem
    var url by remember(itemId) { mutableStateOf(existing?.url ?: "") }
    var title by remember(itemId) { mutableStateOf(existing?.title ?: "") }
    val save = { state.saveLink(itemId, url, title); state.closeEditor() }
    DialogScrim(onDismiss = state::closeEditor) {
        Text(if (itemId == null) "New link" else "Edit link", style = MaterialTheme.typography.h6)
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Address") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusOnShow().confirmOnEnter(save),
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().confirmOnEnter(save),
        )
        Text(
            "ActionDraw never fetches the page — the card just opens it in your browser.",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
        )
        DialogButtons(confirm = "Save", onOk = save, onCancel = state::closeEditor)
    }
}

/** The board's background: pick a picture, say how it fits, dim and soften it, or take it away. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WallpaperDialog(state: BoardState) {
    val paper = state.wallpaper
    var error by remember { mutableStateOf<String?>(null) }
    DialogScrim(onDismiss = state::closeEditor) {
        Text("Wallpaper", style = MaterialTheme.typography.h6)
        Text(
            if (paper == null) "A picture behind the cards. The theme's texture shows through where it does not reach."
            else "Copied into the board's _wallpaper folder, so it moves with the board.",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.75f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                chooseImages(state.root).firstOrNull()?.let { error = state.setWallpaper(it) }
            }) { Text(if (paper == null) "Choose picture…" else "Change picture…") }
            if (paper != null) {
                OutlinedButton(onClick = { state.clearWallpaper() }) { Text("Remove") }
            }
        }
        if (paper != null) {
            Text("Fit", style = MaterialTheme.typography.caption)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WallpaperFit.ALL.forEach { fit ->
                    SelectChip(fit.replaceFirstChar { it.uppercase() }, paper.fit == fit) { state.setWallpaperLook(fit = fit) }
                }
            }
            Text("Dim: ${(paper.dim * 100).toInt()}%", style = MaterialTheme.typography.caption)
            Slider(value = paper.dim, onValueChange = { state.setWallpaperLook(dim = it) }, valueRange = 0f..0.9f)
            Text("Blur: ${(paper.blur * 100).toInt()}%", style = MaterialTheme.typography.caption)
            Slider(value = paper.blur, onValueChange = { state.setWallpaperLook(blur = it) }, valueRange = 0f..1f)
        }
        error?.let { Text(it, color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = state::closeEditor) { Text("Done") }
        }
    }
}

/**
 * Where a board should sit in the tree. Nesting is where the folder is, so choosing here moves
 * the folder — with its pictures and any boards inside it. Nothing is lost either way, which is
 * why picking a destination is confirmation enough.
 */
@Composable
private fun MoveBoardDialog(state: BoardState, dir: File, name: String) {
    val targets by produceState(emptyList<BoardState.BoardNode>(), dir) {
        value = withContext(Dispatchers.IO) { state.moveTargets(dir) }
    }
    val home = state.boardsHome()
    val currentParent = dir.absoluteFile.parentFile
    val scope = rememberCoroutineScope()

    fun moveTo(target: File?) {
        scope.launch {
            withContext(Dispatchers.IO) { state.moveBoard(dir, target) }
            state.closeEditor()
        }
    }

    DialogScrim(onDismiss = state::closeEditor) {
        Text("Move \"$name\"", style = MaterialTheme.typography.h6)
        Text(
            "Its folder moves too, along with everything in it — pictures and any boards nested " +
                "inside. You can move it again at any time.",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.75f),
        )
        Column(Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
            MoveTarget(
                label = "Top level",
                depth = 0,
                here = currentParent?.samePathAs(home) == true,
                tag = "move-to-top",
            ) { moveTo(null) }
            targets.forEach { node ->
                MoveTarget(
                    label = node.name,
                    depth = node.depth + 1,
                    here = currentParent?.samePathAs(node.dir) == true,
                    tag = "move-to-" + node.name,
                ) { moveTo(node.dir) }
            }
        }
        // No confirm button: picking a destination is the action.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = state::closeEditor) { Text("Cancel") }
        }
    }
}

/** One destination row; the board's current home is shown but cannot be chosen again. */
@Composable
private fun MoveTarget(label: String, depth: Int, here: Boolean, tag: String, onPick: () -> Unit) {
    Text(
        "    ".repeat(depth) + label + if (here) "   (where it is now)" else "",
        style = MaterialTheme.typography.body2,
        color = if (here) MaterialTheme.colors.onSurface.copy(alpha = 0.4f) else MaterialTheme.colors.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .then(if (here) Modifier else Modifier.testTag(tag).clickable { onPick() })
            .padding(horizontal = 8.dp, vertical = 7.dp),
    )
}

/**
 * Fetching a preview is the only thing in ActionDraw that leaves the machine, so it asks first
 * and says plainly what that means.
 */
@Composable
private fun FetchPreviewDialog(state: BoardState, itemId: String) {
    val link = state.item(itemId) as? LinkItem
    val scope = rememberCoroutineScope()
    var busy by remember(itemId) { mutableStateOf(false) }
    DialogScrim(onDismiss = state::closeEditor) {
        Text("Fetch preview", style = MaterialTheme.typography.h6)
        Text(
            link?.url.orEmpty(),
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "ActionDraw will contact this address once and save the picture it advertises into " +
                "the board folder. The site learns that you opened the link; nothing else is sent, " +
                "and the board stays offline afterwards.",
            style = MaterialTheme.typography.body2,
        )
        if (busy) Text("Fetching…", style = MaterialTheme.typography.caption)
        DialogButtons(
            confirm = "Fetch",
            onOk = {
                if (!busy) {
                    busy = true
                    scope.launch {
                        withContext(Dispatchers.IO) { state.fetchLinkPreview(itemId) }
                        busy = false
                        state.closeEditor()
                    }
                }
            },
            onCancel = state::closeEditor,
        )
    }
}

/** The colours a picture is made of, as swatches with their hex values. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaletteDialog(state: BoardState, ids: Set<String>) {
    val palettes by produceState(initialValue = emptyList<Pair<ImageItem, List<Int>>>(), ids) {
        value = withContext(Dispatchers.IO) { state.palettesOf(ids) }
    }
    DialogScrim(onDismiss = state::closeEditor) {
        Text("Palette", style = MaterialTheme.typography.h6)
        if (palettes.isEmpty()) {
            Text("Reading the colours…", style = MaterialTheme.typography.body2)
        }
        palettes.forEach { (item, colors) ->
            Text(
                item.caption ?: item.path,
                style = MaterialTheme.typography.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                colors.forEach { color ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF000000L.toInt() or color)),
                        )
                        Text(Palette.hex(color), style = MaterialTheme.typography.caption)
                    }
                }
            }
        }
        DialogButtons(confirm = "Done", onOk = state::closeEditor, onCancel = state::closeEditor)
    }
}

/**
 * Deleting a board asks what "delete" should mean. Removing the board file leaves every picture
 * where it is; deleting the folder does not, so that is a separate, deliberate tick.
 */
@Composable
private fun DeleteBoardDialog(state: BoardState, editor: BoardEditor.DeleteBoard) {
    // A folder ActionDraw made for this board goes with it; a folder that was already yours does
    // not. Either way the tick is there, so the default is a starting point and not a decision
    // taken for you.
    var alsoFolder by remember(editor) { mutableStateOf(editor.ownsFolder) }
    DialogScrim(onDismiss = state::closeEditor) {
        Text("Delete \"${editor.name}\"?", style = MaterialTheme.typography.h6)
        Text(
            editor.dir.path,
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
        )
        Text(
            if (alsoFolder) {
                "The folder and everything in it is deleted, including " +
                    "${editor.pictures} picture(s)" +
                    (if (editor.subBoards > 0) " and ${editor.subBoards} board(s) nested inside it" else "") +
                    ". This cannot be undone."
            } else if (editor.ownsFolder) {
                "The board is removed, but the folder ActionDraw made for it stays behind with " +
                    "its ${editor.pictures} picture(s)."
            } else {
                "The board is removed from ActionDraw. The folder and its " +
                    "${editor.pictures} picture(s) stay exactly where they are."
            },
            style = MaterialTheme.typography.body2,
            color = if (alsoFolder) MaterialTheme.colors.error else MaterialTheme.colors.onSurface,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = alsoFolder, onCheckedChange = { alsoFolder = it })
            Text(
                if (editor.ownsFolder) "Delete the folder and its pictures"
                else "Also delete the folder and its pictures (it was not created by ActionDraw)",
                style = MaterialTheme.typography.body2,
            )
        }
        DialogButtons(
            confirm = if (alsoFolder) "Delete everything" else "Remove board",
            onOk = {
                state.deleteBoard(
                    editor.dir,
                    if (alsoFolder) BoardState.Deletion.DELETE_FOLDER else BoardState.Deletion.FORGET,
                )
                state.closeEditor()
            },
            onCancel = state::closeEditor,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewBoardDialog(state: BoardState, under: File?) {
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf(under ?: state.boardsHome()) }
    var error by remember { mutableStateOf<String?>(null) }
    var template by remember { mutableStateOf(BoardTemplate.ALL.first()) }
    DialogScrim(onDismiss = state::closeEditor) {
        Text(if (under == null) "New Idea Board" else "New sub-board", style = MaterialTheme.typography.h6)
        if (under != null) {
            Text(
                "It will live inside ${under.name}, and show under it in the board list.",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
            )
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            // Enter is the Create button: a name is all a new board needs.
            modifier = Modifier
                .fillMaxWidth()
                .focusOnShow()
                .confirmOnEnter { error = state.createBoard(location, name, template) }
                .testTag("board-name"),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                File(location, BoardState.sanitizeName(name.ifBlank { "…" })).path,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { chooseFolder(location.takeIf { it.isDirectory }, "Board location")?.let { location = it } }) {
                Text("Change…")
            }
        }
        Text("Start with", style = MaterialTheme.typography.caption)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BoardTemplate.ALL.forEach { candidate ->
                SelectChip(candidate.name, template == candidate) { template = candidate }
            }
        }
        if (template.groups.isNotEmpty()) {
            Text(
                template.groups.joinToString(" · "),
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.secondary,
            )
        }
        error?.let { Text(it, color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption) }
        DialogButtons(
            confirm = "Create",
            onOk = { error = state.createBoard(location, name, template) }, // success also closes
            onCancel = state::closeEditor,
        )
    }
}

/**
 * A name, and where the group goes: the top level, or inside one of the top-level groups. One
 * level only, so only top-level groups are offered — a subgroup cannot hold subgroups.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupPlacementDialog(
    state: BoardState,
    title: String,
    confirm: String,
    initialParent: String?,
    onOk: (String, String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var parent by remember { mutableStateOf(initialParent) }
    val parents = state.possibleParents(null)
    DialogScrim(onDismiss = state::closeEditor) {
        Text(title, style = MaterialTheme.typography.h6)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().confirmOnEnter { onOk(name, parent) }.testTag("group-name"),
        )
        if (parents.isNotEmpty()) {
            Text("Inside", style = MaterialTheme.typography.caption)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SelectChip("Top level", parent == null) { parent = null }
                parents.forEach { candidate ->
                    SelectChip(candidate.name, parent == candidate.id) { parent = candidate.id }
                }
            }
        }
        DialogButtons(
            confirm = confirm,
            onOk = { onOk(name, parent) },
            onCancel = state::closeEditor,
        )
    }
}

@Composable
private fun TextPromptDialog(
    title: String,
    initial: String,
    confirm: String,
    multiline: Boolean = false,
    onOk: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var value by remember(title, initial) { mutableStateOf(initial) }
    DialogScrim(onDismiss = onCancel) {
        Text(title, style = MaterialTheme.typography.h6)
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = !multiline,
            modifier = Modifier
                .fillMaxWidth()
                .focusOnShow()
                .let { if (multiline) it.height(150.dp) else it.confirmOnEnter { onOk(value) } }
                .testTag("prompt-field"),
        )
        DialogButtons(confirm = confirm, onOk = { onOk(value) }, onCancel = onCancel)
    }
}

@Composable
private fun DialogButtons(confirm: String, onOk: () -> Unit, onCancel: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedButton(onClick = onCancel) { Text("Cancel") }
        Button(onClick = onOk) { Text(confirm) }
    }
}

@Composable
private fun DialogScrim(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            // Pointer-only: a `clickable` here takes focus, and a Space or Enter meant for a note
            // could then close the dialog from under it.
            .pointerInput(onDismiss) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            elevation = 16.dp,
            modifier = Modifier
                .widthIn(min = 380.dp, max = 540.dp)
                // Swallow taps so the dialog body doesn't dismiss itself.
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}
