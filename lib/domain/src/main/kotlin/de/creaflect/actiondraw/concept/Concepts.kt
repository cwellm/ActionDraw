package de.creaflect.actiondraw.concept

import de.creaflect.actiondraw.LoadResult
import java.io.File

/**
 * The concepts: which there are, and what each holds. A concept is a folder of its own — its
 * pictures, its documents, and the concept file beside them — and is found by its id, so boards
 * that link it still find it after its folder has moved.
 */
interface Concepts {
    // ---- One concept ----

    /** Whether [dir] is a concept: whether it holds a concept file. */
    fun isConcept(dir: File): Boolean

    /** The concept in [dir] as its file holds it, not checked against the folder: for lists. */
    fun peek(dir: File): Concept?

    /** Opens the concept in [dir]; pictures and documents whose files are gone are left out. */
    fun load(dir: File): LoadResult<Concept>

    /**
     * Keeps [concept] as the one in [dir], written off the caller's thread; a newer one for the
     * same folder handed over before it was written replaces it.
     */
    fun save(dir: File, concept: Concept)

    /** [save], and waits for it: whether the concept file was written. */
    fun saveNow(dir: File, concept: Concept): Boolean

    // ---- Which concepts there are ----

    /** What is recorded for the concept in [dir], if it is recorded. */
    fun entryFor(dir: File): ConceptEntry?

    /** Where the concept [id] lives, if it is recorded. */
    fun byId(id: String): ConceptEntry?

    /** Records [concept] — its id, name and kind — as the one living in [dir]. */
    fun register(dir: File, concept: Concept)

    /**
     * Every concept: those recorded, and any concept folder in the concepts home not recorded
     * yet — recorded from now on, given an id if it has none. By kind, then name.
     */
    fun available(): List<ConceptEntry>

    // ---- Making and deleting ----

    /** Makes a concept of [concept] in the concepts home, in a folder named after it. */
    fun create(concept: Concept): ConceptCreation

    /**
     * Deletes the concept in [dir]: the folder and everything in it when [alsoFolder], else only
     * the concept file. Its record goes either way. False when the folder was to go and could not.
     */
    fun delete(dir: File, alsoFolder: Boolean): Boolean

    // ---- Documents ----

    /** The text of the document at [path] in the concept in [dir], or null if it is gone. */
    fun readDocument(dir: File, path: String): String?

    /**
     * Writes a document into the concept in [dir]: over the one at [path], or — when [path] is
     * null — as a new one named after [title]. Returns its path in the concept, null on failure.
     */
    fun writeDocument(dir: File, path: String?, title: String, text: String): String?

    /** Deletes the document at [path] in the concept in [dir]. */
    fun deleteDocument(dir: File, path: String)
}

/** How making a concept went. */
sealed interface ConceptCreation {
    /** Made, in [dir]. */
    data class Created(val dir: File) : ConceptCreation

    /** [dir] could not be made. */
    data class CouldNotCreate(val dir: File) : ConceptCreation

    /** [dir] is there, but the concept file could not be written into it. */
    data class CouldNotWrite(val dir: File) : ConceptCreation
}
