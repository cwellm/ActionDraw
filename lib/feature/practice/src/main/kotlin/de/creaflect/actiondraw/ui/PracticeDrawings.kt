package de.creaflect.actiondraw.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import de.creaflect.actiondraw.Room

/**
 * The ink drawings on the Practice room's index cards, in the design's own lines (the canvas's
 * Practice board). The lenses' signs are the lens kit's (`drawLensSign`).
 */

/** The drawings are made in a 258 × 124 box and scaled to fit. */
private const val BOX_W = 258f
private const val BOX_H = 124f

private val ink = Color(0xFF3B3734)

private fun path(d: String): Path = PathParser().parsePathString(d).toPath()

private fun DrawScope.line(d: String, color: Color = ink, width: Float = 2f) =
    drawPath(path(d), color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))

private fun DrawScope.figure(color: Color, width: Float) {
    drawCircle(color, 9f, Offset(132f, 18f), style = Stroke(width))
    listOf(
        "M126 30 C116 50 104 62 92 78", "M110 38 L142 46", "M82 78 L106 86",
        "M86 80 C74 96 62 108 44 118", "M104 86 C108 100 116 110 130 120",
    ).forEach { line(it, color, width) }
}

/** Draws [exercise]'s little picture, fitted into this scope's size, in [design]'s pigments and ink. */
fun DrawScope.drawExercise(exercise: Exercise, design: DesignSystem) {
    val s = minOf(size.width / BOX_W, size.height / BOX_H)
    translate((size.width - BOX_W * s) / 2f, (size.height - BOX_H * s) / 2f) {
        scale(s, s, pivot = Offset.Zero) {
            when (exercise) {
                Exercise.ACTION -> {
                    line("M64 118 C88 84 112 62 152 24", design.pigmentOf(Room.PRACTICE).mass.copy(alpha = 0.85f), 4f)
                    drawCircle(ink, 9f, Offset(158f, 18f), style = Stroke(2f))
                    listOf(
                        "M152 30 C142 50 130 62 118 78", "M136 38 L168 46", "M108 78 L132 86",
                        "M112 80 C100 96 88 108 70 118", "M130 86 C134 100 142 110 156 120",
                        "M168 46 C182 58 192 66 204 60", "M138 40 C120 30 104 34 92 46",
                    ).forEach { line(it) }
                }
                Exercise.LENS -> {
                    drawRect(ink, Offset(40f, 10f), Size(180f, 106f), style = Stroke(1.5f))
                    val hill = "M40 80 C80 60 120 66 150 74 C175 80 200 70 220 66"
                    clipRect(40f, 10f, 130f, 116f) {
                        drawPath(path("$hill L220 116 L40 116 Z"), ink)
                        drawOval(ink, Offset(77f, 39f), Size(30f, 38f))
                    }
                    clipRect(130f, 10f, 220f, 116f) {
                        line(hill, width = 1.6f)
                        drawCircle(ink, 10f, Offset(186f, 34f), style = Stroke(1.6f))
                    }
                    drawCircle(design.pigmentOf(Room.LENS).mass, 32f, Offset(130f, 60f), style = Stroke(3.5f))
                    line("M153 83 L172 104", design.pigmentOf(Room.LENS).mass, 5f)
                }
                Exercise.MEMORY -> {
                    drawRect(ink, Offset(34f, 12f), Size(104f, 100f), style = Stroke(1.6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))))
                    drawPath(path("M74 32 L98 32 L98 46 C116 54 120 72 116 100 L56 100 C52 72 56 54 74 46 Z"), ink.copy(alpha = 0.2f))
                    line("M146 58 C160 44 172 44 184 52", design.colors.inkQuiet, 1.6f)
                    line("M178 46 L185 52 L177 57", design.colors.inkQuiet, 1.6f)
                    line("M202 44 L220 44 L220 56 C234 62 238 78 234 104 L188 104 C184 78 188 62 202 56 Z")
                }
                Exercise.FLICKER -> {
                    drawRect(Color(0xFFE2D9C9), Offset(46f, 26f), Size(90f, 88f))
                    drawRect(ink, Offset(46f, 26f), Size(90f, 88f), style = Stroke(1.6f))
                    drawCircle(ink, 10f, Offset(90f, 52f), style = Stroke(1.6f))
                    line("M90 62 C84 80 78 92 70 106", width = 1.6f)
                    drawRect(design.colors.paper, Offset(118f, 16f), Size(90f, 88f))
                    drawRect(ink, Offset(118f, 16f), Size(90f, 88f), style = Stroke(1.6f))
                    drawCircle(ink, 10f, Offset(166f, 40f), style = Stroke(1.6f))
                    line("M166 50 C172 66 180 80 192 94", width = 1.6f)
                    line("M216 34 C230 46 230 70 216 84", design.colors.inkQuiet, 1.6f)
                    line("M212 79 L216 85 L222 80", design.colors.inkQuiet, 1.6f)
                }
                Exercise.STAGED -> {
                    drawRect(design.colors.paper, Offset(20f, 30f), Size(62f, 62f))
                    drawPath(path("M20 70 C40 58 60 62 82 56 L82 92 L20 92 Z"), ink)
                    drawCircle(ink, 7f, Offset(62f, 46f))
                    drawRect(ink, Offset(20f, 30f), Size(62f, 62f), style = Stroke(1.4f))
                    line("M88 61 L102 61 M97 56 L102 61 L97 66", design.colors.inkQuiet, 1.5f)
                    drawRect(design.colors.paper, Offset(108f, 30f), Size(62f, 62f))
                    drawRect(ink, Offset(108f, 30f), Size(62f, 62f), style = Stroke(1.4f))
                    line("M108 70 C128 58 148 62 170 56", width = 1.5f)
                    drawCircle(ink, 7f, Offset(150f, 46f), style = Stroke(1.5f))
                    line("M176 61 L190 61 M185 56 L190 61 L185 66", design.colors.inkQuiet, 1.5f)
                    drawRect(Color(0xFFD9CFBE), Offset(196f, 30f), Size(62f, 62f))
                    drawPath(path("M196 70 C216 58 236 62 258 56 L258 92 L196 92 Z"), Color(0xFF8C8377))
                    drawCircle(Color(0xFFEFE7DA), 7f, Offset(238f, 46f))
                    drawRect(ink, Offset(196f, 30f), Size(62f, 62f), style = Stroke(1.4f))
                }
                Exercise.COMPARE -> {
                    figure(ink, 2f)
                    translate(12f, 4f) {
                        rotate(3f, pivot = Offset(120f, 70f)) { figure(design.pigmentOf(Room.PRACTICE).mass.copy(alpha = 0.7f), 2.4f) }
                    }
                }
            }
        }
    }
}
