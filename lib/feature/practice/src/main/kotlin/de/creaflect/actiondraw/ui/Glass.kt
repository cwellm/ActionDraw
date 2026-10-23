package de.creaflect.actiondraw.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The glass (CONCEPT.md, *Lens*): a real lens held over the picture. In the hand it is a round
 * glass following the pointer, showing what is under it larger and bent as its optic bends it;
 * set down on a part of the picture, that part fills the view, seen through it; and the whole
 * picture is always one step back. Drawing what a lens shows — rather than what one knows is
 * there — is the point: a bulge or a pinch takes the knowing away.
 */
enum class Optic(val label: String) {
    /** Flat glass: magnified, nothing bent. */
    PLAIN("Plain"),

    /** A magnifier: the middle swells, the rim draws in. */
    CONVEX("Convex"),

    /** A diverging lens: the middle shrinks away, the rim stretches. */
    CONCAVE("Concave"),

    /** A wide front element: the middle near, the edges bending round a much wider field. */
    FISHEYE("Fisheye"),

    /** A glass rod: one way stretched, the other untouched. */
    CYLINDER("Cylinder"),

    /** Flat, but the colours part towards the rim, as through a prism. */
    PRISM("Prism"),
}

/** Where the glass was set down: on [picture], at a point given as fractions of its width and height, [magnification] times larger than the whole picture is shown. */
data class GlassFocus(val picture: File?, val x: Float, val y: Float, val magnification: Float)

/** The glass and what it is doing; it lives as long as the app, so the optic is kept between sessions. */
class GlassState {
    /** The glass is taken up: it follows the pointer over the picture. */
    var inHand by mutableStateOf(false)
        private set
    var optic by mutableStateOf(Optic.PLAIN)
    var magnification by mutableStateOf(DEFAULT_MAGNIFICATION)
        private set

    /** The glass's radius, in dp. */
    var radius by mutableStateOf(DEFAULT_RADIUS)
        private set

    /** Where it was set down, if it was; see [focusOn]. */
    var focus by mutableStateOf<GlassFocus?>(null)
        private set

    fun takeUp() {
        inHand = true
    }

    /** Puts the glass away — and so whatever it was set down on goes back to the whole picture. */
    fun putAway() {
        inHand = false
        focus = null
    }

    fun toggle() = if (inHand) putAway() else takeUp()

    fun choose(optic: Optic) {
        this.optic = optic
        inHand = true
    }

    fun nextOptic() = choose(Optic.entries[(optic.ordinal + 1) % Optic.entries.size])

    /** How much larger, also for where it is set down. */
    fun magnify(m: Float) {
        magnification = m.coerceIn(MIN_MAGNIFICATION, MAX_MAGNIFICATION)
        focus = focus?.copy(magnification = magnification)
    }

    /** A step of the wheel or the + − keys: [steps] up or down by an eighth or so. */
    fun magnifyBy(steps: Float) = magnify(magnification * STEP.pow(steps))

    fun resizeBy(steps: Float) {
        radius = (radius * STEP.pow(steps)).coerceIn(MIN_RADIUS, MAX_RADIUS)
    }

    /** Sets the glass down on [picture] at ([x], [y]), fractions of its size; that part fills the view. */
    fun setDown(picture: File?, x: Float, y: Float, magnification: Float = this.magnification) {
        inHand = true
        this.magnification = magnification.coerceIn(MIN_MAGNIFICATION, MAX_MAGNIFICATION)
        focus = GlassFocus(picture, x.coerceIn(0f, 1f), y.coerceIn(0f, 1f), this.magnification)
    }

    /** Slides the glass over the picture while it is down, by fractions of the picture's size. */
    fun moveBy(dx: Float, dy: Float) {
        val f = focus ?: return
        focus = f.copy(x = (f.x + dx).coerceIn(0f, 1f), y = (f.y + dy).coerceIn(0f, 1f))
    }

    /** Lifts the glass off the picture: the whole of it again, the glass still in hand. */
    fun wholePicture() {
        focus = null
    }

    /** Where the glass is down on [picture] — none on another picture: a new pose starts whole. */
    fun focusOn(picture: File?): GlassFocus? = focus?.takeIf { it.picture == picture }

    companion object {
        const val MIN_MAGNIFICATION = 1.25f
        const val MAX_MAGNIFICATION = 12f
        const val DEFAULT_MAGNIFICATION = 2.5f
        const val MIN_RADIUS = 50f
        const val MAX_RADIUS = 260f
        const val DEFAULT_RADIUS = 110f
        private const val STEP = 1.15f
    }
}

// ---------------- The optics ----------------

/**
 * One shader for every optic. [image] is the picture, placed in view coordinates by its own
 * local matrix; a pixel at `p` shows the picture at `center + bend(p − center) · zoom`, so `zoom`
 * is 1 / magnification in the hand-held glass and 1 when the glass fills the view (the
 * magnification is then in the matrix). [strength] eases the bending in as the glass is set down.
 * Mirrored by [opticSource], which the tests hold this to.
 */
const val OPTIC_SKSL = """
uniform shader image;
uniform float2 center;
uniform float radius;
uniform float zoom;
uniform float strength;
uniform float optic;

float2 bend(float2 d, float r) {
    if (optic < 0.5) { return d; }
    if (optic < 1.5) { return d * mix(0.55, 1.3, r * r); }
    if (optic < 2.5) { return d * mix(1.8, 0.85, r * r); }
    if (optic < 3.5) {
        float rr = clamp(r, 0.001, 1.1);
        return d * (1.8 * tan(1.35 * rr) / (rr * tan(1.35)));
    }
    if (optic < 4.5) {
        float u = d.x / radius;
        return float2(d.x * mix(0.5, 0.85, u * u), d.y);
    }
    return d;
}

half4 main(float2 p) {
    float2 d = p - center;
    float r = length(d) / radius;
    float2 b = mix(d, bend(d, r), strength) * zoom;
    if (optic > 4.5) {
        float k = 0.03 * strength * (0.25 + r * r);
        half4 c = image.eval(center + b);
        half4 red = image.eval(center + b * (1.0 + k));
        half4 blue = image.eval(center + b * (1.0 - k));
        return half4(red.r, c.g, blue.b, c.a);
    }
    return image.eval(center + b);
}
"""

private val opticRuntime: RuntimeEffect by lazy { RuntimeEffect.makeForShader(OPTIC_SKSL) }

/** The optic's index in [OPTIC_SKSL]. */
private val Optic.index: Float get() = ordinal.toFloat()

/**
 * Where the glass finds the colour it shows at ([dx], [dy]) from its centre, in view pixels — the
 * same arithmetic as [OPTIC_SKSL] (for a prism, its green).
 */
fun opticSource(optic: Optic, dx: Float, dy: Float, radius: Float, zoom: Float, strength: Float = 1f): Offset {
    val r = sqrt(dx * dx + dy * dy) / radius
    fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
    val (bx, by) = when (optic) {
        Optic.PLAIN, Optic.PRISM -> dx to dy
        Optic.CONVEX -> mix(0.55f, 1.3f, r * r).let { dx * it to dy * it }
        Optic.CONCAVE -> mix(1.8f, 0.85f, r * r).let { dx * it to dy * it }
        Optic.FISHEYE -> {
            val rr = r.coerceIn(0.001f, 1.1f)
            (1.8f * tan(1.35f * rr) / (rr * tan(1.35f))).let { dx * it to dy * it }
        }
        Optic.CYLINDER -> {
            val u = dx / radius
            dx * mix(0.5f, 0.85f, u * u) to dy
        }
    }
    return Offset(mix(dx, bx, strength) * zoom, mix(dy, by, strength) * zoom)
}

/** The sampling for a picture seen larger or smaller: mipmaps when shrunk, smooth when enlarged. */
val GlassSampling: SamplingMode = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)

/**
 * The picture through an optic, as a shader in view coordinates: [toView] places the picture,
 * the glass is centred at ([cx], [cy]) with [radius] pixels.
 */
fun opticShader(
    image: Image,
    toView: Matrix33,
    optic: Optic,
    cx: Float,
    cy: Float,
    radius: Float,
    zoom: Float,
    strength: Float,
    sampling: SamplingMode = GlassSampling,
): Shader {
    val picture = image.makeShader(FilterTileMode.DECAL, FilterTileMode.DECAL, sampling, toView)
    val builder = RuntimeShaderBuilder(opticRuntime)
    builder.child("image", picture)
    builder.uniform("center", cx, cy)
    builder.uniform("radius", radius)
    builder.uniform("zoom", zoom)
    builder.uniform("strength", strength)
    builder.uniform("optic", optic.index)
    return builder.makeShader(null)
}

// ---------------- Where the picture lies ----------------

/**
 * The picture in a view of [w] × [h]: shown [scale] times its size with its point ([cx], [cy]),
 * in picture pixels, at the view's centre.
 */
data class PictureView(val w: Float, val h: Float, val scale: Float, val cx: Float, val cy: Float) {
    fun toView(px: Float, py: Float): Offset = Offset(w / 2f + (px - cx) * scale, h / 2f + (py - cy) * scale)
    fun toPicture(v: Offset): Offset = Offset(cx + (v.x - w / 2f) / scale, cy + (v.y - h / 2f) / scale)
    fun matrix(): Matrix33 = Matrix33(scale, 0f, w / 2f - cx * scale, 0f, scale, h / 2f - cy * scale, 0f, 0f, 1f)
}

/** The scale that fits a picture of [iw] × [ih] into [w] × [h] whole. */
fun fitScale(iw: Float, ih: Float, w: Float, h: Float): Float = min(w / iw, h / ih)

/**
 * The picture whole, or [progress] of the way to where the glass is down: the scale grows
 * geometrically, so the zoom feels even, and the middle moves to the chosen point.
 */
fun pictureView(iw: Float, ih: Float, w: Float, h: Float, focus: GlassFocus?, progress: Float): PictureView {
    val fit = fitScale(iw, ih, w, h)
    if (focus == null || progress <= 0f) return PictureView(w, h, fit, iw / 2f, ih / 2f)
    val p = progress.coerceAtMost(1f)
    return PictureView(
        w, h,
        scale = fit * focus.magnification.pow(p),
        cx = iw / 2f + (focus.x * iw - iw / 2f) * p,
        cy = ih / 2f + (focus.y * ih - ih / 2f) * p,
    )
}

/** The magnification that fills [w] × [h] with a part of the picture [pw] × [ph] picture pixels large. */
fun magnificationFor(iw: Float, ih: Float, w: Float, h: Float, pw: Float, ph: Float): Float {
    val fit = fitScale(iw, ih, w, h)
    return min(w / max(pw, 1f), h / max(ph, 1f)) / fit
}

/**
 * A point on screen as the picture layer has it: mirrored, turned upside down, or both, the
 * layer's own coordinates are the screen's flipped. Its own inverse.
 */
fun toLayer(p: Offset, w: Float, h: Float, mirror: Boolean, upsideDown: Boolean): Offset {
    val flipX = mirror != upsideDown
    return Offset(if (flipX) w - p.x else p.x, if (upsideDown) h - p.y else p.y)
}
