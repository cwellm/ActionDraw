package de.creaflect.sketch

import kotlinx.serialization.Serializable
import kotlin.math.pow

/**
 * The leads — and the other tools that fit the same model: a mechanical pencil (one width,
 * whatever the pressure), a charcoal stick (wide, black, all tooth), a fineliner (one width,
 * one blackness, no tooth), a brush pen (width from pressure, no tooth). Parameter sets over
 * one model, not a code path per tool (LEARNINGS L2); keys `1` to `7` in this order.
 */
enum class Lead(val label: String) {
    HARD("H"), MEDIUM("HB"), SOFT("4B"),
    MECHANICAL("0.5"), CHARCOAL("Charcoal"), INK("Fineliner"), BRUSH("Brush"),
}

/**
 * What a lead does per point: width and darkness as functions of pressure and speed.
 *
 * - `width = size · (minWidth + (maxWidth − minWidth) · pressure^gamma)` — sub-linear for a hard
 *   lead (it barely widens), linear for HB, opening fast for a soft one.
 * - `alpha = (floor + (ceiling − floor) · pressure) · (1 − speedK · min(speed / vRef, 1))` — the
 *   ceiling is the lead (a hard pencil pressed hard gets shiny before it gets black), and speed
 *   lightens, strongly for soft leads and little for hard ones.
 *
 * Widths are fractions of the brush size; speed is in page pixels per second. Tilt is the side
 * of the lead, 0 upright to 1 at a full tilt ([StrokeBuilder.FULL_TILT]): it stretches the mark
 * along its direction by [tiltWidth] and lightens it by [tiltAlpha] — a shading stroke, not a line.
 */
@Serializable
data class PencilModel(
    val minWidth: Float,
    val maxWidth: Float,
    val gamma: Float,
    val alphaFloor: Float,
    val alphaCeiling: Float,
    val speedK: Float,
    val vRef: Float,
    /** How soft the dab's edge is, as a fraction of its width: 0 crisp (a hard lead), ~0.16 a halo of loose graphite (a soft one). */
    val edge: Float = 0f,
    /** How much longer than the lead's width the mark is along the tilt's direction at a full tilt: `1 + tiltWidth · tilt`. */
    val tiltWidth: Float = 1.5f,
    /** How much the side of the lead lightens the mark at a full tilt: `alpha · (1 − tiltAlpha · tilt)`. */
    val tiltAlpha: Float = 0.5f,
    /** How much of the tooth shows: 1 a pencil, 0 ink that fills every pit at any pressure, above 1 charcoal that catches only the tops. */
    val grain: Float = 1f,
) {
    /** The lead's own width: what the pressure gives, before the side of the lead stretches the mark. */
    fun width(pressure: Float, size: Float): Float {
        val p = pressure.coerceIn(0f, 1f)
        return size * (minWidth + (maxWidth - minWidth) * p.pow(gamma))
    }

    /** How much longer than wide the mark is at this tilt, along the tilt's direction. */
    fun stretch(tilt: Float): Float = 1f + tiltWidth * tilt.coerceIn(0f, 1f)

    fun alpha(pressure: Float, speed: Float, tilt: Float = 0f): Float {
        val p = pressure.coerceIn(0f, 1f)
        val base = alphaFloor + (alphaCeiling - alphaFloor) * p
        val lightening = 1f - speedK * (speed / vRef).coerceIn(0f, 1f)
        val side = 1f - tiltAlpha * tilt.coerceIn(0f, 1f)
        return (base * lightening * side).coerceIn(0f, 1f)
    }
}

/** The numbers from LEARNINGS L2 — the starting point for the pencil study, not its result. */
object Pencils {
    val HARD = PencilModel(minWidth = 0.25f, maxWidth = 0.6f, gamma = 1.6f, alphaFloor = 0.08f, alphaCeiling = 0.55f, speedK = 0.15f, vRef = 1500f, edge = 0f)
    val MEDIUM = PencilModel(minWidth = 0.2f, maxWidth = 1.0f, gamma = 1.0f, alphaFloor = 0.1f, alphaCeiling = 0.8f, speedK = 0.3f, vRef = 1500f, edge = 0.06f)
    val SOFT = PencilModel(minWidth = 0.2f, maxWidth = 1.4f, gamma = 0.7f, alphaFloor = 0.12f, alphaCeiling = 0.97f, speedK = 0.45f, vRef = 1500f, edge = 0.16f)

    /** A 0.5 mm mechanical pencil: one width whatever the pressure, a hard-ish lead's darkness, hardly a side. */
    val MECHANICAL = PencilModel(minWidth = 0.4f, maxWidth = 0.4f, gamma = 1f, alphaFloor = 0.15f, alphaCeiling = 0.75f, speedK = 0.2f, vRef = 1500f, edge = 0f, tiltWidth = 0.2f, tiltAlpha = 0.2f)
    /** A charcoal stick: wide, black under a light hand already, loose at the edge, all tooth, a broad side. */
    val CHARCOAL = PencilModel(minWidth = 0.5f, maxWidth = 1.6f, gamma = 0.6f, alphaFloor = 0.25f, alphaCeiling = 1f, speedK = 0.5f, vRef = 1500f, edge = 0.25f, tiltWidth = 2f, tiltAlpha = 0.3f, grain = 1.5f)
    /** A fineliner: one width, one blackness, ink that fills every pit; no side, no speed. */
    val INK = PencilModel(minWidth = 0.6f, maxWidth = 0.6f, gamma = 1f, alphaFloor = 1f, alphaCeiling = 1f, speedK = 0f, vRef = 1500f, edge = 0f, tiltWidth = 0f, tiltAlpha = 0f, grain = 0f)
    /** A brush pen: a hair's width to a broad stroke from pressure alone, ink without tooth. */
    val BRUSH = PencilModel(minWidth = 0.08f, maxWidth = 1.6f, gamma = 1.2f, alphaFloor = 1f, alphaCeiling = 1f, speedK = 0f, vRef = 1500f, edge = 0.03f, tiltWidth = 0.4f, tiltAlpha = 0f, grain = 0f)

    /** The rubber at full strength: takes everything away at full pressure, is not lightened by speed, soft-edged. */
    val ERASER = PencilModel(minWidth = 0.3f, maxWidth = 1.0f, gamma = 1.0f, alphaFloor = 0.2f, alphaCeiling = 1f, speedK = 0f, vRef = 1500f, edge = 0.1f, tiltWidth = 0f, tiltAlpha = 0f)

    /**
     * The rubber at a [strength]: 1 lifts everything under it in one pass, a real rubber does
     * not — at 0.45 a pass takes a little under half, and a light mark needs two or three.
     */
    fun eraser(strength: Float): PencilModel {
        val k = strength.coerceIn(0.05f, 1f)
        return ERASER.copy(alphaFloor = 0.2f * k, alphaCeiling = k)
    }

    private val overrides = mutableMapOf<Lead, PencilModel>()

    fun of(lead: Lead): PencilModel = overrides[lead] ?: default(lead)

    fun default(lead: Lead): PencilModel = when (lead) {
        Lead.HARD -> HARD
        Lead.MEDIUM -> MEDIUM
        Lead.SOFT -> SOFT
        Lead.MECHANICAL -> MECHANICAL
        Lead.CHARCOAL -> CHARCOAL
        Lead.INK -> INK
        Lead.BRUSH -> BRUSH
    }

    /**
     * The pencil study's knob: replaces a lead's model for every brush that uses it from now on.
     * A sketch replayed under a changed model renders with the changed model — the document
     * keeps samples, not pixels.
     */
    fun set(lead: Lead, model: PencilModel) {
        overrides[lead] = model
    }

    fun reset(lead: Lead) {
        overrides.remove(lead)
    }
}

/** A lead, a colour (ARGB) and a size in page pixels — the full width of the lead at full pressure. */
data class Brush(
    val lead: Lead = Lead.MEDIUM,
    val color: Int = 0xFF1A1A1A.toInt(),
    val size: Float = 8f,
) {
    val model: PencilModel get() = Pencils.of(lead)
}
