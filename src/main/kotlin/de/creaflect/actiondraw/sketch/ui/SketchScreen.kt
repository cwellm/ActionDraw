package de.creaflect.actiondraw.sketch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.image.ThumbCache
import de.creaflect.actiondraw.sketch.SketchEditor
import de.creaflect.actiondraw.sketch.SketchState
import de.creaflect.actiondraw.ui.SelectChip
import de.creaflect.sketch.Lead
import de.creaflect.sketch.PencilModel
import de.creaflect.sketch.Pencils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import java.io.File
import kotlin.math.roundToInt
import de.creaflect.sketch.Paper

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
 * A page, a pencil, a colour, a pen. A thin toolbar on top; the rest is page: a pen or the
 * mouse draws, the wheel zooms about the cursor, Space+drag or the middle button pans. The pen's
 * readouts and the pencil study's tunables fold out from the toolbar when wanted.
 */
@Composable
fun SketchScreen(state: SketchState, thumbs: ThumbCache) {
    DisposableEffect(state) {
        onDispose { state.mouseUp() }
    }
    Column(Modifier.fillMaxSize()) {
        Toolbar(state)
        state.notice?.let {
            Text(
                "$it  (click to dismiss)",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.secondary,
                modifier = Modifier.padding(horizontal = 12.dp).clickable { state.notice = null }.testTag("sketch-notice"),
            )
        }
        if (state.showPenPanel) PenPanel(state)
        if (state.showTunables) Tunables(state)
        Box(Modifier.fillMaxSize()) {
            Page(state)
            state.reference?.let { Reference(state, it, thumbs, Modifier.align(Alignment.TopEnd)) }
        }
    }
}

// ---------------- The toolbar ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Toolbar(state: SketchState) {
    Surface(color = MaterialTheme.colors.surface.copy(alpha = 0.94f), elevation = 3.dp, modifier = Modifier.fillMaxWidth().testTag("sketch-header")) {
        // A FlowRow, not a Row: a Row clipped its tail off in a 1120-dp window, Back and all.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Column(Modifier.widthIn(max = 200.dp).align(Alignment.CenterVertically)) {
                Text(
                    state.title + if (state.dirty) " •" else "",
                    style = MaterialTheme.typography.subtitle2,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("sketch-title"),
                )
                Text(state.pageName, style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.55f), maxLines = 1)
            }
            Spacer(Modifier.width(6.dp))
            Lead.entries.forEach { lead ->
                SelectChip(lead.label, !state.eraser && state.brush.lead == lead) { state.setLead(lead) }
            }
            state.presets.forEach { preset ->
                SelectChip(preset.name, !state.eraser && state.activePreset == preset.name, tag = "sketch-preset-${preset.name}") { state.applyPreset(preset) }
            }
            SelectChip("Eraser", state.eraser) { state.toggleEraser() }
            if (state.eraser) {
                // A rubber lifts part of the graphite per pass; "hard" takes it all at once.
                SelectChip(if (state.eraserSoft) "soft" else "hard", true, tag = "sketch-eraser-mode") { state.toggleEraserMode() }
            }
            Flat("−") { state.setSize(state.brush.size - 1f) }
            Text("${state.brush.size.roundToInt()}", style = MaterialTheme.typography.body2, modifier = Modifier.testTag("sketch-size"))
            Flat("+") { state.setSize(state.brush.size + 1f) }
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(state.brush.color))
                    .border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.4f), CircleShape)
                    .clickable { state.openEditor(SketchEditor.Colour) }
                    .testTag("sketch-colour"),
            )
            Spacer(Modifier.width(6.dp))
            Flat("Undo", enabled = state.canUndo, tag = "sketch-undo") { state.undo() }
            Flat("Redo", enabled = state.canRedo, tag = "sketch-redo") { state.redo() }
            Flat("${(state.zoom * 100).roundToInt()} %", tag = "sketch-fit") { state.fit() }
            Flat(
                when {
                    state.showPenPanel -> "Pen ✕"
                    state.penHint != null -> "Pen ⚠"
                    else -> "Pen"
                },
                tag = "sketch-pen-panel",
            ) { state.showPenPanel = !state.showPenPanel }
            Flat(if (state.showTunables) "Tune ✕" else "Tune", tag = "sketch-tune") { state.showTunables = !state.showTunables }
            FileMenu(state)
            Flat("Save", tag = "sketch-save") { state.save() }
            Flat("Back", tag = "sketch-back") { state.leave() }
        }
    }
}

/** Text-style button: the toolbar's default weight, so the page stays the loudest thing. */
@Composable
private fun Flat(label: String, enabled: Boolean = true, tag: String? = null, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        // 30 dp with Material's own 8 dp of vertical padding left 14 dp for a 19-dp line of
        // text, and Text clips what overflows: every label lost the bottom of its letters.
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        modifier = Modifier.height(30.dp).let { if (tag != null) it.testTag(tag) else it },
    ) {
        Text(label, style = MaterialTheme.typography.body2, maxLines = 1)
    }
}

/** New, open, save as, to a board, to a concept. */
@Composable
private fun FileMenu(state: SketchState) {
    var open by remember { mutableStateOf(false) }
    Box {
        Flat("Sketch ▾", tag = "sketch-menu") { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(onClick = { open = false; state.openEditor(SketchEditor.NewSketch) }, modifier = Modifier.testTag("sketch-new")) { Text("New…   Ctrl+N") }
            DropdownMenuItem(onClick = { open = false; state.openEditor(SketchEditor.Open) }, modifier = Modifier.testTag("sketch-open")) { Text("Open…   Ctrl+O") }
            DropdownMenuItem(onClick = { open = false; state.openEditor(SketchEditor.SaveAs) }, modifier = Modifier.testTag("sketch-save-as")) { Text("Save as…") }
            DropdownMenuItem(onClick = { open = false; state.openEditor(SketchEditor.ToBoard) }, modifier = Modifier.testTag("sketch-to-board")) { Text("To board…") }
            DropdownMenuItem(onClick = { open = false; state.openEditor(SketchEditor.ToConcept) }, modifier = Modifier.testTag("sketch-to-concept")) { Text("To concept…") }
        }
    }
}

// ---------------- The pen's readouts ----------------

@Composable
private fun PenPanel(state: SketchState) {
    val mono = MaterialTheme.typography.caption.copy(fontFamily = FontFamily.Monospace)
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colors.surface.copy(alpha = 0.8f)).padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(state.penStatus, style = MaterialTheme.typography.caption, color = MaterialTheme.colors.secondary, modifier = Modifier.weight(1f).testTag("sketch-status"))
            Flat(if (state.recording) "Stop recording" else "Record samples", tag = "sketch-record") { state.toggleRecording() }
        }
        state.penHint?.let {
            Text(it, style = MaterialTheme.typography.caption, color = MaterialTheme.colors.error, modifier = Modifier.testTag("sketch-pen-hint"))
        }
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
        state.lastInput?.let { Text("last input: $it", style = mono, modifier = Modifier.testTag("sketch-last-input")) }
        state.recordedTo?.let { file ->
            Text((if (state.recording) "Recording to " else "Recorded to ") + file.path, style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
        }
    }
}

// ---------------- The pencil study's knobs ----------------

/** Every tunable of the current lead, live; the numbers that survive go into LEARNINGS. */
@Composable
private fun Tunables(state: SketchState) {
    val lead = state.brush.lead
    var model by remember(lead, state.tunablesVersion) { mutableStateOf(Pencils.of(lead)) }
    fun apply(next: PencilModel) {
        model = next
        Pencils.set(lead, next)
        state.markTuned()
    }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colors.surface.copy(alpha = 0.8f)).padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${lead.label}: ", style = MaterialTheme.typography.caption, fontWeight = FontWeight.Bold)
            Text(
                "width %.2f..%.2f γ %.2f · alpha %.2f..%.2f · speed k %.2f · edge %.2f · tilt +%.2f −%.2f · grain %.2f".format(model.minWidth, model.maxWidth, model.gamma, model.alphaFloor, model.alphaCeiling, model.speedK, model.edge, model.tiltWidth, model.tiltAlpha, model.grain),
                style = MaterialTheme.typography.caption.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.weight(1f).testTag("sketch-tunables"),
            )
            Flat("Reset") { Pencils.reset(lead); model = Pencils.of(lead); state.markTuned() }
            Flat("Save as preset…", tag = "sketch-save-preset") { state.askPresetName() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Knob("min width", model.minWidth, 0.05f..1f) { apply(model.copy(minWidth = it)) }
            Knob("max width", model.maxWidth, 0.2f..2f) { apply(model.copy(maxWidth = it)) }
            Knob("gamma", model.gamma, 0.3f..3f) { apply(model.copy(gamma = it)) }
            Knob("edge", model.edge, 0f..0.4f) { apply(model.copy(edge = it)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Knob("alpha floor", model.alphaFloor, 0f..0.5f) { apply(model.copy(alphaFloor = it)) }
            Knob("alpha ceiling", model.alphaCeiling, 0.2f..1f) { apply(model.copy(alphaCeiling = it)) }
            Knob("speed k", model.speedK, 0f..0.8f) { apply(model.copy(speedK = it)) }
            Knob("v ref", model.vRef, 300f..4000f) { apply(model.copy(vRef = it)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Knob("tilt: wider by", model.tiltWidth, 0f..3f) { apply(model.copy(tiltWidth = it)) }
            Knob("tilt: lighter by", model.tiltAlpha, 0f..1f) { apply(model.copy(tiltAlpha = it)) }
            Knob("grain", model.grain, 0f..1.5f) { apply(model.copy(grain = it)) }
            Spacer(Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Paper: ", style = MaterialTheme.typography.caption, fontWeight = FontWeight.Bold)
            Paper.entries.forEach { paper ->
                SelectChip(paper.label, state.tooth == paper, tag = "sketch-paper-${paper.name}") { state.setTooth(paper) }
            }
            Text("— the strokes are drawn again on it", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
        }
        if (state.presets.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Presets: ", style = MaterialTheme.typography.caption, fontWeight = FontWeight.Bold)
                state.presets.forEach { preset ->
                    Text("${preset.name} (${preset.base.label})", style = MaterialTheme.typography.caption)
                    Flat("×", tag = "sketch-preset-delete-${preset.name}") { state.deletePreset(preset.name) }
                }
            }
        }
    }
}

@Composable
private fun RowScope.Knob(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.height(28.dp))
    }
}

// ---------------- The page ----------------

/**
 * The page in its view: paper, then the strokes' tiles, drawn at the current zoom and pan
 * straight onto Skia's canvas. Only tiles in view are drawn, and a tile nobody drew on since the
 * last frame is the same picture as before, so the GPU keeps it: a stroke re-uploads only the
 * few tiles it crosses, whatever the size of the page.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Page(state: SketchState) {
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color(0xFF6E6A62))
            .testTag("sketch-page")
            .onSizeChanged { state.viewResized(it) }
            .onGloballyPositioned { state.viewOrigin = it.positionInWindow() }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val change = event.changes.firstOrNull() ?: return@onPointerEvent
                state.lastInput = "wheel Δx %.2f Δy %.2f%s%s".format(
                    change.scrollDelta.x, change.scrollDelta.y,
                    if (event.keyboardModifiers.isCtrlPressed) " ctrl" else "",
                    if (event.keyboardModifiers.isShiftPressed) " shift" else "",
                )
                val delta = change.scrollDelta.y.takeIf { it != 0f } ?: change.scrollDelta.x
                state.wheel(delta, shift = event.keyboardModifiers.isShiftPressed, aboutX = change.position.x, aboutY = change.position.y)
            }
            .pointerInput(state) {
                // Drawing needs no drag threshold — a press is already a mark, a press without a
                // move a dot. Space held or the middle button pans instead; the right button is
                // left alone. Compose's own drag helpers wait for a threshold and only take the
                // primary button, so this reads the events itself.
                awaitEachGesture {
                    var event = awaitPointerEvent()
                    while (event.type != PointerEventType.Press) event = awaitPointerEvent()
                    val down = event.changes.firstOrNull() ?: return@awaitEachGesture
                    if (event.buttons.isSecondaryPressed && !event.buttons.isPrimaryPressed) return@awaitEachGesture
                    val panning = event.buttons.isTertiaryPressed || state.spaceHeld
                    if (!panning) state.mouseDown(down.position.x, down.position.y)
                    down.consume()
                    var last = down.position
                    while (true) {
                        val next = awaitPointerEvent()
                        val change = next.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (!panning) state.mouseUp()
                            break
                        }
                        if (panning) state.pan(change.position.x - last.x, change.position.y - last.y)
                        else state.mouseMove(change.position.x, change.position.y)
                        last = change.position
                        change.consume()
                    }
                }
            },
    ) {
        val session = state.session
        if (session == null) {
            Text(
                "No page yet — Sketch ▾ → New…, or Ctrl+N.",
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            Canvas(Modifier.fillMaxSize()) {
                state.tick // a stroke went onto the page: draw it again
                val zoom = state.zoom
                val panX = state.panX
                val panY = state.panY
                val w = session.width * zoom
                val h = session.height * zoom
                drawRect(Color(session.paper), topLeft = Offset(panX, panY), size = Size(w, h))
                val surface = session.surface
                // Mipmaps when zoomed out, so fine lines do not flicker away; nearest when zoomed
                // right in, so pixels stay pixels.
                val sampling = when {
                    zoom < 1f -> FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)
                    zoom < 2f -> FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)
                    else -> FilterMipmap(FilterMode.NEAREST, MipmapMode.NONE)
                }
                // The paper's tooth, faintly, so the page reads as paper and the graphite sits *in*
                // something. On screen only; the saved picture is clean paper.
                drawIntoCanvas { session.tooth.grain.shade(it.nativeCanvas, panX, panY, panX + w, panY + h, zoom, sampling = sampling) }
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    for (i in 0 until surface.tileCount) {
                        val left = panX + surface.tileLeft(i) * zoom
                        val top = panY + surface.tileTop(i) * zoom
                        val right = left + surface.tileWidth(i) * zoom
                        val bottom = top + surface.tileHeight(i) * zoom
                        if (right < 0f || bottom < 0f || left > size.width || top > size.height) continue
                        native.drawImageRect(
                            surface.tileImage(i),
                            Rect.makeWH(surface.tileWidth(i).toFloat(), surface.tileHeight(i).toFloat()),
                            Rect.makeLTRB(left, top, right, bottom),
                            sampling,
                            null,
                            true,
                        )
                    }
                }
                drawRect(Color(0x55000000), topLeft = Offset(panX, panY), size = Size(w, h), style = Stroke(width = 1f))
            }
        }
    }
}

/** The reference from a session, kept in the corner while sketching: click to enlarge, ✕ to put away. */
@Composable
private fun Reference(state: SketchState, file: File, thumbs: ThumbCache, modifier: Modifier) {
    var large by remember { mutableStateOf(false) }
    val bitmap by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { thumbs.load(file, maxSize = 900) }
    }
    val bmp = bitmap ?: return
    Surface(elevation = 8.dp, shape = RoundedCornerShape(6.dp), modifier = modifier.padding(12.dp).testTag("sketch-reference")) {
        Box {
            Image(
                bitmap = bmp,
                contentDescription = file.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(if (large) 520.dp else 220.dp).clickable { large = !large },
            )
            Text(
                "✕",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(Color(0x88000000), RoundedCornerShape(bottomStart = 6.dp))
                    .clickable { state.reference = null }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
