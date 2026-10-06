package de.creaflect.actiondraw.sketch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.image.ThumbCache
import de.creaflect.actiondraw.sketch.SketchState
import de.creaflect.actiondraw.ui.Atelier
import de.creaflect.actiondraw.ui.Room
import de.creaflect.sketch.Paper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Beside the page, what it is drawn on: the papers as swatches of their own tooth, the shades as
 * chips — either changes the page under its strokes — and the reference from a session, if one
 * came along.
 */
@Composable
internal fun PaperPanel(state: SketchState, thumbs: ThumbCache, largeReference: Boolean, onEnlarge: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .width(232.dp)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(end = 16.dp, top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(color = Atelier.Board, shape = RoundedCornerShape(10.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Overline("Paper")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Paper.entries.forEach { paper ->
                        PaperSwatch(paper, state.shade, chosen = state.tooth == paper, Modifier.weight(1f)) { state.setTooth(paper) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Shade", style = MaterialTheme.typography.caption, color = Atelier.Muted)
                    Spacer(Modifier.weight(1f))
                    SketchState.SHADES.forEach { (name, argb) ->
                        ShadeChip(name, argb, chosen = state.shade == argb) { state.setShade(argb) }
                    }
                }
            }
        }
        state.reference?.let { ReferenceCard(state, it, thumbs, largeReference, onEnlarge) }
    }
}

@Composable
private fun Overline(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.overline, color = Atelier.Muted)
}

/** A swatch of the paper itself: its tooth drawn by the engine that draws the page, a little stronger. */
@Composable
private fun PaperSwatch(paper: Paper, shade: Int, chosen: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val madder = Room.SKETCH.pigment
    Column(
        modifier
            .selectable(chosen, role = Role.RadioButton, onClick = onClick)
            .testTag("sketch-paper-${paper.name}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.05f)
                .clip(RoundedCornerShape(3.dp))
                .border(if (chosen) 2.dp else 1.dp, if (chosen) madder.glow else Atelier.Line, RoundedCornerShape(3.dp)),
        ) {
            drawRect(Color(shade))
            drawIntoCanvas { paper.grain.shade(it.nativeCanvas, 0f, 0f, size.width, size.height, scale = 1f, strength = SWATCH_TOOTH) }
        }
        Text(
            paper.label,
            style = MaterialTheme.typography.caption,
            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
            color = if (chosen) Atelier.Text else Atelier.Muted,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/** The tooth on a swatch: the page shows it faintly; a swatch is small, so a little more. */
private const val SWATCH_TOOTH = 0.32f

@Composable
private fun ShadeChip(name: String, argb: Int, chosen: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(Color(argb))
            .border(if (chosen) 2.dp else 1.dp, if (chosen) Room.SKETCH.pigment.glow else Atelier.Line, CircleShape)
            .selectable(chosen, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name }
            .testTag("sketch-shade-${name.lowercase()}"),
    )
}

/** The reference from a session, kept beside the page: larger over the page on a click, or put away. */
@Composable
private fun ReferenceCard(state: SketchState, file: File, thumbs: ThumbCache, large: Boolean, onEnlarge: () -> Unit) {
    val bitmap by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { thumbs.load(file, maxSize = 900) }
    }
    Surface(color = Atelier.Board, shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Overline("Reference")
            bitmap?.let { bmp ->
                Image(
                    bitmap = bmp,
                    contentDescription = file.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).clickable { onEnlarge() }.testTag("sketch-reference"),
                )
            }
            Row {
                TextButton(onClick = onEnlarge) { Text(if (large) "Smaller" else "Larger", style = MaterialTheme.typography.body2) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { state.reference = null }, modifier = Modifier.testTag("sketch-reference-away")) {
                    Text("Put away", style = MaterialTheme.typography.body2)
                }
            }
        }
    }
}

/** The reference large, over the page's top right corner; a click puts it back beside the page. */
@Composable
internal fun LargeReference(file: File, thumbs: ThumbCache, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { thumbs.load(file, maxSize = 900) }
    }
    val bmp = bitmap ?: return
    Surface(elevation = 8.dp, shape = RoundedCornerShape(4.dp), color = Atelier.Paper, modifier = modifier.padding(12.dp)) {
        Image(
            bitmap = bmp,
            contentDescription = file.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.padding(6.dp).size(520.dp).clickable { onClose() }.testTag("sketch-reference-large"),
        )
    }
}
