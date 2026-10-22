package de.creaflect.actiondraw.platform

import java.io.File

/**
 * The clipboard of the machine the lib runs on, as its host provides it. Copying files puts real
 * file references on it, so pasting them elsewhere duplicates the files; pasting takes a file list
 * or a picture (a browser's "Copy image"). Swappable, so the lib can be tested without touching
 * the clipboard of the machine its tests run on.
 */
interface SystemClipboard {
    /** What a paste brought. */
    sealed class Pasted {
        data class Files(val files: List<File>) : Pasted()

        /** A picture, encoded as PNG — the clipboard's image, in a form the lib can keep as a file. */
        class Bitmap(val png: ByteArray) : Pasted()
    }

    fun copyFiles(files: List<File>)

    fun copyText(text: String)

    /** What is on the clipboard that the lib can take, or null. */
    fun paste(): Pasted?

    /** No clipboard: nothing to paste, and a copy goes nowhere. Where a host has none, or a test wants none. */
    object None : SystemClipboard {
        override fun copyFiles(files: List<File>) = Unit
        override fun copyText(text: String) = Unit
        override fun paste(): Pasted? = null
    }
}
