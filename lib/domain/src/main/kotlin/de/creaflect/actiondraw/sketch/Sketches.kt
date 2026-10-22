package de.creaflect.actiondraw.sketch

import java.io.File

/**
 * The sketches Live Sketch saves: each one twice beside itself, as `<name>.png` to look at and as
 * its document, `<name>.sketch.json`, to go on drawing. The document's content is the sketch
 * engine's business; here it is text to keep.
 */
interface Sketches {
    /** The sketches saved or opened lately, anywhere, and those in the sketches home; newest first. */
    fun recent(): List<File>

    /** The text of the document [file], or null when it cannot be read. */
    fun read(file: File): String?

    /** Notes that the document [file] was opened, so it is among the recent ones. */
    fun opened(file: File)

    /**
     * The sketch [picture] was saved from, when Live Sketch saved it: `name.sketch.json` beside
     * `name.png`. Null for a picture that is not a sketch's.
     */
    fun documentOf(picture: File): File?

    /** Whether a sketch called [name] is in [dir] already, as a picture or as a document. */
    fun isTaken(dir: File, name: String): Boolean

    /**
     * Writes the sketch [name] into [dir]: [png] as its picture, [document] as its document,
     * which is then among the recent ones. Gives the document's file, or what went wrong.
     */
    fun write(dir: File, name: String, png: ByteArray, document: String): Result<File>
}
