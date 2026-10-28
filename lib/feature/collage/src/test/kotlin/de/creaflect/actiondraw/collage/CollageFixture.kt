package de.creaflect.actiondraw.collage

import de.creaflect.actiondraw.FileLib
import de.creaflect.actiondraw.platform.FileDialogs
import de.creaflect.actiondraw.platform.SystemClipboard
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

/**
 * The Collage room over a temp folder, with dialogs and a clipboard whose answers a test sets — and
 * how often the room was left, to look at afterwards.
 */
class CollageFixture {
    val root: File = Files.createTempDirectory("collage-room").toFile()
    val home = File(root, "home")
    val pictures = File(root, "pictures").apply { mkdirs() }
    val lib = FileLib(home)

    /** What the next file dialog answers. */
    var chosen: File? = null

    /** What is on the clipboard. */
    var pasted: SystemClipboard.Pasted? = null

    var left = 0

    val dialogs = object : FileDialogs by FileDialogs.None {
        override fun chooseFile(start: File?, title: String, description: String, accepts: (File) -> Boolean): File? = chosen?.takeIf(accepts)
    }

    val clipboard = object : SystemClipboard by SystemClipboard.None {
        override fun paste(): SystemClipboard.Pasted? = pasted
    }

    fun state() = CollageState(lib, { left++ }, dialogs, clipboard)

    /** A [w] × [h] picture, orange on its left half and blue on its right, as PNG bytes. */
    fun pngBytes(w: Int = 400, h: Int = 200): ByteArray {
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            paint = java.awt.Color(230, 140, 40)
            fillRect(0, 0, w / 2, h)
            paint = java.awt.Color(40, 70, 200)
            fillRect(w / 2, 0, w - w / 2, h)
            dispose()
        }
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    /** That picture as a file called [name].png among the pictures. */
    fun picture(name: String, w: Int = 400, h: Int = 200): File = File(pictures, "$name.png").apply { writeBytes(pngBytes(w, h)) }

    fun cleanup() {
        root.deleteRecursively()
    }
}
