package de.creaflect.actiondraw.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.asSkiaColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.creaflect.actiondraw.Room
import de.creaflect.actiondraw.lens.LensState
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import kotlin.math.min
import org.jetbrains.skia.Rect as SkRect

/** How much of the view a form fills: the sphere's radius, a share of the view's shorter side. */
internal const val FORM_FILL = 0.46f

/** How far a drag turns the form: a radian for every hundred pixels. */
private const val TURN_PER_PX = 0.01f

/**
 * The picture on its form, turned and lit as [lens]'s form is: drawn inside the picture's filter
 * layers, as the glass's picture is, so a lens and the knobs work on the form as on the picture —
 * a sphere in notan, a page upside down.
 */
@Composable
internal fun FormPicture(bitmap: ImageBitmap, lens: LensState, colorFilter: ColorFilter?, modifier: Modifier) {
    val image = remember(bitmap) { Image.makeFromBitmap(bitmap.asSkiaBitmap()) }
    DisposableEffect(image) { onDispose { image.close() } }
    val filter = remember(colorFilter) { colorFilter?.asSkiaColorFilter() }
    val shape = lens.form
    Canvas(modifier) {
        val form = shape.form ?: return@Canvas
        drawIntoCanvas { canvas ->
            Paint().use { paint ->
                paint.colorFilter = filter
                formShader(image, form, shape.yaw, shape.pitch, size.width / 2f, size.height / 2f, FORM_FILL * min(size.width, size.height), shape.lit).use {
                    paint.shader = it
                    canvas.nativeCanvas.drawRect(SkRect.makeWH(size.width, size.height), paint)
                }
            }
        }
    }
}

/**
 * A drag over the picture turns its form: to the right, its front turns right; down, it turns
 * down — mirrored or upside down, as it is seen.
 */
internal fun Modifier.formInput(lens: LensState): Modifier =
    pointerHoverIcon(PointerIcon.Hand).pointerInput(lens) {
        detectDragGestures { change, drag ->
            change.consume()
            val dx = if (lens.mirror != lens.upsideDown) -drag.x else drag.x
            val dy = if (lens.upsideDown) -drag.y else drag.y
            lens.form.turn(-dx * TURN_PER_PX, -dy * TURN_PER_PX)
        }
    }

/** The forms as small drawings, in the form's settings above the tray; choosing one puts the picture on it. */
@Composable
internal fun FormChoices(lens: LensState) {
    val design = LocalDesign.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("form-choices")) {
        Form.entries.forEach { form ->
            val chosen = lens.form.form == form
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
                // As wide as its name, and never narrower than its drawing: "Chrome ball" is the longest.
                modifier = Modifier
                    .widthIn(min = 54.dp)
                    .selectable(chosen, role = Role.RadioButton) { lens.form.choose(form) }
                    .testTag("form-" + form.name.lowercase()),
            ) {
                Box(
                    Modifier.size(44.dp, 40.dp).clip(RoundedCornerShape(8.dp)).background(if (chosen) Design.colors.board else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(34.dp, 26.dp)) { drawFormSign(form, chosen, design) }
                }
                Text(form.label, fontSize = 11.sp, color = if (chosen) Design.colors.text else Design.colors.muted, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** A form as a small drawing of itself, in a 34 × 26 box: what it is, before its name is read. */
private fun DrawScope.drawFormSign(form: Form, chosen: Boolean, design: DesignSystem) {
    val s = size.width / 34f
    val fill = design.pigmentOf(Room.LENS).glow.copy(alpha = if (chosen) 0.85f else 0.45f)
    val edge = if (chosen) design.colors.text else design.colors.textSoft
    val stroke = Stroke(1f)
    fun pt(x: Float, y: Float) = Offset(x * s, y * s)
    fun oval(l: Float, t: Float, r: Float, b: Float) = Rect(pt(l, t), pt(r, b))
    when (form) {
        Form.SPHERE -> {
            drawCircle(fill, 11f * s, pt(17f, 13f))
            drawCircle(edge, 11f * s, pt(17f, 13f), style = stroke)
            drawOval(edge, oval(6f, 10.5f, 28f, 15.5f).topLeft, oval(6f, 10.5f, 28f, 15.5f).size, style = stroke)
        }
        Form.CYLINDER -> {
            val body = Path().apply {
                addRect(oval(10f, 5f, 24f, 21f))
                addOval(oval(10f, 18.5f, 24f, 23.5f))
            }
            drawPath(body, fill)
            drawOval(fill, oval(10f, 2.5f, 24f, 7.5f).topLeft, oval(10f, 2.5f, 24f, 7.5f).size)
            drawOval(edge, oval(10f, 2.5f, 24f, 7.5f).topLeft, oval(10f, 2.5f, 24f, 7.5f).size, style = stroke)
            drawLine(edge, pt(10f, 5f), pt(10f, 21f), 1f)
            drawLine(edge, pt(24f, 5f), pt(24f, 21f), 1f)
            drawArc(edge, 0f, 180f, false, oval(10f, 18.5f, 24f, 23.5f).topLeft, oval(10f, 18.5f, 24f, 23.5f).size, style = stroke)
        }
        Form.CONE -> {
            val cone = Path().apply {
                moveTo(17f * s, 1.5f * s)
                lineTo(27f * s, 20.5f * s)
                lineTo(7f * s, 20.5f * s)
                close()
                addOval(oval(7f, 18f, 27f, 23f))
            }
            drawPath(cone, fill)
            drawLine(edge, pt(17f, 1.5f), pt(7f, 20.5f), 1f)
            drawLine(edge, pt(17f, 1.5f), pt(27f, 20.5f), 1f)
            drawArc(edge, 0f, 180f, false, oval(7f, 18f, 27f, 23f).topLeft, oval(7f, 18f, 27f, 23f).size, style = stroke)
        }
        Form.TORUS -> {
            val ring = Path().apply {
                fillType = PathFillType.EvenOdd
                addOval(oval(3f, 5f, 31f, 22f))
                addOval(oval(12f, 11f, 22f, 16f))
            }
            drawPath(ring, fill)
            drawPath(ring, edge, style = stroke)
        }
        Form.PLANE -> {
            val page = Path().apply {
                moveTo(5f * s, 21f * s)
                lineTo(11f * s, 5f * s)
                lineTo(29f * s, 5f * s)
                lineTo(23f * s, 21f * s)
                close()
            }
            drawPath(page, fill)
            drawPath(page, edge, style = stroke)
        }
        Form.CHROME -> {
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFFE6EBF0), Color(0xFF7F8C99), Color(0xFF2A3038)), center = pt(13f, 9f), radius = 14f * s),
                11f * s,
                pt(17f, 13f),
            )
            drawCircle(edge, 11f * s, pt(17f, 13f), style = stroke)
            drawCircle(Color.White.copy(alpha = 0.9f), 1.8f * s, pt(13f, 8.5f))
        }
    }
}
