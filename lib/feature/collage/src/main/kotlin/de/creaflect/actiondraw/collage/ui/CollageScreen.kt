package de.creaflect.actiondraw.collage.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.creaflect.actiondraw.Room
import de.creaflect.actiondraw.collage.CollageState
import de.creaflect.actiondraw.collage.Collages
import de.creaflect.actiondraw.collage.Tool
import de.creaflect.actiondraw.ui.Design
import de.creaflect.actiondraw.ui.RoomHeader
import de.creaflect.actiondraw.ui.pigment

/**
 * The Collage room (CONCEPT.md, *Collage*; F10.6), as the design has it: the header — the collage's
 * name, its menu, a picture to add, the way back — the cutting mat in the middle with the tools on
 * its left, and the pieces on its right. With nothing on the mat, it says how pictures come onto it.
 */
@Composable
fun CollageScreen(state: CollageState, onHome: (() -> Unit)? = null) {
    // Into the room: the collage last kept, unless one is open.
    LaunchedEffect(state) { state.onEnter() }
    val images = rememberPieceImages(state)
    Column(Modifier.fillMaxSize()) {
        Header(state, onHome)
        Line(state)
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            Rail(state)
            Box(Modifier.weight(1f).fillMaxHeight()) {
                CollageMat(state, images, Modifier.fillMaxSize())
                if (state.collage.pieces.isEmpty()) Invitation(state, Modifier.align(Alignment.Center))
            }
            PieceList(state, images, Modifier.width(240.dp).fillMaxHeight())
        }
    }
}

@Composable
private fun Header(state: CollageState, onHome: (() -> Unit)?) {
    var renaming by remember { mutableStateOf(false) }
    RoomHeader(
        Room.COLLAGE,
        onHome,
        modifier = Modifier.padding(horizontal = 16.dp).testTag("collage-header"),
        title = {
            if (renaming) {
                NameField(state.name, "collage-name-field", fontSize = 16.sp, modifier = Modifier.width(260.dp)) { next ->
                    renaming = false
                    if (next != null) state.rename(next)
                }
            } else {
                Text(
                    state.name,
                    style = MaterialTheme.typography.body1,
                    color = Design.colors.textSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .widthIn(max = 320.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(role = Role.Button, onClickLabel = "Rename the collage") { renaming = true }
                        .padding(horizontal = 4.dp)
                        .testTag("collage-name"),
                )
            }
        },
        actions = {
            CollageMenu(state)
            Flat("Add a picture…", tag = "collage-add") { state.chooseFile() }
            Flat("Back", tag = "collage-back") { state.leave() }
        },
    )
}

/** *Collage ▾*: a new collage, and the ones kept lately to open again. */
@Composable
private fun CollageMenu(state: CollageState) {
    var open by remember { mutableStateOf(false) }
    Box {
        Flat("Collage ▾", tag = "collage-menu") { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(onClick = {
                open = false
                state.newCollage()
            }, modifier = Modifier.testTag("collage-new")) { Text("New collage") }
            val recent = state.recent().filter { it != state.file }.take(8)
            if (recent.isNotEmpty()) Divider()
            recent.forEach { file ->
                val name = Collages.nameOf(file)
                DropdownMenuItem(onClick = {
                    open = false
                    state.open(file)
                }, modifier = Modifier.testTag("collage-open-$name")) { Text(name) }
            }
        }
    }
}

/** What the last opening, adding or renaming came to. */
@Composable
private fun Line(state: CollageState) {
    Box(Modifier.fillMaxWidth().height(22.dp).padding(horizontal = 24.dp)) {
        state.notice?.let { Text(it, fontSize = 12.sp, color = Room.COLLAGE.pigment.glow, modifier = Modifier.testTag("collage-notice")) }
    }
}

/**
 * The tools down the mat's left, as the design has them — *Move*, the scissors, the tear and the
 * tape, one in hand at a time — and what is done to the chosen piece at once: flip it over, bring
 * it forward, send it back.
 */
@Composable
private fun Rail(state: CollageState) {
    val chosen = state.selectedPiece != null
    val tool = state.tool
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Design.colors.raised)
            .padding(vertical = 10.dp)
            .testTag("collage-rail"),
    ) {
        RailButton("Move", MOVE, on = tool == Tool.MOVE, enabled = true, tag = "rail-move") { state.choose(Tool.MOVE) }
        RailButton("Scissors", SCISSORS, on = tool == Tool.SCISSORS, enabled = true, tag = "rail-scissors") { state.choose(Tool.SCISSORS) }
        RailButton("Tear", TEAR, on = tool == Tool.TEAR, enabled = true, tag = "rail-tear") { state.choose(Tool.TEAR) }
        RailButton("Tape", TAPE, on = tool == Tool.TAPE, enabled = true, tag = "rail-tape") { state.choose(Tool.TAPE) }
        Box(Modifier.padding(vertical = 2.dp).width(40.dp).height(1.dp).background(Design.colors.muted.copy(alpha = 0.3f)))
        RailButton("Flip", FLIP, on = false, enabled = chosen, tag = "rail-flip") { state.flip() }
        RailButton("Forward", FORWARD, on = false, enabled = chosen, tag = "rail-forward") { state.forward() }
        RailButton("Backward", BACKWARD, on = false, enabled = chosen, tag = "rail-backward") { state.backward() }
    }
}

// The rail's glyphs, in an 18 × 18 box: the pointer, open scissors, a sheet torn in two, a strip
// of tape, a figure and its mirror, a piece over the stack and one under it.
private const val MOVE = "M4 2 L4 15 L7.6 11.6 L10 16.4 L12 15.4 L9.7 10.7 L14.6 10.7 Z"
private const val SCISSORS = "M3 13.5 A2.5 2.5 0 1 0 8 13.5 A2.5 2.5 0 1 0 3 13.5 Z M10 13.5 A2.5 2.5 0 1 0 15 13.5 A2.5 2.5 0 1 0 10 13.5 Z M7 11.6 L12.5 2 M11 11.6 L5.5 2"
private const val TEAR = "M2.5 3 H9.5 L8 5.5 L10 8 L8 10.5 L10 13 L8.5 15 H2.5 Z M12 3 H15.5 V15 H12.5 L11 13 L13 10.5 L11 8 L13 5.5 Z"
private const val TAPE = "M2.5 7.5 L14.5 4.5 L14 5.9 L15.3 7 L15.5 8.5 L3.5 11.5 L4 10.1 L2.8 9 Z"
private const val FLIP = "M9 2 V16 M7 4 L2 14 H7 Z M11 4 L16 14 H11 Z"
private const val FORWARD = "M3 16 H15 M3 13 H15 M9 10 V2 M5.5 5.5 L9 2 L12.5 5.5"
private const val BACKWARD = "M3 2 H15 M3 5 H15 M9 8 V16 M5.5 12.5 L9 16 L12.5 12.5"

@Composable
private fun RailButton(label: String, icon: String, on: Boolean, enabled: Boolean, tag: String, onClick: () -> Unit) {
    val glow = Room.COLLAGE.pigment.glow
    val glyph = remember(icon) { PathParser().parsePathString(icon).toPath() }
    val ink = when {
        on -> Design.colors.text
        enabled -> Design.colors.textSoft
        else -> Design.colors.muted.copy(alpha = 0.5f)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .width(60.dp)
            .clip(RoundedCornerShape(8.dp))
            .let { if (on) it.border(1.5.dp, glow, RoundedCornerShape(8.dp)).background(Design.colors.board) else it }
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(vertical = 8.dp)
            .testTag(tag),
    ) {
        Canvas(Modifier.size(18.dp)) {
            val s = size.width / 18f
            drawContext.transform.scale(s, s, Offset.Zero)
            drawPath(glyph, if (on) glow else ink, style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Text(label, fontSize = 11.sp, color = ink, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/** Nothing on the mat yet: how pictures come onto it. */
@Composable
private fun Invitation(state: CollageState, modifier: Modifier) {
    Column(
        modifier
            .width(440.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xCC1C1A17))
            .padding(24.dp)
            .testTag("collage-invitation"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Cut pictures, test a scene", style = Design.hand.copy(fontSize = 30.sp), color = Design.colors.text)
        Text(
            "Send a picture here with To a collage — from a board's card, a concept, a sketch or the Lens — or add one from a file, or paste one. Then cut, tear, tape, move and stack the pieces.",
            style = MaterialTheme.typography.body2,
            color = Design.colors.textSoft,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Flat("Add a picture…", tag = "collage-invite-add") { state.chooseFile() }
            Flat("Paste", tag = "collage-invite-paste") { state.paste() }
        }
    }
}

/** Text-style button: quiet, so the pieces stay the loudest thing. */
@Composable
private fun Flat(label: String, enabled: Boolean = true, tag: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        // Material's own 8 dp of vertical padding would clip a 30-dp button's letters.
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        modifier = Modifier.height(30.dp).testTag(tag),
    ) {
        Text(label, fontSize = 13.sp, color = if (enabled) Design.colors.text else Design.colors.muted)
    }
}
