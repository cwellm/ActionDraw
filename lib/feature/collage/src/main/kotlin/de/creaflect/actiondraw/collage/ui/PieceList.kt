package de.creaflect.actiondraw.collage.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.creaflect.actiondraw.Room
import de.creaflect.actiondraw.collage.CollageState
import de.creaflect.actiondraw.collage.Piece
import de.creaflect.actiondraw.ui.Design
import de.creaflect.actiondraw.ui.LocalDesign
import de.creaflect.actiondraw.ui.pigment
import kotlin.math.roundToInt

/** How tall a row of the pieces list is: what a drag counts places by. */
private val ROW = 52.dp

/**
 * The pieces, from the top of the stack down, as the design has them on the right of the mat: each
 * as it lies, small, and its name. A click chooses one, a double click renames it, a drag up or down
 * moves it up or down the stack; the chosen one is marked in Naples yellow and can be taken away.
 */
@Composable
internal fun PieceList(state: CollageState, images: PieceImages, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Column(modifier.clip(shape).background(Design.colors.raised).padding(vertical = 12.dp).testTag("collage-pieces")) {
        Text(
            "PIECES",
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Design.colors.muted,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        val pieces = state.collage.pieces
        if (pieces.isEmpty()) {
            Text("None yet", fontSize = 12.sp, color = Design.colors.muted, modifier = Modifier.padding(horizontal = 16.dp))
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            pieces.indices.reversed().forEach { index -> PieceRow(state, images, pieces[index], index) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PieceRow(state: CollageState, images: PieceImages, piece: Piece, index: Int) {
    val design = LocalDesign.current
    val glow = Room.COLLAGE.pigment.glow
    val chosen = state.selected == piece.id
    var editing by remember(piece.id) { mutableStateOf(false) }
    var dragged by remember(piece.id) { mutableStateOf(0f) }
    val row = with(LocalDensity.current) { ROW.toPx() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW)
            .graphicsLayer { translationY = dragged }
            .background(if (chosen) Design.colors.board else Color.Transparent)
            .drawBehind { if (chosen) drawRect(glow, size = Size(3.dp.toPx(), size.height)) }
            .combinedClickable(
                role = Role.Button,
                onClickLabel = piece.name,
                onDoubleClick = {
                    state.select(piece.id)
                    editing = true
                },
                onClick = { state.select(piece.id) },
            )
            .pointerInput(piece.id, index) {
                // A drag down the list moves the piece down the stack, a row a place.
                detectVerticalDragGestures(
                    onDragEnd = {
                        val places = (dragged / row).roundToInt()
                        dragged = 0f
                        if (places != 0) state.reorder(piece.id, index - places)
                    },
                    onDragCancel = { dragged = 0f },
                ) { change, dy ->
                    change.consume()
                    dragged += dy
                }
            }
            .padding(start = 14.dp, end = 10.dp)
            .testTag("piece-${piece.id}"),
    ) {
        Canvas(Modifier.size(36.dp).clip(RoundedCornerShape(4.dp))) {
            drawPiece(piece, images[piece.source], MatView.around(piece, size), design, lifted = false)
        }
        if (editing) {
            NameField(piece.name, "piece-name-field", fontSize = 13.sp, modifier = Modifier.weight(1f)) { next ->
                editing = false
                if (next != null) state.renamePiece(piece.id, next)
            }
        } else {
            Text(
                piece.name,
                fontSize = 13.sp,
                fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                color = if (chosen) Design.colors.text else Design.colors.textSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        if (chosen && !editing) {
            Text(
                "×",
                fontSize = 16.sp,
                color = Design.colors.muted,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "Take the piece away") { state.remove(piece.id) }
                    .padding(horizontal = 6.dp)
                    .testTag("piece-remove"),
            )
        } else {
            Spacer(Modifier.width(18.dp))
        }
    }
}

/**
 * A name being typed in place of where it is shown: [initial] chosen whole to type over. Enter or
 * leaving the field gives what was typed to [onDone]; Esc gives null — nothing changed.
 */
@Composable
internal fun NameField(initial: String, tag: String, fontSize: TextUnit, modifier: Modifier = Modifier, onDone: (String?) -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    var focused by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    fun finish(with: String?) {
        if (done) return
        done = true
        onDone(with)
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        textStyle = TextStyle(fontSize = fontSize, color = Design.colors.text),
        cursorBrush = SolidColor(Design.colors.text),
        modifier = modifier
            .focusRequester(focus)
            .onFocusChanged {
                if (it.isFocused) focused = true else if (focused) finish(text.text)
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter -> { finish(text.text); true }
                    Key.Escape -> { finish(null); true }
                    else -> false
                }
            }
            .testTag(tag),
    )
}
