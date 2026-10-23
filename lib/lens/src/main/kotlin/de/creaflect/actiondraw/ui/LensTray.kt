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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Slider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.lens.LensState
import kotlin.math.roundToInt

/** What the settings above the tray can show: the chosen lens's, the light, blur, shards, the glass. */
internal enum class LensSettings { LENS, LIGHT, BLUR, SHARDS, GLASS }

private fun ViewMode.hasSettings(): Boolean =
    this == ViewMode.POSTERIZE || this == ViewMode.PIXELATE || this == ViewMode.SILHOUETTE || this == ViewMode.NOTAN

/** Whether there is anything to set: a lens with settings chosen, blur or shards on, the glass in hand. */
internal fun LensSettings.isOn(lens: LensState): Boolean = when (this) {
    LensSettings.LENS -> lens.viewMode.hasSettings()
    LensSettings.LIGHT -> true
    LensSettings.BLUR -> lens.blur
    LensSettings.SHARDS -> lens.defraction
    LensSettings.GLASS -> lens.glass.inHand
}

/** Choosing a lens with settings, or turning blur, shards or the glass on - by click or key - opens its settings. */
@Composable
internal fun OpenSettingsWithTheirControls(lens: LensState, settings: MutableState<LensSettings?>) {
    fun follow(target: LensSettings, on: Boolean) {
        if (on) settings.value = target else if (settings.value == target) settings.value = null
    }
    LaunchedEffect(lens.viewMode) { follow(LensSettings.LENS, lens.viewMode.hasSettings()) }
    LaunchedEffect(lens.blur) { if (lens.blur || settings.value == LensSettings.BLUR) follow(LensSettings.BLUR, lens.blur) }
    LaunchedEffect(lens.defraction) { if (lens.defraction || settings.value == LensSettings.SHARDS) follow(LensSettings.SHARDS, lens.defraction) }
    LaunchedEffect(lens.glass.inHand) { if (lens.glass.inHand || settings.value == LensSettings.GLASS) follow(LensSettings.GLASS, lens.glass.inHand) }
}

/**
 * The tray: the lenses as glass slides, then the knobs - light, blur, mirror, flip, invert, shards,
 * grid, the glass - lit in the pigment of the [room] it lies in. It wraps onto two lines in a narrow
 * window. [settings] are the settings open above it, which [LensControls] places
 * ([LensSettingsPopover]); [onAnchor] says where each control with settings sits, in the root's x.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LensTray(
    lens: LensState,
    bitmap: ImageBitmap?,
    settings: MutableState<LensSettings?>,
    room: Room,
    onAnchor: (LensSettings, Float) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(color = Design.colors.board, shape = shape, modifier = Modifier.lampShadow(Lift.LIFTED, shape)) {
        FlowRow(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LensSlides(
                lens, bitmap, room,
                onChosenAgain = { settings.value = if (settings.value == LensSettings.LENS) null else LensSettings.LENS },
                onAnchor = { x -> onAnchor(LensSettings.LENS, x) },
            )
            Box(Modifier.width(1.dp).height(72.dp).background(Design.colors.line).align(Alignment.CenterVertically))
            Knobs(lens, settings, room, onAnchor)
        }
    }
}

/** The settings of one thing, above the tray: a title, a way to put them away, and what can be set. */
@Composable
internal fun LensSettingsPopover(lens: LensState, target: LensSettings, onClose: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val title = when (target) {
        LensSettings.LENS -> LENSES.firstOrNull { it.first == lens.viewMode }?.second ?: "Lens"
        LensSettings.LIGHT -> "Light"
        LensSettings.BLUR -> "Blur"
        LensSettings.SHARDS -> "Shards"
        LensSettings.GLASS -> "The glass"
    }
    Surface(color = Design.colors.raised, shape = shape, modifier = Modifier.lampShadow(Lift.LIFTED, shape).testTag("settings-" + target.name.lowercase())) {
        // As wide as what it holds, not the window - a popover over the picture must leave the
        // picture be - so nothing in it fills the width; the x sits over its top right corner.
        Box {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Design.colors.text, modifier = Modifier.padding(end = 28.dp))
                when (target) {
                    LensSettings.LENS -> LensParameters(lens)
                    LensSettings.LIGHT -> {
                        ParamSlider(label = temperatureLabel(lens.temperature), value = lens.temperature, range = -1f..1f, steps = 39) { lens.temperature = it }
                    }
                    LensSettings.BLUR -> {
                        ParamSlider(label = "Radius: ${lens.blurRadius} dp", value = lens.blurRadius.toFloat(), range = 2f..40f, steps = 18) { lens.blurRadius = it.roundToInt() }
                    }
                    LensSettings.SHARDS -> {
                        ParamSlider(label = "Size: ${lens.defractionBlock} px", value = lens.defractionBlock.toFloat(), range = 32f..192f, steps = 9) { lens.defractionBlock = it.roundToInt() }
                        ParamSlider(label = "Strength: " + "%.0f%%".format(lens.defractionStrength * 100), value = lens.defractionStrength, range = 0.1f..1f, steps = 8) { lens.defractionStrength = it }
                    }
                    LensSettings.GLASS -> {
                        OpticChoices(lens)
                        ParamSlider(
                            label = "×" + "%.1f".format(lens.glass.magnification),
                            value = lens.glass.magnification,
                            range = GlassState.MIN_MAGNIFICATION..GlassState.MAX_MAGNIFICATION,
                            steps = 0,
                        ) { lens.glass.magnify(it) }
                    }
                }
            }
            Text(
                "×",
                fontSize = 16.sp,
                color = Design.colors.muted,
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

/**
 * The lenses as glass slides, keys 1 to 9 under them. Each shows the picture on screen through
 * its own lens, so you see what it will do before choosing it; the chosen one rises.
 */
@Composable
private fun LensSlides(lens: LensState, bitmap: ImageBitmap?, room: Room, onChosenAgain: () -> Unit, onAnchor: (Float) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.testTag("lens-tray")) {
        LENSES.forEachIndexed { i, (mode, name) ->
            LensSlide(lens, mode, name, key = i + 1, bitmap = bitmap, room = room, onChosenAgain = onChosenAgain, onAnchor = onAnchor)
        }
    }
}

@Composable
private fun LensSlide(lens: LensState, mode: ViewMode, name: String, key: Int, bitmap: ImageBitmap?, room: Room, onChosenAgain: () -> Unit, onAnchor: (Float) -> Unit) {
    val chosen = lens.viewMode == mode
    val rise by animateFloatAsState(if (chosen) 1f else 0f, tween(if (LocalReducedMotion.current) Motion.REDUCED_MS else 200))
    val glow = room.pigment.glow
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .width(58.dp)
            .onGloballyPositioned { if (chosen) onAnchor(it.boundsInRoot().center.x) }
            .clickable(role = Role.Button, onClickLabel = name) { if (chosen) onChosenAgain() else lens.viewMode = mode }
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
                val effect = rememberLensEffect(mode, lens)
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
        Text(name, fontSize = 11.sp, maxLines = 1, softWrap = false, fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal, color = if (chosen) Design.colors.text else Design.colors.muted)
        Text("$key", fontSize = 10.sp, color = Design.colors.inkQuiet)
    }
}

/** The settings of the chosen lens, where it has any. */
@Composable
private fun LensParameters(lens: LensState) {
    when (lens.viewMode) {
        ViewMode.POSTERIZE -> ParamSlider(label = "Bands: ${lens.posterizeLevels}", value = lens.posterizeLevels.toFloat(), range = 2f..8f, steps = 5) { lens.posterizeLevels = it.roundToInt() }
        ViewMode.PIXELATE -> ParamSlider(label = "Block: ${lens.pixelateBlock} px", value = lens.pixelateBlock.toFloat(), range = 4f..48f, steps = 10) { lens.pixelateBlock = it.roundToInt() }
        ViewMode.SILHOUETTE -> ParamSlider(label = "Threshold: " + "%.2f".format(lens.silhouetteThreshold), value = lens.silhouetteThreshold, range = 0.05f..0.95f, steps = 17) { lens.silhouetteThreshold = it }
        ViewMode.NOTAN -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Values:", fontSize = 12.sp, color = Design.colors.textSoft)
                Spacer(Modifier.width(6.dp))
                SelectChip("2", lens.notanBands == 2) { lens.notanBands = 2 }
                Spacer(Modifier.width(4.dp))
                SelectChip("3", lens.notanBands == 3) { lens.notanBands = 3 }
            }
            ParamSlider(label = "Threshold: " + "%.2f".format(lens.notanThreshold), value = lens.notanThreshold, range = 0.05f..0.95f, steps = 17) { lens.notanThreshold = it }
        }
        else -> Unit
    }
}

/** The adjustments as round knobs, each with its key: they stack on any lens. */
@Composable
private fun Knobs(lens: LensState, settings: MutableState<LensSettings?>, room: Room, onAnchor: (LensSettings, Float) -> Unit) {
    fun anchor(target: LensSettings) = Modifier.onGloballyPositioned { onAnchor(target, it.boundsInRoot().center.x) }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        // The light is not on or off: the knob opens its setting, and glows while it is not neutral.
        Knob(
            "Light", ", .", "M9 2.5 A6.5 6.5 0 1 1 8.99 2.5 M9 2.5 V15.5", on = lens.temperature != 0f, room = room,
            tag = "knob-light", modifier = anchor(LensSettings.LIGHT),
        ) { settings.value = if (settings.value == LensSettings.LIGHT) null else LensSettings.LIGHT }
        Knob("Blur", "B", "M9 2.5 A6.5 6.5 0 1 1 8.99 2.5", dashed = true, on = lens.blur, room = room, modifier = anchor(LensSettings.BLUR)) { lens.blur = it }
        Knob("Mirror", "M", "M9 2 V16 M7 4 L2 13 H7 Z M11 4 L16 13 H11 Z", on = lens.mirror, room = room) { lens.mirror = it }
        Knob("Flip", "U", "M5 14 V7 A4 4 0 0 1 13 7 V14 M10.5 11.5 L13 14 L15.5 11.5", on = lens.upsideDown, room = room) { lens.upsideDown = it }
        Knob("Invert", "I", "M2.5 2.5 H15.5 V15.5 H2.5 Z M2.5 15.5 L15.5 2.5", on = lens.invert, room = room) { lens.invert = it }
        Knob("Shards", "D", "M2 3 L9 7 L16 2 M9 7 L7 16 M9 7 L16 11 M2 12 L7 16", on = lens.defraction, room = room, modifier = anchor(LensSettings.SHARDS)) { lens.toggleDefraction() }
        Knob(
            if (lens.gridMode == GridMode.OFF) "Grid" else lens.gridMode.name.lowercase().replaceFirstChar { it.uppercase() },
            "G", "M6.5 2 V16 M11.5 2 V16 M2 6.5 H16 M2 11.5 H16", on = lens.gridMode != GridMode.OFF, room = room,
        ) { lens.cycleGrid() }
        // The glass (F10.9): taken up and put away; its optics and magnification in its settings.
        Knob("Glass", "L", "M7.5 2.5 A5 5 0 1 1 7.49 2.5 M11 11 L16 16", on = lens.glass.inHand, room = room, tag = "glass", modifier = anchor(LensSettings.GLASS)) { lens.glass.toggle() }
    }
}

/** One knob: a round button with a pencil glyph ([icon], in an 18-unit box), its name and its key; lit in [room]'s pigment while on. */
@Composable
private fun Knob(
    label: String,
    key: String,
    icon: String,
    on: Boolean,
    room: Room,
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
            Modifier.size(40.dp).clip(CircleShape).background(if (on) room.pigment.glow else Design.colors.raised),
            contentAlignment = Alignment.Center,
        ) {
            val ink = if (on) Design.colors.onGlow else Design.colors.textSoft
            Canvas(Modifier.size(18.dp)) {
                val s = size.width / 18f
                drawContext.transform.scale(s, s, Offset.Zero)
                drawPath(
                    glyph,
                    ink,
                    style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(2f, 2.4f)) else null),
                )
            }
        }
        Text(label, fontSize = 11.sp, color = if (on) Design.colors.text else Design.colors.muted)
        Text(key, fontSize = 10.sp, color = Design.colors.inkQuiet)
    }
}

/** Reads as "Light: neutral" / "Light: warm +40%", which means more than a bare number. */
private fun temperatureLabel(temperature: Float): String = when {
    temperature > 0.01f -> "Light: warm +%.0f%%".format(temperature * 100)
    temperature < -0.01f -> "Light: cool %.0f%%".format(temperature * 100)
    else -> "Light: neutral"
}

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
        Text(label, fontSize = 12.sp, color = Design.colors.textSoft)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.width(240.dp),
        )
    }
}
