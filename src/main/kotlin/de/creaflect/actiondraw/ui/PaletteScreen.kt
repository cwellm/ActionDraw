package de.creaflect.actiondraw.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * One well of the palette: its [room], the [verb] lettered under the room's name, the one [line]
 * the centre well says about it, and its next step — the centre's button. A room that is not
 * built yet has no [action]: its well is dry, and cannot be chosen.
 */
data class PaletteWell(val room: Room, val verb: String, val line: String, val action: String?) {
    val open: Boolean get() = action != null
}

/** The selection [step] wells round the palette from [from], passing over the dry ones. */
fun stepRoom(wells: List<PaletteWell>, from: Room, step: Int): Room {
    val open = PALETTE_ORDER.filter { room -> wells.any { it.room == room && it.open } }
    if (open.isEmpty()) return from
    val i = open.indexOf(from)
    if (i < 0) return open.first()
    return open[Math.floorMod(i + step, open.size)]
}

/** The arrow keys turn the palette: right and down go clockwise, left and up back. */
fun paletteStep(key: Key): Int? = when (key) {
    Key.DirectionRight, Key.DirectionDown -> 1
    Key.DirectionLeft, Key.DirectionUp -> -1
    else -> null
}

/**
 * Home: a porcelain mixing palette on the table under the lamp (CONCEPT.md, *Home: the palette*).
 * Six wells round the rim, one per room, each holding its pigment; the centre well is the mix —
 * the selected room in a sentence and its next step. A click selects a well, a click on the
 * selected one opens it; the arrow keys and Tab turn the selection, Enter opens. Keyboard focus
 * always sits on the selected well, so Enter means the same thing as a second click.
 */
@Composable
fun PaletteScreen(
    wells: List<PaletteWell>,
    selected: Room,
    onSelect: (Room) -> Unit,
    onOpen: (Room) -> Unit,
    /** Drawn over the whole palette, last: the top right corner's links and what they open. */
    corner: @Composable () -> Unit = {},
    /** Where each well's centre lies in the window — for the pigment to spread from. */
    onWellPlaced: (Room, Offset) -> Unit = { _, _ -> },
) {
    val byRoom = wells.associateBy { it.room }
    val requesters = remember { PALETTE_ORDER.associateWith { FocusRequester() } }
    LaunchedEffect(selected) {
        if (byRoom[selected]?.open == true) runCatching { requesters.getValue(selected).requestFocus() }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        0f to Color(0xFF2B2622), 0.48f to Color(0xFF1C1916), 1f to Color(0xFF141210),
                        center = Offset(size.width / 2, size.height * 0.52f),
                        radius = maxOf(size.width, size.height) * 0.62f,
                    ),
                )
            }
            .grain()
            .onKeyEvent { event ->
                val step = if (event.type == KeyEventType.KeyDown) paletteStep(event.key) else null
                if (step != null) onSelect(stepRoom(wells, selected, step))
                step != null
            }
            .testTag("palette"),
    ) {
        val disk = min(maxWidth * 0.46f, maxHeight * 0.64f).coerceIn(420.dp, 600.dp)
        val cx = maxWidth / 2
        val cy = maxHeight * 0.52f

        if (maxHeight > 600.dp && maxWidth > 900.dp) {
            Canvas(Modifier.fillMaxSize()) {
                drawPencil(Offset(48.dp.toPx(), size.height - 70.dp.toPx()), 300.dp.toPx(), 18.dp.toPx(), -11f, Color(0xFF44525F))
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp),
        ) {
            PigmentDab(Room.PRACTICE.pigment, size = 16.dp, seed = Room.PRACTICE.seed)
            Text("ActionDraw", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Atelier.Text)
        }

        Porcelain(disk, Modifier.offset(cx - disk / 2, cy - disk / 2))

        PALETTE_ORDER.forEachIndexed { i, room ->
            val well = byRoom[room] ?: return@forEachIndexed
            val a = -PI / 2 + 2 * PI * i / PALETTE_ORDER.size
            val wellSize = disk * 0.204f
            val ring = disk * 0.353f
            val x = cx + ring * cos(a).toFloat()
            val y = cy + ring * sin(a).toFloat()
            Well(
                well,
                selected = room == selected,
                requester = requesters.getValue(room),
                onClick = { if (room == selected) onOpen(room) else onSelect(room) },
                onFocused = { if (room != selected) onSelect(room) },
                modifier = Modifier
                    .offset(x - wellSize / 2, y - wellSize / 2)
                    .size(wellSize)
                    .onGloballyPositioned { onWellPlaced(room, it.boundsInRoot().center) },
            )
            WellLabel(well, room == selected, a, disk * 0.595f, cx, cy, onSelect = { onSelect(room) })
        }

        byRoom[selected]?.let { well ->
            val centre = disk * 0.42f
            Mix(well, centre, Modifier.offset(cx - centre / 2, cy - centre / 2), onOpen = { onOpen(well.room) })
        }

        Text(
            "Esc in any room brings you back here",
            style = AtelierType.Hand,
            color = Atelier.Muted,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 32.dp).rotate(-3f),
        )

        // Last, so what the corner opens (a sheet over a dimmed palette) lies over everything.
        corner()
    }
}

/** The palette itself: a porcelain disk under the lamp, with a raised lip. */
@Composable
private fun Porcelain(size: Dp, modifier: Modifier) {
    Canvas(modifier.size(size).lampShadow(Lift.HELD, CircleShape, alpha = 0.6f)) {
        val r = this.size.minDimension / 2
        drawCircle(
            Brush.radialGradient(
                0f to Color.White, 0.42f to Color(0xFFF5F1EA), 1f to Color(0xFFE3DCD0),
                center = Offset(this.size.width * 0.38f, this.size.height * 0.3f),
                radius = r * 1.5f,
            ),
            r,
        )
        drawCircle(Brush.verticalGradient(0.6f to Color(0x005A4632), 1f to Color(0x335A4632)), r)
        drawCircle(Color(0x223C2D1E), r - 14.dp.toPx(), style = Stroke(width = 1.5.dp.toPx()))
        drawCircle(Color.White.copy(alpha = 0.85f), r - 13.dp.toPx(), center + Offset(0f, 1.dp.toPx()), style = Stroke(width = 1.dp.toPx()))
    }
}

@Composable
private fun Well(
    well: PaletteWell,
    selected: Boolean,
    requester: FocusRequester,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    // The selected dab is wet: a ripple runs out from it, over and over — unless motion is reduced.
    // Read only while drawing, so the ripple redraws the well without recomposing it.
    val ripple = if (LocalReducedMotion.current) null else rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f))),
    )
    val glow = well.room.pigment.glow
    val click by rememberUpdatedState(onClick)
    Box(
        modifier
            .graphicsLayer {
                val s = if (hovered && well.open) 1.05f else 1f
                scaleX = s
                scaleY = s
            }
            .drawBehind {
                val r = size.minDimension / 2
                if (selected) {
                    if (ripple != null) {
                        val p = ripple.value
                        drawCircle(glow.copy(alpha = 0.8f * (1 - p)), r + 6.dp.toPx() + 16.dp.toPx() * p, style = Stroke(2.dp.toPx()))
                    }
                    drawCircle(glow, r + 3.dp.toPx(), style = Stroke(4.dp.toPx()))
                }
                drawWell(center, r)
                val dab = Size(size.width * 0.71f, size.height * 0.64f)
                drawRoomDab(well.room, center - Offset(dab.width / 2, dab.height / 2), dab, wet = well.open)
            }
            // Keyboard focus chooses a well (Tab); a pointer does not focus, it taps. A clickable
            // would take focus on the press, and the focus would choose the well before the
            // release opened it: one click would open every room.
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .focusable(enabled = well.open, interactionSource = source)
            .hoverable(source, enabled = well.open)
            .onKeyEvent { event ->
                val press = event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Spacebar)
                if (press && well.open) click()
                press && well.open
            }
            .clip(CircleShape)
            .pointerInput(well.room) { detectTapGestures { if (well.open) click() } }
            .semantics {
                role = Role.Button
                contentDescription = well.room.label + if (well.open) "" else " — not built yet"
                if (well.open) onClick { click(); true } else disabled()
            }
            .testTag("well-${well.room.name.lowercase()}"),
    )
}

/** A room's name outside the rim, its verb lettered under it in the room's glow. */
@Composable
private fun WellLabel(well: PaletteWell, selected: Boolean, angle: Double, distance: Dp, cx: Dp, cy: Dp, onSelect: () -> Unit) {
    val width = 220.dp
    val c = cos(angle).toFloat()
    val s = sin(angle).toFloat()
    val px = cx + distance * c
    val py = cy + distance * s
    val (x, y, align) = when {
        kotlin.math.abs(c) < 0.1f -> Triple(px - width / 2, if (s < 0) py - 44.dp else py - 14.dp, Alignment.CenterHorizontally)
        c > 0 -> Triple(px, py - 28.dp, Alignment.Start)
        else -> Triple(px - width, py - 28.dp, Alignment.End)
    }
    val textAlign = when (align) {
        Alignment.Start -> TextAlign.Start
        Alignment.End -> TextAlign.End
        else -> TextAlign.Center
    }
    Column(
        horizontalAlignment = align,
        modifier = Modifier
            .offset(x, y)
            .width(width)
            .pointerInput(well.open) { detectTapGestures { if (well.open) onSelect() } }
            .testTag("label-${well.room.name.lowercase()}"),
    ) {
        Text(
            well.room.label,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            color = Atelier.Text.copy(alpha = if (selected) 1f else 0.72f),
            textAlign = textAlign,
        )
        Text(
            if (well.open) well.verb else "coming",
            style = AtelierType.Hand.copy(fontSize = 23.sp),
            color = if (well.open) well.room.pigment.glow else Atelier.Muted,
            textAlign = textAlign,
        )
    }
}

/** The centre well: the selected room in one sentence, washed in its pigment, and its next step. */
@Composable
private fun Mix(well: PaletteWell, size: Dp, modifier: Modifier, onOpen: () -> Unit) {
    val pigment = well.room.pigment
    Box(
        modifier
            .size(size)
            .drawBehind {
                val r = this.size.minDimension / 2
                drawWell(center, r)
                drawCircle(Brush.radialGradient(0f to pigment.mass.copy(alpha = 0.3f), 1f to Color.Transparent, center = center + Offset(0f, r * 0.12f), radius = r * 0.8f), r)
            }
            .testTag("palette-mix"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = size * 0.08f),
        ) {
            Text(well.room.label, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Atelier.Ink)
            Text(well.line, fontSize = 12.5.sp, lineHeight = 16.sp, color = Atelier.InkQuiet, textAlign = TextAlign.Center)
            well.action?.let { action ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    // The button may lie wider than the text above it, across the bowl.
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .wrapContentWidth(unbounded = true)
                        .widthIn(max = size * 0.96f)
                        .clip(RoundedCornerShape(50))
                        .background(Atelier.Ink)
                        .clickable(role = Role.Button, onClick = onOpen)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("palette-open"),
                ) {
                    PigmentDab(pigment, size = 9.dp, seed = well.room.seed)
                    Text(action, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Atelier.Paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
