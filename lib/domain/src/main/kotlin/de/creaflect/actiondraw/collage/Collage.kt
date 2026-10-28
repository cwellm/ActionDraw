package de.creaflect.actiondraw.collage

import java.io.File

/** A place on a picture, in its pixels, or on the mat, in mat units. */
data class Spot(val x: Float, val y: Float)

/**
 * A piece of a collage (F10.6): [source] cut along [outline] — its corners, in the source picture's
 * pixels — lying on the mat with the middle of its outline at ([x], [y]), turned [turn] degrees
 * clockwise, [scale] mat units to a picture pixel, and [flipped] over left to right.
 *
 * A piece with no [source] is a strip of masking tape, its outline in its own units; it [holds] the
 * pieces it was laid across, and they move as one with it.
 */
data class Piece(
    val id: String,
    val name: String,
    val source: File?,
    val outline: List<Spot>,
    val x: Float,
    val y: Float,
    val turn: Float = 0f,
    val scale: Float = 1f,
    val flipped: Boolean = false,
    val holds: List<String> = emptyList(),
) {
    /** Whether this is a strip of tape rather than a cut picture. */
    val isTape: Boolean get() = source == null
}

/**
 * A collage: pictures cut apart and laid on a cutting mat to test whether a scene holds — its
 * [pieces] from the bottom of the stack to the top. Where a piece lies is in mat units: the mat is
 * [MAT_WIDTH] × [MAT_HEIGHT], with a mark on its ruler every [RULER] units.
 */
data class Collage(val pieces: List<Piece> = emptyList()) {
    companion object {
        const val MAT_WIDTH = 1900f
        const val MAT_HEIGHT = 1300f
        const val RULER = 100f
    }
}

/**
 * The collages the Collage room keeps: each as its document, `<name>.collage.json`, wherever it
 * was made — at first in the collages home — and, once saved as a picture, `<name>.png` beside it.
 */
interface Collages {
    /** The collages kept or opened lately, anywhere, and those in the collages home; newest first. */
    fun recent(): List<File>

    /** The collage in the document [file]; null when it cannot be read. */
    fun read(file: File): Collage?

    /** Keeps [collage] in the document [file], which is then among the recent ones; false when it could not be written. */
    fun write(file: File, collage: Collage): Boolean

    /** Where a new collage called [name] is kept in [dir]: its document, under a name no other collage there has. */
    fun freeFile(dir: File, name: String): File

    /** The document [file] under [name] in its folder — and its picture with it — or null when that name is taken or it could not be moved. */
    fun rename(file: File, name: String): File?

    /** Notes that the document [file] was opened, so it is among the recent ones. */
    fun opened(file: File)

    companion object {
        /** What a collage's document is called after its name. */
        const val SUFFIX = ".collage.json"

        /** The collage's name, from its document. */
        fun nameOf(file: File): String = file.name.removeSuffix(SUFFIX)
    }
}
