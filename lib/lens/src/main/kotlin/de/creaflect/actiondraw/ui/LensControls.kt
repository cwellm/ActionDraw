package de.creaflect.actiondraw.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.lens.LensState
import kotlin.math.roundToInt

/** One of the lens's controls as the room around them holds it: its tag, where it goes, what it is. */
typealias LensPiece = @Composable (tag: String, modifier: Modifier, content: @Composable () -> Unit) -> Unit

/** A control held as it is, under its tag. */
val PlainLensPiece: LensPiece = { tag, modifier, content -> Box(modifier.testTag(tag)) { content() } }

/**
 * The lens's controls along the bottom of whatever shows a picture: the tray, lit in [room]'s
 * pigment, and above it the settings of what was just chosen or turned on — centred over its
 * control, kept on screen, never across the picture. [piece] holds each of the two, tagged
 * `<tag>-settings` and `<tag>-chrome` (a session lets them step back with the rest of its chrome);
 * [barHeight] is told how tall the tray is, so the picture can make room for it.
 */
@Composable
fun BoxScope.LensControls(
    lens: LensState,
    bitmap: ImageBitmap?,
    room: Room,
    barHeight: MutableState<Int>,
    tag: String = "lens",
    piece: LensPiece = PlainLensPiece,
) {
    // The settings above the tray: of what was just chosen or turned on.
    val settings = remember { mutableStateOf<LensSettings?>(null) }
    OpenSettingsWithTheirControls(lens, settings)
    // Where each control with settings sits along the tray, so they open right above it.
    val anchors = remember { mutableStateMapOf<LensSettings, Float>() }
    val originX = remember { mutableStateOf(0f) }
    Box(Modifier.matchParentSize().onGloballyPositioned { coordinates -> coordinates.positionInRoot().x.let { if (originX.value != it) originX.value = it } }) {
        settings.value?.takeIf { it.isOn(lens) }?.let { target ->
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
                piece("$tag-settings", Modifier) {
                    LensSettingsPopover(lens, target, onClose = { settings.value = null })
                }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { barHeight.value = it.height }) {
            piece("$tag-chrome", Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp, start = 12.dp, end = 12.dp), contentAlignment = Alignment.Center) {
                    LensTray(lens, bitmap, settings, room, onAnchor = { target, x -> (x - originX.value).let { if (anchors[target] != it) anchors[target] = it } })
                }
            }
        }
    }
}
