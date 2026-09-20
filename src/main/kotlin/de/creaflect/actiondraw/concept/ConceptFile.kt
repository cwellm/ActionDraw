package de.creaflect.actiondraw.concept

import de.creaflect.actiondraw.board.BoardItem
import kotlinx.serialization.Serializable

/**
 * The concept sidecar (`.actiondraw_concept.json`), stored in the concept's folder next to its
 * pictures and documents. A concept is a *thing* — this character, this landscape, this creature
 * — that lives once and is linked onto any number of boards
 * ([docs/Concepts-Ideation.md](../../../../../../../../docs/Concepts-Ideation.md)).
 *
 * Its cards are the board's card types, reused as they are: pictures, notes, links. Documents are
 * `.md` files in `_docs/`, listed by relative path so they stay readable in any editor and never
 * balloon the sidecar. Unknown JSON keys are ignored on load, as for boards.
 */
@Serializable
data class ConceptFile(
    val version: Int = 1,
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
)

object ConceptKinds {
    /** Suggestions, not a fence: the kind field accepts anything. */
    val SUGGESTED = listOf("character", "creature", "landscape", "prop", "colour", "other")
}
