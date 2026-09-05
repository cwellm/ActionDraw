package de.creaflect.actiondraw.board.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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

/** Renders whichever board dialog is open — mounted once at app level, above every screen. */
@Composable
fun BoardDialogs(state: BoardState) {
    when (val editor = state.editor) {
        null -> {}

        BoardEditor.NewBoard -> NewBoardDialog(state)

        is BoardEditor.DeleteBoard -> DeleteBoardDialog(state, editor)

        BoardEditor.EditSession -> SessionRecipeDialog(state)

        BoardEditor.GroupSelection -> TextPromptDialog(
            title = "Group ${state.selection.size} selected card(s)",
            initial = "",
            confirm = "Group",
            onOk = { state.groupSelection(it); state.closeEditor() },
            onCancel = state::closeEditor,
        )

        BoardEditor.NewGroup -> TextPromptDialog(
            title = "New group",
            initial = "",
            confirm = "Create",
            onOk = { state.addGroup(it); state.closeEditor() },
            onCancel = state::closeEditor,
        )

        is BoardEditor.RenameGroup -> TextPromptDialog(
            title = "Rename group",
            initial = state.sortedGroups.find { it.id == editor.groupId }?.name ?: "",
            confirm = "Rename",
            onOk = { state.renameGroup(editor.groupId, it); state.closeEditor() },
            onCancel = state::closeEditor,
        )

        is BoardEditor.EditNote -> NoteDialog(state, editor.itemId)

        is BoardEditor.EditLink -> LinkDialog(state, editor.itemId)

        is BoardEditor.FetchPreview -> FetchPreviewDialog(state, editor.itemId)

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
private fun NoteDialog(state: BoardState, itemId: String?) {
    val existing = itemId?.let(state::item) as? NoteItem
    var text by remember(itemId) { mutableStateOf(existing?.text ?: "") }
    DialogScrim(onDismiss = state::closeEditor) {
        Text(if (itemId == null) "New note" else "Edit note", style = MaterialTheme.typography.h6)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().height(150.dp),
        )
        Text(
            "**bold** and *italic* work; the note stays plain text in the board file.",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
        )
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
            onOk = { state.saveNote(itemId, text); state.closeEditor() },
            onCancel = state::closeEditor,
        )
    }
}

/** A card that points at a page. The url is stored as typed and opened in the system browser. */
@Composable
private fun LinkDialog(state: BoardState, itemId: String?) {
    val existing = itemId?.let(state::item) as? LinkItem
    var url by remember(itemId) { mutableStateOf(existing?.url ?: "") }
    var title by remember(itemId) { mutableStateOf(existing?.title ?: "") }
    DialogScrim(onDismiss = state::closeEditor) {
        Text(if (itemId == null) "New link" else "Edit link", style = MaterialTheme.typography.h6)
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Address") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "ActionDraw never fetches the page — the card just opens it in your browser.",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
        )
        DialogButtons(
            confirm = "Save",
            onOk = { state.saveLink(itemId, url, title); state.closeEditor() },
            onCancel = state::closeEditor,
        )
    }
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
                    "${editor.pictures} picture(s). This cannot be undone."
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
private fun NewBoardDialog(state: BoardState) {
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf(state.boardsHome()) }
    var error by remember { mutableStateOf<String?>(null) }
    var template by remember { mutableStateOf(BoardTemplate.ALL.first()) }
    DialogScrim(onDismiss = state::closeEditor) {
        Text("New Idea Board", style = MaterialTheme.typography.h6)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
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
            modifier = Modifier.fillMaxWidth().let { if (multiline) it.height(150.dp) else it },
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
            .clickable(remember { MutableInteractionSource() }, indication = null) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            elevation = 16.dp,
            modifier = Modifier
                .widthIn(min = 380.dp, max = 540.dp)
                // Swallow clicks so the dialog body doesn't dismiss itself.
                .clickable(remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}
