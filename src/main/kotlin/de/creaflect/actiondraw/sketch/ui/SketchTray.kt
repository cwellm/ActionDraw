package de.creaflect.actiondraw.sketch.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.sketch.SketchEditor
import de.creaflect.actiondraw.sketch.SketchState
import de.creaflect.actiondraw.ui.Atelier
import de.creaflect.actiondraw.ui.LocalReducedMotion
import de.creaflect.actiondraw.ui.Room
import de.creaflect.actiondraw.ui.SelectChip
import de.creaflect.actiondraw.ui.drawLampShadow
import de.creaflect.sketch.Lead
import kotlin.math.roundToInt

/**
 * The pencil tray (CONCEPT.md, *Sketch*): the seven tools and the rubber lying as themselves, each
 * with its key; the chosen one slid out towards the page. Under it what is set for the tool —
 * size, colour, presets — and the Pen and Tune panels, which fold out from here over the page.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SketchTray(state: SketchState, modifier: Modifier = Modifier) {
    Column(
        modifier
            .width(236.dp)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(color = Atelier.Board, shape = RoundedCornerShape(10.dp)) {
            Column(Modifier.padding(vertical = 6.dp)) {
                Lead.entries.forEachIndexed { i, lead ->
                    ToolRow(
                        key = "${i + 1}",
                        label = lead.label,
                        chosen = !state.eraser && state.brush.lead == lead,
                        tag = "sketch-tool-${lead.name}",
                        onClick = { state.setLead(lead) },
                    ) { drawTool(lead) }
                }
                Divider(color = Atelier.Line, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                ToolRow(key = "E", label = "Rubber", chosen = state.eraser, tag = "sketch-rubber", onClick = { state.toggleEraser() }) {
                    drawRubber()
                }
                if (state.eraser) {
                    // A rubber lifts part of the graphite per pass; "hard" takes it all at once.
                    Box(Modifier.padding(start = 40.dp, bottom = 6.dp)) {
                        SelectChip(if (state.eraserSoft) "soft" else "hard", true, tag = "sketch-eraser-mode") { state.toggleEraserMode() }
                    }
                }
            }
        }
        Surface(color = Atelier.Board, shape = RoundedCornerShape(10.dp)) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Size", style = MaterialTheme.typography.caption, color = Atelier.Muted)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${state.brush.size.roundToInt()} px",
                        style = MaterialTheme.typography.caption,
                        fontWeight = FontWeight.Bold,
                        color = Atelier.TextSoft,
                        modifier = Modifier.testTag("sketch-size"),
                    )
                }
                Slider(
                    value = state.brush.size,
                    onValueChange = { state.setSize(it.roundToInt().toFloat()) },
                    valueRange = 1f..80f,
                    colors = SliderDefaults.colors(thumbColor = Room.SKETCH.pigment.glow, activeTrackColor = Room.SKETCH.pigment.glow),
                    modifier = Modifier.height(24.dp).testTag("sketch-size-slider"),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Colour", style = MaterialTheme.typography.caption, color = Atelier.Muted)
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(state.brush.color))
                            .border(1.dp, Atelier.Line, CircleShape)
                            .clickable { state.openEditor(SketchEditor.Colour) }
                            .testTag("sketch-colour"),
                    )
                }
                if (state.presets.isNotEmpty()) {
                    Text("Presets", style = MaterialTheme.typography.caption, color = Atelier.Muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.presets.forEach { preset ->
                            SelectChip(preset.name, !state.eraser && state.activePreset == preset.name, tag = "sketch-preset-${preset.name}") { state.applyPreset(preset) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SelectChip(
                        when {
                            state.penHint != null -> "Pen ⚠"
                            else -> "Pen"
                        },
                        state.showPenPanel,
                        tag = "sketch-pen-panel",
                    ) { state.showPenPanel = !state.showPenPanel }
                    SelectChip("Tune", state.showTunables, tag = "sketch-tune") { state.showTunables = !state.showTunables }
                }
            }
        }
    }
}

/** One tool in the tray: its key, the thing itself, its name; slid out when it is the one in hand. */
@Composable
private fun ToolRow(key: String, label: String, chosen: Boolean, tag: String, onClick: () -> Unit, draw: DrawScope.() -> Unit) {
    val reduced = LocalReducedMotion.current
    val slide by animateDpAsState(if (chosen) 12.dp else 0.dp, tween(if (reduced) 0 else 160))
    val madder = Room.SKETCH.pigment
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .selectable(chosen, role = Role.RadioButton, onClick = onClick)
            .testTag(tag)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(key, style = MaterialTheme.typography.caption, color = if (chosen) madder.glow else Atelier.Muted, modifier = Modifier.width(16.dp))
        Box(Modifier.width(92.dp).height(30.dp).offset(x = slide)) {
            Canvas(Modifier.fillMaxSize().padding(vertical = 8.dp)) { draw() }
            if (chosen) {
                Box(Modifier.align(Alignment.BottomStart).padding(start = 4.dp).width(80.dp).height(2.dp).background(madder.glow))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.body2,
            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
            color = if (chosen) Atelier.Text else Atelier.TextSoft,
            maxLines = 1,
        )
    }
}

// ---------------- The tools, drawn ----------------

/** A tool lying in the tray, its point to the right, filling the draw scope's height. */
private fun DrawScope.drawTool(lead: Lead) {
    val w = size.width
    val t = size.height
    shadow(Size(w - t * 0.4f, t))
    when (lead) {
        Lead.HARD -> pencil(Color(0xFFA9B4BF), Color(0xFF77726D))
        Lead.MEDIUM -> pencil(Color(0xFF3E5873), Color(0xFF3A3734))
        Lead.SOFT -> pencil(Color(0xFF2A2420), Color(0xFF151312))
        Lead.MECHANICAL -> mechanical()
        Lead.CHARCOAL -> charcoal()
        Lead.INK -> fineliner()
        Lead.BRUSH -> brush()
    }
}

private fun DrawScope.shadow(of: Size) {
    val outline = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, of.width, of.height, CornerRadius(of.height * 0.3f))) }
    drawLampShadow(outline, size.height * 0.45f, alpha = 0.55f)
}

/** Lacquer in three facets, lit from above. */
private fun DrawScope.facets(color: Color, x: Float, width: Float) {
    val t = size.height
    drawRect(lerp(color, Color.White, 0.14f), Offset(x, 0f), Size(width, t * 0.34f))
    drawRect(color, Offset(x, t * 0.34f), Size(width, t * 0.33f))
    drawRect(lerp(color, Color.Black, 0.3f), Offset(x, t * 0.67f), Size(width, t * 0.33f))
}

private fun DrawScope.pencil(lacquer: Color, graphite: Color) {
    val w = size.width
    val t = size.height
    val point = t * 0.9f
    val cone = t * 2.2f
    val barrel = w - cone - point
    facets(lacquer, 0f, barrel)
    // The cut end shows the wood and the lead.
    drawRect(Color(0xFFD9B585), Offset(0f, 0f), Size(t * 0.18f, t))
    drawPath(
        Path().apply { moveTo(barrel, 0f); lineTo(barrel + cone, t * 0.32f); lineTo(barrel + cone, t * 0.68f); lineTo(barrel, t); close() },
        Brush.verticalGradient(listOf(Color(0xFFEED4AE), Color(0xFFD9B585), Color(0xFFBE9663)), 0f, t),
    )
    drawPath(Path().apply { moveTo(barrel + cone, t * 0.32f); lineTo(w, t / 2); lineTo(barrel + cone, t * 0.68f); close() }, graphite)
}

private fun DrawScope.mechanical() {
    val w = size.width
    val t = size.height
    val tip = t * 2.4f
    val barrel = w - tip
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFFDCE1E6), Color(0xFF9AA2AA), Color(0xFF6C747C)), 0f, t),
        Offset.Zero,
        Size(barrel, t),
        CornerRadius(t * 0.25f),
    )
    // The grip, knurled, and the clip.
    for (i in 0 until 6) drawRect(Color(0x33000000), Offset(barrel * 0.62f + i * t * 0.35f, 0f), Size(t * 0.12f, t))
    drawRect(Color(0xFFBFC5CB), Offset(barrel * 0.08f, -t * 0.12f), Size(barrel * 0.38f, t * 0.22f))
    drawPath(
        Path().apply { moveTo(barrel, 0f); lineTo(barrel + tip * 0.7f, t * 0.36f); lineTo(barrel + tip * 0.7f, t * 0.64f); lineTo(barrel, t); close() },
        Brush.verticalGradient(listOf(Color(0xFFC9CED3), Color(0xFF7D858D)), 0f, t),
    )
    drawRect(Color(0xFF8D949B), Offset(barrel + tip * 0.7f, t * 0.44f), Size(tip * 0.2f, t * 0.12f))
    drawRect(Color(0xFF2E2B28), Offset(barrel + tip * 0.9f, t * 0.46f), Size(tip * 0.1f, t * 0.08f))
}

private fun DrawScope.charcoal() {
    val w = size.width * 0.82f
    val t = size.height
    // A stick broken off at both ends, matte, a little ragged.
    val stick = Path().apply {
        moveTo(t * 0.15f, t * 0.05f)
        lineTo(w - t * 0.3f, 0f)
        lineTo(w, t * 0.35f)
        lineTo(w - t * 0.12f, t * 0.95f)
        lineTo(t * 0.1f, t)
        lineTo(0f, t * 0.55f)
        close()
    }
    drawPath(stick, Brush.verticalGradient(listOf(Color(0xFF3A3633), Color(0xFF1E1B19), Color(0xFF141210)), 0f, t))
    drawRect(Color(0x22FFFFFF), Offset(t * 0.4f, t * 0.12f), Size(w * 0.6f, t * 0.08f))
}

private fun DrawScope.fineliner() {
    val w = size.width
    val t = size.height
    val nib = t * 1.1f
    val cone = t * 1.4f
    val barrel = w - cone - nib
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFF555B61), Color(0xFF2E3236), Color(0xFF1C1F22)), 0f, t),
        Offset.Zero,
        Size(barrel, t),
        CornerRadius(t * 0.3f),
    )
    // The cap's end and its band.
    drawRect(Color(0xFF6E757C), Offset(barrel * 0.28f, 0f), Size(t * 0.35f, t))
    drawPath(
        Path().apply { moveTo(barrel, t * 0.08f); lineTo(barrel + cone, t * 0.36f); lineTo(barrel + cone, t * 0.64f); lineTo(barrel, t * 0.92f); close() },
        Color(0xFF2E3236),
    )
    drawRect(Color(0xFFB0B4B8), Offset(barrel + cone, t * 0.44f), Size(nib, t * 0.12f))
}

private fun DrawScope.brush() {
    val w = size.width
    val t = size.height
    val hair = t * 2.6f
    val ferrule = t * 1.6f
    val handle = w - hair - ferrule
    // A handle tapering from the ferrule to the end.
    drawPath(
        Path().apply { moveTo(0f, t * 0.3f); lineTo(handle, t * 0.05f); lineTo(handle, t * 0.95f); lineTo(0f, t * 0.7f); close() },
        Brush.verticalGradient(listOf(Color(0xFFE2AE77), Color(0xFFC98B4A), Color(0xFF9C6232)), 0f, t),
    )
    for (i in 0 until 4) {
        drawRect(if (i % 2 == 0) Color(0xFFD9D6D0) else Color(0xFF9C9891), Offset(handle + ferrule * i / 4, 0f), Size(ferrule / 4, t))
    }
    drawPath(
        Path().apply {
            moveTo(handle + ferrule, t * 0.08f)
            cubicTo(handle + ferrule + hair * 0.5f, t * 0.05f, w - hair * 0.2f, t * 0.35f, w, t / 2)
            cubicTo(w - hair * 0.2f, t * 0.65f, handle + ferrule + hair * 0.5f, t * 0.95f, handle + ferrule, t * 0.92f)
            close()
        },
        Brush.horizontalGradient(listOf(Color(0xFF4A3A2C), Color(0xFF21170F)), handle + ferrule, w),
    )
}

/** The rubber: a white block in its paper sleeve, shorter than the tools. */
private fun DrawScope.drawRubber() {
    val w = size.width * 0.52f
    val t = size.height * 1.3f
    val y = (size.height - t) / 2
    val outline = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, y, w, y + t, CornerRadius(t * 0.12f))) }
    drawLampShadow(outline, size.height * 0.45f, alpha = 0.55f)
    drawRoundRect(Color(0xFFEFEAE0), Offset(0f, y), Size(w, t), CornerRadius(t * 0.12f))
    drawRect(Brush.verticalGradient(listOf(Color(0xFF3F61A8), Color(0xFF2B4785)), y, y + t), Offset(0f, y), Size(w * 0.58f, t))
    drawRect(Color(0x22FFFFFF), Offset(0f, y), Size(w * 0.58f, t * 0.2f))
}
