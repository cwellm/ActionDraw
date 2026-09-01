package de.creaflect.actiondraw.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.AppState
import de.creaflect.actiondraw.SessionPlans

/** The start menu; [boardButton] lets the app shell add the Idea-Boards entry next to Draw. */
@Composable
fun MenuScreen(state: AppState, boardButton: @Composable RowScope.() -> Unit = {}) {
    Box(
        Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The settings scroll when the window is short; the two primary actions are pinned
            // underneath them, so Draw and Boards are reachable at any window size.
            SessionSettings(state, Modifier.weight(1f))

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { state.start() },
                    enabled = state.selectedCount > 0,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Text("Draw", style = MaterialTheme.typography.h6)
                }
                boardButton()
            }
        }
    }
}

/** Folder choice, session type and timing — everything that configures the next session. */
@Composable
private fun SessionSettings(state: AppState, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("ActionDraw", style = MaterialTheme.typography.h3, color = MaterialTheme.colors.primary)
        Text(
            "Timed reference practice — get into the flow and draw.",
            style = MaterialTheme.typography.subtitle1,
            textAlign = TextAlign.Center,
        )

        // ---- Folder ----
        SectionLabel("Reference folder")
        Button(onClick = { chooseFolder(state.folder)?.let { state.selectFolder(it) } }) {
            Text(if (state.folder == null) "Select folder…" else "Change folder…")
        }
        state.folder?.let { dir ->
            Text(dir.absolutePath, style = MaterialTheme.typography.body2, textAlign = TextAlign.Center)
            Text(
                if (state.selection == null)
                    "${state.unseenCount} unseen of ${state.totalCount} images"
                else
                    "${state.unseenCount} unseen of ${state.selectedCount} selected (${state.totalCount} in folder)",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.secondary,
            )
            OutlinedButton(onClick = { state.openPicker() }) {
                Text(
                    if (state.selection == null) "Choose pictures… (all ${state.totalCount})"
                    else "Choose pictures… (${state.selectedCount} of ${state.totalCount})",
                )
            }
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

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = state.autoAdvance, onCheckedChange = { state.autoAdvance = it })
            Text("Auto-advance to the next picture (off = countdown only)")
        }

        if (state.lastSessionPoses > 0) {
            Text(
                "Last session: ${state.lastSessionPoses} poses · ${formatDuration(state.lastSessionSeconds)}",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
            )
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
