package de.creaflect.actiondraw.board.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuRepresentation
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.rememberPopupPositionProviderAtPosition
import de.creaflect.actiondraw.board.BoardEditor
import de.creaflect.actiondraw.board.BoardState
import de.creaflect.actiondraw.board.ImageItem
import de.creaflect.actiondraw.ui.Design
import de.creaflect.actiondraw.ui.Lift
import de.creaflect.actiondraw.ui.LocalDesign
import de.creaflect.actiondraw.ui.Room
import de.creaflect.actiondraw.ui.drawRoomDab
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The ring menu (CONCEPT.md, *Boards*): a right click on a card opens six porcelain buttons round
 * it, each a way out of the board into a room, marked with that room's pigment — Draw these,
 * Sketch over, To a collage, To a concept, Tag, Group. The middle one opens the full menu, which
 * keeps every other command. A context menu's *representation*, so `ContextMenuArea` still decides
 * when it opens and what the full menu holds; [list] is how that full menu is shown.
 */
class RingMenu(private val state: BoardState, private val list: ContextMenuRepresentation) : ContextMenuRepresentation {
    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    override fun Representation(state: ContextMenuState, items: () -> List<ContextMenuItem>) {
        val status = state.status
        if (status !is ContextMenuState.Status.Open) return
        // What is showing for this opening: the ring, the concepts to choose from, or the full menu.
        var step by remember(status) { mutableStateOf(RingStep.RING) }
        if (step == RingStep.LIST) {
            list.Representation(state, items)
            return
        }
        val close = { state.status = ContextMenuState.Status.Closed }
        Popup(
            popupPositionProvider = rememberPopupPositionProviderAtPosition(positionPx = status.rect.center, alignment = Alignment.Center),
            onDismissRequest = close,
            properties = PopupProperties(focusable = true),
        ) {
            when (step) {
                RingStep.RING -> Ring(this@RingMenu.state, close, onConcepts = { step = RingStep.CONCEPTS }, onMore = { step = RingStep.LIST })
                RingStep.CONCEPTS -> ConceptChoice(this@RingMenu.state, close, onBack = { step = RingStep.RING })
                RingStep.LIST -> Unit
            }
        }
    }
}

private enum class RingStep { RING, CONCEPTS, LIST }

/** One way out of the board: where it leads, what it is called, and whether it can be taken now. */
private class RingAction(val tag: String, val room: Room, val label: String, val enabled: Boolean, val run: () -> Unit)

private val RingRadius = 94.dp
private val ButtonSize = 54.dp
private val RingBox = 320.dp

@Composable
private fun Ring(board: BoardState, close: () -> Unit, onConcepts: () -> Unit, onMore: () -> Unit) {
    val pictures = board.selectedItems.filterIsInstance<ImageItem>()
    val single = pictures.singleOrNull()?.takeIf { board.selection.size == 1 }
    val own = board.selection.any { !board.isBorrowed(it) }
    val actions = listOf(
        RingAction("ring-draw", Room.PRACTICE, "Draw these", board.selectedImageFiles.isNotEmpty()) { board.drawSelection() },
        RingAction(
            "ring-sketch", Room.SKETCH,
            if (single != null && board.sketchOf(single) != null) "Continue sketch" else "Sketch over",
            single != null,
        ) { single?.let(board::sketchOver) },
        // Collage is a dry well until it is built (M9).
        RingAction("ring-collage", Room.COLLAGE, "To a collage", false) {},
        RingAction("ring-concept", Room.CONCEPTS, "To a concept", own && board.conceptsAvailable().isNotEmpty()) { onConcepts() },
        RingAction("ring-tag", Room.BOARDS, "Tag", board.selection.isNotEmpty()) { board.openEditor(BoardEditor.EditTags(board.selection)) },
        RingAction("ring-group", Room.BOARDS, "Group", own) { board.startGrouping() },
    )
    val focus = LocalFocusManager.current
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Box(
        Modifier
            .size(RingBox)
            .testTag("ring-menu")
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Escape -> { close(); true }
                    // Round the ring with the arrows, as Tab does.
                    Key.DirectionRight, Key.DirectionDown -> { focus.moveFocus(FocusDirection.Next); true }
                    Key.DirectionLeft, Key.DirectionUp -> { focus.moveFocus(FocusDirection.Previous); true }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        actions.forEachIndexed { i, action ->
            val angle = -PI / 2 + 2 * PI * i / actions.size
            val dx = RingRadius * cos(angle).toFloat()
            val dy = RingRadius * sin(angle).toFloat()
            RingButton(
                action.room, action.label, action.enabled,
                modifier = Modifier
                    .offset(dx, dy)
                    .testTag(action.tag)
                    .let { if (i == actions.indexOfFirst { it.enabled }) it.focusRequester(first) else it },
                onClick = {
                    // To a concept asks which one; everything else is done and the ring goes.
                    if (action.tag != "ring-concept") close()
                    action.run()
                },
            )
        }
        RingButton(null, "More…", true, Modifier.testTag("ring-more"), size = 40.dp, onClick = onMore)
    }
}

/**
 * A porcelain button: a small well with the room's pigment in it, its label lettered under it.
 * A room that is not built yet is a dry well, its label saying so.
 */
@Composable
private fun RingButton(room: Room?, label: String, enabled: Boolean, modifier: Modifier, size: Dp = ButtonSize, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    val lit = enabled && (hovered || focused)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
            .width(size + 34.dp)
            .clip(RoundedCornerShape(12.dp))
            .hoverable(interactions)
            .clickable(interactionSource = interactions, indication = null, enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .onPreviewKeyEvent { event ->
                // Enter or Space on the focused button takes it, as a click does.
                if (enabled && event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Spacebar)) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .semantics { contentDescription = label },
    ) {
        val design = LocalDesign.current
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2f
            val c = center
            with(design.materials) {
                drawLampShadow(Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r * 0.94f)) }, depthOf(Lift.LIFTED).toPx() * 0.6f, alpha = 0.5f)
            }
            // Porcelain, lit from the top left, the inside of the well in shadow along its top.
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFBF8F2), design.colors.paper, design.colors.paperShade), center = c - Offset(r * 0.3f, r * 0.3f), radius = r * 1.4f), r, c)
            drawCircle(Brush.verticalGradient(listOf(Color(0x33000000), Color.Transparent), c.y - r * 0.62f, c.y + r * 0.2f), r * 0.62f, c)
            if (room != null) {
                drawRoomDab(design, room, c - Offset(r * 0.42f, r * 0.37f), Size(r * 0.84f, r * 0.74f), wet = enabled)
            } else {
                // The middle: three dots, ink on porcelain.
                listOf(-1, 0, 1).forEach { k -> drawCircle(design.colors.inkQuiet, r * 0.08f, c + Offset(k * r * 0.28f, 0f)) }
            }
            if (lit) drawCircle(room?.let { design.pigmentOf(it).glow } ?: design.colors.text, r + 2.dp.toPx(), c, style = Stroke(2.dp.toPx()))
        }
        Text(
            if (enabled || room == null) label else "$label · coming",
            fontSize = 11.sp,
            lineHeight = 13.sp,
            textAlign = TextAlign.Center,
            color = if (enabled) Design.colors.text else Design.colors.muted,
            maxLines = 2,
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.62f)).padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

/** Which concept the selection goes to: every concept there is, linked onto the board if it is not yet. */
@Composable
private fun ConceptChoice(board: BoardState, close: () -> Unit, onBack: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val concepts = board.conceptsAvailable()
    Column(
        Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Design.colors.raised)
            .padding(vertical = 6.dp)
            .testTag("ring-concepts")
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onBack()
                    true
                } else {
                    false
                }
            },
    ) {
        Text("To which concept?", fontSize = 11.sp, color = Design.colors.muted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        concepts.forEachIndexed { i, concept ->
            Text(
                (if (concept.kind.isNotBlank()) concept.kind + " · " else "") + concept.name,
                fontSize = 13.sp,
                color = Design.colors.text,
                modifier = Modifier
                    .let { if (i == 0) it.focusRequester(first) else it }
                    .clickable {
                        close()
                        board.selectionToConcept(concept.id)
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("ring-concept-" + concept.name),
            )
        }
    }
}

/**
 * A card's right click: the ring, with [items] as the full menu behind its middle button. The
 * full menu is shown as the app shows every other context menu.
 */
@Composable
internal fun CardMenuArea(state: BoardState, items: () -> List<ContextMenuItem>, content: @Composable () -> Unit) {
    val list = LocalContextMenuRepresentation.current
    val ring = remember(state, list) { RingMenu(state, list) }
    CompositionLocalProvider(LocalContextMenuRepresentation provides ring) {
        ContextMenuArea(items = items, content = content)
    }
}
