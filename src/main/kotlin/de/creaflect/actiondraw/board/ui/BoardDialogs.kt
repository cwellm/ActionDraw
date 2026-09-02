package de.creaflect.actiondraw.board.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.SessionPlans
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.board.BoardEditor
import de.creaflect.actiondraw.board.BoardState
import de.creaflect.actiondraw.board.ImageItem
import de.creaflect.actiondraw.board.NoteItem
import de.creaflect.actiondraw.board.SessionRecipe
import de.creaflect.actiondraw.ui.IntervalSelector
import de.creaflect.actiondraw.ui.SelectChip
import de.creaflect.actiondraw.ui.chooseFolder
import java.io.File

/** Renders whichever board dialog is open — mounted once at app level, above every screen. */
@Composable
fun BoardDialogs(state: BoardState) {
    when (val editor = state.editor) {
        null -> {}

        BoardEditor.NewBoard -> NewBoardDialog(state)

        BoardEditor.EditSession -> SessionRecipeDialog(state)

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

        is BoardEditor.EditNote -> TextPromptDialog(
            title = if (editor.itemId == null) "New note" else "Edit note",
            initial = (editor.itemId?.let(state::item) as? NoteItem)?.text ?: "",
            confirm = "Save",
            multiline = true,
            onOk = { state.saveNote(editor.itemId, it); state.closeEditor() },
            onCancel = state::closeEditor,
        )

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

@Composable
private fun NewBoardDialog(state: BoardState) {
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf(state.boardsHome()) }
    var error by remember { mutableStateOf<String?>(null) }
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
        error?.let { Text(it, color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption) }
        DialogButtons(
            confirm = "Create",
            onOk = { error = state.createBoard(location, name) }, // success also closes the dialog
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
