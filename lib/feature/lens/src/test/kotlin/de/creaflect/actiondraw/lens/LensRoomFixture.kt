package de.creaflect.actiondraw.lens

import de.creaflect.actiondraw.FileLib
import de.creaflect.actiondraw.platform.FileDialogs
import de.creaflect.actiondraw.platform.SystemClipboard
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

/**
 * The Lens room over a temp folder, with a host, dialogs and a clipboard whose answers a test
 * sets — and what the room asked of them, to look at afterwards.
 */
class LensRoomFixture {
    val root: File = Files.createTempDirectory("lens-room").toFile()
    val home = File(root, "home")
    val pictures = File(root, "pictures").apply { mkdirs() }
    val board = File(root, "Buch").apply { mkdirs() }

    /** What the next file dialog answers. */
    var chosen: File? = null

    /** Where the next save dialog says the view goes. */
    var saveTo: File? = null

    /** The name the last save dialog suggested. */
    var suggested: String? = null

    /** What is on the clipboard. */
    var pasted: SystemClipboard.Pasted? = null

    val pinned = mutableListOf<Pair<File, List<File>>>()
    var left = 0

    val host = object : LensHost {
        override fun boards() = listOf("Buch" to board)
        override fun pin(dir: File, pictures: List<File>): String {
            pinned += dir to pictures
            return "Pinned ${pictures.size} to Buch."
        }
        override fun leaveLens() {
            left++
        }
    }

    val dialogs = object : FileDialogs by FileDialogs.None {
        override fun chooseFile(start: File?, title: String, description: String, accepts: (File) -> Boolean): File? = chosen?.takeIf(accepts)
        override fun chooseSaveFile(suggested: String, start: File?): File? {
            this@LensRoomFixture.suggested = suggested
            return saveTo
        }
    }

    val clipboard = object : SystemClipboard by SystemClipboard.None {
        override fun paste(): SystemClipboard.Pasted? = pasted
    }

    fun state() = LensRoomState(FileLib(home), host, dialogs, clipboard)

    /** A [w] × [h] picture, orange on its left half and blue on its right, as PNG bytes. */
    fun pngBytes(w: Int = 40, h: Int = 20): ByteArray {
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            paint = java.awt.Color.ORANGE
            fillRect(0, 0, w / 2, h)
            paint = java.awt.Color(40, 70, 200)
            fillRect(w / 2, 0, w - w / 2, h)
            dispose()
        }
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    /** That picture as a file called [name].png among the pictures. */
    fun picture(name: String, w: Int = 40, h: Int = 20): File = File(pictures, "$name.png").apply { writeBytes(pngBytes(w, h)) }

    fun cleanup() {
        root.deleteRecursively()
    }
}
