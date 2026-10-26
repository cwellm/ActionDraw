package de.creaflect.actiondraw.board

import java.io.File

/**
 * The concepts as the other rooms reach them: the board reads them, Live Sketch saves into them.
 * The concept room stands behind this seam, so the others know nothing of concept files or the
 * Concepts screens — only that a concept has an id, a folder and items; the same kind of seam as
 * [BoardSource]. [None] is a room without concepts, which is what the tests mostly use.
 */
interface ConceptSource {
    fun available(): List<ConceptRef>

    /** The concept's folder, where a picture made for it is written first; null if it cannot be found. */
    fun folderOf(id: String): File?

    fun snapshot(id: String): ConceptSnapshot?

    /**
     * Puts cards into the concept: [pictures] are copied into its folder, [others] (notes, links)
     * are added as they are. Returns the items as the concept now holds them — the pictures
     * first, in the order given — or null if the concept could not be written.
     */
    fun addToConcept(id: String, pictures: List<File>, others: List<BoardItem>): List<BoardItem>?

    object None : ConceptSource {
        override fun available(): List<ConceptRef> = emptyList()
        override fun folderOf(id: String): File? = null
        override fun snapshot(id: String): ConceptSnapshot? = null
        override fun addToConcept(id: String, pictures: List<File>, others: List<BoardItem>): List<BoardItem>? = null
    }
}
