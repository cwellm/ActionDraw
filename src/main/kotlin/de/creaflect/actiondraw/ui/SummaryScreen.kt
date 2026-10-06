package de.creaflect.actiondraw.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.creaflect.actiondraw.AppState
import de.creaflect.actiondraw.PinTargets
import de.creaflect.actiondraw.Screen
import de.creaflect.actiondraw.image.ThumbCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The end of a session (F9.6): what was drawn, and the pictures flagged for another go laid out
 * as prints with a pencil mark — exactly what is worth pinning to a board.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SummaryScreen(state: AppState, pinTargets: PinTargets? = null, thumbs: ThumbCache? = null) {
    val fromBoard = state.sessionOrigin == Screen.Board
    Box(Modifier.fillMaxSize().grain().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            modifier = Modifier.widthIn(max = 760.dp),
        ) {
            Text(
                if (state.lastSessionCompleted) "Session complete" else "Nice work",
                style = AtelierType.typography.h3,
                color = Atelier.Text,
            )
            Text(
                if (state.lastSessionCompleted) "every pose drawn" else "stopped early, still counted",
                style = AtelierType.Hand.copy(fontSize = 26.sp),
                color = Room.PRACTICE.pigment.glow,
            )
            Text(
                "${state.lastSessionPoses} ${if (state.lastSessionPoses == 1) "pose" else "poses"} · ${formatDuration(state.lastSessionSeconds)} drawing",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Atelier.Text,
            )
            Text(
                "${state.unseenCount} of ${state.totalCount} pictures still unseen in this folder",
                fontSize = 13.sp,
                color = Atelier.Muted,
                textAlign = TextAlign.Center,
            )

            // What you flagged for redo is exactly what is worth collecting on a board.
            val flagged = state.sessionFlaggedFiles
            if (flagged.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Flagged for another go", fontSize = 12.sp, letterSpacing = 1.8.sp, color = Atelier.Muted)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.testTag("summary-flagged"),
                ) {
                    flagged.take(8).forEachIndexed { i, file -> FlaggedPrint(file, thumbs, tilt = if (i % 2 == 0) -1.5f else 1.2f) }
                }
                if (flagged.size > 8) Text("and ${flagged.size - 8} more", fontSize = 12.sp, color = Atelier.Muted)
            }
            if (pinTargets != null && flagged.isNotEmpty()) {
                var open by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { open = true }) {
                        PigmentDab(Room.BOARDS.pigment, size = 11.dp, seed = Room.BOARDS.seed)
                        Spacer(Modifier.width(8.dp))
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
                Text(it, fontSize = 12.sp, color = Room.CONCEPTS.pigment.glow)
            }

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { state.start() }, modifier = Modifier.height(48.dp)) { Text("Go again", fontSize = 16.sp) }
                OutlinedButton(onClick = { state.backToMenu() }, modifier = Modifier.height(48.dp)) {
                    Text(if (fromBoard) "Back to the board" else "Back to Practice")
                }
            }
            Text(
                if (fromBoard) "Enter or Esc → the board" else "Enter or Esc → Practice",
                fontSize = 12.sp,
                color = Atelier.Muted,
            )
        }
    }
}

/** A flagged picture as a small print, ringed in pencil and marked "again". */
@Composable
private fun FlaggedPrint(file: File, thumbs: ThumbCache?, tilt: Float) {
    val thumb: ImageBitmap? by produceState<ImageBitmap?>(null, file, thumbs) {
        value = thumbs?.let { t -> withContext(Dispatchers.IO) { t.load(file) } }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { rotationZ = tilt }) {
        Box(
            Modifier
                .size(104.dp)
                .lampShadow(Lift.RESTING)
                .background(Atelier.Paper)
                .padding(6.dp),
        ) {
            thumb?.let { Image(it, contentDescription = file.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                ?: Box(Modifier.fillMaxSize().background(Color(0xFF3A342E)))
            // A pencil ring round the corner where the mark is.
            Canvas(Modifier.size(30.dp).align(Alignment.TopEnd)) {
                drawCircle(Color(0xFF2F2B28), size.minDimension / 2.4f, center + Offset(2f, -2f), style = Stroke(1.6.dp.toPx()))
            }
        }
        Text("again", style = AtelierType.Hand.copy(fontSize = 19.sp), color = Atelier.TextSoft)
    }
}
