package de.creaflect.actiondraw.platform

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.File

/**
 * The system's clipboard. Copying files puts real file references on it, so pasting in Explorer
 * duplicates the files; pasting takes a file list or a raw bitmap (a browser's "Copy image").
 * Swappable, so the board can be tested without touching the clipboard of the machine.
 */
interface SystemClipboard {
    /** What a paste brought. */
    sealed class Pasted {
        data class Files(val files: List<File>) : Pasted()

        data class Bitmap(val image: BufferedImage) : Pasted()
    }

    fun copyFiles(files: List<File>)

    fun copyText(text: String)

    /** What is on the clipboard that the app can take, or null. */
    fun paste(): Pasted?
}

/** The clipboard of the machine, through AWT. All of it best-effort: a busy clipboard is no crash. */
object AwtClipboard : SystemClipboard {
    override fun copyFiles(files: List<File>) {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(FileListTransferable(files), null) }
    }

    override fun copyText(text: String) {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
    }

    override fun paste(): SystemClipboard.Pasted? =
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.getContents(null) }.getOrNull()?.let(::read)

    /** What [contents] holds for the app: a file list before a picture, nothing for text alone. */
    internal fun read(contents: Transferable): SystemClipboard.Pasted? = runCatching {
        when {
            contents.isDataFlavorSupported(DataFlavor.javaFileListFlavor) -> {
                val files = (contents.getTransferData(DataFlavor.javaFileListFlavor) as List<*>).filterIsInstance<File>()
                SystemClipboard.Pasted.Files(files).takeIf { files.isNotEmpty() }
            }

            contents.isDataFlavorSupported(DataFlavor.imageFlavor) ->
                SystemClipboard.Pasted.Bitmap(toBuffered(contents.getTransferData(DataFlavor.imageFlavor) as java.awt.Image))

            else -> null
        }
    }.getOrNull()

    /** Clipboard images arrive as generic AWT images; writing a PNG needs a [BufferedImage]. */
    private fun toBuffered(image: java.awt.Image): BufferedImage {
        if (image is BufferedImage) return image
        val w = image.getWidth(null).coerceAtLeast(1)
        val h = image.getHeight(null).coerceAtLeast(1)
        val buffered = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = buffered.createGraphics()
        g.drawImage(image, 0, 0, null)
        g.dispose()
        return buffered
    }

    internal class FileListTransferable(private val files: List<File>) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)

        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.javaFileListFlavor

        override fun getTransferData(flavor: DataFlavor): Any =
            if (isDataFlavorSupported(flavor)) files else throw UnsupportedFlavorException(flavor)
    }
}
