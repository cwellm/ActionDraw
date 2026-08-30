package de.creaflect.actiondraw.board.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
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
import de.creaflect.actiondraw.board.BoardEditor
import de.creaflect.actiondraw.board.BoardState
import de.creaflect.actiondraw.board.ImageItem
import de.creaflect.actiondraw.board.NoteItem
import de.creaflect.actiondraw.ui.chooseFolder
import java.io.File

/** Renders whichever board dialog is open — mounted once at app level, above every screen. */
@Composable
fun BoardDialogs(state: BoardState) {
    when (val editor = state.editor) {
        null -> {}

        BoardEditor.NewBoard -> NewBoardDialog(state)

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
