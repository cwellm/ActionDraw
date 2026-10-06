package de.creaflect.actiondraw.ui

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
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Slider
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
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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
        Column(Modifier.fillMaxSize().watchPointer(watch)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ImageArea(state, bitmap, current, Modifier.fillMaxSize())
                Enso(state, dimmed = receded.value, modifier = Modifier.align(Alignment.TopEnd).padding(20.dp))
            }
            SessionChrome(state, watch, receded) { ControlBar(state, bitmap, onToggleFullscreen, pinTargets, onSketch) }
        }
    }
}

/**
 * While a pose runs and the mouse rests — the drawing happens on paper — the controls step back
 * (CONCEPT.md, *Recede*); bringing the pointer down to them, or pausing, brings them back. The
 * keys work all the while.
 */
@Composable
private fun SessionChrome(state: AppState, watch: PointerWatch, receded: MutableState<Boolean>, bar: @Composable () -> Unit) {
    val paused = state.isPaused
    LaunchedEffect(paused) {
        if (paused) {
            receded.value = false
            return@LaunchedEffect
        }
        // Every move starts the wait again; a move does not bring the bar back, nearing it does.
        snapshotFlow { watch.moves }.collectLatest {
            delay(Motion.RECEDE_AFTER_MS)
            receded.value = true
        }
    }
    RecedingChrome(
        receded = receded.value,
        watch = watch,
        edge = ChromeEdge.BOTTOM,
        modifier = Modifier.testTag("session-chrome"),
        onOver = { receded.value = false },
        content = bar,
    )
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
                    state.rampPlan?.let { "pose ${state.rampPose + 1} of ${state.rampTotalPoses}" } ?: "${state.sessionPoses} drawn",
                    fontSize = 11.sp,
                    color = Atelier.Muted,
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
 * "Pin ▾" — files the picture on screen away on an Idea Board. The session itself knows nothing
 * about boards; [PinTargets] supplies both the list and the action. It leads into the Boards
 * room, so it carries ultramarine's dab.
 */
@Composable
private fun PinMenu(state: AppState, pinTargets: PinTargets) {
    var open by remember { mutableStateOf(false) }
    val current = state.currentImage
    Box {
        OutlinedButton(onClick = { open = true }, enabled = current != null) {
            PigmentDab(Room.BOARDS.pigment, size = 11.dp, seed = Room.BOARDS.seed)
            Spacer(Modifier.width(8.dp))
            Text("Pin ▾")
        }
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

@Composable
private fun ImageArea(state: AppState, bitmap: ImageBitmap?, current: File?, modifier: Modifier) {
    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        val bmp = bitmap
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
            Image(
                bitmap = bmp,
                contentDescription = current?.name,
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("reference")
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
            // Proportion overlay sits above the image and is unaffected by its filters/rotation.
            if (state.gridMode != GridMode.OFF) {
                ProportionOverlay(bmp, state.gridMode, Modifier.fillMaxSize())
            }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlBar(
    state: AppState,
    bitmap: ImageBitmap?,
    onToggleFullscreen: () -> Unit,
    pinTargets: PinTargets?,
    onSketch: ((File) -> Unit)? = null,
) {
    Surface(color = Atelier.Board) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // The transport, the redo flag, and the ways into other rooms.
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedButton(onClick = { state.previous() }) { Text("◀ Prev") }
                OutlinedButton(onClick = { state.play() }) { Text("Play") }
                Button(onClick = { state.togglePause() }) { Text(if (state.isPaused) "Resume" else "Pause") }
                OutlinedButton(onClick = { state.stop() }) { Text("■ Stop") }
                OutlinedButton(onClick = { state.next() }) { Text("Next ▶") }
                Spacer(Modifier.width(12.dp))
                SelectChip("Redo", state.isCurrentRedo, tag = "session-redo") { state.toggleRedoCurrent() }
                SelectChip("Auto-advance", state.autoAdvance) { state.autoAdvance = !state.autoAdvance }
                OutlinedButton(onClick = onToggleFullscreen) { Text("Fullscreen") }
                pinTargets?.let { PinMenu(state, it) }
                // The paper beside the monitor, digitised: sketch with the reference kept in view.
                onSketch?.let { open ->
                    val current = state.currentImage
                    OutlinedButton(onClick = { current?.let(open) }, enabled = current != null, modifier = Modifier.testTag("session-sketch")) {
                        PigmentDab(Room.SKETCH.pigment, size = 11.dp, seed = Room.SKETCH.seed)
                        Spacer(Modifier.width(8.dp))
                        Text("Sketch")
                    }
                }
            }

            // What the last pin did, until the next one.
            state.pinNotice?.let { notice ->
                Text(notice, fontSize = 12.sp, color = Room.CONCEPTS.pigment.glow, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }

            // The lens tray and the knobs beside it.
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LensTray(state, bitmap)
                Box(Modifier.width(1.dp).height(84.dp).background(Atelier.Line).align(Alignment.CenterVertically))
                Knobs(state)
            }

            // The settings of whatever is on: the lens's, the light, blur, shards.
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
            ) {
                LensParameters(state)
                // White balance is always on offer: it is an adjustment, not a mode.
                ParamSlider(label = temperatureLabel(state.temperature), value = state.temperature, range = -1f..1f, steps = 39) { state.temperature = it }
                if (state.blur) {
                    ParamSlider(label = "Blur: ${state.blurRadius} dp", value = state.blurRadius.toFloat(), range = 2f..40f, steps = 18) { state.blurRadius = it.roundToInt() }
                }
                if (state.defraction) {
                    ParamSlider(label = "Shards: ${state.defractionBlock} px", value = state.defractionBlock.toFloat(), range = 32f..192f, steps = 9) { state.defractionBlock = it.roundToInt() }
                    ParamSlider(label = "Strength: " + "%.0f%%".format(state.defractionStrength * 100), value = state.defractionStrength, range = 0.1f..1f, steps = 8) { state.defractionStrength = it }
                }
            }

            // Adjusting the time is only allowed while paused; elapsed time is left untouched.
            // (In a ramp the durations are fixed by the plan, so the slider only shows in fixed mode.)
            if (state.isPaused && !state.isRamp) {
                IntervalSelector(seconds = state.intervalSeconds, onChange = { state.intervalSeconds = it })
            }

            Text(
                "Space pause · ←/→ prev/next · 1-9 lens · N notan · ,/. cooler/warmer · 0 neutral light · " +
                    "H hide/peek · A auto · B blur · I invert · D shards · M mirror · U flip · G grid · " +
                    "R redo · F fullscreen · Esc stop",
                fontSize = 11.sp,
                color = Atelier.Muted.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The lenses as glass slides, keys 1 to 9 under them. Each shows the picture on screen through
 * its own lens, so you see what it will do before choosing it; the chosen one rises.
 */
@Composable
private fun LensTray(state: AppState, bitmap: ImageBitmap?) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.testTag("lens-tray")) {
        LENSES.forEachIndexed { i, (mode, name) ->
            LensSlide(state, mode, name, key = i + 1, bitmap = bitmap)
        }
    }
}

@Composable
private fun LensSlide(state: AppState, mode: ViewMode, name: String, key: Int, bitmap: ImageBitmap?) {
    val chosen = state.viewMode == mode
    val rise by animateFloatAsState(if (chosen) 1f else 0f, tween(if (LocalReducedMotion.current) Motion.REDUCED_MS else 200))
    val glow = Room.PRACTICE.pigment.glow
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .width(62.dp)
            .clickable(role = Role.Button, onClickLabel = name) { state.viewMode = mode }
            .semantics { selected = chosen }
            .testTag("lens-" + mode.name.lowercase())
            .graphicsLayer { translationY = -8.dp.toPx() * rise },
    ) {
        Box(
            Modifier
                .size(52.dp)
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
private fun Knobs(state: AppState) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        Knob("Blur", "B", "M9 2.5 A6.5 6.5 0 1 1 8.99 2.5", dashed = true, on = state.blur) { state.blur = it }
        Knob("Mirror", "M", "M9 2 V16 M7 4 L2 13 H7 Z M11 4 L16 13 H11 Z", on = state.mirror) { state.mirror = it }
        Knob("Flip", "U", "M5 14 V7 A4 4 0 0 1 13 7 V14 M10.5 11.5 L13 14 L15.5 11.5", on = state.upsideDown) { state.upsideDown = it }
        Knob("Invert", "I", "M2.5 2.5 H15.5 V15.5 H2.5 Z M2.5 15.5 L15.5 2.5", on = state.invert) { state.invert = it }
        Knob("Shards", "D", "M2 3 L9 7 L16 2 M9 7 L7 16 M9 7 L16 11 M2 12 L7 16", on = state.defraction) { state.toggleDefraction() }
        Knob(
            if (state.gridMode == GridMode.OFF) "Grid" else state.gridMode.name.lowercase().replaceFirstChar { it.uppercase() },
            "G", "M6.5 2 V16 M11.5 2 V16 M2 6.5 H16 M2 11.5 H16", on = state.gridMode != GridMode.OFF,
        ) { state.gridMode = GridMode.entries[(state.gridMode.ordinal + 1) % GridMode.entries.size] }
    }
}

/** One knob: a round button with a pencil glyph ([icon], in an 18-unit box), its name and its key. */
@Composable
private fun Knob(label: String, key: String, icon: String, on: Boolean, dashed: Boolean = false, onChange: (Boolean) -> Unit) {
    val glyph = remember(icon) { PathParser().parsePathString(icon).toPath() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .width(48.dp)
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
            .testTag("knob-" + key.lowercase()),
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
