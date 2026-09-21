package de.creaflect.actiondraw.sketch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.sketch.SketchState
import de.creaflect.actiondraw.ui.SelectChip
import de.creaflect.sketch.Lead

/** The Live Sketch entry on the menu, equal in weight to the others. */
@Composable
fun RowScope.SketchMenuButton(onOpen: () -> Unit) {
    Button(
        onClick = onOpen,
        modifier = Modifier.weight(1f).height(56.dp).testTag("menu-sketch"),
    ) {
        Text("Live Sketch", style = MaterialTheme.typography.h6)
    }
}

/**
 * The pressure probe — Live Sketch's first screen, the Durchstich: what the pen reports, live
 * and in numbers, and a page that draws through the engine with whatever pressure arrives. A
 * mouse draws too, at pressure 1. Everything the probe learns goes into LEARNINGS.
 */
@Composable
fun SketchScreen(state: SketchState, onBack: () -> Unit) {
    DisposableEffect(state) {
        onDispose { state.endStroke() }
    }
    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colors.surface.copy(alpha = 0.94f), elevation = 3.dp, modifier = Modifier.fillMaxWidth().testTag("sketch-header")) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Live Sketch — pen probe", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.primary)
                    Spacer(Modifier.width(8.dp))
                    Lead.entries.forEach { lead ->
                        SelectChip(lead.label, state.brush.lead == lead) { state.setLead(lead) }
                    }
                    TextButton(onClick = { state.setSize(state.brush.size - 2f) }) { Text("thinner") }
                    Text("${state.brush.size.toInt()} px", style = MaterialTheme.typography.caption)
                    TextButton(onClick = { state.setSize(state.brush.size + 2f) }) { Text("thicker") }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { state.toggleRecording() }, modifier = Modifier.testTag("sketch-record")) {
                        Text(if (state.recording) "Stop recording" else "Record samples")
                    }
                    OutlinedButton(onClick = { state.clear() }, modifier = Modifier.testTag("sketch-clear")) { Text("Clear") }
                    TextButton(onClick = onBack, modifier = Modifier.testTag("sketch-back")) { Text("Back") }
                }
                Readouts(state)
            }
        }
        Page(state)
    }
}

/** The numbers: what the last sample said, how fast they come, where they go when recorded. */
@Composable
private fun Readouts(state: SketchState) {
    val mono = MaterialTheme.typography.caption.copy(fontFamily = FontFamily.Monospace)
    Text(state.status, style = MaterialTheme.typography.caption, color = MaterialTheme.colors.secondary, modifier = Modifier.testTag("sketch-status"))
    val s = state.last
    val line = if (s == null) {
        "no pen sample yet · samples 0"
    } else {
        "pressure %.3f · tilt %d/%d · rot %d · %s · %s%s · at %.0f,%.0f · %d Hz · samples %d".format(
            s.pressure, s.tiltX, s.tiltY, s.rotation,
            if (s.contact) "CONTACT" else "hover",
            s.pointerType.name.lowercase(),
            (if (s.barrel) " · barrel" else "") + (if (s.eraser) " · eraser" else ""),
            s.x, s.y, state.rateHz, state.sampleCount,
        )
    }
    Text(line, style = mono, modifier = Modifier.testTag("sketch-readout"))
    state.recordedTo?.let { file ->
        Text(
            (if (state.recording) "Recording to " else "Recorded to ") + file.path,
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
        )
    }
}

/** The page: the engine's surface, shown pixel for pixel, drawn on by pen or mouse. */
@Composable
private fun Page(state: SketchState) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFE9E4D8))
            .padding(12.dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.White)
                .testTag("sketch-page")
                .onSizeChanged { state.ensurePage(it.width, it.height) }
                .onGloballyPositioned { state.pageOrigin = it.positionInWindow() }
                .pointerInput(state) {
                    detectDragGestures(
                        onDragStart = { state.onMouse(it.x, it.y) },
                        onDrag = { change, _ ->
                            change.consume()
                            state.onMouse(change.position.x, change.position.y)
                        },
                        onDragEnd = { state.endStroke() },
                        onDragCancel = { state.endStroke() },
                    )
                },
        ) {
            val tick = state.tick
            val page = state.page
            if (page != null) {
                val bitmap = remember(tick, page) { page.snapshot().toComposeImageBitmap() }
                Canvas(Modifier.fillMaxSize()) {
                    drawImage(bitmap)
                }
            }
        }
    }
}
