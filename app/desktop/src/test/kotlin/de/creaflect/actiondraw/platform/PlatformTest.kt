package de.creaflect.actiondraw.platform

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Nothing here opens a browser or touches the machine's clipboard. */
class PlatformTest {
    @Test
    fun anAddressWithoutASchemeOpensAsHttps() {
        assertEquals("https://example.com/x", DesktopBrowser.addressOf("example.com/x").toString())
        assertEquals("http://example.com", DesktopBrowser.addressOf("http://example.com").toString(), "a scheme given is kept")
    }

    @Test
    fun aPasteTakesFilesBeforeAPicture() {
        val files = listOf(File("a.png"), File("b.jpg"))
        val both = object : Transferable {
            override fun getTransferDataFlavors() = arrayOf(DataFlavor.javaFileListFlavor, DataFlavor.imageFlavor)
            override fun isDataFlavorSupported(flavor: DataFlavor) = flavor in transferDataFlavors
            override fun getTransferData(flavor: DataFlavor): Any =
                if (flavor == DataFlavor.javaFileListFlavor) files else BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB)
        }
        assertEquals(SystemClipboard.Pasted.Files(files), AwtClipboard.read(both))
        assertEquals(SystemClipboard.Pasted.Files(files), AwtClipboard.read(AwtClipboard.FileListTransferable(files)), "what copyFiles puts there")
    }

    @Test
    fun aCopiedPictureArrivesAsABitmap() {
        // A browser's "Copy image": a picture and nothing else on the clipboard.
        val picture = BufferedImage(6, 4, BufferedImage.TYPE_INT_RGB)
        val copied = object : Transferable {
            override fun getTransferDataFlavors() = arrayOf(DataFlavor.imageFlavor)
            override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.imageFlavor
            override fun getTransferData(flavor: DataFlavor): Any = picture
        }
        val pasted = assertIs<SystemClipboard.Pasted.Bitmap>(AwtClipboard.read(copied))
        // Kept as PNG: what the lib writes into the board, at the picture's own size.
        val back = ImageIO.read(ByteArrayInputStream(pasted.png))
        assertEquals(6 to 4, back.width to back.height)
    }

    @Test
    fun textAloneOrAnEmptyListIsNothingToPaste() {
        assertNull(AwtClipboard.read(StringSelection("just words")))
        assertNull(AwtClipboard.read(AwtClipboard.FileListTransferable(emptyList())))
    }
}
