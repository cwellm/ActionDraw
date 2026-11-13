package de.creaflect.actiondraw

import de.creaflect.actiondraw.board.Boards
import de.creaflect.actiondraw.board.LinkPreviews
import de.creaflect.actiondraw.collage.Collages
import de.creaflect.actiondraw.concept.Concepts
import de.creaflect.actiondraw.image.Pictures
import de.creaflect.actiondraw.image.PracticeHistory
import de.creaflect.actiondraw.sketch.Sketches

/**
 * The bare lib, as whoever builds on it sees it: everything there is to get and keep — boards,
 * concepts, pictures, what has been drawn, sketches, collages, the person's preferences — through one
 * object. How any of it is stored is not said here; `lib:data` implements it over the files, and
 * a test may hand in its own piece where it wants to see what happens without the disk or the
 * network.
 */
interface Lib {
    /** What the person chose, kept for the next run. */
    val preferences: Preferences

    /** The boards: which there are, and what each holds. */
    val boards: Boards

    /** The concepts: which there are, what each holds, and its documents. */
    val concepts: Concepts

    /** Pictures on disk: finding, decoding, thumbnails, colours, bringing them into a folder. */
    val pictures: Pictures

    /** Which pictures of a folder have been drawn, and which are to be drawn again. */
    val practice: PracticeHistory

    /** The sketches Live Sketch saves. */
    val sketches: Sketches

    /** The collages the Collage room keeps. */
    val collages: Collages

    /** A link's preview picture, fetched when asked for — the lib's only way online. */
    val previews: LinkPreviews

    /** The lib's logs, for finding out what a pen or a mouse really delivers. */
    val diagnostics: Diagnostics
}
