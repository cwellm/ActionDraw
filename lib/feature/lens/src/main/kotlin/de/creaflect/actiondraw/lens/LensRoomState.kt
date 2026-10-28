package de.creaflect.actiondraw.lens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.creaflect.actiondraw.Lib
import de.creaflect.actiondraw.Room
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.board.BoardRef
import de.creaflect.actiondraw.board.BoardSource
import de.creaflect.actiondraw.handoff.Handed
import de.creaflect.actiondraw.handoff.Handoff
import de.creaflect.actiondraw.handoff.Receiver
import de.creaflect.actiondraw.handoff.Route
import de.creaflect.actiondraw.image.Pictures
import de.creaflect.actiondraw.platform.FileDialogs
import de.creaflect.actiondraw.platform.SystemClipboard
import de.creaflect.actiondraw.safeFileName
import de.creaflect.actiondraw.ui.HeightsSource
import de.creaflect.actiondraw.ui.LENSES
import de.creaflect.actiondraw.ui.keptIn
import org.jetbrains.skia.Image
import java.io.File

/**
 * What the Lens room asks of the app itself: the way out, back to where it was entered from. The
 * boards a view is pinned to are the domain's `BoardSource`; the rooms a picture goes on to, the
 * hand-off seam's (`Handoff`).
 */
fun interface LensHost {
    /** Back to where the room was entered from. */
    fun leaveLens()

    /** Nowhere to go: the room on its own, as a test has it. */
    object None : LensHost {
        override fun leaveLens() = Unit
    }
}

/** What the Lens room is looking at: a picture on disk, or one that came as PNG bytes — pasted, or a sketch. */
sealed class LensPicture {
    /** What the picture is called, without an extension. */
    abstract val name: String

    data class OnDisk(val file: File) : LensPicture() {
        override val name: String get() = file.nameWithoutExtension
    }

    class Pasted(val png: ByteArray, override val name: String) : LensPicture()
}

/** How the room lays the picture out: through the lens alone, beside the original, or through two lenses at once — [short], where the header is narrow. */
enum class LensLayout(val label: String, val short: String) {
    ALONE("Alone", "Alone"),
    BESIDE("Beside the original", "Beside"),
    TWO("Two lenses", "Two lenses"),
}

/** The two lenses compared side by side: the first on the left, the second on the right. */
enum class LensSide { FIRST, SECOND }

/** A view waiting to be drawn, and then saved where the person chose or pinned to a board. */
sealed class LensExport {
    data class Save(val target: File) : LensExport()

    data class Pin(val dir: File) : LensExport()
}

/**
 * The Lens room (CONCEPT.md, *Lens*): any picture — a file, the clipboard, a board's card, a sketch
 * — seen through the lens with no clock running: alone, beside the original, or through two lenses
 * at once; and the view saved as a picture, or pinned to a board beside its original. Plain class
 * backed by Compose state, so the screen recomposes on change.
 *
 * [lib] is where pictures are decoded and written and the glass is kept from one run to the next;
 * [host] the way out; [dialogs] and [clipboard] the host's, for opening and pasting; [boards] what
 * a view is pinned to; [handoff] the rooms a picture is taken into from here, and that hand
 * pictures here.
 */
class LensRoomState(
    private val lib: Lib,
    private val host: LensHost = LensHost.None,
    /** The host's file dialogs, for opening a picture and saving the view. */
    val dialogs: FileDialogs = FileDialogs.None,
    private val clipboard: SystemClipboard = SystemClipboard.None,
    private val boards: BoardSource = BoardSource.None,
    private val handoff: Handoff = Handoff.None,
) : Receiver {
    /** Where the room's pictures are decoded and written. */
    val pictures: Pictures get() = lib.pictures

    /** The picture being looked at, or null until one is opened. */
    var picture by mutableStateOf<LensPicture?>(null)
        private set

    /** The picture's file, while it is one: what the glass knows it by. */
    val file: File? get() = (picture as? LensPicture.OnDisk)?.file

    /** Where a relief's heights are chosen from: a picture, by the host's dialog. */
    private val heights = HeightsSource(dialogs, lib.pictures)

    /** The lens the picture is seen through, its glass kept between runs. */
    val first = LensState().also {
        it.glass.keptIn(lib.preferences)
        it.form.heightsFrom = heights
    }

    /** The second lens, on the right while two are compared. */
    val second = LensState().also {
        it.glass.keptIn(lib.preferences)
        it.form.heightsFrom = heights
    }

    /** The original as it is, beside the lens: a lens never turned, and no glass taken up over it. */
    val plain = LensState()

    var layout by mutableStateOf(LensLayout.ALONE)
        private set

    /** Which of the two lenses the tray and the keys work, while two are compared. */
    var active by mutableStateOf(LensSide.FIRST)
        private set

    /** The lens the tray and the keys work: the first, unless two are compared and the second is chosen. */
    val lens: LensState get() = if (layout == LensLayout.TWO && active == LensSide.SECOND) second else first

    /** What the last opening, paste, save or pin came to, until the next one. */
    var notice by mutableStateOf<String?>(null)
        private set

    /** A view to be drawn and then saved or pinned; the screen draws it and hands it to [finish]. */
    var export by mutableStateOf<LensExport?>(null)
        private set

    // ---- The picture ----

    /** Looks at [file]; the glasses are lifted off whatever they were down on. */
    fun open(file: File) = show(LensPicture.OnDisk(file))

    /** Looks at a picture that came as [png] bytes — pasted, or a sketch — called [name]. */
    fun openPng(png: ByteArray, name: String) = show(LensPicture.Pasted(png, name))

    private fun show(next: LensPicture) {
        picture = next
        notice = null
        listOf(first, second).forEach { it.glass.wholePicture() }
    }

    /** Asks for a picture to look at; false when none was chosen. */
    fun chooseFile(): Boolean {
        val start = file?.parentFile ?: lib.preferences.lastFolder()
        val chosen = dialogs.chooseFile(start, "Open a picture", "Pictures", lib.pictures::isPicture) ?: return false
        open(chosen)
        return true
    }

    /** Looks at what is on the clipboard: a picture file, or a picture itself. False when there is neither. */
    fun paste(): Boolean {
        val pasted = clipboard.paste()
        when (pasted) {
            is SystemClipboard.Pasted.Files -> pasted.files.firstOrNull(lib.pictures::isPicture)?.let { open(it); return true }
            is SystemClipboard.Pasted.Bitmap -> { openPng(pasted.png, "Pasted picture"); return true }
            null -> Unit
        }
        notice = "There is no picture on the clipboard"
        return false
    }

    /** The picture decoded whole, or null when it cannot be. Call it off the UI thread. */
    fun decode(picture: LensPicture): Image? = when (picture) {
        is LensPicture.OnDisk -> lib.pictures.decode(picture.file)
        is LensPicture.Pasted -> runCatching { Image.makeFromEncoded(picture.png) }.getOrNull()
    }

    // ---- Taken from the other rooms (the hand-off seam) ----

    override val room: Room get() = Room.LENS

    /** Any picture, a file or a page, is looked at here. */
    override fun offer(what: Handed): String = "Check the values"

    override fun take(what: Handed) = when (what) {
        is Handed.Picture -> open(what.file)
        is Handed.Drawn -> openPng(what.png(), what.name)
    }

    /** The picture as it is handed on from here: the file, or the bytes it came as. */
    private fun handed(picture: LensPicture): Handed = when (picture) {
        is LensPicture.OnDisk -> Handed.Picture(picture.file)
        is LensPicture.Pasted -> Handed.Drawn(picture.name) { picture.png }
    }

    /** Where the picture can be taken from here: into Sketch, to draw over it… */
    fun routes(): List<Route> = picture?.let { handoff.routes(handed(it), Room.LENS) }.orEmpty()

    /** Takes the picture into [to]'s room. */
    fun handOff(to: Room) {
        picture?.let { handoff.send(handed(it), to) }
    }

    // ---- How it is laid out ----

    /**
     * Lays the picture out [next]. Two lenses compared start as two different ones: the second
     * takes Edge — or Notan, where the first is Edge — until it is turned itself.
     */
    fun arrange(next: LensLayout) {
        layout = next
        if (next == LensLayout.TWO && second.viewMode == ViewMode.NONE) {
            second.viewMode = if (first.viewMode == ViewMode.EDGE) ViewMode.NOTAN else ViewMode.EDGE
        }
        if (next != LensLayout.TWO) active = LensSide.FIRST
    }

    /** Works [side]'s lens from now on — while two are compared; otherwise there is only the one. */
    fun choose(side: LensSide) {
        if (layout == LensLayout.TWO) active = side
    }

    /** Tab: the other lens, while two are compared. */
    fun otherLens(): Boolean {
        if (layout != LensLayout.TWO) return false
        active = if (active == LensSide.FIRST) LensSide.SECOND else LensSide.FIRST
        return true
    }

    // ---- Out of the room ----

    /** The boards a view can be pinned to. */
    fun boards(): List<BoardRef> = boards.available()

    /** What the view is called: the picture's name and the lens it is seen through — "pose - notan". */
    fun viewName(): String {
        val name = picture?.name ?: "picture"
        val through = lens.viewMode.takeIf { it != ViewMode.NONE }?.let { mode -> LENSES.first { it.first == mode }.second.lowercase() } ?: "seen"
        return safeFileName("$name - $through")
    }

    /** Asks where the view should go; once the screen has drawn it, [finish] writes it there. */
    fun save() {
        if (picture == null) return
        val target = dialogs.chooseSaveFile(viewName() + ".png", file?.parentFile) ?: return
        export = LensExport.Save(target)
    }

    /** Pins the view to the board in [dir], beside its original, once the screen has drawn it. */
    fun pinTo(dir: File) {
        if (picture == null) return
        export = LensExport.Pin(dir)
    }

    /**
     * The view the screen drew for [export] — [png], or null when it could not be drawn — saved
     * where it was to go, or written into the board's folder and pinned there with its original.
     */
    fun finish(png: ByteArray?) {
        val pending = export ?: return
        export = null
        if (png == null) {
            notice = "The view could not be drawn"
            return
        }
        notice = when (pending) {
            is LensExport.Save -> lib.pictures.writePng(png, pending.target)?.let { "Saved as ${it.name}" } ?: "Could not save ${pending.target.name}"
            is LensExport.Pin -> {
                val written = lib.pictures.writePng(png, File(pending.dir, viewName() + ".png"), beside = true)
                if (written == null) "Could not write into the board" else boards.pin(pending.dir, listOfNotNull(file, written))
            }
        }
    }

    /** Esc at the top of the room: back to where it was entered from. */
    fun leave() = host.leaveLens()
}
