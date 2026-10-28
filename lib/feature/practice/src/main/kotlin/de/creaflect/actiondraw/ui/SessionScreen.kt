package de.creaflect.actiondraw.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.creaflect.actiondraw.Room
import de.creaflect.actiondraw.practice.PracticeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private val LowTimeColor = Color(0xFFEF5350)

/**
 * A pose (F9.6): the picture, the ensō timer over its top right corner, and under it the controls
 * — the transport, the lens tray of glass slides, the knobs — stepping back while you draw.
 */
@Composable
fun SessionScreen(
    state: PracticeState,
    onToggleFullscreen: () -> Unit,
    isFullscreen: Boolean,
) {
    // Per-second countdown. Restarts on navigation (index/pose) and suspends while paused.
    LaunchedEffect(state.index, state.rampPose, state.isPaused) {
        while (!state.isPaused) {
            delay(1000)
            state.tick()
        }
    }

    val current = state.currentImage
    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(null, current) {
        value = current?.let { f ->
            withContext(Dispatchers.IO) { runCatching { state.pictures.decode(f)?.toComposeImageBitmap() }.getOrNull() }
        }
    }
    val receded = remember { mutableStateOf(false) }

    if (isFullscreen) {
        // The picture fills the screen; only the ensō floats over its corner.
        Box(Modifier.fillMaxSize()) {
            ImageArea(state, bitmap, current, Modifier.fillMaxSize())
            Enso(state, dimmed = false, modifier = Modifier.align(Alignment.TopEnd).padding(20.dp))
        }
    } else {
        val watch = remember { PointerWatch() }
        val reduced = LocalReducedMotion.current
        SessionRecede(state, watch, receded)
        // While the controls are back, the picture takes their room (CONCEPT.md, *Recede*): it
        // grows as they fade and gives the room back as they return. Read in layout only, so the
        // fade measures the picture again and recomposes nothing.
        val give = animateFloatAsState(
            if (receded.value) 1f else 0f,
            tween(if (reduced) Motion.REDUCED_MS else if (receded.value) Motion.RECEDE_MS else Motion.RETURN_MS),
        )
        val barHeight = remember { mutableStateOf(0) }
        Box(Modifier.fillMaxSize().watchPointer(watch)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .layout { measurable, constraints ->
                        val room = (barHeight.value * (1f - give.value)).roundToInt()
                        val h = (constraints.maxHeight - room).coerceAtLeast(0)
                        val picture = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                        layout(constraints.maxWidth, constraints.maxHeight) { picture.place(0, 0) }
                    },
            ) {
                ImageArea(state, bitmap, current, Modifier.fillMaxSize())
            }
            // Top left: the way out, and what is running.
            SessionPiece(receded, watch, ChromeEdge.TOP, "session-top", Modifier.align(Alignment.TopStart)) {
                TopRow(state, onToggleFullscreen)
            }
            // Top right: the enso, which never leaves - a click pauses - and under it what can be
            // done with this picture, kept above the tray.
            val tray = with(LocalDensity.current) { barHeight.value.toDp() }
            Column(
                Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 20.dp, bottom = tray + 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Enso(
                    state,
                    dimmed = receded.value,
                    modifier = Modifier.clickable(role = Role.Button, onClickLabel = if (state.isPaused) "Go on" else "Pause") { state.togglePause() },
                )
                SessionPiece(receded, watch, ChromeEdge.RIGHT, "session-side") { SideButtons(state) }
            }
            if (state.isPaused) PausePanel(state, Modifier.align(Alignment.TopCenter).padding(top = 20.dp))
            // Along the bottom the lens's tray, and the settings above it, stepping back with the rest.
            LensControls(state.lens, bitmap, Room.PRACTICE, barHeight, tag = "session") { tag, modifier, content ->
                SessionPiece(receded, watch, ChromeEdge.BOTTOM, tag, modifier, content)
            }
        }
    }
}

/**
 * While a pose runs and the mouse rests - the drawing happens on paper - the controls step back
 * (CONCEPT.md, *Recede*); bringing the pointer to any of them, or pausing, brings them back. The
 * keys work all the while.
 */
@Composable
private fun SessionRecede(state: PracticeState, watch: PointerWatch, receded: MutableState<Boolean>) {
    val paused = state.isPaused
    LaunchedEffect(paused) {
        if (paused) {
            receded.value = false
            return@LaunchedEffect
        }
        // Every move starts the wait again, from the move itself; a move does not bring the controls
        // back, nearing them does. (Why the moves are a flow: `PointerWatch.moves`.)
        watch.moves.onStart { emit(Unit) }.collectLatest {
            delay(Motion.RECEDE_AFTER_MS)
            receded.value = true
        }
    }
}

/** One piece of the pose's chrome, stepping back with the others and back as the pointer nears its [edge]. */
@Composable
private fun SessionPiece(
    receded: MutableState<Boolean>,
    watch: PointerWatch,
    edge: ChromeEdge,
    tag: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    RecedingChrome(
        receded = receded.value,
        watch = watch,
        edge = edge,
        modifier = modifier.testTag(tag),
        onOver = { receded.value = false },
        content = content,
    )
}

// ---------------- Round the picture ----------------

/** Top left: Stop, and what is running - its plan and where the pictures come from - and two quiet switches. */
@Composable
private fun TopRow(state: PracticeState, onToggleFullscreen: () -> Unit) {
    Column(Modifier.padding(start = 20.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Design.colors.raised)
                    .clickable(role = Role.Button, onClickLabel = "Stop the session") { state.stop() }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .testTag("session-stop"),
            ) {
                Text("✕", fontSize = 13.sp, color = Design.colors.text)
                Text("Stop", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Design.colors.text)
                Text("Esc", fontSize = 11.sp, color = Design.colors.muted)
            }
            Text(sessionCaption(state), fontSize = 13.sp, color = Design.colors.textSoft, maxLines = 1, modifier = Modifier.overPicture().testTag("session-caption"))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.overPicture()) {
            QuietSwitch("Auto-advance", "A", state.autoAdvance, "session-auto") { state.autoAdvance = !state.autoAdvance }
            QuietSwitch("Fullscreen", "F", false, "session-fullscreen") { onToggleFullscreen() }
        }
        // What the last pin did, until the next one.
        state.pinNotice?.let { Text(it, fontSize = 12.sp, color = Room.CONCEPTS.pigment.glow, modifier = Modifier.overPicture()) }
    }
}

/** Words lying over the picture get a dark wash under them, so they read on any picture. */
private fun Modifier.overPicture(): Modifier =
    clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 2.dp)

/** The plan running and where its pictures come from: "Classic gesture · Drachenbuch". */
internal fun sessionCaption(state: PracticeState): String =
    listOfNotNull(state.rampPlan?.name ?: (formatTime(state.intervalSeconds) + " each"), state.folder?.name).joinToString(" · ")

/** A switch said in words, its key beside it: lit when on. */
@Composable
private fun QuietSwitch(label: String, key: String, on: Boolean, tag: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(role = Role.Switch, onClickLabel = label, onClick = onClick)
            .semantics { selected = on }
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag(tag),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (on) Room.PRACTICE.pigment.glow else Design.colors.line))
        Text(label, fontSize = 12.sp, color = if (on) Design.colors.text else Design.colors.muted)
        Text(key, fontSize = 10.sp, color = Design.colors.inkQuiet)
    }
}

/**
 * Under the enso: back and on, and what can be done with this picture - flag it to redo, hide it,
 * take it into another room (the hand-off seam's ways: Sketch, the Lens…), pin it to a board. Those
 * that lead into other rooms carry their dabs.
 */
@Composable
private fun SideButtons(state: PracticeState) {
    val current = state.currentImage
    val routes = remember(current) { state.routes() }
    ColumnsFromTheRight(gap = 10.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundButton("Back", PREV_ICON, size = 34.dp, label = false, tag = "session-prev") { state.previous() }
            RoundButton("On", NEXT_ICON, size = 34.dp, label = false, tag = "session-next") { state.next() }
        }
        RoundButton("Redo", REDO_ICON, on = state.isCurrentRedo, tag = "session-redo") { state.toggleRedoCurrent() }
        RoundButton("Hide", EYE_ICON, on = state.referenceFlipped, tag = "session-hide") { state.toggleReference() }
        // Named for the room it leads into, said in full to a screen reader: "Sketch" - Sketch over.
        routes.forEach { route ->
            RoundButton(
                route.to.label, iconOf(route.to), room = route.to, description = route.label,
                tag = "session-" + route.to.name.lowercase(),
            ) { state.handOff(route.to) }
        }
        PinButton(state)
    }
}

/**
 * [content] down a column from the top, each item in its middle — and where the room runs out, as it
 * does above the tray in a low window, on down another column to the left of it, and so on, the
 * columns against the right edge.
 */
@Composable
private fun ColumnsFromTheRight(gap: Dp, content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val space = gap.roundToPx()
        val columns = ArrayList<MutableList<Placeable>>()
        var used = 0
        measurables.map { it.measure(Constraints()) }.forEach { item ->
            if (columns.isEmpty() || (columns.last().isNotEmpty() && used + space + item.height > constraints.maxHeight)) {
                columns += ArrayList<Placeable>()
                used = -space
            }
            columns.last() += item
            used += space + item.height
        }
        val widths = columns.map { column -> column.maxOf { it.width } }
        val wide = widths.sum() + space * (columns.size - 1).coerceAtLeast(0)
        val high = columns.maxOfOrNull { column -> column.sumOf { it.height } + space * (column.size - 1) } ?: 0
        layout(wide, high) {
            var right = wide
            columns.forEachIndexed { i, column ->
                val left = right - widths[i]
                var y = 0
                column.forEach { item ->
                    item.place(left + (widths[i] - item.width) / 2, y)
                    y += item.height + space
                }
                right = left - space
            }
        }
    }
}

/** A room's glyph on a round button, in the 18-unit box: a pencil into Sketch, a glass into the Lens. */
private fun iconOf(room: Room): String = when (room) {
    Room.SKETCH -> PENCIL_ICON
    Room.LENS -> LENS_ICON
    else -> ONWARD_ICON
}

/**
 * Pin - files the picture on screen away on an Idea Board. The session itself knows nothing
 * about boards: the domain's `BoardSource` lists them and files the picture.
 */
@Composable
private fun PinButton(state: PracticeState) {
    var open by remember { mutableStateOf(false) }
    val current = state.currentImage
    Box {
        RoundButton("Pin", PIN_ICON, room = Room.BOARDS, enabled = current != null, tag = "session-pin") { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val boards = state.boards()
            if (boards.isEmpty()) {
                DropdownMenuItem(onClick = { open = false }) { Text("No boards yet") }
            }
            boards.forEach { (name, dir) ->
                DropdownMenuItem(onClick = {
                    open = false
                    current?.let { state.pin(dir, listOf(it)) }
                }) { Text(name) }
            }
        }
    }
}

private const val PREV_ICON = "M11 3 L5 9 L11 15"
private const val NEXT_ICON = "M7 3 L13 9 L7 15"
private const val REDO_ICON = "M14.5 9 A5.5 5.5 0 1 1 9 3.5 L13 3.5 M10.5 1 L13 3.5 L10.5 6"
private const val EYE_ICON = "M1.5 9 Q9 1.5 16.5 9 Q9 16.5 1.5 9 Z M9 6.5 A2.5 2.5 0 1 1 8.99 6.5"
private const val PENCIL_ICON = "M3.5 14.5 L4.5 11 L12 3.5 L14.5 6 L7 13.5 Z M10.5 5 L13 7.5"
private const val PIN_ICON = "M9 2 A3.5 3.5 0 1 1 8.99 2 M9 9 L9 16.5"
private const val LENS_ICON = "M7.5 2.5 A5 5 0 1 1 7.49 2.5 M11 11 L16 16"
private const val ONWARD_ICON = "M3 9 H15 M10 4 L15 9 L10 14"

/** A round button with a pencil glyph ([icon], an 18-unit box) and its name; [room]'s dab where it leads elsewhere. */
@Composable
private fun RoundButton(
    name: String,
    icon: String,
    on: Boolean = false,
    room: Room? = null,
    enabled: Boolean = true,
    size: Dp = 46.dp,
    label: Boolean = true,
    /** What a screen reader says it does, where that is more than its [name]. */
    description: String = name,
    tag: String,
    onClick: () -> Unit,
) {
    val glyph = remember(icon) { PathParser().parsePathString(icon).toPath() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics {
                selected = on
                contentDescription = description
            }
            .testTag(tag),
    ) {
        Box {
            Box(
                Modifier.size(size).clip(CircleShape).background(if (on) Room.PRACTICE.pigment.glow else Design.colors.raised),
                contentAlignment = Alignment.Center,
            ) {
                val ink = when {
                    !enabled -> Design.colors.muted
                    on -> Design.colors.onGlow
                    else -> Design.colors.textSoft
                }
                Canvas(Modifier.size(18.dp)) {
                    val k = this.size.width / 18f
                    drawContext.transform.scale(k, k, Offset.Zero)
                    drawPath(
                        glyph,
                        ink,
                        style = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
            room?.let { PigmentDab(it.pigment, size = 11.dp, seed = it.seed, wet = enabled, modifier = Modifier.align(Alignment.TopEnd)) }
        }
        if (label) Text(name, fontSize = 11.sp, color = if (on) Design.colors.text else Design.colors.textSoft, modifier = Modifier.overPicture())
    }
}

/** Paused: said once, plainly, with the time per picture to set (fixed time only - a ramp's poses are its own). */
@Composable
private fun PausePanel(state: PracticeState, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Surface(color = Design.colors.raised, shape = shape, modifier = modifier.lampShadow(Lift.LIFTED, shape).testTag("session-paused")) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Paused — a click on the ensō or Space goes on", fontSize = 13.sp, color = Design.colors.textSoft)
            if (!state.isRamp) IntervalSelector(seconds = state.intervalSeconds, onChange = { state.intervalSeconds = it })
        }
    }
}

// ---------------- The ensō ----------------

/**
 * The pose's time as a brush circle that closes as the pose runs out (CONCEPT.md, *Practice: in a
 * pose*): read without reading a number, though the number is inside it. Under it the ramp's
 * poses as ticks, and what a memory pose is doing. While the controls are back it waits, faint.
 */
@Composable
private fun Enso(state: PracticeState, dimmed: Boolean, modifier: Modifier) {
    val reduced = LocalReducedMotion.current
    // The brush moves on with each second of the pose: a timer, not an animation to sit through.
    val sweep = ensoSweep(state.elapsedSeconds, state.currentIntervalSeconds)
    val alpha by animateFloatAsState(if (dimmed) 0.4f else 1f, tween(if (reduced) Motion.REDUCED_MS else Motion.RECEDE_MS))
    val low = state.remainingSeconds <= 5 && state.overtimeSeconds == 0
    val brush = if (low) LowTimeColor else Room.PRACTICE.pigment.glow
    val dryBrush = Room.PRACTICE.pigment.mass.copy(alpha = 0.8f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.graphicsLayer { this.alpha = alpha }.testTag("enso"),
    ) {
        Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val width = 9.dp.toPx()
                val r = size.minDimension / 2f - width
                drawCircle(Color.Black.copy(alpha = 0.5f), r + width * 1.6f)
                drawCircle(Color(0xFF2C2824), r, style = Stroke(width))
                val arc = 356f * sweep
                val topLeft = center - Offset(r, r)
                drawArc(brush, -100f, arc, false, topLeft, Size(2 * r, 2 * r), style = Stroke(width, cap = StrokeCap.Round))
                // Dry brush where the stroke began: a thinner, darker line along the first stretch.
                drawArc(dryBrush, -100f, arc.coerceAtMost(110f), false, topLeft, Size(2 * r, 2 * r), style = Stroke(width * 0.4f, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(timerText(state), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = if (low) LowTimeColor else Design.colors.text)
                Text(
                    if (state.isPaused) "paused"
                    else state.rampPlan?.let { "pose ${state.rampPose + 1} of ${state.rampTotalPoses}" } ?: "${state.sessionPoses} drawn",
                    fontSize = 11.sp,
                    color = if (state.isPaused) Room.PRACTICE.pigment.glow else Design.colors.muted,
                )
            }
        }
        state.rampPlan?.let { plan ->
            val bars = rampBars(plan)
            if (bars.size <= 40) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.width(132.dp)) {
                    bars.forEachIndexed { i, b ->
                        val done = i < state.rampPose
                        val now = i == state.rampPose
                        Box(
                            Modifier
                                .width(4.dp)
                                .height(7.dp + 9.dp * b)
                                .clip(RoundedCornerShape(1.dp))
                                .then(
                                    when {
                                        now -> Modifier.background(Room.PRACTICE.pigment.glow)
                                        done -> Modifier.background(Design.colors.inkQuiet)
                                        else -> Modifier.background(Color(0x553A342E))
                                    },
                                ),
                        )
                    }
                }
            }
        }
        poseNote(state)?.let {
            Text(
                it,
                fontSize = 12.sp,
                color = if (state.comparing) Room.PRACTICE.pigment.glow else Design.colors.textSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(170.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(6.dp),
            )
        }
    }
}

// ---------------- The picture ----------------

/**
 * The pose's picture, seen through the room's lens with the glass over it — or, while the pose hides
 * it, the veil in its place.
 */
@Composable
private fun ImageArea(state: PracticeState, bitmap: ImageBitmap?, current: File?, modifier: Modifier) {
    LensView(state.lens, bitmap, current, modifier, veil = if (state.referenceHidden) { { MemoryVeil() } } else null)
}

/** What stands in for the reference while you draw from memory. */
@Composable
private fun MemoryVeil() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.testTag("memory-veil"),
    ) {
        Text("From memory", style = Design.hand.copy(fontSize = 40.sp), color = Color.White.copy(alpha = 0.7f))
        Text("H peeks", style = MaterialTheme.typography.body2, color = Color.White.copy(alpha = 0.35f))
    }
}

/** Which beat of a memory pose is running, or null when there is nothing to say. */
private fun poseNote(state: PracticeState): String? = when {
    state.comparing -> "Compare — Next ▶ when you are done"
    state.referenceHidden -> "Drawing from memory — H peeks"
    state.isMemoryPose -> "Study — hides in ${state.studyRemainingSeconds}s"
    state.referenceFlipped -> "Reference covered — H shows it"
    else -> null
}

/** Remaining time, or — in manual mode past the interval — the overtime as "+m:ss". */
private fun timerText(state: PracticeState): String =
    if (state.overtimeSeconds > 0) "+" + formatTime(state.overtimeSeconds)
    else formatTime(state.remainingSeconds)
