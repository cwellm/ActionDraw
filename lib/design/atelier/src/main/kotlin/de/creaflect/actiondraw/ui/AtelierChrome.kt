package de.creaflect.actiondraw.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.creaflect.actiondraw.Room
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * What every room has in common, as the atelier draws it (CONCEPT.md, *Home: the palette*): the
 * way back to the palette, the room's dab of pigment, and its 2 px line along the top — where the
 * pigment settled after the bloom that opened the room.
 */
object AtelierChrome : Chrome {
    /** The room's pigment as a 2 px line, laid along the top edge of the room. */
    @Composable
    override fun RoomLine(room: Room, modifier: Modifier) {
        Box(modifier.fillMaxWidth().height(2.dp).background(Atelier.pigmentOf(room).mass).testTag("room-line-${room.name.lowercase()}"))
    }

    @Composable
    override fun PigmentDab(pigment: Pigment, size: Dp, seed: Int, wet: Boolean, modifier: Modifier) {
        Canvas(modifier.size(size, size * 0.88f)) {
            with(AtelierMaterials) { drawDab(Offset.Zero, this@Canvas.size, pigment, seed, wet) }
        }
    }

    /** The palette in miniature — a ring with a dot of each pigment — leading back home. */
    @Composable
    override fun HomeButton(onHome: () -> Unit, size: Dp, modifier: Modifier) {
        Box(
            modifier
                .size(size)
                .clip(CircleShape)
                .background(Atelier.colors.board)
                .clickable(role = Role.Button, onClickLabel = "Back to the palette", onClick = onHome)
                .semantics { contentDescription = "Back to the palette" }
                .testTag("home"),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(size * 0.55f)) {
                val r = this.size.minDimension / 2
                val c = Offset(r, r)
                drawCircle(Atelier.colors.textSoft, r * 0.82f, c, style = Stroke(width = r * 0.14f))
                PALETTE_ORDER.forEachIndexed { i, room ->
                    val a = -PI / 2 + 2 * PI * i / PALETTE_ORDER.size
                    val p = c + Offset((r * 0.5f * cos(a)).toFloat(), (r * 0.5f * sin(a)).toFloat())
                    drawCircle(Atelier.pigmentOf(room).glow, r * 0.16f, p)
                }
            }
        }
    }

    @Composable
    override fun RoomHeader(
        room: Room,
        onHome: (() -> Unit)?,
        modifier: Modifier,
        title: @Composable RowScope.() -> Unit,
        actions: @Composable RowScope.() -> Unit,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("room-header"),
        ) {
            if (onHome != null) HomeButton(onHome, 40.dp, Modifier)
            PigmentDab(Atelier.pigmentOf(room), 14.dp, room.seed, true, Modifier)
            Text(
                room.label,
                style = MaterialTheme.typography.h6.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                color = Atelier.colors.text,
            )
            title()
            Spacer(Modifier.weight(1f))
            actions()
        }
    }

    @Composable
    override fun CrossRoomButton(room: Room, label: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
            modifier = modifier.height(32.dp),
        ) {
            PigmentDab(Atelier.pigmentOf(room), 11.dp, room.seed, enabled, Modifier)
            Spacer(Modifier.width(7.dp))
            // In the surface's own ink, light on the table and dark on cork or papyrus.
            Text(label, style = MaterialTheme.typography.body2, color = MaterialTheme.colors.onSurface.copy(alpha = if (enabled) 0.87f else 0.38f), maxLines = 1)
        }
    }
}
