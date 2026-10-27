package de.creaflect.actiondraw.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A form the picture is wrapped onto (F10.10, CONCEPT.md, *Lens*). Seeing how a flat picture bends
 * over a ball, a can, a cone, a ring or a page tilted away — or how a chrome ball reflects it — is
 * seeing form: what the drawing of a sleeve, a face or a floor has to get right.
 */
enum class Form(val label: String) {
    /** The picture wrapped once round a ball, its middle facing you. */
    SPHERE("Sphere"),

    /** The picture round a can, as tall as it is for its width. */
    CYLINDER("Cylinder"),

    /** The picture round a cone, narrowing to its tip. */
    CONE("Cone"),

    /** The picture round a ring: along it, and round its tube. */
    TORUS("Torus"),

    /** The picture flat, on a page tilted away: foreshortened, as on a floor or a wall. */
    PLANE("Plane"),

    /** A mirrored ball: the picture all round you, reflected in it. */
    CHROME("Chrome ball"),
}

/**
 * The form the picture is on — or none — and how it is turned and lit. Plain class backed by
 * Compose state; it belongs to a [de.creaflect.actiondraw.lens.LensState], beside the glass, and
 * the two take turns: the picture is on a form or under the glass, not both.
 */
class FormState {
    var form by mutableStateOf<Form?>(null)
        private set

    /** Turned about the upright, in radians: a drag to the right turns the front to the right. */
    var yaw by mutableStateOf(0f)
        private set

    /** Tipped about the level, in radians: a drag down turns the front down. */
    var pitch by mutableStateOf(0f)
        private set

    /** Lit by the lamp, so the form reads; off, only the picture's own values. */
    var lit by mutableStateOf(true)

    /** Told when a form is chosen: the glass is put away. */
    var onShow: (() -> Unit)? = null

    /** The picture on [next], first seen from where that form reads best — or on no form. */
    fun choose(next: Form?) {
        if (next != null && next != form) turnTo(next)
        form = next
        if (next != null) onShow?.invoke()
    }

    /** P: the next form, and after the last none. */
    fun next() = choose(
        when (val now = form) {
            null -> Form.entries.first()
            Form.entries.last() -> null
            else -> Form.entries[now.ordinal + 1]
        },
    )

    /** A drag: turned by [dYaw] and tipped by [dPitch], in radians — never further than on its edge. */
    fun turn(dYaw: Float, dPitch: Float) {
        yaw = wrap(yaw + dYaw)
        pitch = (pitch + dPitch).coerceIn(-MAX_PITCH, MAX_PITCH)
    }

    /** Back to where the form reads best. */
    fun straighten() {
        form?.let(::turnTo)
    }

    /** As [other] is: its form, turned and lit as it is. What a saved view is drawn on. */
    fun copyFrom(other: FormState) {
        form = other.form
        yaw = other.yaw
        pitch = other.pitch
        lit = other.lit
    }

    private fun turnTo(form: Form) {
        // A solid seen a little from above and turned; a page tilted well back, as a floor is.
        yaw = if (form == Form.PLANE) 0.25f else -0.45f
        pitch = if (form == Form.PLANE) 0.95f else -0.3f
    }

    companion object {
        /** Tipped no further than nearly on its edge: past it, up and down would swap. */
        const val MAX_PITCH = 1.5f

        private fun wrap(a: Float): Float {
            val turn = 2f * PI.toFloat()
            return a - turn * floor((a + PI.toFloat()) / turn)
        }
    }
}

// ---------------- Drawing it ----------------

/**
 * One shader for every form. A ray from a camera five radii away goes through each pixel; turned
 * into the form's own frame, it meets the form — a sphere, a can, a cone, a ring (marched), a page —
 * or misses it and leaves the pixel clear. Where it meets the form, the picture is read at the
 * point's place on it ([image] in picture pixels, [picture] its size) and lit by the lamp, from up
 * and to the left; a chrome ball reflects the ray instead, and reads the picture as the world all
 * round, the lamp shining in it and its rim darker where it turns away. Four rays a pixel, so the
 * form's edge is smooth. Mirrored by [formRay] — the unlit picture on each form — which the tests
 * hold this to.
 */
const val FORM_SKSL = """
uniform shader image;
uniform float2 centre;
uniform float radius;
uniform float3 rx;
uniform float3 ry;
uniform float3 rz;
uniform float kind;
uniform float2 dims;
uniform float2 picture;
uniform float lit;

const float PI = 3.14159265;
const float CAM = 5.0;
const float SPHERE = 0.92;

float3 toForm(float3 v) { return float3(dot(rx, v), dot(ry, v), dot(rz, v)); }
float3 toView(float3 v) { return rx * v.x + ry * v.y + rz * v.z; }

float around(float3 s) { return atan(s.x, -s.z) / (2.0 * PI) + 0.5; }

half4 ray(float2 p) {
    float2 q = (p - centre) / radius;
    float3 rd = normalize(float3(q.x, -q.y, CAM));
    float3 o = toForm(float3(0.0, 0.0, -CAM));
    float3 d = toForm(rd);
    float t = -1.0;
    float3 n = float3(0.0, 1.0, 0.0);
    float2 uv = float2(0.0, 0.0);
    float plain = 0.0;
    if (kind < 0.5 || kind > 4.5) {
        float b = dot(o, d);
        float c = dot(o, o) - SPHERE * SPHERE;
        float h = b * b - c;
        if (h >= 0.0) {
            t = -b - sqrt(h);
            float3 s = o + t * d;
            n = s;
            uv = float2(around(s), acos(clamp(s.y / SPHERE, -1.0, 1.0)) / PI);
        }
    } else if (kind < 1.5) {
        float rho = dims.x;
        float hh = dims.y;
        float a = d.x * d.x + d.z * d.z;
        float b = o.x * d.x + o.z * d.z;
        float c = o.x * o.x + o.z * o.z - rho * rho;
        float h = b * b - a * c;
        if (a > 0.000001 && h >= 0.0) {
            float t1 = (-b - sqrt(h)) / a;
            float3 s = o + t1 * d;
            if (t1 > 0.0 && abs(s.y) <= hh) {
                t = t1;
                n = float3(s.x, 0.0, s.z);
                uv = float2(around(s), (hh - s.y) / (2.0 * hh));
            }
        }
        if (abs(d.y) > 0.000001) {
            float yc = d.y > 0.0 ? -hh : hh;
            float tc = (yc - o.y) / d.y;
            float3 sc = o + tc * d;
            if (tc > 0.0 && (t < 0.0 || tc < t) && sc.x * sc.x + sc.z * sc.z <= rho * rho) {
                t = tc;
                n = float3(0.0, yc, 0.0);
                plain = 1.0;
            }
        }
    } else if (kind < 2.5) {
        float rho = dims.x;
        float hh = dims.y;
        float k2 = (rho / (2.0 * hh)) * (rho / (2.0 * hh));
        float aa = hh - o.y;
        float a = d.x * d.x + d.z * d.z - k2 * d.y * d.y;
        float b = o.x * d.x + o.z * d.z + k2 * aa * d.y;
        float c = o.x * o.x + o.z * o.z - k2 * aa * aa;
        float h = b * b - a * c;
        if (h >= 0.0 && abs(a) > 0.000001) {
            float sq = sqrt(h);
            float t1 = (-b - sq) / a;
            float t2 = (-b + sq) / a;
            float y1 = o.y + t1 * d.y;
            float y2 = o.y + t2 * d.y;
            float tn = -1.0;
            if (t1 > 0.0 && y1 >= -hh && y1 <= hh) { tn = t1; }
            if (t2 > 0.0 && y2 >= -hh && y2 <= hh && (tn < 0.0 || t2 < tn)) { tn = t2; }
            if (tn > 0.0) {
                t = tn;
                float3 s = o + t * d;
                n = float3(s.x, k2 * (hh - s.y), s.z);
                uv = float2(around(s), (hh - s.y) / (2.0 * hh));
            }
        }
        if (abs(d.y) > 0.000001) {
            float tc = (-hh - o.y) / d.y;
            float3 sc = o + tc * d;
            if (tc > 0.0 && (t < 0.0 || tc < t) && sc.x * sc.x + sc.z * sc.z <= rho * rho) {
                t = tc;
                n = float3(0.0, -1.0, 0.0);
                plain = 1.0;
            }
        }
    } else if (kind < 3.5) {
        float big = dims.x;
        float tube = dims.y;
        float b = dot(o, d);
        float c = dot(o, o) - (big + tube) * (big + tube);
        float h = b * b - c;
        if (h >= 0.0) {
            float tt = max(0.0, -b - sqrt(h));
            float tEnd = -b + sqrt(h);
            for (int i = 0; i < 48; i++) {
                float3 s = o + tt * d;
                float dist = length(float2(length(s.xz) - big, s.y)) - tube;
                if (dist < 0.0005) { t = tt; break; }
                tt += dist;
                if (tt > tEnd) { break; }
            }
            if (t > 0.0) {
                float3 s = o + t * d;
                float l = max(length(s.xz), 0.00001);
                n = float3(s.x * (1.0 - big / l), s.y, s.z * (1.0 - big / l));
                uv = float2(around(s), fract(0.25 - atan(s.y, l - big) / (2.0 * PI)));
            }
        }
    } else {
        if (abs(d.z) > 0.000001) {
            float tp = -o.z / d.z;
            float3 s = o + tp * d;
            if (tp > 0.0 && abs(s.x) <= dims.x && abs(s.y) <= dims.y) {
                t = tp;
                n = float3(0.0, 0.0, -1.0);
                uv = float2(s.x / (2.0 * dims.x) + 0.5, 0.5 - s.y / (2.0 * dims.y));
            }
        }
    }
    if (t < 0.0) { return half4(0.0); }
    float3 nv = normalize(toView(n));
    if (dot(nv, rd) > 0.0) { nv = -nv; }
    if (kind > 4.5) {
        float3 rv = reflect(rd, nv);
        float3 r = toForm(rv);
        float2 e = float2(around(r), acos(clamp(r.y, -1.0, 1.0)) / PI);
        half4 seen = image.eval(float2(e.x, min(e.y, 0.999)) * picture);
        half3 chrome = seen.rgb * 0.92;
        if (lit > 0.5) {
            // The lamp in the ball, and its rim darker where the ball turns away.
            float shine = pow(max(0.0, dot(rv, normalize(float3(-0.5, 0.6, -0.62)))), 48.0);
            float rim = 0.55 + 0.45 * max(0.0, -dot(nv, rd));
            chrome = chrome * rim + half3(shine * 0.85);
        }
        return half4(chrome, seen.a);
    }
    half4 colour = plain > 0.5 ? half4(0.86, 0.83, 0.78, 1.0) : image.eval(float2(uv.x, min(uv.y, 0.999)) * picture);
    if (lit > 0.5) {
        float light = 0.32 + 0.68 * max(0.0, dot(nv, normalize(float3(-0.5, 0.6, -0.62))));
        colour = half4(colour.rgb * light, colour.a);
    }
    return colour;
}

half4 main(float2 p) {
    return (ray(p + float2(-0.25, -0.25)) + ray(p + float2(0.25, -0.25)) + ray(p + float2(-0.25, 0.25)) + ray(p + float2(0.25, 0.25))) * 0.25;
}
"""

private val formRuntime: RuntimeEffect by lazy { RuntimeEffect.makeForShader(FORM_SKSL) }

/**
 * The form's own measures, for a picture [aspect] wide for its height: a can as tall as the picture
 * is for its width round it, a page with the picture's proportions; the cone and the ring as they
 * are. In the form's frame, where the sphere's radius is a little under one.
 */
fun formDims(form: Form, aspect: Float): Pair<Float, Float> = when (form) {
    Form.CYLINDER -> {
        val rho = 0.78f
        val hh = PI.toFloat() * rho / aspect
        val fit = 0.78f / max(rho, hh)
        rho * fit to hh * fit
    }
    Form.CONE -> 0.75f to 0.85f
    Form.TORUS -> 0.6f to 0.28f
    Form.PLANE -> if (aspect >= 1f) 0.85f to 0.85f / aspect else 0.85f * aspect to 0.85f
    Form.SPHERE, Form.CHROME -> 0f to 0f
}

/** The turn from the view into the form's frame, as its three rows: turned by [yaw], tipped by [pitch]. */
fun formRows(yaw: Float, pitch: Float): Array<FloatArray> {
    val cy = cos(yaw)
    val sy = sin(yaw)
    val cp = cos(pitch)
    val sp = sin(pitch)
    return arrayOf(floatArrayOf(cy, sy * sp, -sy * cp), floatArrayOf(0f, cp, sp), floatArrayOf(sy, -cy * sp, cy * cp))
}

/**
 * The picture [image] on [form], turned by [yaw] and tipped by [pitch], as a shader in view
 * coordinates: the form's middle at ([cx], [cy]), [radius] pixels to the sphere's edge.
 */
fun formShader(
    image: Image,
    form: Form,
    yaw: Float,
    pitch: Float,
    cx: Float,
    cy: Float,
    radius: Float,
    lit: Boolean,
    sampling: SamplingMode = GlassSampling,
): Shader {
    val picture = image.makeShader(FilterTileMode.REPEAT, FilterTileMode.REPEAT, sampling, null)
    val rows = formRows(yaw, pitch)
    val dims = formDims(form, image.width.toFloat() / image.height)
    val builder = RuntimeShaderBuilder(formRuntime)
    builder.child("image", picture)
    builder.uniform("centre", cx, cy)
    builder.uniform("radius", radius)
    builder.uniform("rx", rows[0][0], rows[0][1], rows[0][2])
    builder.uniform("ry", rows[1][0], rows[1][1], rows[1][2])
    builder.uniform("rz", rows[2][0], rows[2][1], rows[2][2])
    builder.uniform("kind", form.ordinal.toFloat())
    builder.uniform("dims", dims.first, dims.second)
    builder.uniform("picture", image.width.toFloat(), image.height.toFloat())
    builder.uniform("lit", if (lit) 1f else 0f)
    return builder.makeShader(null)
}

/** Where a ray meets a form: the place on the picture it reads ([u], [v], fractions of it) — or an end with none — and the lamp's light there. */
data class FormHit(val u: Float, val v: Float, val plain: Boolean, val light: Float)

/**
 * Where the ray through ([qx], [qy]) — a point of the view in sphere radii from the form's middle,
 * y down — meets [form], turned by [rows] and measured by [dims]: the same arithmetic as one ray of
 * [FORM_SKSL]. Null where it misses.
 */
fun formRay(form: Form, qx: Float, qy: Float, rows: Array<FloatArray>, dims: Pair<Float, Float>): FormHit? {
    val cam = 5f
    fun toForm(v: V3) = V3(dot(rows[0], v), dot(rows[1], v), dot(rows[2], v))
    fun toView(v: V3) = V3(
        rows[0][0] * v.x + rows[1][0] * v.y + rows[2][0] * v.z,
        rows[0][1] * v.x + rows[1][1] * v.y + rows[2][1] * v.z,
        rows[0][2] * v.x + rows[1][2] * v.y + rows[2][2] * v.z,
    )
    fun around(s: V3) = (atan2(s.x, -s.z) / (2.0 * PI) + 0.5).toFloat()
    val rd = V3(qx, -qy, cam).normal()
    val o = toForm(V3(0f, 0f, -cam))
    val d = toForm(rd)
    var t = -1f
    var n = V3(0f, 1f, 0f)
    var u = 0f
    var v = 0f
    var plain = false
    val (dx, dy) = dims
    when (form) {
        Form.SPHERE, Form.CHROME -> {
            val sphere = 0.92f
            val b = o dot d
            val c = (o dot o) - sphere * sphere
            val h = b * b - c
            if (h >= 0f) {
                t = -b - sqrt(h)
                val s = o + d * t
                n = s
                u = around(s)
                v = (acos((s.y / sphere).coerceIn(-1f, 1f)) / PI).toFloat()
            }
        }
        Form.CYLINDER -> {
            val a = d.x * d.x + d.z * d.z
            val b = o.x * d.x + o.z * d.z
            val c = o.x * o.x + o.z * o.z - dx * dx
            val h = b * b - a * c
            if (a > 0.000001f && h >= 0f) {
                val t1 = (-b - sqrt(h)) / a
                val s = o + d * t1
                if (t1 > 0f && abs(s.y) <= dy) {
                    t = t1
                    n = V3(s.x, 0f, s.z)
                    u = around(s)
                    v = (dy - s.y) / (2f * dy)
                }
            }
            if (abs(d.y) > 0.000001f) {
                val yc = if (d.y > 0f) -dy else dy
                val tc = (yc - o.y) / d.y
                val sc = o + d * tc
                if (tc > 0f && (t < 0f || tc < t) && sc.x * sc.x + sc.z * sc.z <= dx * dx) {
                    t = tc
                    n = V3(0f, yc, 0f)
                    plain = true
                }
            }
        }
        Form.CONE -> {
            val k2 = (dx / (2f * dy)) * (dx / (2f * dy))
            val aa = dy - o.y
            val a = d.x * d.x + d.z * d.z - k2 * d.y * d.y
            val b = o.x * d.x + o.z * d.z + k2 * aa * d.y
            val c = o.x * o.x + o.z * o.z - k2 * aa * aa
            val h = b * b - a * c
            if (h >= 0f && abs(a) > 0.000001f) {
                val sq = sqrt(h)
                val t1 = (-b - sq) / a
                val t2 = (-b + sq) / a
                val y1 = o.y + t1 * d.y
                val y2 = o.y + t2 * d.y
                var tn = -1f
                if (t1 > 0f && y1 >= -dy && y1 <= dy) tn = t1
                if (t2 > 0f && y2 >= -dy && y2 <= dy && (tn < 0f || t2 < tn)) tn = t2
                if (tn > 0f) {
                    t = tn
                    val s = o + d * t
                    n = V3(s.x, k2 * (dy - s.y), s.z)
                    u = around(s)
                    v = (dy - s.y) / (2f * dy)
                }
            }
            if (abs(d.y) > 0.000001f) {
                val tc = (-dy - o.y) / d.y
                val sc = o + d * tc
                if (tc > 0f && (t < 0f || tc < t) && sc.x * sc.x + sc.z * sc.z <= dx * dx) {
                    t = tc
                    n = V3(0f, -1f, 0f)
                    plain = true
                }
            }
        }
        Form.TORUS -> {
            val b = o dot d
            val c = (o dot o) - (dx + dy) * (dx + dy)
            val h = b * b - c
            if (h >= 0f) {
                var tt = max(0f, -b - sqrt(h))
                val tEnd = -b + sqrt(h)
                for (i in 0 until 48) {
                    val s = o + d * tt
                    val dist = sqrt((sqrt(s.x * s.x + s.z * s.z) - dx).let { it * it } + s.y * s.y) - dy
                    if (dist < 0.0005f) {
                        t = tt
                        break
                    }
                    tt += dist
                    if (tt > tEnd) break
                }
                if (t > 0f) {
                    val s = o + d * t
                    val l = max(sqrt(s.x * s.x + s.z * s.z), 0.00001f)
                    n = V3(s.x * (1f - dx / l), s.y, s.z * (1f - dx / l))
                    u = around(s)
                    val w = (0.25 - atan2(s.y, l - dx) / (2.0 * PI)).toFloat()
                    v = w - floor(w)
                }
            }
        }
        Form.PLANE -> {
            if (abs(d.z) > 0.000001f) {
                val tp = -o.z / d.z
                val s = o + d * tp
                if (tp > 0f && abs(s.x) <= dx && abs(s.y) <= dy) {
                    t = tp
                    n = V3(0f, 0f, -1f)
                    u = s.x / (2f * dx) + 0.5f
                    v = 0.5f - s.y / (2f * dy)
                }
            }
        }
    }
    if (t < 0f) return null
    var nv = toView(n).normal()
    if ((nv dot rd) > 0f) nv = nv * -1f
    if (form == Form.CHROME) {
        val r = toForm(rd - nv * (2f * (rd dot nv)))
        return FormHit(around(r), (acos(r.y.coerceIn(-1f, 1f)) / PI).toFloat(), plain = false, light = 0.92f)
    }
    val lamp = V3(-0.5f, 0.6f, -0.62f).normal()
    return FormHit(u, v, plain, 0.32f + 0.68f * max(0f, nv dot lamp))
}

/** A point or a direction in three dimensions, for [formRay]'s arithmetic. */
private data class V3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Float) = V3(x * k, y * k, z * k)
    infix fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    fun normal(): V3 = sqrt(this dot this).let { V3(x / it, y / it, z / it) }
}

private fun dot(row: FloatArray, v: V3) = row[0] * v.x + row[1] * v.y + row[2] * v.z
