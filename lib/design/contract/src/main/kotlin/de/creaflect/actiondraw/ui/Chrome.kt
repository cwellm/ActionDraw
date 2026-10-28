package de.creaflect.actiondraw.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.Room

/**
 * What every room has in common (CONCEPT.md, *Home: the palette*), as the design system draws it:
 * the way back to the palette, the room's dab of pigment, its line along the top, its header, and
 * the button that hands something on to another room. Screens call the functions below, which ask
 * the design in use.
 */
interface Chrome {
    @Composable
    fun RoomLine(room: Room, modifier: Modifier)

    @Composable
    fun PigmentDab(pigment: Pigment, size: Dp, seed: Int, wet: Boolean, modifier: Modifier)

    @Composable
    fun HomeButton(onHome: () -> Unit, size: Dp, modifier: Modifier)

    @Composable
    fun RoomHeader(
        room: Room,
        onHome: (() -> Unit)?,
        modifier: Modifier,
        title: @Composable RowScope.() -> Unit,
        actions: @Composable RowScope.() -> Unit,
    )

    @Composable
    fun CrossRoomButton(room: Room, label: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit)
}

/** The room's pigment as a line along the top edge of the room. */
@Composable
fun RoomLine(room: Room, modifier: Modifier = Modifier) = Design.chrome.RoomLine(room, modifier)

/** A small dab of a room's pigment — before its name, or before a button leading into it. */
@Composable
fun PigmentDab(pigment: Pigment, size: Dp = 14.dp, seed: Int = 0, wet: Boolean = true, modifier: Modifier = Modifier) =
    Design.chrome.PigmentDab(pigment, size, seed, wet, modifier)

/** The palette in miniature, leading back home. */
@Composable
fun HomeButton(onHome: () -> Unit, size: Dp = 40.dp, modifier: Modifier = Modifier) = Design.chrome.HomeButton(onHome, size, modifier)

/**
 * The header of a room: home, the room's dab and name, then [title] (what is open in the room)
 * and, at the far end, [actions]. Without [onHome] — a room shown on its own — there is no way home.
 */
@Composable
fun RoomHeader(
    room: Room,
    onHome: (() -> Unit)?,
    modifier: Modifier = Modifier,
    title: @Composable RowScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) = Design.chrome.RoomHeader(room, onHome, modifier, title, actions)

/**
 * A hand-off into another room — *To a board*, *Sketch*, *Pin* — marked with that room's dab, so
 * where a thing goes is seen before it is read.
 */
@Composable
fun CrossRoomButton(room: Room, label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) =
    Design.chrome.CrossRoomButton(room, label, modifier, enabled, onClick)
