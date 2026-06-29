package de.creaflect.actiondraw.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.AppState

@Composable
fun SummaryScreen(state: AppState) {
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

            Button(onClick = { state.start() }) { Text("Go again") }
            OutlinedButton(onClick = { state.backToMenu() }) { Text("Back to menu") }
            Text(
                "Enter or Esc → menu",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}
