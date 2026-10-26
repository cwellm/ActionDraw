package de.creaflect.actiondraw.board

import java.io.File

/**
 * The boards as the other rooms reach them: which there are, the pictures on one, and pinning
 * pictures onto one. The board room answers it; the app hands it to every room that draws from a
 * board or files a picture away on one — the same kind of seam as [ConceptSource].
 */
interface BoardSource {
    /** Every board there is, wherever it lives. */
    fun available(): List<BoardRef>

    /** The pictures on the board in [dir] — its own and its linked concepts' — read without opening it. */
    fun pictures(dir: File): List<File>

    /**
     * Puts [pictures] onto the board in [dir] without opening it: a picture inside its folder is
     * referred to where it is, one from outside is copied in. Returns a line to show.
     */
    fun pin(dir: File, pictures: List<File>): String

    /** No boards: nothing to draw from, and nowhere to pin. Where a host has none, or a test wants none. */
    object None : BoardSource {
        override fun available(): List<BoardRef> = emptyList()
        override fun pictures(dir: File): List<File> = emptyList()
        override fun pin(dir: File, pictures: List<File>) = ""
    }
}
