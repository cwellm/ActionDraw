package de.creaflect.actiondraw.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import de.creaflect.actiondraw.ViewMode

/** The lenses in the order of their keys, 1 to 9, with the names on their slides. */
val LENSES: List<Pair<ViewMode, String>> = listOf(
    ViewMode.NONE to "None",
    ViewMode.GRAYSCALE to "B&W",
    ViewMode.SQUINT to "Squint",
    ViewMode.SEPIA to "Sepia",
    ViewMode.POSTERIZE to "Posterize",
    ViewMode.PIXELATE to "Pixelate",
    ViewMode.EDGE to "Edge",
    ViewMode.SILHOUETTE to "Silhouette",
    ViewMode.NOTAN to "Notan",
)

private fun path(d: String): Path = PathParser().parsePathString(d).toPath()

/** A lens's slide drawn as a sign of what it does — for when there is no picture to look through. */
fun DrawScope.drawLensSign(mode: ViewMode) {
    val w = size.width
    val h = size.height
    val all = Rect(Offset.Zero, size)
    when (mode) {
        ViewMode.NONE -> drawRect(Brush.linearGradient(listOf(Color(0xFFD9A27A), Color(0xFF6E8DA8)), Offset.Zero, Offset(w, h)))
        ViewMode.GRAYSCALE -> drawRect(Brush.linearGradient(listOf(Color(0xFFC8C2BA), Color(0xFF5E5A56)), Offset.Zero, Offset(w, h)))
        ViewMode.SQUINT -> drawRect(Brush.linearGradient(listOf(Color(0xFFA99C8E), Color(0xFF85827D)), Offset.Zero, Offset(w, h)))
        ViewMode.SEPIA -> drawRect(Brush.linearGradient(listOf(Color(0xFFD8B98F), Color(0xFF6B4E31)), Offset.Zero, Offset(w, h)))
        ViewMode.POSTERIZE -> {
            drawRect(Color(0xFFCFC6BA), Offset.Zero, Size(w, h / 3f))
            drawRect(Color(0xFF8A8178), Offset(0f, h / 3f), Size(w, h / 3f))
            drawRect(Color(0xFF45403B), Offset(0f, 2 * h / 3f), Size(w, h / 3f + 1f))
        }
        ViewMode.PIXELATE -> {
            val n = 4
            for (i in 0 until n) for (j in 0 until n) {
                val v = 0.35f + 0.4f * (((i * 7 + j * 3) % 5) / 4f)
                drawRect(Color(v, v * 0.95f, v * 0.9f), Offset(w * i / n, h * j / n), Size(w / n + 1f, h / n + 1f))
            }
        }
        ViewMode.EDGE -> {
            drawRect(Color(0xFF1A1715))
            scale(w / 40f, h / 40f, pivot = Offset.Zero) {
                drawPath(path("M2 28 C12 20 22 24 38 18"), Color(0xFFEEE7DB), style = Stroke(1.3f))
                drawCircle(Color(0xFFEEE7DB), 6f, Offset(10f, 12f), style = Stroke(1.3f))
            }
        }
        ViewMode.SILHOUETTE -> {
            drawRect(Color(0xFFECE4D6))
            drawRoundRect(Color(0xFF1F1C1A), Offset(w * 0.29f, h * 0.27f), Size(w * 0.42f, h * 0.8f), androidx.compose.ui.geometry.CornerRadius(w * 0.21f))
        }
        ViewMode.NOTAN -> {
            drawRect(Color(0xFFECE4D6))
            drawPath(Path().apply { moveTo(0f, h * 0.62f); lineTo(w, h * 0.38f); lineTo(w, h); lineTo(0f, h); close() }, Color(0xFF1F1C1A))
        }
    }
    drawRect(Color.White.copy(alpha = 0.12f), all.topLeft, all.size, style = Stroke(1f))
}
