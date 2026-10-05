package de.creaflect.actiondraw.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * What every room has in common (CONCEPT.md, *Home: the palette*): the way back to the palette,
 * the room's dab of pigment, and its 2 px line along the top — where the pigment settled after
 * the bloom that opened the room.
 */

/** The room's pigment as a 2 px line, laid along the top edge of the room. */
@Composable
fun RoomLine(room: Room, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(2.dp).background(room.pigment.mass).testTag("room-line-${room.name.lowercase()}"))
}

/** A small dab of a room's pigment — before its name, or before a button leading into it. */
@Composable
fun PigmentDab(pigment: Pigment, size: Dp = 14.dp, seed: Int = 0, wet: Boolean = true, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size, size * 0.88f)) {
        drawDab(Offset.Zero, this.size, pigment, seed, wet)
    }
}

/** The palette in miniature — a ring with a dot of each pigment — leading back home. */
@Composable
fun HomeButton(onHome: () -> Unit, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Atelier.Board)
            .clickable(role = Role.Button, onClickLabel = "Back to the palette", onClick = onHome)
            .semantics { contentDescription = "Back to the palette" }
            .testTag("home"),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.55f)) {
            val r = this.size.minDimension / 2
            val c = Offset(r, r)
            drawCircle(Atelier.TextSoft, r * 0.82f, c, style = Stroke(width = r * 0.14f))
            PALETTE_ORDER.forEachIndexed { i, room ->
                val a = -PI / 2 + 2 * PI * i / PALETTE_ORDER.size
                val p = c + Offset((r * 0.5f * cos(a)).toFloat(), (r * 0.5f * sin(a)).toFloat())
                drawCircle(room.pigment.glow, r * 0.16f, p)
            }
        }
    }
}

/**
 * The header of a room: home, the room's dab and name, then [title] (what is open in the room)
 * and, at the far end, [actions].
 */
@Composable
fun RoomHeader(
    room: Room,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    title: @Composable RowScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("room-header"),
    ) {
        HomeButton(onHome)
        PigmentDab(room.pigment, seed = room.seed)
        Text(
            room.label,
            style = MaterialTheme.typography.h6.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
            color = Atelier.Text,
        )
        title()
        Spacer(Modifier.weight(1f))
        actions()
    }
}

/** The wells round the palette, clockwise from the top. */
val PALETTE_ORDER = listOf(Room.PRACTICE, Room.LENS, Room.SKETCH, Room.COLLAGE, Room.CONCEPTS, Room.BOARDS)

/** A dab's seed per room, so each room's dab keeps its own shape everywhere it appears. */
val Room.seed: Int get() = ordinal * 31 + 5

/** Draws [room]'s dab filling [size] at [topLeft]. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoomDab(room: Room, topLeft: Offset, size: Size, wet: Boolean = true) =
    drawDab(topLeft, size, room.pigment, room.seed, wet)
