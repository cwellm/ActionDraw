package de.creaflect.actiondraw.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sun.jna.Native
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import kotlin.math.hypot

/**
 * The atelier's motion (CONCEPT.md, *Motion: wet, weighty, drawn*): four movements, each with one
 * job. With reduced motion on, every one of them becomes a short cross-fade.
 */
object Motion {
    /**
     * Bloom: a room opens as its pigment spreads from the well. The design's ease-out
     * (0.2, 0.8, 0.2, 1) covered the screen in 80 ms — a flash, not a spreading; this one lets the
     * pigment be seen leaving the well before it rushes to the corners.
     */
    const val BLOOM_MS = 420
    val BLOOM_EASING = CubicBezierEasing(0.35f, 0f, 0.25f, 1f)

    /** The share of the bloom by which the pigment covers the screen and the room is opened under it. */
    const val BLOOM_COVERS = 0.55f

    /** Everything, with reduced motion on. */
    const val REDUCED_MS = 120

    /** Recede: how long the pen is down before the chrome fades, and how long the fade takes. */
    const val RECEDE_AFTER_MS = 1500L
    const val RECEDE_MS = 600
    const val RETURN_MS = 200

    /** How close to its edge the pointer must come for receded chrome to show again, and how strongly. */
    val APPROACH = 56.dp
    const val APPROACH_ALPHA = 0.7f

    /**
     * Settle: a card lifts in [LIFT_MS] to [LIFT_SCALE] larger, leans into its sideways speed
     * ([LEAN_PER_PX] degrees per pixel of a move, at most [MAX_LEAN]), and lands on a spring that
     * overshoots a little.
     */
    const val LIFT_MS = 120
    const val LIFT_SCALE = 0.03f
    const val LEAN_PER_PX = 0.5f
    const val MAX_LEAN = 4f
    const val SETTLE_DAMPING = 0.7f
    const val SETTLE_STIFFNESS = 380f

    /** Draw-on: a new group's frame is drawn round its cards in this long, then its tag appears. */
    const val DRAW_ON_MS = 360
}

/** Whether a card is lifted off the table, for tests to read. */
val Lifted = SemanticsPropertyKey<Boolean>("Lifted")
var SemanticsPropertyReceiver.lifted by Lifted

/** How far a card leans while dragged sideways by [dx] pixels in one move. */
fun leanFor(dx: Float): Float = (dx * Motion.LEAN_PER_PX).coerceIn(-Motion.MAX_LEAN, Motion.MAX_LEAN)

/** Whether to keep movement to cross-fades: ActionDraw's own setting, or the system's when unset. */
val LocalReducedMotion = compositionLocalOf { false }

/**
 * Windows' *Show animations in Windows* (`SPI_GETCLIENTAREAANIMATION`): off means the person
 * asked for less movement. Anywhere else, or if it cannot be read, movement is on.
 */
fun systemPrefersReducedMotion(): Boolean = runCatching {
    if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return false
    val on = IntByReference(1)
    User32Spi.INSTANCE.SystemParametersInfoW(SPI_GETCLIENTAREAANIMATION, 0, on, 0) && on.value == 0
}.getOrDefault(false)

private const val SPI_GETCLIENTAREAANIMATION = 0x1042

@Suppress("FunctionName")
private interface User32Spi : StdCallLibrary {
    fun SystemParametersInfoW(uiAction: Int, uiParam: Int, pvParam: IntByReference, fWinIni: Int): Boolean

    companion object {
        val INSTANCE: User32Spi by lazy { Native.load("user32", User32Spi::class.java, W32APIOptions.DEFAULT_OPTIONS) }
    }
}

// ---------------- Recede ----------------

/**
 * How visible a bar of chrome is: all there unless it has [receded]; once receded, back at
 * [Motion.APPROACH_ALPHA] while the pointer is [near] its edge, and wholly while [over] it.
 */
fun chromeAlpha(receded: Boolean, near: Boolean, over: Boolean): Float = when {
    !receded || over -> 1f
    near -> Motion.APPROACH_ALPHA
    else -> 0f
}

/** The alpha a bar of chrome is aiming for, for tests to read: chrome is drawn, not announced. */
val ChromeAlpha = SemanticsPropertyKey<Float>("ChromeAlpha")
var SemanticsPropertyReceiver.chromeAlpha by ChromeAlpha

/** Where the pointer is over a room (in window coordinates) and how often it has moved. */
class PointerWatch {
    var position by mutableStateOf(Offset.Unspecified)
        internal set
    var moves by mutableStateOf(0)
        internal set
    internal var origin = Offset.Zero
}

/** Watches the pointer over this element and everything in it, taking nothing from anyone. */
fun Modifier.watchPointer(watch: PointerWatch): Modifier = this
    .onGloballyPositioned { watch.origin = it.positionInRoot() }
    .pointerInput(watch) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: continue
                watch.position = change.position + watch.origin
                if (event.type == PointerEventType.Move) watch.moves++
            }
        }
    }

/** The edge of the room a bar of chrome lies along. */
enum class ChromeEdge { TOP, BOTTOM }

/**
 * A bar of chrome that steps back while the work goes on. [receded] says when — each room knows
 * what working means there. Receded, it comes back at [Motion.APPROACH_ALPHA] as the pointer
 * nears its [edge] and wholly when the pointer is over it, which also calls [onOver]. Only this
 * recomposes as the pointer moves: what lies around it, a sketch page among them, is untouched.
 */
@Composable
fun RecedingChrome(
    receded: Boolean,
    watch: PointerWatch,
    edge: ChromeEdge,
    modifier: Modifier = Modifier,
    onOver: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val isReceded by rememberUpdatedState(receded)
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val approach = with(LocalDensity.current) { Motion.APPROACH.toPx() }
    val over by remember { derivedStateOf { watch.position.isSpecified && bounds.contains(watch.position) } }
    val target by remember {
        derivedStateOf {
            val p = watch.position
            val near = p.isSpecified && when (edge) {
                ChromeEdge.TOP -> p.y <= bounds.bottom + approach
                ChromeEdge.BOTTOM -> p.y >= bounds.top - approach
            }
            chromeAlpha(isReceded, near, over)
        }
    }
    LaunchedEffect(over) { if (over) onOver() }
    val alpha by animateFloatAsState(
        target,
        tween(if (reduced) Motion.REDUCED_MS else if (target < 1f) Motion.RECEDE_MS else Motion.RETURN_MS),
    )
    Box(
        modifier
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .graphicsLayer { this.alpha = alpha }
            .semantics { chromeAlpha = target },
    ) { content() }
}

// ---------------- Bloom ----------------

/**
 * Pigment spreading on wet paper: a disk whose edge wanders with the direction (a cauliflower
 * edge), darker in a rim just inside it where the pigment gathers as the water dries, with a
 * little granulation inside. Premultiplied, so it lays over whatever is underneath.
 */
internal const val BLOOM_SKSL = """
uniform float2 center;
uniform float radius;
uniform float4 color;
uniform float seed;

float hash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453123);
}

float vnoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + float2(1.0, 0.0));
    float c = hash(i + float2(0.0, 1.0));
    float d = hash(i + float2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

half4 main(float2 xy) {
    float2 d = xy - center;
    float r = length(d);
    float2 dir = r > 0.0 ? d / r : float2(1.0, 0.0);
    float n = vnoise(dir * 3.0 + seed) * 0.65 + vnoise(dir * 9.0 + seed * 1.7) * 0.35;
    float edge = radius * (0.9 + 0.2 * n);
    float soft = max(3.0, radius * 0.02);
    float inside = 1.0 - smoothstep(edge - soft, edge, r);
    float rimWidth = max(6.0, radius * 0.06);
    float rim = smoothstep(edge - rimWidth, edge - soft * 0.5, r);
    // Granulation in two turned octaves, so no grid shows through.
    float2 q = float2(xy.x * 0.8 - xy.y * 0.6, xy.x * 0.6 + xy.y * 0.8);
    float grain = (vnoise(q * 0.035 + seed) * 0.6 + vnoise(xy.yx * 0.09 - seed) * 0.4) * 0.045;
    float3 c = color.rgb * (1.04 - 0.28 * rim - grain);
    float a = inside * color.a;
    return half4(half3(c * a), half(a));
}
"""

/** A room's pigment spreading from its well ([opening]), or draining back into it. */
data class Bloom(val room: Room, val origin: Offset, val opening: Boolean, val id: Long = System.nanoTime())

/** One frame of a bloom: the pigment's radius as a share of the way to the farthest corner, and its alpha. */
data class BloomFrame(val radius: Float, val alpha: Float)

/**
 * The bloom at [progress] (0 to 1, linear in time). Opening, the pigment spreads to cover the
 * screen by [Motion.BLOOM_COVERS] and then thins away over the room; draining, it starts full and
 * shrinks back into the well, thinning only at the end. Reduced, it is a faint wash that fades.
 */
fun bloomFrame(progress: Float, opening: Boolean, reduced: Boolean): BloomFrame {
    val p = progress.coerceIn(0f, 1f)
    return when {
        reduced -> BloomFrame(1f, 0.35f * (1f - p))
        opening -> BloomFrame(
            Motion.BLOOM_EASING.transform((p / Motion.BLOOM_COVERS).coerceAtMost(1f)),
            if (p <= Motion.BLOOM_COVERS) 1f else 1f - (p - Motion.BLOOM_COVERS) / (1f - Motion.BLOOM_COVERS),
        )
        else -> BloomFrame(1f - Motion.BLOOM_EASING.transform(p), if (p < 0.8f) 1f else (1f - p) / 0.2f)
    }
}

/** When, as a share of the bloom, the room is opened under the pigment. */
fun bloomCovers(reduced: Boolean): Float = if (reduced) 0f else Motion.BLOOM_COVERS

/**
 * Plays [bloom] over everything: [onCovered] when the pigment covers the screen (the moment to
 * open the room under it), [onDone] when it has gone.
 */
@Composable
fun BloomLayer(bloom: Bloom?, onCovered: () -> Unit, onDone: () -> Unit) {
    val b = bloom ?: return
    val reduced = LocalReducedMotion.current
    val progress = remember(b.id) { Animatable(0f) }
    val covered by rememberUpdatedState(onCovered)
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(b.id) {
        val coverAt = bloomCovers(reduced)
        var fired = !b.opening
        if (!fired && coverAt == 0f) {
            fired = true
            covered()
        }
        progress.animateTo(1f, tween(if (reduced) Motion.REDUCED_MS else Motion.BLOOM_MS, easing = LinearEasing)) {
            if (!fired && value >= coverAt) {
                fired = true
                covered()
            }
        }
        if (!fired) covered()
        done()
    }
    val effect = remember { runCatching { RuntimeEffect.makeForShader(BLOOM_SKSL) }.getOrNull() }
    Canvas(Modifier.fillMaxSize()) {
        val frame = bloomFrame(progress.value, b.opening, reduced)
        val origin = if (b.origin.isSpecified) b.origin else center
        val far = maxOf(
            hypot(origin.x, origin.y), hypot(size.width - origin.x, origin.y),
            hypot(origin.x, size.height - origin.y), hypot(size.width - origin.x, size.height - origin.y),
        )
        // The disk's edge wanders by ±10 %, so it must reach a little past the farthest corner.
        val radius = frame.radius * far * 1.15f
        val c = b.room.pigment.mass
        if (effect != null) {
            val shader = RuntimeShaderBuilder(effect).apply {
                uniform("center", origin.x, origin.y)
                uniform("radius", radius)
                uniform("color", c.red, c.green, c.blue, frame.alpha)
                uniform("seed", (b.id % 997).toFloat())
            }.makeShader(null)
            drawRect(ShaderBrush(shader))
        } else {
            drawCircle(c.copy(alpha = frame.alpha), radius, origin)
        }
    }
}

