package de.creaflect.actiondraw.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.AppState
import de.creaflect.actiondraw.SessionPlans
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager

@Composable
fun MenuScreen(state: AppState) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text("ActionDraw", style = MaterialTheme.typography.h2, color = MaterialTheme.colors.primary)
            Text(
                "Timed reference practice — get into the flow and draw.",
                style = MaterialTheme.typography.subtitle1,
                textAlign = TextAlign.Center,
            )

            // ---- Folder ----
            SectionLabel("Reference folder")
            Button(onClick = { chooseFolder()?.let { state.selectFolder(it) } }) {
                Text("Select folder…")
            }
            state.folder?.let { dir ->
                Text(
                    dir.absolutePath,
                    style = MaterialTheme.typography.body2,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "${state.unseenCount} unseen of ${state.totalCount} images",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.secondary,
                )
            }

            // ---- Session type ----
            SectionLabel("Session")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SelectChip("Fixed time", state.rampPlan == null) { state.rampPlan = null }
                SessionPlans.ALL.forEach { plan ->
                    SelectChip(plan.name, state.rampPlan == plan) { state.rampPlan = plan }
                }
            }

            val plan = state.rampPlan
            if (plan == null) {
                IntervalSelector(seconds = state.intervalSeconds, onChange = { state.intervalSeconds = it })
            } else {
                Text(
                    "${plan.totalPoses} poses · ${formatDuration(plan.totalSeconds)} total",
                    style = MaterialTheme.typography.body1,
                )
                Text(
                    plan.steps.joinToString("  →  ") { "${formatTime(it.seconds)}×${it.count}" },
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
            }

            if (state.lastSessionPoses > 0) {
                Text(
                    "Last session: ${state.lastSessionPoses} poses · ${formatDuration(state.lastSessionSeconds)}",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                )
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { state.start() },
                enabled = state.totalCount > 0,
                colors = ButtonDefaults.buttonColors(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 36.dp, vertical = 12.dp),
            ) {
                Text("Start drawing", style = MaterialTheme.typography.h6)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.overline,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
    )
}

private fun chooseFolder(): File? {
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "Select image folder"
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}
