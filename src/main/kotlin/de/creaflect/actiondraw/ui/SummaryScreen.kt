package de.creaflect.actiondraw.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.AppState
import de.creaflect.actiondraw.PinTargets
import de.creaflect.actiondraw.Screen

@Composable
fun SummaryScreen(state: AppState, pinTargets: PinTargets? = null) {
    val fromBoard = state.sessionOrigin == Screen.Board
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            Text(
                if (state.lastSessionCompleted) "Session complete 🎉" else "Nice work",
                style = MaterialTheme.typography.h3,
                color = MaterialTheme.colors.primary,
            )
            Text(
                "${state.lastSessionPoses} ${if (state.lastSessionPoses == 1) "pose" else "poses"} drawn",
                style = MaterialTheme.typography.h5,
            )
            Text(
                "Time spent drawing: ${formatDuration(state.lastSessionSeconds)}",
                style = MaterialTheme.typography.subtitle1,
            )
            Text(
                "${state.unseenCount} of ${state.totalCount} images still unseen in this folder",
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(12.dp))

            // What you flagged for redo is exactly what is worth collecting on a board.
            val flagged = state.sessionFlaggedFiles
            if (pinTargets != null && flagged.isNotEmpty()) {
                var open by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { open = true }) {
                        Text("Pin ${flagged.size} flagged to a board ▾")
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        val boards = pinTargets.boards()
                        if (boards.isEmpty()) {
                            DropdownMenuItem(onClick = { open = false }) { Text("No boards yet") }
                        }
                        boards.forEach { (name, dir) ->
                            DropdownMenuItem(onClick = {
                                open = false
                                state.pinNotice = pinTargets.pin(dir, flagged)
                            }) { Text(name) }
                        }
                    }
                }
            }
            state.pinNotice?.let {
                Text(it, style = MaterialTheme.typography.caption, color = MaterialTheme.colors.secondary)
            }

            Button(onClick = { state.start() }) { Text("Go again") }
            OutlinedButton(onClick = { state.backToMenu() }) {
                Text(if (fromBoard) "Back to board" else "Back to menu")
            }
            Text(
                if (fromBoard) "Enter or Esc → board" else "Enter or Esc → menu",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}
