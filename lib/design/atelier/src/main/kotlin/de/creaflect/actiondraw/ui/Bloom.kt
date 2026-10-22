package de.creaflect.actiondraw.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ShaderBrush
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import kotlin.math.hypot

// The atelier's way of opening a room (CONCEPT.md, *Motion*): the room's pigment spreading from
// its well on wet paper, and draining back into it. Timed by the kit's [Motion].

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
    val c = b.room.pigment.mass
    Canvas(Modifier.fillMaxSize()) {
        val frame = bloomFrame(progress.value, b.opening, reduced)
        val origin = if (b.origin.isSpecified) b.origin else center
        val far = maxOf(
            hypot(origin.x, origin.y), hypot(size.width - origin.x, origin.y),
            hypot(origin.x, size.height - origin.y), hypot(size.width - origin.x, size.height - origin.y),
        )
        // The disk's edge wanders by ±10 %, so it must reach a little past the farthest corner.
        val radius = frame.radius * far * 1.15f
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
