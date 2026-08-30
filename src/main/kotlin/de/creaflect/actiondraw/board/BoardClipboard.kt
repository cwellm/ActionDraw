package de.creaflect.actiondraw.board

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.File

/**
 * System-clipboard bridge for the board. Copy-out puts real file references on the clipboard, so
 * pasting in Explorer duplicates the files; paste-in accepts both file lists and raw bitmaps
 * (browser → "Copy image"). All IO is best-effort.
 */
object BoardClipboard {
    sealed class Pasted {
        data class Files(val files: List<File>) : Pasted()
        data class Bitmap(val image: BufferedImage) : Pasted()
    }

    fun copyFiles(files: List<File>) {
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(FileListTransferable(files), null)
        }
    }

    fun copyText(text: String) {
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        }
    }

    fun paste(): Pasted? = runCatching {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        when {
            clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor) -> {
                val files = (clipboard.getData(DataFlavor.javaFileListFlavor) as List<*>)
                    .filterIsInstance<File>()
                Pasted.Files(files).takeIf { files.isNotEmpty() }
            }

            clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor) ->
                Pasted.Bitmap(toBuffered(clipboard.getData(DataFlavor.imageFlavor) as java.awt.Image))

            else -> null
        }
    }.getOrNull()

    /** Clipboard images arrive as generic AWT images; PNG writing needs a [BufferedImage]. */
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

    private class FileListTransferable(private val files: List<File>) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean =
            flavor == DataFlavor.javaFileListFlavor

        override fun getTransferData(flavor: DataFlavor): Any =
            if (isDataFlavorSupported(flavor)) files else throw UnsupportedFlavorException(flavor)
    }
}
