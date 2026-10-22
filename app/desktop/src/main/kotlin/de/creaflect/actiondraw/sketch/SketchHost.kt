package de.creaflect.actiondraw.sketch

import de.creaflect.actiondraw.board.ConceptRef
import java.io.File

/**
 * Everything Live Sketch is allowed to ask of the rest of the app — the same kind of seam as
 * `BoardHost` and `ConceptHost`. A sketch is saved *into* a board's or a concept's folder and
 * then handed over as a file already there, so the board or concept references it in place.
 */
interface SketchHost {
    /** Every board: name and folder. */
    fun boards(): List<Pair<String, File>>

    /** Puts pictures already inside the board's folder onto the board; returns a line to show. */
    fun addToBoard(dir: File, pictures: List<File>): String

    fun concepts(): List<ConceptRef>

    /** The concept's folder, or null if it cannot be found. */
    fun conceptDir(id: String): File?

    /** Puts pictures already inside the concept's folder into the concept. */
    fun addToConcept(id: String, pictures: List<File>): Boolean

    /** Navigate back to the menu. */
    fun leaveSketch()
}
