package de.creaflect.actiondraw.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * The six rooms of the palette (CONCEPT.md, *Home: the palette*). Each has a pigment, which the
 * design system chooses: it marks what is held, selected or running in the room — sprinkled,
 * never a whole surface — and a button leading into a room carries its dab.
 */
enum class Room(val label: String) {
    PRACTICE("Practice"),
    LENS("Lens"),
    BOARDS("Boards"),
    CONCEPTS("Concepts"),
    SKETCH("Sketch"),
    COLLAGE("Collage"),
}

/**
 * A pigment: [mass] is the paint on paper and porcelain, [glow] the same pigment on the dark
 * ground, light enough to be read as text there; [onMass] is the text colour for a label on the
 * mass.
 */
@Immutable
data class Pigment(val name: String, val mass: Color, val glow: Color, val onMass: Color)

/** The room's pigment in the design system in use. */
val Room.pigment: Pigment
    @Composable
    @ReadOnlyComposable
    get() = LocalDesign.current.pigmentOf(this)

/** The wells round the palette, clockwise from the top. */
val PALETTE_ORDER = listOf(Room.PRACTICE, Room.LENS, Room.SKETCH, Room.COLLAGE, Room.CONCEPTS, Room.BOARDS)

/** A dab's seed per room, so each room's dab keeps its own shape everywhere it appears. */
val Room.seed: Int get() = ordinal * 31 + 5
