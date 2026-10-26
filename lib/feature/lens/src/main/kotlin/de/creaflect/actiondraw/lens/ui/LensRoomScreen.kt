package de.creaflect.actiondraw.lens.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.creaflect.actiondraw.Room
import de.creaflect.actiondraw.lens.LensLayout
import de.creaflect.actiondraw.lens.LensRoomState
import de.creaflect.actiondraw.lens.LensSide
import de.creaflect.actiondraw.lens.LensState
import de.creaflect.actiondraw.ui.CrossRoomButton
import de.creaflect.actiondraw.ui.Design
import de.creaflect.actiondraw.ui.LENSES
import de.creaflect.actiondraw.ui.LensControls
import de.creaflect.actiondraw.ui.LensView
import de.creaflect.actiondraw.ui.LocalDesign
import de.creaflect.actiondraw.ui.PigmentDab
import de.creaflect.actiondraw.ui.RoomHeader
import de.creaflect.actiondraw.ui.SelectChip
import de.creaflect.actiondraw.ui.fitScale
import de.creaflect.actiondraw.ui.lensedPng
import de.creaflect.actiondraw.ui.pigment
import de.creaflect.actiondraw.ui.seed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Lens room (CONCEPT.md, *Lens*): the header — what is open, the ways in and out — and the
 * picture through the lens, alone, beside the original or through two lenses at once, with the
 * lens's tray along the bottom. With nothing open yet, an invitation to open one.
 */
@Composable
fun LensRoomScreen(state: LensRoomState, onHome: (() -> Unit)? = null) {
    val picture = state.picture
    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(null, picture) {
        value = null
        value = picture?.let { p -> withContext(Dispatchers.IO) { runCatching { state.decode(p)?.toComposeImageBitmap() }.getOrNull() } }
    }
    // How large the worked view is, so a saved view looks as it does here.
    val shown = remember { mutableStateOf(IntSize.Zero) }
    SaveTheView(state, bitmap, shown)
    val barHeight = remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Header(state, onHome)
        Line(state)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (picture == null) {
                Invitation(state, Modifier.align(Alignment.Center))
            } else {
                val room = with(LocalDensity.current) { barHeight.value.toDp() }
                Views(state, bitmap, shown, Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = room))
                LensControls(state.lens, bitmap, Room.LENS, barHeight)
            }
        }
    }
}

/**
 * A view asked for — to be saved or pinned — is drawn here, where the picture and the design are,
 * off the UI thread, and handed back to the room.
 */
@Composable
private fun SaveTheView(state: LensRoomState, bitmap: ImageBitmap?, shown: MutableState<IntSize>) {
    val design = LocalDesign.current
    val density = LocalDensity.current.density
    LaunchedEffect(state.export, bitmap) {
        val bmp = bitmap ?: return@LaunchedEffect
        if (state.export == null) return@LaunchedEffect
        val area = shown.value
        val scale = if (area.width > 0 && area.height > 0) fitScale(bmp.width.toFloat(), bmp.height.toFloat(), area.width.toFloat(), area.height.toFloat()) else 1f
        val lens = state.lens
        val png = withContext(Dispatchers.Default) { lensedPng(bmp, lens, scale, density, design) }
        state.finish(png)
    }
}

@Composable
private fun Header(state: LensRoomState, onHome: (() -> Unit)?) {
    RoomHeader(
        Room.LENS,
        onHome,
        modifier = Modifier.padding(horizontal = 16.dp).testTag("lens-header"),
        title = {
            Text(
                state.picture?.name ?: "Any picture, seen differently",
                style = MaterialTheme.typography.body1,
                color = Design.colors.textSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).widthIn(max = 260.dp).testTag("lens-title"),
            )
        },
        actions = {
            Flat("Open…", tag = "lens-open") { state.chooseFile() }
            Flat("Paste", tag = "lens-paste") { state.paste() }
            if (state.picture != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 8.dp)) {
                    LensLayout.entries.forEach { layout ->
                        SelectChip(layout.label, state.layout == layout, tag = "lens-layout-" + layout.name.lowercase()) { state.arrange(layout) }
                    }
                }
                Flat("Save…", tag = "lens-save") { state.save() }
                PinButton(state)
                // Into the other rooms with the picture: the ways the hand-off seam has.
                state.routes().forEach { route ->
                    CrossRoomButton(route.to, route.label, Modifier.testTag("lens-to-" + route.to.name.lowercase())) { state.handOff(route.to) }
                }
            }
            Flat("Back", tag = "lens-back") { state.leave() }
        },
    )
}

/** What the last opening, save or pin came to — or that a view is being drawn. */
@Composable
private fun Line(state: LensRoomState) {
    val line = if (state.export != null) "Drawing the view…" else state.notice
    Box(Modifier.fillMaxWidth().height(22.dp).padding(horizontal = 24.dp)) {
        line?.let { Text(it, fontSize = 12.sp, color = Room.LENS.pigment.glow, modifier = Modifier.testTag("lens-notice")) }
    }
}

/** Pin to a board: the view goes onto the board chosen, beside its original. */
@Composable
private fun PinButton(state: LensRoomState) {
    var open by remember { mutableStateOf(false) }
    Box {
        CrossRoomButton(Room.BOARDS, "Pin to a board ▾", Modifier.testTag("lens-pin")) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val boards = state.boards()
            if (boards.isEmpty()) {
                DropdownMenuItem(onClick = { open = false }) { Text("No boards yet") }
            }
            boards.forEach { (name, dir) ->
                DropdownMenuItem(onClick = {
                    open = false
                    state.pinTo(dir)
                }, modifier = Modifier.testTag("lens-pin-" + name)) { Text(name) }
            }
        }
    }
}

/** Nothing open yet: what the room is for, and the two ways in it has of its own. */
@Composable
private fun Invitation(state: LensRoomState, modifier: Modifier) {
    Column(
        modifier.width(460.dp).testTag("lens-invitation"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Any picture, seen differently", style = Design.hand.copy(fontSize = 34.sp), color = Design.colors.text)
        Text(
            "Open one, paste one, or send one here from a board's card or a sketch — then see it through notan, edge, a squint or the glass, with no clock running.",
            style = MaterialTheme.typography.body2,
            color = Design.colors.textSoft,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Flat("Open a picture…", tag = "lens-invite-open") { state.chooseFile() }
            Flat("Paste", tag = "lens-invite-paste") { state.paste() }
        }
    }
}

/** The picture as the room lays it out: through the lens, beside the original, or through two lenses. */
@Composable
private fun Views(state: LensRoomState, bitmap: ImageBitmap?, shown: MutableState<IntSize>, modifier: Modifier) {
    // The worked view tells how large it is; a saved view is drawn to look as it does.
    fun Modifier.worked(on: Boolean) = if (on) onSizeChanged { shown.value = it } else this
    when (state.layout) {
        LensLayout.ALONE -> Side(null, chosen = false, onChoose = null, tag = "lens-side-first", modifier) {
            LensView(state.first, bitmap, state.file, Modifier.fillMaxSize().worked(true))
        }
        LensLayout.BESIDE -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Side("The original", chosen = false, onChoose = null, tag = "lens-side-original", Modifier.weight(1f)) {
                LensView(state.plain, bitmap, state.file, Modifier.fillMaxSize())
            }
            Side(lensName(state.first), chosen = false, onChoose = null, tag = "lens-side-first", Modifier.weight(1f)) {
                LensView(state.first, bitmap, state.file, Modifier.fillMaxSize().worked(true))
            }
        }
        LensLayout.TWO -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(LensSide.FIRST to state.first, LensSide.SECOND to state.second).forEachIndexed { i, (side, lens) ->
                val chosen = state.active == side
                Side("${i + 1} · " + lensName(lens), chosen, onChoose = { state.choose(side) }, tag = "lens-side-" + side.name.lowercase(), Modifier.weight(1f)) {
                    LensView(lens, bitmap, state.file, Modifier.fillMaxSize().worked(chosen))
                }
            }
        }
    }
}

/** A lens's name, as its slide has it. */
private fun lensName(lens: LensState): String = LENSES.first { it.first == lens.viewMode }.second.let { if (it == "None") "No lens" else it }

/**
 * One side of the view: its [label] above it — chosen in the room's pigment, a click choosing it
 * where there is a choice — and the picture under it, framed while it is the one being worked.
 */
@Composable
private fun Side(label: String?, chosen: Boolean, onChoose: (() -> Unit)?, tag: String, modifier: Modifier, content: @Composable () -> Unit) {
    val glow = Room.LENS.pigment.glow
    Column(modifier.testTag(tag), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .let { if (onChoose != null) it.clickable(role = Role.Tab, onClickLabel = label, onClick = onChoose) else it }
                    .semantics(mergeDescendants = true) { selected = chosen }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .testTag("$tag-label"),
            ) {
                if (chosen) PigmentDab(Room.LENS.pigment, size = 10.dp, seed = Room.LENS.seed)
                Text(label, fontSize = 13.sp, fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal, color = if (chosen) Design.colors.text else Design.colors.textSoft)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().let { if (chosen) it.border(2.dp, glow) else it }) { content() }
    }
}

/** Text-style button: quiet, so the picture stays the loudest thing. */
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
