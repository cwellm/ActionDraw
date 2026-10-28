package de.creaflect.actiondraw.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import de.creaflect.actiondraw.Room

/**
 * [content] in [room]'s colours: Material's own controls — a chosen chip, a slider, a switch, a
 * filled button — take the room's pigment, as everything held or chosen in a room does (CONCEPT.md,
 * *Colour is pigment*). A design system's Material palette leads with one colour for the whole
 * app; whatever shows a room wraps it in this, so a control never wears another room's paint.
 * Without a room — the palette — the design's own colours stay.
 */
@Composable
fun RoomTheme(room: Room?, content: @Composable () -> Unit) {
    if (room == null) return content()
    val pigment = room.pigment
    MaterialTheme(
        colors = MaterialTheme.colors.copy(primary = pigment.glow, primaryVariant = pigment.mass),
        content = content,
    )
}
