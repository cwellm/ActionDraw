package de.creaflect.actiondraw.board.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.board.BoardState

// What the board remembers between runs, as sections of a settings sheet: the board's own sheet
// below, and the app's, which puts every room's sections together.

/** Where new boards are made. */
@Composable
fun ColumnScope.BoardsHomeSetting(boards: BoardState) {
    Text("Boards home", style = MaterialTheme.typography.caption)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            boards.boardsHome().path,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = {
            boards.dialogs.chooseFolder(boards.boardsHome().takeIf { it.isDirectory }, "Boards home")?.let { boards.setBoardsHomeDir(it) }
        }) { Text("Change…") }
    }
    Text(
        "Where new boards are made. Boards you already have stay where they are.",
        style = MaterialTheme.typography.caption,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
    )
}

/** How cards move on the canvas. */
@Composable
fun ColumnScope.BoardCanvasSetting(boards: BoardState) {
    Text("Board canvas", style = MaterialTheme.typography.caption)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = boards.snapping,
            onCheckedChange = { boards.setSnappingPreference(it) },
            modifier = Modifier.testTag("settings-snap"),
        )
        Text("Snap dragged cards to their neighbours' centre lines", style = MaterialTheme.typography.body2)
    }
}

/** The board's own settings sheet: only what concerns boards; the rest is under the start menu's. */
@Composable
internal fun ColumnScope.BoardSettingsSheet(boards: BoardState, onClose: () -> Unit) {
    Text("Board settings", style = MaterialTheme.typography.h6)
    Text(
        "What concerns boards. The rest is under Settings on the start menu.",
        style = MaterialTheme.typography.caption,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
    )
    BoardsHomeSetting(boards)
    BoardCanvasSetting(boards)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onClose) { Text("Done") }
    }
}
