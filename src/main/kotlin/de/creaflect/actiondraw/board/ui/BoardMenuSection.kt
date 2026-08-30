package de.creaflect.actiondraw.board.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.board.BoardEditor
import de.creaflect.actiondraw.board.BoardState
import de.creaflect.actiondraw.ui.chooseFolder

/** The "Idea Boards" entry point on the menu: create, open, and the recent boards. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoardMenuSection(state: BoardState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier,
    ) {
        Text(
            "IDEA BOARDS",
            style = MaterialTheme.typography.overline,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { state.openEditor(BoardEditor.NewBoard) }) { Text("New board…") }
            OutlinedButton(onClick = {
                chooseFolder(state.boardsHome().takeIf { it.isDirectory }, "Open board folder")
                    ?.let(state::openBoard)
            }) { Text("Open board…") }
        }
        if (state.openFailed) {
            Text(
                "Couldn't read that board file (and no usable backup).",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.error,
            )
        }
        if (state.recent.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.recent.forEach { dir ->
                    OutlinedButton(
                        onClick = { state.openBoard(dir) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) { Text(dir.name, style = MaterialTheme.typography.caption) }
                }
            }
        }
    }
}
