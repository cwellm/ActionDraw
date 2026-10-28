package de.creaflect.actiondraw.collage

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.creaflect.actiondraw.Lib
import de.creaflect.actiondraw.Room
import de.creaflect.actiondraw.handoff.Handed
import de.creaflect.actiondraw.handoff.Receiver
import de.creaflect.actiondraw.image.Pictures
import de.creaflect.actiondraw.newId
import de.creaflect.actiondraw.platform.FileDialogs
import de.creaflect.actiondraw.platform.SystemClipboard
import de.creaflect.actiondraw.safeFileName
import java.io.File
import kotlin.math.min

/**
 * What the Collage room asks of the app itself: the way out, back to where it was entered from.
 * Pictures come to it through the hand-off seam (`Receiver`), which the app wires.
 */
fun interface CollageHost {
    /** Back to where the room was entered from. */
    fun leaveCollage()

    /** Nowhere to go: the room on its own, as a test has it. */
    object None : CollageHost {
        override fun leaveCollage() = Unit
    }
}

/**
 * The Collage room (CONCEPT.md, *Collage*; F10.6): pictures cut apart and laid on a cutting mat,
 * to test whether a scene holds. One collage is open at a time; it is kept as it changes — every
 * piece added, moved, turned, flipped, restacked or taken away — in its document, which is made in
 * the collages home with the first piece. Every change can be undone. Plain class backed by Compose
 * state, so the screen recomposes on change.
 *
 * [lib] keeps the collages and reads the pictures; [host] is the way out; [dialogs] and [clipboard]
 * the host's, for adding a picture and pasting one. Pictures handed from the other rooms — a board's
 * card, a concept's picture, a sketch's page, a view in the Lens — come in as pieces.
 */
class CollageState(
    private val lib: Lib,
    private val host: CollageHost = CollageHost.None,
    /** The host's file dialogs, for adding a picture. */
    val dialogs: FileDialogs = FileDialogs.None,
    private val clipboard: SystemClipboard = SystemClipboard.None,
) : Receiver {
    /** Where the pieces' pictures are read. */
    val pictures: Pictures get() = lib.pictures

    /** The open collage's document; null until its first piece is kept. */
    var file by mutableStateOf<File?>(null)
        private set

    /** What the open collage is called. */
    var name by mutableStateOf(UNTITLED)
        private set

    var collage by mutableStateOf(Collage())
        private set

    /** The piece chosen, which the tools and the keys work on. */
    var selected by mutableStateOf<String?>(null)
        private set

    /** What the last opening, adding or renaming came to, until the next one. */
    var notice by mutableStateOf<String?>(null)
        private set

    private var undos by mutableStateOf(emptyList<Collage>())
    private var redos by mutableStateOf(emptyList<Collage>())

    /** The collage as a drag began, so the whole drag is one change to undo. */
    private var dragFrom: Collage? = null

    val canUndo: Boolean get() = undos.isNotEmpty()
    val canRedo: Boolean get() = redos.isNotEmpty()

    /** The piece chosen, if it is still there. */
    val selectedPiece: Piece? get() = selected?.let { id -> collage.pieces.firstOrNull { it.id == id } }

    // ---- The collage ----

    /** Coming into the room: the collage last kept, unless one is open already. */
    fun onEnter() {
        if (file == null && collage.pieces.isEmpty()) recent().firstOrNull()?.let(::open)
    }

    /** The collages kept or opened lately, newest first. */
    fun recent(): List<File> = lib.collages.recent()

    /** A new, empty collage; its document is made with its first piece. */
    fun newCollage() {
        show(null, UNTITLED, Collage())
    }

    /** Opens the collage kept in [target]; false, and says so, when it cannot be read. */
    fun open(target: File): Boolean {
        val read = lib.collages.read(target)
        if (read == null) {
            notice = "Could not read ${target.name}"
            return false
        }
        lib.collages.opened(target)
        show(target, Collages.nameOf(target), read)
        return true
    }

    private fun show(document: File?, called: String, next: Collage) {
        file = document
        name = called
        collage = next
        selected = null
        undos = emptyList()
        redos = emptyList()
        notice = null
    }

    /** Calls the collage [next]: its document — and its picture, once there is one — takes the name. */
    fun rename(next: String): Boolean {
        val clean = safeFileName(next)
        if (clean.isEmpty() || clean == name) return false
        val document = file
        if (document == null) {
            name = clean
            return true
        }
        val moved = lib.collages.rename(document, clean)
        if (moved == null) {
            notice = "There is a collage called $clean already"
            return false
        }
        file = moved
        name = clean
        return true
    }

    // ---- Pieces in ----

    /** Asks for a picture and lays it on the mat; false when none was chosen. */
    fun chooseFile(): Boolean {
        val chosen = dialogs.chooseFile(lib.preferences.lastFolder(), "Add a picture", "Pictures", lib.pictures::isPicture) ?: return false
        chosen.parentFile?.let(lib.preferences::setLastFolder)
        return addPicture(chosen) != null
    }

    /** Lays what is on the clipboard on the mat: a picture file, or a picture itself. False when there is neither. */
    fun paste(): Boolean {
        when (val pasted = clipboard.paste()) {
            is SystemClipboard.Pasted.Files -> pasted.files.firstOrNull(lib.pictures::isPicture)?.let { return addPicture(it) != null }
            is SystemClipboard.Pasted.Bitmap -> return addPng(pasted.png, "Pasted picture") != null
            null -> Unit
        }
        notice = "There is no picture on the clipboard"
        return false
    }

    /**
     * Lays [source] on the mat whole, as a piece to cut from: as large as fits a good part of the
     * mat, its middle at [at] — or, where none is given, a little apart from the last one laid in
     * the middle. It is chosen, on top of the others. Null, and said, when it cannot be read.
     */
    fun addPicture(source: File, at: Spot? = null): Piece? {
        val (w, h) = lib.pictures.sizeOf(source) ?: run {
            notice = "Could not read ${source.name}"
            return null
        }
        val fit = min(Collage.MAT_WIDTH * PIECE_SHARE / w, Collage.MAT_HEIGHT * PIECE_SHARE / h)
        val apart = (collage.pieces.size % 6) * STAGGER
        val spot = at ?: Spot(Collage.MAT_WIDTH / 2f + apart, Collage.MAT_HEIGHT / 2f + apart)
        val piece = Piece(
            id = newId(),
            name = source.nameWithoutExtension,
            source = source.absoluteFile,
            outline = listOf(Spot(0f, 0f), Spot(w.toFloat(), 0f), Spot(w.toFloat(), h.toFloat()), Spot(0f, h.toFloat())),
            x = spot.x,
            y = spot.y,
            scale = fit,
        )
        change { it.copy(pieces = it.pieces + piece) }
        selected = piece.id
        notice = null
        return piece
    }

    /** Lays a picture that came as [png] bytes — pasted, or a sketch's page — on the mat, written beside the collage as [called]. */
    fun addPng(png: ByteArray, called: String): Piece? {
        val dir = file?.absoluteFile?.parentFile ?: lib.preferences.collagesHome()
        val written = lib.pictures.writePng(png, File(dir, safeFileName(called).ifEmpty { "picture" } + ".png"), beside = true)
        if (written == null) {
            notice = "Could not keep the picture"
            return null
        }
        return addPicture(written)
    }

    // ---- Pieces moved ----

    /** Chooses the piece [id] — or none. */
    fun select(id: String?) {
        selected = id?.takeIf { want -> collage.pieces.any { it.id == want } }
    }

    /** The topmost piece at [spot] on the mat, if any: the one a click there takes. */
    fun pieceAt(spot: Spot): Piece? = collage.pieces.lastOrNull { spot in it }

    /** A drag begins: what it does until [endDrag] is one change. */
    fun beginDrag() {
        dragFrom = collage
    }

    /** During a drag: the piece [id] laid with its middle at ([x], [y]). */
    fun moveTo(id: String, x: Float, y: Float) = live(id) { it.copy(x = x, y = y) }

    /** During a drag: the piece [id] turned to [degrees]. */
    fun turnTo(id: String, degrees: Float) = live(id) { it.copy(turn = normalised(degrees)) }

    /** During a drag: the piece [id] at [scale] mat units to a picture pixel — never smaller than a speck. */
    fun scaleTo(id: String, scale: Float) = live(id) { it.copy(scale = scale.coerceAtLeast(MIN_SCALE)) }

    /** The drag ends: it is kept, and can be undone as one change. */
    fun endDrag() {
        val from = dragFrom ?: return
        dragFrom = null
        if (from != collage) {
            record(from)
            keep()
        }
    }

    /** The chosen piece moved by ([dx], [dy]) mat units: the arrow keys. */
    fun nudge(dx: Float, dy: Float) = edit { it.copy(x = it.x + dx, y = it.y + dy) }

    /** The chosen piece turned by [degrees]. */
    fun turnBy(degrees: Float) = edit { it.copy(turn = normalised(it.turn + degrees)) }

    /** The chosen piece flipped over, left to right. */
    fun flip() = edit { it.copy(flipped = !it.flipped) }

    /** The chosen piece one up the stack, over the piece above it. */
    fun forward() = restack(+1)

    /** The chosen piece one down the stack, under the piece below it. */
    fun backward() = restack(-1)

    private fun restack(by: Int) {
        val id = selected ?: return
        val at = collage.pieces.indexOfFirst { it.id == id }
        if (at < 0) return
        reorder(id, at + by)
    }

    /** The piece [id] to place [index] in the stack, counted from the bottom. */
    fun reorder(id: String, index: Int) = change { c ->
        val piece = c.pieces.firstOrNull { it.id == id } ?: return@change c
        val rest = c.pieces - piece
        c.copy(pieces = rest.toMutableList().apply { add(index.coerceIn(0, rest.size), piece) })
    }

    /** The piece [id] — the chosen one, where none is said — taken off the mat. */
    fun remove(id: String? = selected) {
        val gone = id ?: return
        change { c -> c.copy(pieces = c.pieces.filter { it.id != gone }) }
        if (selected == gone) selected = null
    }

    /** The piece [id] called [next]. */
    fun renamePiece(id: String, next: String) {
        val clean = next.trim()
        if (clean.isEmpty()) return
        change { c -> c.copy(pieces = c.pieces.map { if (it.id == id) it.copy(name = clean) else it }) }
    }

    fun undo() {
        val previous = undos.lastOrNull() ?: return
        undos = undos.dropLast(1)
        redos = redos + collage
        collage = previous
        keep()
    }

    fun redo() {
        val next = redos.lastOrNull() ?: return
        redos = redos.dropLast(1)
        undos = undos + collage
        collage = next
        keep()
    }

    /** Esc: the chosen piece let go of, or else back to where the room was entered from. */
    fun stepBack() {
        if (selected != null) selected = null else leave()
    }

    /** Back to where the room was entered from. */
    fun leave() = host.leaveCollage()

    private fun edit(how: (Piece) -> Piece) {
        val id = selected ?: return
        change { c -> c.copy(pieces = c.pieces.map { if (it.id == id) how(it) else it }) }
    }

    private fun live(id: String, how: (Piece) -> Piece) {
        collage = collage.copy(pieces = collage.pieces.map { if (it.id == id) how(it) else it })
    }

    private fun change(how: (Collage) -> Collage) {
        val before = collage
        val after = how(before)
        if (after == before) return
        collage = after
        record(before)
        keep()
    }

    private fun record(before: Collage) {
        undos = (undos + before).takeLast(MAX_UNDO)
        redos = emptyList()
    }

    /** The collage kept in its document — made in the collages home with the first piece. */
    private fun keep() {
        val document = file ?: lib.collages.freeFile(lib.preferences.collagesHome(), name).also {
            file = it
            name = Collages.nameOf(it)
        }
        if (!lib.collages.write(document, collage)) notice = "Could not keep ${document.name}"
    }

    // ---- Taken from the other rooms (the hand-off seam) ----

    override val room: Room get() = Room.COLLAGE

    /** Any picture, a file or a page, is cut from here. */
    override fun offer(what: Handed): String = "To a collage"

    /** The picture laid on the collage last kept — or a new one — as a piece to cut from. */
    override fun take(what: Handed) {
        onEnter()
        when (what) {
            is Handed.Picture -> addPicture(what.file)
            is Handed.Drawn -> addPng(what.png(), what.name)
        }
    }

    companion object {
        const val UNTITLED = "Untitled collage"

        /** How much of the mat a picture laid on it fills, at most, across or down. */
        const val PIECE_SHARE = 0.4f

        /** How far apart pictures laid in the middle one after another lie, in mat units. */
        const val STAGGER = 40f

        /** The smallest a piece can be scaled to: a speck, but still there to take hold of. */
        const val MIN_SCALE = 0.01f

        const val MAX_UNDO = 200

        private fun normalised(degrees: Float): Float = ((degrees % 360f) + 360f) % 360f
    }
}
