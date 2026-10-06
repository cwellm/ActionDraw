package de.creaflect.actiondraw.sketch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
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
import de.creaflect.actiondraw.ui.Atelier
import de.creaflect.actiondraw.ui.ChromeEdge
import de.creaflect.actiondraw.ui.CrossRoomButton
import de.creaflect.actiondraw.ui.Motion
import de.creaflect.actiondraw.ui.PointerWatch
import de.creaflect.actiondraw.ui.RecedingChrome
import de.creaflect.actiondraw.ui.Room
import de.creaflect.actiondraw.ui.RoomHeader
import de.creaflect.actiondraw.ui.drawLampShadow
import de.creaflect.actiondraw.ui.drawTape
import de.creaflect.actiondraw.ui.watchPointer
import de.creaflect.sketch.PencilModel
import de.creaflect.sketch.Pencils
import kotlinx.coroutines.delay
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import kotlin.math.roundToInt

/**
 * The Sketch room (CONCEPT.md, *Sketch*): the page in the middle, taped to the table; the pencil
 * tray on its left, the paper on its right, the room's header above and a status line under it.
 * A pen or the mouse draws, the wheel zooms about the cursor, Space+drag or the middle button
 * pans. While a stroke goes on, everything round the page steps back.
 */
@Composable
fun SketchScreen(state: SketchState, thumbs: ThumbCache, onHome: (() -> Unit)? = null) {
    DisposableEffect(state) {
        onDispose { state.mouseUp() }
    }
    val watch = remember { PointerWatch() }
    val receded = remember { mutableStateOf(false) }
    var largeReference by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Atelier.Ground).watchPointer(watch)) {
        RecedeWhileDrawing(state, receded)
        SketchChrome(state, watch, receded, ChromeEdge.TOP, "sketch-chrome") { Header(state, onHome) }
        state.notice?.let {
            Text(
                "$it  (click to dismiss)",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.secondary,
                modifier = Modifier.padding(horizontal = 16.dp).clickable { state.notice = null }.testTag("sketch-notice"),
            )
        }
        Row(Modifier.fillMaxWidth().weight(1f)) {
            SketchChrome(state, watch, receded, ChromeEdge.LEFT, "sketch-tray") { SketchTray(state) }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Page(state)
                    // Pen and Tune fold out from the tray, over the page's near edge.
                    if (state.showPenPanel || state.showTunables) {
                        Column(Modifier.align(Alignment.TopStart).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state.showPenPanel) FoldOut { PenPanel(state) }
                            if (state.showTunables) FoldOut { Tunables(state) }
                        }
                    }
                    val reference = state.reference
                    if (largeReference && reference != null) {
                        LargeReference(reference, thumbs, onClose = { largeReference = false }, modifier = Modifier.align(Alignment.TopEnd))
                    }
                }
                StatusLine(state)
            }
            SketchChrome(state, watch, receded, ChromeEdge.RIGHT, "sketch-paper-panel") {
                PaperPanel(state, thumbs, largeReference, onEnlarge = { largeReference = !largeReference })
            }
        }
    }
}

/**
 * Once a stroke has gone on for a moment, the chrome round the page steps back (CONCEPT.md,
 * *Recede*). Its own composable, so a stroke starting and ending recomposes this and not the page.
 */
@Composable
private fun RecedeWhileDrawing(state: SketchState, receded: MutableState<Boolean>) {
    val drawing = state.drawing
    LaunchedEffect(drawing) {
        if (drawing) {
            delay(Motion.RECEDE_AFTER_MS)
            receded.value = true
        }
    }
}

/**
 * One piece of the chrome — the header, the tray, the paper — stepped back while [receded], and
 * back as the pointer nears its [edge]. Not while the Pen or Tune panel is open: then the chrome
 * is what is being worked with. Coming up to any piece brings them all back.
 */
@Composable
private fun SketchChrome(
    state: SketchState,
    watch: PointerWatch,
    receded: MutableState<Boolean>,
    edge: ChromeEdge,
    tag: String,
    content: @Composable () -> Unit,
) {
    RecedingChrome(
        receded = receded.value && !state.showPenPanel && !state.showTunables,
        watch = watch,
        edge = edge,
        modifier = Modifier.testTag(tag),
        onOver = { if (!state.drawing) receded.value = false },
        content = content,
    )
}

// ---------------- The header ----------------

@Composable
private fun Header(state: SketchState, onHome: (() -> Unit)?) {
    Box(Modifier.testTag("sketch-header")) {
        RoomHeader(
            Room.SKETCH,
            onHome,
            modifier = Modifier.padding(horizontal = 16.dp),
            title = {
                Column(Modifier.weight(1f, fill = false).widthIn(max = 240.dp)) {
                    Text(
                        state.title + if (state.dirty) " •" else "",
                        style = MaterialTheme.typography.body1,
                        color = Atelier.TextSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("sketch-title"),
                    )
                }
            },
            actions = {
                Flat("Undo", enabled = state.canUndo, tag = "sketch-undo") { state.undo() }
                Flat("Redo", enabled = state.canRedo, tag = "sketch-redo") { state.redo() }
                FileMenu(state)
                Flat("Save", tag = "sketch-save") { state.save() }
                CrossRoomButton(Room.BOARDS, "To a board", Modifier.testTag("sketch-to-board")) { state.openEditor(SketchEditor.ToBoard) }
                CrossRoomButton(Room.CONCEPTS, "To a concept", Modifier.testTag("sketch-to-concept")) { state.openEditor(SketchEditor.ToConcept) }
                Flat("Back", tag = "sketch-back") { state.leave() }
            },
        )
    }
}

/** Text-style button: quiet, so the page stays the loudest thing. */
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

/** New, open, save as. */
@Composable
private fun FileMenu(state: SketchState) {
    var open by remember { mutableStateOf(false) }
    Box {
        Flat("File ▾", tag = "sketch-menu") { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(onClick = { open = false; state.openEditor(SketchEditor.NewSketch) }, modifier = Modifier.testTag("sketch-new")) { Text("New…   Ctrl+N") }
            DropdownMenuItem(onClick = { open = false; state.openEditor(SketchEditor.Open) }, modifier = Modifier.testTag("sketch-open")) { Text("Open…   Ctrl+O") }
            DropdownMenuItem(onClick = { open = false; state.openEditor(SketchEditor.SaveAs) }, modifier = Modifier.testTag("sketch-save-as")) { Text("Save as…") }
        }
    }
}

// ---------------- Under the page ----------------

/**
 * What the pen is doing and how near the page is, in one quiet line under it — the readouts the
 * toolbar used to carry. The zoom is a button: a click fits the page.
 */
@Composable
private fun StatusLine(state: SketchState) {
    val hint = state.penHint
    val sample = state.last
    val (dot, text) = when {
        hint != null -> Room.PRACTICE.pigment.glow to "The pen arrives as a mouse, without pressure — see Pen"
        sample != null -> Room.CONCEPTS.pigment.glow to
            "Pen · " + (if (sample.tiltX != 0 || sample.tiltY != 0) "pressure and tilt arriving" else "pressure arriving") + " · ${state.rateHz} Hz"
        else -> Atelier.Muted to state.penStatus
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(7.dp).background(dot, CircleShape))
        Text(
            text,
            style = MaterialTheme.typography.caption,
            color = if (hint != null) dot else Atelier.Muted,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).testTag("sketch-status"),
        )
        Text(state.pageName, style = MaterialTheme.typography.caption, color = Atelier.Muted, maxLines = 1, softWrap = false)
        Flat("${(state.zoom * 100).roundToInt()} %", tag = "sketch-fit") { state.fit() }
    }
}

// ---------------- Pen and Tune, folded out ----------------

@Composable
private fun FoldOut(content: @Composable () -> Unit) {
    Surface(color = Atelier.Raised, shape = RoundedCornerShape(10.dp), elevation = 6.dp, modifier = Modifier.widthIn(max = 640.dp)) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) { content() }
    }
}

@Composable
private fun PenPanel(state: SketchState) {
    val mono = MaterialTheme.typography.caption.copy(fontFamily = FontFamily.Monospace)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(state.penStatus, style = MaterialTheme.typography.caption, color = Atelier.TextSoft, modifier = Modifier.weight(1f).testTag("sketch-pen-status"))
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
        Text(line, style = mono, color = Atelier.TextSoft, modifier = Modifier.testTag("sketch-readout"))
        state.lastInput?.let { Text("last input: $it", style = mono, color = Atelier.TextSoft, modifier = Modifier.testTag("sketch-last-input")) }
        state.recordedTo?.let { file ->
            Text((if (state.recording) "Recording to " else "Recorded to ") + file.path, style = MaterialTheme.typography.caption, color = Atelier.Muted)
        }
    }
}

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
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${lead.label}: ", style = MaterialTheme.typography.caption, fontWeight = FontWeight.Bold, color = Atelier.Text)
            Text(
                "width %.2f..%.2f γ %.2f · alpha %.2f..%.2f · speed k %.2f · edge %.2f · tilt +%.2f −%.2f · grain %.2f".format(model.minWidth, model.maxWidth, model.gamma, model.alphaFloor, model.alphaCeiling, model.speedK, model.edge, model.tiltWidth, model.tiltAlpha, model.grain),
                style = MaterialTheme.typography.caption.copy(fontFamily = FontFamily.Monospace),
                color = Atelier.TextSoft,
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
        if (state.presets.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Presets: ", style = MaterialTheme.typography.caption, fontWeight = FontWeight.Bold, color = Atelier.Text)
                state.presets.forEach { preset ->
                    Text("${preset.name} (${preset.base.label})", style = MaterialTheme.typography.caption, color = Atelier.TextSoft)
                    Flat("×", tag = "sketch-preset-delete-${preset.name}") { state.deletePreset(preset.name) }
                }
            }
        }
    }
}

@Composable
private fun RowScope.Knob(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.caption, color = Atelier.Muted)
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.height(28.dp))
    }
}

// ---------------- The page ----------------

/**
 * The page in its view, on the table: the lamp's shadow under it, the paper, then the strokes'
 * tiles, drawn at the current zoom and pan straight onto Skia's canvas, and tape over its corners.
 * Only tiles in view are drawn, and a tile nobody drew on since the last frame is the same picture
 * as before, so the GPU keeps it: a stroke re-uploads only the few tiles it crosses, whatever the
 * size of the page. The shadow and the tape read only the view, so a stroke never redraws them.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Page(state: SketchState) {
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
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
                "No page yet — File ▾ → New…, or Ctrl+N.",
                color = Atelier.Muted,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            // Under the page: the lamp's shadow.
            Canvas(Modifier.fillMaxSize()) {
                val page = ComposeRect(state.panX, state.panY, state.panX + session.width * state.zoom, state.panY + session.height * state.zoom)
                drawLampShadow(Path().apply { addRect(page) }, 12.dp.toPx(), alpha = 0.6f)
            }
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
            }
            // Over its corners: tape, across each corner.
            Canvas(Modifier.fillMaxSize()) {
                val l = state.panX
                val t = state.panY
                val r = l + session.width * state.zoom
                val b = t + session.height * state.zoom
                val tape = Size(58.dp.toPx(), 18.dp.toPx())
                drawTape(Offset(l, t), tape, -45f, seed = 1)
                drawTape(Offset(r, t), tape, 45f, seed = 2)
                drawTape(Offset(l, b), tape, 45f, seed = 3)
                drawTape(Offset(r, b), tape, -45f, seed = 4)
            }
        }
    }
}
