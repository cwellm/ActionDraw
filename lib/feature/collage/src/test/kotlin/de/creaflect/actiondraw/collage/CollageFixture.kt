package de.creaflect.actiondraw.collage

import de.creaflect.actiondraw.FileLib
import de.creaflect.actiondraw.Room
import de.creaflect.actiondraw.board.BoardRef
import de.creaflect.actiondraw.board.BoardSource
import de.creaflect.actiondraw.board.ConceptRef
import de.creaflect.actiondraw.board.ConceptSnapshot
import de.creaflect.actiondraw.board.ConceptSource
import de.creaflect.actiondraw.board.ImageItem
import de.creaflect.actiondraw.handoff.Handed
import de.creaflect.actiondraw.handoff.Handoff
import de.creaflect.actiondraw.handoff.Route
import de.creaflect.actiondraw.platform.FileDialogs
import de.creaflect.actiondraw.platform.SystemClipboard
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

/**
 * The Collage room over a temp folder, with dialogs and a clipboard whose answers a test sets, a
 * board and a concept to draw from, and the other rooms as a list of what was sent there — and how
 * often the room was left, what was pinned and sent, to look at afterwards.
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

    /** The one board there is, and the pictures on it. */
    val board = File(root, "boards/Drachenbuch").apply { mkdirs() }
    var boardPictures: List<File> = emptyList()

    /** What was pinned, onto which board. */
    val pinned = ArrayList<Pair<File, List<File>>>()

    val boards = object : BoardSource {
        override fun available() = listOf(BoardRef("Drachenbuch", board))
        override fun pictures(dir: File) = if (dir == board) boardPictures else emptyList()
        override fun pin(dir: File, pictures: List<File>): String {
            pinned += dir to pictures
            return "Pinned to Drachenbuch"
        }
    }

    /** The one concept there is, its folder and the pictures in it, by their names there. */
    val concept = File(root, "concepts/wing").apply { mkdirs() }
    var conceptPictures: List<String> = emptyList()

    val concepts = object : ConceptSource by ConceptSource.None {
        override fun available() = listOf(ConceptRef("wing", "Wing"))
        override fun snapshot(id: String) =
            if (id == "wing") ConceptSnapshot("wing", "Wing", concept, conceptPictures.mapIndexed { i, path -> ImageItem("c$i", path) }) else null
    }

    /** What was sent into which room. */
    val sent = ArrayList<Pair<Handed, Room>>()

    val handoff = object : Handoff {
        override fun routes(what: Handed, from: Room) = listOf(Route(Room.SKETCH, "Sketch over"), Route(Room.LENS, "Check the values"))
        override fun send(what: Handed, to: Room) {
            sent += what to to
        }
    }

    fun state() = CollageState(lib, { left++ }, dialogs, clipboard, boards, concepts, handoff)

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
