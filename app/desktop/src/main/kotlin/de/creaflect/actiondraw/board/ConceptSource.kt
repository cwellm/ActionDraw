package de.creaflect.actiondraw.board

import java.io.File

/**
 * Where the board reads concepts from. The concept module stands behind this seam, so the board
 * knows nothing of concept files or the Concepts screens — only that a concept has an id, a
 * folder and items. [None] is a board without concepts, which is what the tests mostly use.
 */
interface ConceptSource {
    fun available(): List<ConceptRef>

    fun snapshot(id: String): ConceptSnapshot?

    /**
     * Puts cards into the concept: [pictures] are copied into its folder, [others] (notes, links)
     * are added as they are. Returns the items as the concept now holds them — the pictures
     * first, in the order given — or null if the concept could not be written.
     */
    fun addToConcept(id: String, pictures: List<File>, others: List<BoardItem>): List<BoardItem>?

    object None : ConceptSource {
        override fun available(): List<ConceptRef> = emptyList()
        override fun snapshot(id: String): ConceptSnapshot? = null
        override fun addToConcept(id: String, pictures: List<File>, others: List<BoardItem>): List<BoardItem>? = null
    }
}
