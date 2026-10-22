package de.creaflect.actiondraw.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp

/**
 * How far a thing stands off the table. One lamp casts every shadow (CONCEPT.md, *Materials*): the
 * higher a thing is, the further its shadow falls and the softer it gets.
 */
enum class Lift {
    RESTING,
    LIFTED,
    HELD,
}

/** The tooth of a sheet of paper, from hot-pressed to cold-pressed. */
enum class Tooth {
    SMOOTH,
    MEDIUM,
    ROUGH,
}

/** How strongly a sheet's tooth shows: enough to read as paper, not so much as to look dirty. */
const val PAPER_TOOTH = 0.2f

/**
 * The design's materials: the texture of the ground and of paper, the one lamp's shadows, and the
 * small things on the table — paint dabs, wells, tape, pins, a pencil, a paperclip. Everything is
 * drawn in code. A screen reaches them as [Design.materials]; the modifiers below read them from
 * the design in use, and the drawing functions are called inside `with(materials) { … }` in a
 * draw scope, which has no design of its own to ask.
 */
interface Materials {
    /** How far [lift] stands off the table. */
    fun depthOf(lift: Lift): Dp

    /** The faint rule of lined paper, an index card's top line, and masking tape's paper. */
    val ruleBlue: Color
    val ruleRed: Color
    val tape: Color

    /** The ground's grain over whatever the element draws. */
    fun grain(): Modifier

    /** The ground's grain under whatever the element draws: for a surface things lie on. */
    fun grainBehind(): Modifier

    /** The tooth of real paper over the colour under it: after a background, before the content. */
    fun paperTooth(tooth: Tooth, strength: Float): Modifier

    /** Lines ruled across a card: the first [first] from the top, then every [spacing]. */
    fun ruled(first: Dp, spacing: Dp, rule: Color, header: Color?): Modifier

    /** The lamp's shadow under the element, in the outline of [shape]. */
    fun lampShadow(lift: Lift, shape: Shape, alpha: Float): Modifier

    /** A shadow of [path] cast by the lamp, for something [depth] px off the table. */
    fun DrawScope.drawLampShadow(path: Path, depth: Float, alpha: Float = 0.45f)

    /** A dab of [pigment] filling [size] at [topLeft]; the same [seed], the same dab. */
    fun DrawScope.drawDab(topLeft: Offset, size: Size, pigment: Pigment, seed: Int, wet: Boolean = true)

    /** A well, sunk into a palette, at [center]. */
    fun DrawScope.drawWell(center: Offset, radius: Float)

    /** A strip of tape [size] long and wide, turned by [degrees] about [center]. */
    fun DrawScope.drawTape(center: Offset, size: Size, degrees: Float, seed: Int, color: Color = tape)

    /** A push-pin's head of [pigment]. */
    fun DrawScope.drawPin(center: Offset, radius: Float, pigment: Pigment)

    /** A pencil lying on the table, its point at [tip], turned by [degrees]. */
    fun DrawScope.drawPencil(tip: Offset, length: Float, thickness: Float, degrees: Float, lacquer: Color)

    /** A paperclip, [height] tall, standing at [topLeft]: what holds a link to the board. */
    fun DrawScope.drawPaperclip(topLeft: Offset, height: Float, color: Color = Color(0xFF8D949B))
}

/** The ground's grain over whatever this draws. */
fun Modifier.grain(): Modifier = composed { LocalDesign.current.materials.grain() }

/** The ground's grain under whatever this draws: for a surface things lie on, like a board. */
fun Modifier.grainBehind(): Modifier = composed { LocalDesign.current.materials.grainBehind() }

/**
 * The tooth of real paper over whatever colour is under it. For drawing after a background and
 * before the content: `.background(Design.colors.paper).paperTooth()`.
 */
fun Modifier.paperTooth(tooth: Tooth = Tooth.MEDIUM, strength: Float = PAPER_TOOTH): Modifier =
    composed { LocalDesign.current.materials.paperTooth(tooth, strength) }

/**
 * Lines ruled across a card: the first [first] from the top, then every [spacing], in [rule] (the
 * design's faint blue unless given); an index card has a red one at the top too ([header]).
 */
fun Modifier.ruled(first: Dp, spacing: Dp, rule: Color = Color.Unspecified, header: Color? = null): Modifier = composed {
    val materials = LocalDesign.current.materials
    materials.ruled(first, spacing, if (rule.isSpecified) rule else materials.ruleBlue, header)
}

/** The lamp's shadow under this element, in the outline of [shape]. */
fun Modifier.lampShadow(lift: Lift, shape: Shape = RectangleShape, alpha: Float = 0.45f): Modifier =
    composed { LocalDesign.current.materials.lampShadow(lift, shape, alpha) }

/**
 * Draws [room]'s dab filling [size] at [topLeft], in [design] — for a draw scope, which cannot ask
 * for the design itself: take `LocalDesign.current` before drawing.
 */
fun DrawScope.drawRoomDab(design: DesignSystem, room: Room, topLeft: Offset, size: Size, wet: Boolean = true) =
    with(design.materials) { drawDab(topLeft, size, design.pigmentOf(room), room.seed, wet) }

/** An index card's red top line, in the design in use. */
val RuleRed: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalDesign.current.materials.ruleRed

/** Masking tape's colour, in the design in use. */
val TapeColor: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalDesign.current.materials.tape
