package de.creaflect.actiondraw.board.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.board.BoardEditor
import de.creaflect.actiondraw.board.BoardState

/**
 * The Idea-Boards entry on the menu — a primary button equal in weight to "Draw". It opens the
 * board picker (list of boards, New board…, Explore…), not a folder dialog.
 */
@Composable
fun RowScope.BoardMenuButton(state: BoardState) {
    Button(
        onClick = { state.openBoardList() },
        modifier = Modifier.weight(1f).height(56.dp),
    ) {
        Text("Boards", style = MaterialTheme.typography.h6)
    }
}
