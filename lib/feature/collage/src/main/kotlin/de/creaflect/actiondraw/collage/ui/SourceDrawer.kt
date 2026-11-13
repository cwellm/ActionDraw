package de.creaflect.actiondraw.collage.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.creaflect.actiondraw.Room
import de.creaflect.actiondraw.collage.CollageState
import de.creaflect.actiondraw.collage.DrawerSource
import de.creaflect.actiondraw.ui.Design
import de.creaflect.actiondraw.ui.PigmentDab
import de.creaflect.actiondraw.ui.pigment
import de.creaflect.actiondraw.ui.seed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** How tall the drawer's prints are. */
private val PRINT = 64.dp

/** A picture carried out of the drawer: its file, its print, and where the pointer is in the room. */
internal data class Carry(val file: File, val image: ImageBitmap?, val at: Offset)

/**
 * The source drawer under the mat (F10.6), as the design has it: on its left what it draws from —
 * a board or a concept, each with its room's dab and a menu of the others — and beside it that
 * source's pictures as small prints. A click lays one on the mat; dragged onto the mat, it lies where
 * it is let go. As often as you like: each time a new piece to cut from. [origin] is where the room
 * lies in the window, so a print carried is followed in the room's own coordinates.
 */
@Composable
internal fun SourceDrawer(state: CollageState, origin: () -> Offset, onCarry: (Carry?) -> Unit, onDrop: (Carry) -> Unit, modifier: Modifier = Modifier) {
    val source = state.source
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.height(PRINT + 16.dp).testTag("collage-drawer"),
    ) {
        Column(Modifier.width(170.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SourceTab(
                Room.BOARDS,
                label = (source as? DrawerSource.Board)?.name ?: "Boards",
                chosen = source is DrawerSource.Board,
                tag = "drawer-boards",
                none = "No boards yet",
                entries = { state.boards().map { ref -> ref.name to { state.drawFrom(DrawerSource.Board(ref)) } } },
            )
            SourceTab(
                Room.CONCEPTS,
                label = (source as? DrawerSource.Concept)?.name ?: "Concepts",
                chosen = source is DrawerSource.Concept,
                tag = "drawer-concepts",
                none = "No concepts yet",
                entries = { state.concepts().map { ref -> ref.name to { state.drawFrom(DrawerSource.Concept(ref)) } } },
            )
        }
        val pictures = state.sourcePictures
        if (pictures.isEmpty()) {
            Text(
                if (source == null) "Pictures from your boards and concepts show here, to cut from" else "No pictures in ${source.name}",
                fontSize = 12.sp,
                color = Design.colors.muted,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()),
        ) {
            pictures.forEachIndexed { i, file -> Print(state, file, i, origin, onCarry, onDrop) }
        }
    }
}

/** What the drawer can draw from of one kind — boards, or concepts — under its room's dab: a menu of them, the one chosen marked. */
@Composable
private fun SourceTab(room: Room, label: String, chosen: Boolean, tag: String, none: String, entries: () -> List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (chosen) Design.colors.raised else Design.colors.board)
                .clickable(role = Role.Button, onClickLabel = "Draw from ${room.label}") { open = true }
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag(tag),
        ) {
            PigmentDab(room.pigment, size = 10.dp, seed = room.seed)
            Text(
                "$label ▾",
                fontSize = 13.sp,
                fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                color = if (chosen) Design.colors.text else Design.colors.textSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val all = entries()
            if (all.isEmpty()) DropdownMenuItem(onClick = { open = false }, enabled = false) { Text(none) }
            all.forEach { (name, choose) ->
                DropdownMenuItem(onClick = {
                    open = false
                    choose()
                }, modifier = Modifier.testTag("$tag-$name")) { Text(name) }
            }
        }
    }
}

/** One picture in the drawer, as a small print on its paper: clicked, laid on the mat; dragged, carried there. */
@Composable
private fun Print(state: CollageState, file: File, index: Int, origin: () -> Offset, onCarry: (Carry?) -> Unit, onDrop: (Carry) -> Unit) {
    val image by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { state.pictures.thumbnail(file, maxSize = 160)?.toComposeImageBitmap() }
    }
    val wide = image?.let { (PRINT.value * it.width / it.height.coerceAtLeast(1)).coerceIn(40f, 120f) } ?: PRINT.value
    var at by remember { mutableStateOf(Offset.Zero) }
    var carry by remember { mutableStateOf<Carry?>(null) }
    val shown by rememberUpdatedState(image)
    Box(
        Modifier
            .size(wide.dp + 6.dp, PRINT + 6.dp)
            .background(Design.colors.paper)
            .padding(3.dp)
            .onGloballyPositioned { at = it.positionInRoot() - origin() }
            .pointerInput(file) {
                detectDragGestures(
                    onDragStart = { from ->
                        carry = Carry(file, shown, at + from)
                        onCarry(carry)
                    },
                    onDragEnd = {
                        carry?.let(onDrop)
                        carry = null
                        onCarry(null)
                    },
                    onDragCancel = {
                        carry = null
                        onCarry(null)
                    },
                ) { change, _ ->
                    change.consume()
                    carry = carry?.copy(at = at + change.position)
                    onCarry(carry)
                }
            }
            .clickable(role = Role.Button, onClickLabel = "Lay ${file.nameWithoutExtension} on the mat") { state.addPicture(file) }
            .testTag("drawer-picture-$index"),
    ) {
        image?.let { Image(it, file.nameWithoutExtension, Modifier.fillMaxWidth().fillMaxHeight(), contentScale = ContentScale.Crop) }
    }
}

/** The print carried, under the pointer, on its way to the mat. */
@Composable
internal fun CarriedPrint(carry: Carry) {
    val image = carry.image ?: return
    val density = LocalDensity.current
    val wide = (90f * image.width / image.height.coerceAtLeast(1)).coerceIn(50f, 160f)
    val (w, h) = with(density) { wide.dp.toPx() to 90.dp.toPx() }
    Spacer(
        Modifier
            .offset { IntOffset((carry.at.x - w / 2).roundToInt(), (carry.at.y - h / 2).roundToInt()) }
            .size(wide.dp, 90.dp)
            .graphicsLayer { alpha = 0.85f }
            .background(Design.colors.paper),
    )
    Image(
        image,
        null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .offset { IntOffset((carry.at.x - w / 2).roundToInt(), (carry.at.y - h / 2).roundToInt()) }
            .size(wide.dp, 90.dp)
            .padding(3.dp)
            .graphicsLayer { alpha = 0.85f },
    )
}
