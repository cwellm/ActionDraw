package de.creaflect.actiondraw.concept

import de.creaflect.actiondraw.board.BoardItem
import de.creaflect.actiondraw.board.BoardLayouts
import de.creaflect.actiondraw.board.Camera

/**
 * A concept: a *thing* — this character, this landscape, this creature
 * — that lives once and is linked onto any number of boards
 * ([docs/Concepts-Ideation.md](../../../../../../../../docs/Concepts-Ideation.md)).
 *
 * Its cards are the board's card types, reused as they are: pictures, notes, links. Documents are
 * `.md` files in `_docs/`, listed by relative path so they stay readable in any editor and never
 * balloon what is stored. How a concept is stored (`.actiondraw_concept.json`) is the model's
 * business, mapped by `lib:data`.
 */
data class Concept(
    /** Stable identity; boards refer to a concept by this, never by path. */
    val id: String = "",
    val name: String = "",
    /** A label, not a schema: character, creature, landscape, prop, colour — or anything typed. */
    val kind: String = "",
    /** A few words about the concept, shown on its page; plain text. */
    val notes: String = "",
    val items: List<BoardItem> = emptyList(),
    /** Relative paths of the concept's documents, `/`-separated, in display order. */
    val documents: List<String> = emptyList(),
    /** [BoardLayouts.GRID] (a grid of cards) or [BoardLayouts.FREE] (placed by hand — the concept's own arrangement). */
    val layout: String = BoardLayouts.GRID,
    /** Last free-layout viewport, so the concept reopens where it was left. */
    val camera: Camera? = null,
)

object ConceptKinds {
    /** Suggestions, not a fence: the kind field accepts anything. */
    val SUGGESTED = listOf("character", "creature", "landscape", "prop", "colour", "other")
}
