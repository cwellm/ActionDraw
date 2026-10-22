package de.creaflect.actiondraw.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.creaflect.actiondraw.AppState
import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.PinTargets
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.image.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

private val LowTimeColor = Color(0xFFEF5350)

/**
 * A pose (F9.6): the picture, the ensō timer over its top right corner, and under it the controls
 * — the transport, the lens tray of glass slides, the knobs — stepping back while you draw.
 */
@Composable
fun SessionScreen(
    state: AppState,
    onToggleFullscreen: () -> Unit,
    isFullscreen: Boolean,
    pinTargets: PinTargets? = null,
    /** Opens Live Sketch with the picture on screen as the reference; null hides the button. */
    onSketch: ((File) -> Unit)? = null,
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
            withContext(Dispatchers.IO) { runCatching { ImageLoader.load(f) }.getOrNull() }
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
        // The settings above the tray: of what was just chosen or turned on.
        val settings = remember { mutableStateOf<SessionSettings?>(null) }
        OpenSettingsWithTheirControls(state, settings)
        // Where each control with settings sits along the tray, so they open right above it.
        val anchors = remember { mutableStateMapOf<SessionSettings, Float>() }
        val originX = remember { mutableStateOf(0f) }
        Box(Modifier.fillMaxSize().watchPointer(watch).onGloballyPositioned { coordinates -> coordinates.positionInRoot().x.let { if (originX.value != it) originX.value = it } }) {
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
            // done with this picture.
            Column(
                Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Enso(
                    state,
                    dimmed = receded.value,
                    modifier = Modifier.clickable(role = Role.Button, onClickLabel = if (state.isPaused) "Go on" else "Pause") { state.togglePause() },
                )
                SessionPiece(receded, watch, ChromeEdge.RIGHT, "session-side") { SideButtons(state, pinTargets, onSketch) }
            }
            if (state.isPaused) PausePanel(state, Modifier.align(Alignment.TopCenter).padding(top = 20.dp))
            settings.value?.takeIf { it.isOn(state) }?.let { target ->
                val above = with(LocalDensity.current) { barHeight.value.toDp() } + 6.dp
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(bottom = above)
                        .layout { measurable, constraints ->
                            // Centred over its control, kept on screen.
                            val popover = measurable.measure(constraints.copy(minWidth = 0))
                            val margin = 12.dp.roundToPx()
                            val centre = anchors[target] ?: (constraints.maxWidth / 2f)
                            val x = (centre - popover.width / 2f).roundToInt()
                                .coerceIn(margin, (constraints.maxWidth - popover.width - margin).coerceAtLeast(margin))
                            layout(constraints.maxWidth, popover.height) { popover.place(x, 0) }
                        },
                ) {
                    SessionPiece(receded, watch, ChromeEdge.BOTTOM, "session-settings") {
                        SettingsPopover(state, target, onClose = { settings.value = null })
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { barHeight.value = it.height }) {
                SessionPiece(receded, watch, ChromeEdge.BOTTOM, "session-chrome", Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp, start = 12.dp, end = 12.dp), contentAlignment = Alignment.Center) {
                        Tray(state, bitmap, settings, onAnchor = { target, x -> (x - originX.value).let { if (anchors[target] != it) anchors[target] = it } })
                    }
                }
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
private fun SessionRecede(state: AppState, watch: PointerWatch, receded: MutableState<Boolean>) {
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

/** What the settings above the tray can show: the chosen lens's, the light, blur, shards, the glass. */
private enum class SessionSettings { LENS, LIGHT, BLUR, SHARDS, GLASS }

private fun ViewMode.hasSettings(): Boolean =
    this == ViewMode.POSTERIZE || this == ViewMode.PIXELATE || this == ViewMode.SILHOUETTE || this == ViewMode.NOTAN

/** Whether there is anything to set: a lens with settings chosen, blur or shards on, the glass in hand. */
private fun SessionSettings.isOn(state: AppState): Boolean = when (this) {
    SessionSettings.LENS -> state.viewMode.hasSettings()
    SessionSettings.LIGHT -> true
    SessionSettings.BLUR -> state.blur
    SessionSettings.SHARDS -> state.defraction
    SessionSettings.GLASS -> state.glass.inHand
}

/** Choosing a lens with settings, or turning blur, shards or the glass on - by click or key - opens its settings. */
@Composable
private fun OpenSettingsWithTheirControls(state: AppState, settings: MutableState<SessionSettings?>) {
    fun follow(target: SessionSettings, on: Boolean) {
        if (on) settings.value = target else if (settings.value == target) settings.value = null
    }
    LaunchedEffect(state.viewMode) { follow(SessionSettings.LENS, state.viewMode.hasSettings()) }
    LaunchedEffect(state.blur) { if (state.blur || settings.value == SessionSettings.BLUR) follow(SessionSettings.BLUR, state.blur) }
    LaunchedEffect(state.defraction) { if (state.defraction || settings.value == SessionSettings.SHARDS) follow(SessionSettings.SHARDS, state.defraction) }
    LaunchedEffect(state.glass.inHand) { if (state.glass.inHand || settings.value == SessionSettings.GLASS) follow(SessionSettings.GLASS, state.glass.inHand) }
}

/** Top left: Stop, and what is running - its plan and where the pictures come from - and two quiet switches. */
@Composable
private fun TopRow(state: AppState, onToggleFullscreen: () -> Unit) {
    Column(Modifier.padding(start = 20.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Atelier.Raised)
                    .clickable(role = Role.Button, onClickLabel = "Stop the session") { state.stop() }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .testTag("session-stop"),
            ) {
                Text("✕", fontSize = 13.sp, color = Atelier.Text)
                Text("Stop", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Atelier.Text)
                Text("Esc", fontSize = 11.sp, color = Atelier.Muted)
            }
            Text(sessionCaption(state), fontSize = 13.sp, color = Atelier.TextSoft, maxLines = 1, modifier = Modifier.overPicture().testTag("session-caption"))
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
internal fun sessionCaption(state: AppState): String =
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
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (on) Room.PRACTICE.pigment.glow else Atelier.Line))
        Text(label, fontSize = 12.sp, color = if (on) Atelier.Text else Atelier.Muted)
        Text(key, fontSize = 10.sp, color = Atelier.InkQuiet)
    }
}

/**
 * Under the enso: back and on, and what can be done with this picture - flag it to redo, hide it,
 * sketch from it, pin it to a board. The last two lead into other rooms and carry their dabs.
 */
@Composable
private fun SideButtons(state: AppState, pinTargets: PinTargets?, onSketch: ((File) -> Unit)?) {
    val current = state.currentImage
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundButton("Back", PREV_ICON, size = 34.dp, label = false, tag = "session-prev") { state.previous() }
            RoundButton("On", NEXT_ICON, size = 34.dp, label = false, tag = "session-next") { state.next() }
        }
        RoundButton("Redo", REDO_ICON, on = state.isCurrentRedo, tag = "session-redo") { state.toggleRedoCurrent() }
        RoundButton("Hide", EYE_ICON, on = state.referenceFlipped, tag = "session-hide") { state.toggleReference() }
        onSketch?.let { open ->
            RoundButton("Sketch", PENCIL_ICON, room = Room.SKETCH, enabled = current != null, tag = "session-sketch") { current?.let(open) }
        }
        pinTargets?.let { PinButton(state, it) }
    }
}

/**
 * Pin - files the picture on screen away on an Idea Board. The session itself knows nothing
 * about boards; [PinTargets] supplies both the list and the action.
 */
@Composable
private fun PinButton(state: AppState, pinTargets: PinTargets) {
    var open by remember { mutableStateOf(false) }
    val current = state.currentImage
    Box {
        RoundButton("Pin", PIN_ICON, room = Room.BOARDS, enabled = current != null, tag = "session-pin") { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val boards = pinTargets.boards()
            if (boards.isEmpty()) {
                DropdownMenuItem(onClick = { open = false }) { Text("No boards yet") }
            }
            boards.forEach { (name, dir) ->
                DropdownMenuItem(onClick = {
                    open = false
                    current?.let { state.pinNotice = pinTargets.pin(dir, listOf(it)) }
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

/** A round button with a pencil glyph ([icon], an 18-unit box) and its name; [room]'s dab where it leads elsewhere. */
@Composable
private fun RoundButton(
    name: String,
    icon: String,
    on: Boolean = false,
    room: Room? = null,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 46.dp,
    label: Boolean = true,
    tag: String,
    onClick: () -> Unit,
) {
    val glyph = remember(icon) { PathParser().parsePathString(icon).toPath() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = name, onClick = onClick)
            .semantics {
                selected = on
                contentDescription = name
            }
            .testTag(tag),
    ) {
        Box {
            Box(
                Modifier.size(size).clip(CircleShape).background(if (on) Room.PRACTICE.pigment.glow else Atelier.Raised),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(18.dp)) {
                    val k = this.size.width / 18f
                    drawContext.transform.scale(k, k, Offset.Zero)
                    drawPath(
                        glyph,
                        when {
                            !enabled -> Atelier.Muted
                            on -> Atelier.OnGlow
                            else -> Atelier.TextSoft
                        },
                        style = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
            room?.let { PigmentDab(it.pigment, size = 11.dp, seed = it.seed, wet = enabled, modifier = Modifier.align(Alignment.TopEnd)) }
        }
        if (label) Text(name, fontSize = 11.sp, color = if (on) Atelier.Text else Atelier.TextSoft, modifier = Modifier.overPicture())
    }
}

/** Paused: said once, plainly, with the time per picture to set (fixed time only - a ramp's poses are its own). */
@Composable
private fun PausePanel(state: AppState, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Surface(color = Atelier.Raised, shape = shape, modifier = modifier.lampShadow(Lift.LIFTED, shape).testTag("session-paused")) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Paused — a click on the ensō or Space goes on", fontSize = 13.sp, color = Atelier.TextSoft)
            if (!state.isRamp) IntervalSelector(seconds = state.intervalSeconds, onChange = { state.intervalSeconds = it })
        }
    }
}

/**
 * The tray along the bottom: the lenses as glass slides, then the knobs - light, blur, mirror, flip,
 * invert, shards, grid, the glass. It wraps onto two lines in a narrow window.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tray(
    state: AppState,
    bitmap: ImageBitmap?,
    settings: MutableState<SessionSettings?>,
    onAnchor: (SessionSettings, Float) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(color = Atelier.Board, shape = shape, modifier = Modifier.lampShadow(Lift.LIFTED, shape)) {
        FlowRow(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LensTray(
                state, bitmap,
                onChosenAgain = { settings.value = if (settings.value == SessionSettings.LENS) null else SessionSettings.LENS },
                onAnchor = { x -> onAnchor(SessionSettings.LENS, x) },
            )
            Box(Modifier.width(1.dp).height(72.dp).background(Atelier.Line).align(Alignment.CenterVertically))
            Knobs(state, settings, onAnchor)
        }
    }
}

/** The settings of one thing, above the tray: a title, a way to put them away, and what can be set. */
@Composable
private fun SettingsPopover(state: AppState, target: SessionSettings, onClose: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val title = when (target) {
        SessionSettings.LENS -> LENSES.firstOrNull { it.first == state.viewMode }?.second ?: "Lens"
        SessionSettings.LIGHT -> "Light"
        SessionSettings.BLUR -> "Blur"
        SessionSettings.SHARDS -> "Shards"
        SessionSettings.GLASS -> "The glass"
    }
    Surface(color = Atelier.Raised, shape = shape, modifier = Modifier.lampShadow(Lift.LIFTED, shape).testTag("settings-" + target.name.lowercase())) {
        // As wide as what it holds, not the window - a popover over the picture must leave the
        // picture be - so nothing in it fills the width; the x sits over its top right corner.
        Box {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Atelier.Text, modifier = Modifier.padding(end = 28.dp))
                when (target) {
                    SessionSettings.LENS -> LensParameters(state)
                    SessionSettings.LIGHT -> {
                        ParamSlider(label = temperatureLabel(state.temperature), value = state.temperature, range = -1f..1f, steps = 39) { state.temperature = it }
                    }
                    SessionSettings.BLUR -> {
                        ParamSlider(label = "Radius: ${state.blurRadius} dp", value = state.blurRadius.toFloat(), range = 2f..40f, steps = 18) { state.blurRadius = it.roundToInt() }
                    }
                    SessionSettings.SHARDS -> {
                        ParamSlider(label = "Size: ${state.defractionBlock} px", value = state.defractionBlock.toFloat(), range = 32f..192f, steps = 9) { state.defractionBlock = it.roundToInt() }
                        ParamSlider(label = "Strength: " + "%.0f%%".format(state.defractionStrength * 100), value = state.defractionStrength, range = 0.1f..1f, steps = 8) { state.defractionStrength = it }
                    }
                    SessionSettings.GLASS -> {
                        OpticChoices(state)
                        ParamSlider(
                            label = "×" + "%.1f".format(state.glass.magnification),
                            value = state.glass.magnification,
                            range = GlassState.MIN_MAGNIFICATION..GlassState.MAX_MAGNIFICATION,
                            steps = 0,
                        ) { state.glass.magnify(it) }
                    }
                }
            }
            Text(
                "×",
                fontSize = 16.sp,
                color = Atelier.Muted,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 8.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "Close") { onClose() }
                    .padding(horizontal = 6.dp)
                    .testTag("settings-close"),
            )
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
private fun Enso(state: AppState, dimmed: Boolean, modifier: Modifier) {
    val reduced = LocalReducedMotion.current
    // The brush moves on with each second of the pose: a timer, not an animation to sit through.
    val sweep = ensoSweep(state.elapsedSeconds, state.currentIntervalSeconds)
    val alpha by animateFloatAsState(if (dimmed) 0.4f else 1f, tween(if (reduced) Motion.REDUCED_MS else Motion.RECEDE_MS))
    val low = state.remainingSeconds <= 5 && state.overtimeSeconds == 0
    val brush = if (low) LowTimeColor else Room.PRACTICE.pigment.glow
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
                drawArc(Room.PRACTICE.pigment.mass.copy(alpha = 0.8f), -100f, arc.coerceAtMost(110f), false, topLeft, Size(2 * r, 2 * r), style = Stroke(width * 0.4f, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(timerText(state), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = if (low) LowTimeColor else Atelier.Text)
                Text(
                    if (state.isPaused) "paused"
                    else state.rampPlan?.let { "pose ${state.rampPose + 1} of ${state.rampTotalPoses}" } ?: "${state.sessionPoses} drawn",
                    fontSize = 11.sp,
                    color = if (state.isPaused) Room.PRACTICE.pigment.glow else Atelier.Muted,
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
                                        done -> Modifier.background(Atelier.InkQuiet)
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
                color = if (state.comparing) Room.PRACTICE.pigment.glow else Atelier.TextSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(170.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(6.dp),
            )
        }
    }
}

// ---------------- The picture ----------------

/** The colour filter a lens applies, if it is one of the plain colour ones. */
private fun lensColorFilter(mode: ViewMode): ColorFilter? = when (mode) {
    ViewMode.GRAYSCALE -> grayscaleFilter()
    ViewMode.SQUINT -> squintFilter()
    ViewMode.SEPIA -> sepiaFilter()
    else -> null
}

/** The render effect a lens applies, with the session's own settings for it. */
@Composable
private fun rememberLensEffect(mode: ViewMode, state: AppState): RenderEffect? = when (mode) {
    ViewMode.EDGE -> remember { edgeRenderEffect() }
    ViewMode.SILHOUETTE -> remember(state.silhouetteThreshold) { silhouetteRenderEffect(state.silhouetteThreshold) }
    ViewMode.POSTERIZE -> remember(state.posterizeLevels) { posterizeRenderEffect(state.posterizeLevels) }
    ViewMode.PIXELATE -> remember(state.pixelateBlock) { pixelateRenderEffect(state.pixelateBlock) }
    ViewMode.NOTAN -> remember(state.notanBands, state.notanThreshold) { notanRenderEffect(state.notanBands, state.notanThreshold) }
    else -> null
}

/**
 * Keeps [lastFocus] at where the glass was last down on [current]. Its own composable, so the glass
 * moving recomposes this and nothing else; an effect rather than a snapshotFlow, for the reason
 * given at `PointerWatch.moves`.
 */
@Composable
private fun LastFocus(glass: GlassState, current: File?, lastFocus: MutableState<GlassFocus?>) {
    val focus = glass.focusOn(current)
    LaunchedEffect(focus) { if (focus != null) lastFocus.value = focus }
}

/**
 * The picture, with every lens and knob on it, and the glass (F10.9) over it: taken up, it shows
 * the picture larger under the pointer; set down, the part it is on fills the area — zooming
 * there and back over [Motion.GLASS_MS] — and the whole picture waits in the corner.
 */
@Composable
private fun ImageArea(state: AppState, bitmap: ImageBitmap?, current: File?, modifier: Modifier) {
    val glass = state.glass
    val pointer = remember { GlassPointer() }
    val focused by remember(current) { derivedStateOf { glass.focusOn(current) != null } }
    val progress = remember { Animatable(0f) }
    val reduced = LocalReducedMotion.current
    LaunchedEffect(focused) {
        progress.animateTo(if (focused) 1f else 0f, tween(if (reduced) Motion.REDUCED_MS else Motion.GLASS_MS, easing = Motion.GLASS_EASING))
    }
    // Where the glass was last down on this picture, so lifting it zooms back out from there.
    val lastFocus = remember(current) { mutableStateOf<GlassFocus?>(null) }
    LastFocus(glass, current, lastFocus)
    val bmp = bitmap
    val usable = bmp != null && !state.referenceHidden
    Box(
        modifier = modifier
            .background(Color.Black)
            .onSizeChanged { pointer.area = Size(it.width.toFloat(), it.height.toFloat()) }
            .then(if (usable) Modifier.glassInput(state, pointer, current, bmp!!.width.toFloat(), bmp.height.toFloat(), focused) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (state.referenceHidden) {
            // Drawn nowhere, not merely covered: nothing of the picture should reach the screen.
            MemoryVeil()
        } else if (bmp != null) {
            val colorFilter = lensColorFilter(state.viewMode)
            val renderEffect = rememberLensEffect(state.viewMode, state)
            val invertEffect = remember { invertRenderEffect() }
            val temperatureEffect = remember(state.temperature) { temperatureRenderEffect(state.temperature) }
            val defractionEffect = remember(state.defractionSeed, state.defractionBlock, state.defractionStrength) {
                defractionRenderEffect(state.defractionSeed, state.defractionBlock, state.defractionStrength)
            }
            GlassPicture(
                bitmap = bmp,
                state = state,
                picture = current,
                pointer = pointer,
                progress = progress,
                lastFocus = lastFocus,
                colorFilter = colorFilter,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("reference")
                    .semantics { contentDescription = current?.name ?: "" }
                    .graphicsLayer { // outermost: colour inversion of the final result
                        this.renderEffect = if (state.invert) invertEffect else null
                    }
                    .graphicsLayer { // white balance, over whatever the view mode produced
                        this.renderEffect = if (state.temperature != 0f) temperatureEffect else null
                    }
                    .graphicsLayer { // orientation
                        if (state.upsideDown) rotationZ = 180f
                        if (state.mirror) scaleX = -1f
                    }
                    .blur(if (state.blur) state.blurRadius.dp else 0.dp)
                    .graphicsLayer { this.renderEffect = renderEffect } // view mode
                    .graphicsLayer { // innermost: shards cut from the raw image
                        this.renderEffect = if (state.defraction) defractionEffect else null
                    },
            )
            // Proportion overlay sits above the image and is unaffected by its filters/rotation;
            // it measures the whole picture, so it waits while the glass is down on a part.
            if (state.gridMode != GridMode.OFF && !focused) {
                ProportionOverlay(bmp, state.gridMode, Modifier.fillMaxSize())
            }
            GlassOverlay(bmp, state, current, pointer, progress, focused)
        } else {
            Text(if (current == null) "No image" else "Loading…", color = Color.White)
        }
    }
}

/** Proportion overlay (thirds / phi / diagonal) + a stronger centre cross, within the fitted image rect. */
@Composable
private fun ProportionOverlay(bitmap: ImageBitmap, mode: GridMode, modifier: Modifier) {
    val lineColor = Color.White.copy(alpha = 0.45f)
    val centerColor = Color.White.copy(alpha = 0.8f)
    val lines = gridLines(mode)
    Canvas(modifier) {
        val scale = min(size.width / bitmap.width, size.height / bitmap.height)
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        val left = (size.width - w) / 2f
        val top = (size.height - h) / 2f
        val stroke = 1.5.dp.toPx()
        fun at(nx: Float, ny: Float) = Offset(left + w * nx, top + h * ny)

        lines.forEach { drawLine(lineColor, at(it.x1, it.y1), at(it.x2, it.y2), stroke) }
        // Shared centre cross + outer border for every active mode.
        drawLine(centerColor, at(0.5f, 0f), at(0.5f, 1f), stroke)
        drawLine(centerColor, at(0f, 0.5f), at(1f, 0.5f), stroke)
        drawRect(lineColor, topLeft = Offset(left, top), size = Size(w, h), style = Stroke(stroke))
    }
}

// ---------------- The controls ----------------

/**
 * The lenses as glass slides, keys 1 to 9 under them. Each shows the picture on screen through
 * its own lens, so you see what it will do before choosing it; the chosen one rises.
 */
@Composable
private fun LensTray(state: AppState, bitmap: ImageBitmap?, onChosenAgain: () -> Unit, onAnchor: (Float) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.testTag("lens-tray")) {
        LENSES.forEachIndexed { i, (mode, name) ->
            LensSlide(state, mode, name, key = i + 1, bitmap = bitmap, onChosenAgain = onChosenAgain, onAnchor = onAnchor)
        }
    }
}

@Composable
private fun LensSlide(state: AppState, mode: ViewMode, name: String, key: Int, bitmap: ImageBitmap?, onChosenAgain: () -> Unit, onAnchor: (Float) -> Unit) {
    val chosen = state.viewMode == mode
    val rise by animateFloatAsState(if (chosen) 1f else 0f, tween(if (LocalReducedMotion.current) Motion.REDUCED_MS else 200))
    val glow = Room.PRACTICE.pigment.glow
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .width(58.dp)
            .onGloballyPositioned { if (chosen) onAnchor(it.boundsInRoot().center.x) }
            .clickable(role = Role.Button, onClickLabel = name) { if (chosen) onChosenAgain() else state.viewMode = mode }
            .semantics { selected = chosen }
            .testTag("lens-" + mode.name.lowercase())
            .graphicsLayer { translationY = -8.dp.toPx() * rise },
    ) {
        Box(
            Modifier
                .size(48.dp)
                .drawBehind {
                    if (chosen) drawRoundRect(glow, Offset(-3.dp.toPx(), -3.dp.toPx()), Size(size.width + 6.dp.toPx(), size.height + 6.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()), style = Stroke(2.dp.toPx()))
                }
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black),
        ) {
            if (bitmap != null) {
                val effect = rememberLensEffect(mode, state)
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = lensColorFilter(mode),
                    modifier = Modifier.fillMaxSize().graphicsLayer { renderEffect = effect },
                )
            } else {
                Canvas(Modifier.fillMaxSize()) { drawLensSign(mode) }
            }
        }
        Text(name, fontSize = 11.sp, maxLines = 1, softWrap = false, fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal, color = if (chosen) Atelier.Text else Atelier.Muted)
        Text("$key", fontSize = 10.sp, color = Atelier.InkQuiet)
    }
}

/** The settings of the chosen lens, where it has any. */
@Composable
private fun LensParameters(state: AppState) {
    when (state.viewMode) {
        ViewMode.POSTERIZE -> ParamSlider(label = "Bands: ${state.posterizeLevels}", value = state.posterizeLevels.toFloat(), range = 2f..8f, steps = 5) { state.posterizeLevels = it.roundToInt() }
        ViewMode.PIXELATE -> ParamSlider(label = "Block: ${state.pixelateBlock} px", value = state.pixelateBlock.toFloat(), range = 4f..48f, steps = 10) { state.pixelateBlock = it.roundToInt() }
        ViewMode.SILHOUETTE -> ParamSlider(label = "Threshold: " + "%.2f".format(state.silhouetteThreshold), value = state.silhouetteThreshold, range = 0.05f..0.95f, steps = 17) { state.silhouetteThreshold = it }
        ViewMode.NOTAN -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Values:", fontSize = 12.sp, color = Atelier.TextSoft)
                Spacer(Modifier.width(6.dp))
                SelectChip("2", state.notanBands == 2) { state.notanBands = 2 }
                Spacer(Modifier.width(4.dp))
                SelectChip("3", state.notanBands == 3) { state.notanBands = 3 }
            }
            ParamSlider(label = "Threshold: " + "%.2f".format(state.notanThreshold), value = state.notanThreshold, range = 0.05f..0.95f, steps = 17) { state.notanThreshold = it }
        }
        else -> Unit
    }
}

/** The adjustments as round knobs, each with its key: they stack on any lens. */
@Composable
private fun Knobs(state: AppState, settings: MutableState<SessionSettings?>, onAnchor: (SessionSettings, Float) -> Unit) {
    fun anchor(target: SessionSettings) = Modifier.onGloballyPositioned { onAnchor(target, it.boundsInRoot().center.x) }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        // The light is not on or off: the knob opens its setting, and glows while it is not neutral.
        Knob(
            "Light", ", .", "M9 2.5 A6.5 6.5 0 1 1 8.99 2.5 M9 2.5 V15.5", on = state.temperature != 0f,
            tag = "knob-light", modifier = anchor(SessionSettings.LIGHT),
        ) { settings.value = if (settings.value == SessionSettings.LIGHT) null else SessionSettings.LIGHT }
        Knob("Blur", "B", "M9 2.5 A6.5 6.5 0 1 1 8.99 2.5", dashed = true, on = state.blur, modifier = anchor(SessionSettings.BLUR)) { state.blur = it }
        Knob("Mirror", "M", "M9 2 V16 M7 4 L2 13 H7 Z M11 4 L16 13 H11 Z", on = state.mirror) { state.mirror = it }
        Knob("Flip", "U", "M5 14 V7 A4 4 0 0 1 13 7 V14 M10.5 11.5 L13 14 L15.5 11.5", on = state.upsideDown) { state.upsideDown = it }
        Knob("Invert", "I", "M2.5 2.5 H15.5 V15.5 H2.5 Z M2.5 15.5 L15.5 2.5", on = state.invert) { state.invert = it }
        Knob("Shards", "D", "M2 3 L9 7 L16 2 M9 7 L7 16 M9 7 L16 11 M2 12 L7 16", on = state.defraction, modifier = anchor(SessionSettings.SHARDS)) { state.toggleDefraction() }
        Knob(
            if (state.gridMode == GridMode.OFF) "Grid" else state.gridMode.name.lowercase().replaceFirstChar { it.uppercase() },
            "G", "M6.5 2 V16 M11.5 2 V16 M2 6.5 H16 M2 11.5 H16", on = state.gridMode != GridMode.OFF,
        ) { state.gridMode = GridMode.entries[(state.gridMode.ordinal + 1) % GridMode.entries.size] }
        // The glass (F10.9): taken up and put away; its optics and magnification in its settings.
        Knob("Glass", "L", "M7.5 2.5 A5 5 0 1 1 7.49 2.5 M11 11 L16 16", on = state.glass.inHand, tag = "glass", modifier = anchor(SessionSettings.GLASS)) { state.glass.toggle() }
    }
}

/** One knob: a round button with a pencil glyph ([icon], in an 18-unit box), its name and its key. */
@Composable
private fun Knob(
    label: String,
    key: String,
    icon: String,
    on: Boolean,
    dashed: Boolean = false,
    tag: String = "knob-" + key.lowercase(),
    modifier: Modifier = Modifier,
    onChange: (Boolean) -> Unit,
) {
    val glyph = remember(icon) { PathParser().parsePathString(icon).toPath() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
            .width(46.dp)
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
            .testTag(tag),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (on) Room.PRACTICE.pigment.glow else Atelier.Raised),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(18.dp)) {
                val s = size.width / 18f
                drawContext.transform.scale(s, s, Offset.Zero)
                drawPath(
                    glyph,
                    if (on) Atelier.OnGlow else Atelier.TextSoft,
                    style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(2f, 2.4f)) else null),
                )
            }
        }
        Text(label, fontSize = 11.sp, color = if (on) Atelier.Text else Atelier.Muted)
        Text(key, fontSize = 10.sp, color = Atelier.InkQuiet)
    }
}

/** Reads as "Light: neutral" / "Light: warm +40%", which means more than a bare number. */
private fun temperatureLabel(temperature: Float): String = when {
    temperature > 0.01f -> "Light: warm +%.0f%%".format(temperature * 100)
    temperature < -0.01f -> "Light: cool %.0f%%".format(temperature * 100)
    else -> "Light: neutral"
}

/** What stands in for the reference while you draw from memory. */
@Composable
private fun MemoryVeil() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.testTag("memory-veil"),
    ) {
        Text("From memory", style = AtelierType.Hand.copy(fontSize = 40.sp), color = Color.White.copy(alpha = 0.7f))
        Text("H peeks", style = MaterialTheme.typography.body2, color = Color.White.copy(alpha = 0.35f))
    }
}

/** Which beat of a memory pose is running, or null when there is nothing to say. */
private fun poseNote(state: AppState): String? = when {
    state.comparing -> "Compare — Next ▶ when you are done"
    state.referenceHidden -> "Drawing from memory — H peeks"
    state.isMemoryPose -> "Study — hides in ${state.studyRemainingSeconds}s"
    state.referenceFlipped -> "Reference covered — H shows it"
    else -> null
}

/** Remaining time, or — in manual mode past the interval — the overtime as "+m:ss". */
private fun timerText(state: AppState): String =
    if (state.overtimeSeconds > 0) "+" + formatTime(state.overtimeSeconds)
    else formatTime(state.remainingSeconds)

@Composable
private fun ParamSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.sp, color = Atelier.TextSoft)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.width(240.dp),
        )
    }
}
