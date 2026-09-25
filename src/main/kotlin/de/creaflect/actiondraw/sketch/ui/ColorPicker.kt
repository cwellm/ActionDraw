package de.creaflect.actiondraw.sketch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Hue, saturation, value in 0..1 — the picker's own model; ARGB in and out. */
data class Hsv(val h: Float, val s: Float, val v: Float) {
    fun toArgb(): Int = Color.hsv(h * 360f, s, v).toArgb()

    companion object {
        fun fromArgb(argb: Int): Hsv {
            val r = ((argb shr 16) and 0xFF) / 255f
            val g = ((argb shr 8) and 0xFF) / 255f
            val b = (argb and 0xFF) / 255f
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val delta = max - min
            val h = when {
                delta == 0f -> 0f
                max == r -> ((g - b) / delta + 6f) % 6f / 6f
                max == g -> ((b - r) / delta + 2f) / 6f
                else -> ((r - g) / delta + 4f) / 6f
            }
            val s = if (max == 0f) 0f else delta / max
            return Hsv(h, s, max)
        }
    }
}

private fun Color.toArgb(): Int {
    val a = (alpha * 255f + 0.5f).toInt()
    val r = (red * 255f + 0.5f).toInt()
    val g = (green * 255f + 0.5f).toInt()
    val b = (blue * 255f + 0.5f).toInt()
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

fun hexOf(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

fun parseHex(text: String): Int? {
    val t = text.trim().removePrefix("#")
    if (t.length != 6 || !t.all { it.isLetterOrDigit() }) return null
    return runCatching { (0xFF000000.toInt() or t.toInt(16)) }.getOrNull()
}

/**
 * A colour picker for a pencil: a saturation/value square for the current hue, a hue strip, the
 * hex, the recent colours, and any palettes the caller brings (a board's). Every change goes
 * out at once through [onPick]; there is nothing to confirm.
 */
@Composable
fun ColorPicker(
    current: Int,
    recent: List<Int>,
    palettes: List<Pair<String, List<Int>>> = emptyList(),
    onPick: (Int) -> Unit,
) {
    // Read from the colour once, then kept as hue/saturation/value: derived again from the
    // colour after every pick, a grey (saturation 0) would forget its hue and the square would
    // jump to red under the pointer.
    var hsv by remember { mutableStateOf(Hsv.fromArgb(current)) }
    var hexText by remember { mutableStateOf(hexOf(current)) }

    fun pick(next: Hsv) {
        hsv = next
        hexText = hexOf(next.toArgb())
        onPick(next.toArgb())
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.width(260.dp)) {
        // Saturation across, value down, at the current hue.
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(4.dp))
                .testTag("colour-square")
                .pointerInput(hsv.h) {
                    fun at(p: Offset) = pick(hsv.copy(s = (p.x / size.width).coerceIn(0f, 1f), v = 1f - (p.y / size.height).coerceIn(0f, 1f)))
                    detectDragGestures(onDragStart = { at(it) }, onDrag = { change, _ -> change.consume(); at(change.position) })
                }
                .pointerInput(hsv.h) {
                    detectTapGestures { pick(hsv.copy(s = (it.x / size.width).coerceIn(0f, 1f), v = 1f - (it.y / size.height).coerceIn(0f, 1f))) }
                },
        ) {
            val hueColor = Color.hsv(hsv.h * 360f, 1f, 1f)
            drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val x = hsv.s * size.width
            val y = (1f - hsv.v) * size.height
            drawCircle(Color.White, radius = 7f, center = Offset(x, y))
            drawCircle(Color.Black, radius = 5f, center = Offset(x, y))
        }
        // The hue strip.
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .testTag("colour-hue")
                .pointerInput(Unit) {
                    fun at(p: Offset) = pick(hsv.copy(h = (p.x / size.width).coerceIn(0f, 0.9999f)))
                    detectDragGestures(onDragStart = { at(it) }, onDrag = { change, _ -> change.consume(); at(change.position) })
                }
                .pointerInput(Unit) { detectTapGestures { pick(hsv.copy(h = (it.x / size.width).coerceIn(0f, 0.9999f))) } },
        ) {
            val stops = (0..12).map { Color.hsv(it * 30f % 360f, 1f, 1f) }
            drawRect(Brush.horizontalGradient(stops))
            val x = hsv.h * size.width
            drawRect(Color.White, topLeft = Offset(x - 2f, 0f), size = Size(4f, size.height))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Color(hsv.toArgb())).border(1.dp, Color(0x40000000), CircleShape))
            OutlinedTextField(
                value = hexText,
                onValueChange = { text ->
                    hexText = text
                    parseHex(text)?.let { pick(Hsv.fromArgb(it)) }
                },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("colour-hex"),
            )
        }
        Swatches("Recent", recent) { pick(Hsv.fromArgb(it)) }
        palettes.forEach { (name, colors) -> Swatches(name, colors) { pick(Hsv.fromArgb(it)) } }
    }
}

@Composable
private fun Swatches(label: String, colors: List<Int>, onPick: (Int) -> Unit) {
    if (colors.isEmpty()) return
    Text(label, style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 2.dp)) {
        colors.take(10).forEach { argb ->
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(argb or 0xFF000000.toInt()))
                    .border(1.dp, Color(0x40000000), CircleShape)
                    .clickable { onPick(argb) }
                    .testTag("swatch-" + hexOf(argb)),
            )
        }
    }
}
