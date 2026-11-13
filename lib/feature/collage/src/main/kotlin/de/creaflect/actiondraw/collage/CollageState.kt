package de.creaflect.actiondraw.collage

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.creaflect.actiondraw.Lib
import de.creaflect.actiondraw.Room
import de.creaflect.actiondraw.board.BoardRef
import de.creaflect.actiondraw.board.BoardSource
import de.creaflect.actiondraw.board.ConceptRef
import de.creaflect.actiondraw.board.ConceptSource
import de.creaflect.actiondraw.board.ImageItem
import de.creaflect.actiondraw.handoff.Handed
import de.creaflect.actiondraw.handoff.Handoff
import de.creaflect.actiondraw.handoff.Receiver
import de.creaflect.actiondraw.handoff.Route
import de.creaflect.actiondraw.image.Pictures
import de.creaflect.actiondraw.newId
import de.creaflect.actiondraw.platform.FileDialogs
import de.creaflect.actiondraw.platform.SystemClipboard
import de.creaflect.actiondraw.safeFileName
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

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

/** What a press on the mat does (F10.6): take hold of a piece, cut, tear, or lay a strip of tape. */
enum class Tool { MOVE, SCISSORS, TEAR, TAPE }

/** Where the source drawer draws pictures from (F10.6): a board, or a concept. */
sealed interface DrawerSource {
    val name: String

    data class Board(val ref: BoardRef) : DrawerSource {
        override val name: String get() = ref.name
    }

    data class Concept(val ref: ConceptRef) : DrawerSource {
        override val name: String get() = ref.name
    }
}

/** A way out of the room asked for (F10.6), waiting for the screen to draw the collage as a picture. */
sealed interface CollageExport {
    /** Saved as `<name>.png` beside the collage. */
    data object Save : CollageExport

    /** Saved, and pinned to the board in [dir]. */
    data class Pin(val dir: File) : CollageExport

    /** Saved, and taken into [to]'s room. */
    data class Send(val to: Room) : CollageExport
}

/**
 * The Collage room (CONCEPT.md, *Collage*; F10.6): pictures cut apart and laid on a cutting mat,
 * to test whether a scene holds. One collage is open at a time; it is kept as it changes — every
 * piece added, moved, turned, flipped, restacked, cut, torn, taped or taken away — in its document,
 * which is made in the collages home with the first piece. Every change can be undone. Plain class
 * backed by Compose state, so the screen recomposes on change.
 *
 * Pieces taped together move as one: whatever is done to a piece by hand — moved, turned, grown,
 * flipped — is done to everything the tape across it holds, about the piece taken hold of.
 *
 * [lib] keeps the collages and reads the pictures; [host] is the way out; [dialogs] and [clipboard]
 * the host's, for adding a picture and pasting one. Pictures handed from the other rooms — a board's
 * card, a concept's picture, a sketch's page, a view in the Lens — come in as pieces; [boards] and
 * [concepts] fill the source drawer, and a collage is pinned to the boards; [handoff] takes it, saved
 * as a picture, into the other rooms.
 */
class CollageState(
    private val lib: Lib,
    private val host: CollageHost = CollageHost.None,
    /** The host's file dialogs, for adding a picture. */
    val dialogs: FileDialogs = FileDialogs.None,
    private val clipboard: SystemClipboard = SystemClipboard.None,
    private val boards: BoardSource = BoardSource.None,
    private val concepts: ConceptSource = ConceptSource.None,
    private val handoff: Handoff = Handoff.None,
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

    /** What a press on the mat does. */
    var tool by mutableStateOf(Tool.MOVE)
        private set

    /** The path being cut along, in mat units: the corners clicked and the lines dragged so far. */
    var path by mutableStateOf(emptyList<Spot>())
        private set

    /** Whether the scissors and the tear go through every picture under them, not one alone. */
    var throughAll by mutableStateOf(false)
        private set

    /** What the source drawer draws from; null where there is no board or concept to draw from. */
    var source by mutableStateOf<DrawerSource?>(null)
        private set

    /** The pictures in the drawer's [source], as they were when it was chosen or the room entered. */
    var sourcePictures by mutableStateOf(emptyList<File>())
        private set

    /** A way out asked for, until the screen has drawn the collage for it. */
    var export by mutableStateOf<CollageExport?>(null)
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

    /** Coming into the room: the collage last kept, unless one is open already, and the drawer's pictures as they are now. */
    fun onEnter() {
        if (file == null && collage.pieces.isEmpty()) recent().firstOrNull()?.let(::open)
        drawFrom(source ?: boards().firstOrNull()?.let(DrawerSource::Board) ?: concepts().firstOrNull()?.let(DrawerSource::Concept))
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
        path = emptyList()
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

    /** During a drag: the piece [id] laid with its middle at ([x], [y]) — and what is taped to it with it. */
    fun moveTo(id: String, x: Float, y: Float) = live(id) { held, taken -> held.shifted(x - taken.x, y - taken.y) }

    /** During a drag: the piece [id] turned to [degrees] — and what is taped to it about it. */
    fun turnTo(id: String, degrees: Float) = live(id) { held, taken -> held.turnedAbout(taken, normalised(degrees) - taken.turn) }

    /** During a drag: the piece [id] at [scale] mat units to a picture pixel, never smaller than a speck — and what is taped to it grown with it. */
    fun scaleTo(id: String, scale: Float) = live(id) { held, taken -> held.grownAbout(taken, scale.coerceAtLeast(MIN_SCALE) / taken.scale) }

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
    fun nudge(dx: Float, dy: Float) = edit { held, _ -> held.shifted(dx, dy) }

    /** The chosen piece turned by [degrees]. */
    fun turnBy(degrees: Float) = edit { held, taken -> held.turnedAbout(taken, degrees) }

    /** The chosen piece turned over, left to right, as a sheet is on the table: a piece turned one way lies turned the other. */
    fun flip() = edit { held, taken -> held.flippedAbout(taken) }

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

    /** The piece [id] — the chosen one, where none is said — taken off the mat; tape across it holds it no more. */
    fun remove(id: String? = selected) {
        val gone = id ?: return
        change { c -> c.copy(pieces = c.pieces.filter { it.id != gone }.map { if (gone in it.holds) it.copy(holds = it.holds - gone) else it }) }
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

    /** Esc: the path being cut dropped, else the chosen piece let go of, else Move in hand again, else back to where the room was entered from. */
    fun stepBack() {
        when {
            path.isNotEmpty() -> path = emptyList()
            selected != null -> selected = null
            tool != Tool.MOVE -> tool = Tool.MOVE
            else -> leave()
        }
    }

    /** Back to where the room was entered from. */
    fun leave() = host.leaveCollage()

    /** The chosen piece, and what is taped to it, changed by [how] — given each of them and the chosen piece as it lies. */
    private fun edit(how: (Piece, Piece) -> Piece) {
        val id = selected ?: return
        change { it.moved(id, how) }
    }

    private fun live(id: String, how: (Piece, Piece) -> Piece) {
        collage = collage.moved(id, how)
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

    // ---- Cut, torn and taped ----

    /** [next] in hand; a path being cut is dropped. */
    fun choose(next: Tool) {
        tool = next
        path = emptyList()
    }

    /** The path being cut along, as it is drawn: [spots] on the mat. */
    fun trace(spots: List<Spot>) {
        path = spots
    }

    /** The path drawn so far cut along — or, with the tear in hand, torn along — and let go of; [closed]: all the way round. */
    fun cutAlongPath(closed: Boolean): Boolean {
        val along = path
        path = emptyList()
        return if (tool == Tool.TEAR) tear(along, closed) else cut(along, closed)
    }

    /** The scissors and the tear through every picture under them — [on] — or through one alone. */
    fun cutThroughAll(on: Boolean) {
        throughAll = on
    }

    /**
     * Cuts along [along], on the mat — [closed]: all the way round — through the chosen piece, where
     * the cut goes through it, else through the topmost picture it does; or, [throughAll], through
     * every picture it goes through. Tape is not cut. Every part a piece falls into is a piece of
     * its own, lying where it lay: the largest keeps its name and its place in the stack, the others
     * lie just above it called after it, and the one cut off — or cut out — of the first piece cut
     * is chosen. [torn]: a tear, and the parts called so. One change, however many pieces it
     * parted. False, and said, where the cut went through nothing.
     */
    fun cut(along: List<Spot>, closed: Boolean, torn: Boolean = false): Boolean {
        val pictures = collage.pieces.asReversed().filter { !it.isTape }
        val order = if (throughAll) pictures else (listOfNotNull(selectedPiece?.takeIf { !it.isTape }) + pictures).distinct()
        val partings = ArrayList<Parting>()
        for (piece in order) {
            val onIt = along.map(piece::toPicture)
            val parts = divide(piece.outline, onIt, closed)
            if (parts.size < 2) continue
            partings += Parting(piece, parts, onIt.takeIf { closed })
            if (!throughAll) break
        }
        if (partings.isEmpty()) {
            notice = if (torn) "Tear across a piece, from edge to edge" else "Cut across a piece from edge to edge, or all the way round a part of it"
            return false
        }
        apart(partings, if (torn) "torn" else "cut")
        return true
    }

    /** Tears along [along], on the mat, as paper tears: the path made ragged, then parted along like a cut — [closed]: all the way round. */
    fun tear(along: List<Spot>, closed: Boolean = false): Boolean {
        if (along.size < 2) return false
        val seed = along.fold(along.size.toLong()) { h, p -> h * 31 + (p.x * 8).toInt() * 17 + (p.y * 8).toInt() }
        val ragged = if (closed) torn(along + along.first(), seed).dropLast(1) else torn(along, seed)
        return cut(ragged, closed, torn = true)
    }

    /** A piece a cut went through, the [parts] it falls into, and — for a cut all the way round — the path [round] it, on its picture. */
    private class Parting(val piece: Piece, val parts: List<List<Spot>>, val round: List<Spot>?)

    /** Each piece parted, as one change: its parts in its place in the stack, the tape across it holding those under it. */
    private fun apart(partings: List<Parting>, how: String) {
        val names = collage.pieces.map { it.name }.toMutableSet()
        val parted = LinkedHashMap<String, List<Piece>>()
        var chosen: String? = null
        for (parting in partings) {
            val parts = parting.parts
            val piece = parting.piece
            // Cut out: what lies inside a path that went all the way round.
            val out = parts.map { parting.round != null && insideOf(parting.round, insidePoint(it)) }
            val keep = parts.indices.filter { !out[it] }.maxByOrNull { areaOf(parts[it]) } ?: parts.indices.maxBy { areaOf(parts[it]) }
            val base = piece.name.replace(TRAIL, "")
            val others = parts.indices.filter { it != keep }.sortedByDescending { areaOf(parts[it]) }
            val made = others.map { i ->
                val name = generateSequence(1) { it + 1 }.map { n -> if (n == 1) "$base, $how" else "$base, $how $n" }.first { it !in names }
                names += name
                piece.recut(parts[i]).copy(id = newId(), name = name)
            }
            val kept = piece.recut(parts[keep])
            parted[piece.id] = listOf(kept) + made
            if (chosen == null) chosen = made.getOrNull(others.indices.firstOrNull { out[others[it]] } ?: 0)?.id ?: kept.id
        }
        change { c ->
            val stacked = c.pieces.flatMap { parted[it.id] ?: listOf(it) }
            // Tape across a piece cut holds whichever of its parts lie under it.
            c.copy(
                pieces = stacked.map { t ->
                    if (!t.isTape || t.holds.none { it in parted }) return@map t
                    val strip = t.onMat()
                    val under = t.holds.flatMap { parted[it].orEmpty() }.filter { overlaps(strip, it.onMat()) }.map { it.id }
                    t.copy(holds = (t.holds.filter { it !in parted } + under).distinct())
                },
            )
        }
        selected = chosen
        notice = null
    }

    /**
     * Lays a strip of tape from [from] to [to] on the mat — a short one there, where they are as good
     * as one place — over everything. It holds the pictures it lies across: they move as one with it
     * from now on. It is chosen.
     */
    fun layTape(from: Spot, to: Spot): Piece {
        val strip = strip(from, to)
        val onMat = strip.onMat()
        val laid = strip.copy(holds = collage.pieces.filter { !it.isTape && overlaps(onMat, it.onMat()) }.map { it.id })
        change { it.copy(pieces = it.pieces + laid) }
        selected = laid.id
        notice = null
        return laid
    }

    /** The strip of tape a drag from [from] to [to] lays, before it is laid: torn at its ends the same way for as long as it begins at [from]. */
    fun strip(from: Spot, to: Spot): Piece {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val long = hypot(dx, dy)
        val laidOut = long >= MIN_TAPE
        return Piece(
            id = newId(),
            name = "Tape",
            source = null,
            outline = tapeStrip(if (laidOut) long else TAPE_LENGTH, TAPE_WIDTH, (from.x * 31f + from.y).toLong()),
            x = if (laidOut) (from.x + to.x) / 2f else from.x,
            y = if (laidOut) (from.y + to.y) / 2f else from.y,
            turn = if (laidOut) normalised(Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()) else 0f,
        )
    }

    /** The pieces that move as one with the piece [id]: itself, the tape that holds it, what that tape holds, and so on. */
    fun heldWith(id: String): Set<String> = collage.heldWith(id)

    // ---- The source drawer ----

    /** The boards the drawer can draw from, and the collage can be pinned to. */
    fun boards(): List<BoardRef> = boards.available()

    /** The concepts the drawer can draw from. */
    fun concepts(): List<ConceptRef> = concepts.available()

    /** The drawer drawing from [next] — its pictures read now — or from nothing. */
    fun drawFrom(next: DrawerSource?) {
        source = next
        sourcePictures = when (next) {
            null -> emptyList()
            is DrawerSource.Board -> boards.pictures(next.ref.dir)
            is DrawerSource.Concept -> concepts.snapshot(next.ref.id)?.let { s -> s.items.filterIsInstance<ImageItem>().map { File(s.root, it.path) } }.orEmpty()
        }.filter(lib.pictures::isPicture)
    }

    // ---- Out of the room ----

    /** The picture the collage is saved as: `<name>.png` beside its document; null before it has one. */
    fun pictureFile(): File? = file?.let { File(it.absoluteFile.parentFile, Collages.nameOf(it) + ".png") }

    /** Where the collage can be taken from here, saved as a picture: into Sketch to draw over it, into the Lens… None while the mat is bare. */
    fun routes(): List<Route> {
        val picture = pictureFile()?.takeIf { collage.pieces.isNotEmpty() } ?: return emptyList()
        return handoff.routes(Handed.Picture(picture), Room.COLLAGE)
    }

    /** Saves the collage as a picture beside it, once the screen has drawn it. */
    fun saveAsPicture() = ask(CollageExport.Save)

    /** Saves the collage as a picture and pins it to the board in [dir], once the screen has drawn it. */
    fun pinTo(dir: File) = ask(CollageExport.Pin(dir))

    /** Saves the collage as a picture and takes it into [to]'s room, once the screen has drawn it. */
    fun handOff(to: Room) = ask(CollageExport.Send(to))

    private fun ask(what: CollageExport) {
        if (collage.pieces.isEmpty() || file == null) {
            notice = "There is nothing on the mat yet"
            return
        }
        export = what
    }

    /**
     * The collage the screen drew for [export] — [png], or null when it could not be drawn — saved
     * as `<name>.png` beside it, replacing the one saved before; then pinned or taken on.
     */
    fun finish(png: ByteArray?) {
        val pending = export ?: return
        export = null
        val target = pictureFile()
        if (png == null || target == null) {
            notice = "The collage could not be drawn"
            return
        }
        val written = lib.pictures.writePng(png, target)
        if (written == null) {
            notice = "Could not save ${target.name}"
            return
        }
        notice = when (pending) {
            CollageExport.Save -> "Saved as ${written.name}, beside the collage"
            is CollageExport.Pin -> boards.pin(pending.dir, listOf(written))
            is CollageExport.Send -> null
        }
        if (pending is CollageExport.Send) handoff.send(Handed.Picture(written), pending.to)
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

        /** A strip of tape: how wide, how long laid with a click, and how far a drag goes before it lays one as long. In mat units. */
        const val TAPE_WIDTH = 34f
        const val TAPE_LENGTH = 120f
        const val MIN_TAPE = 24f

        /** What a cut part's name ends in, so a part cut again is called after the piece, not after the part. */
        private val TRAIL = Regex(""", (cut|torn)( \d+)?$""")

        private fun normalised(degrees: Float): Float = ((degrees % 360f) + 360f) % 360f

        private fun Collage.heldWith(id: String): Set<String> {
            val group = mutableSetOf(id)
            var grew = true
            while (grew) {
                grew = false
                for (t in pieces) {
                    if (!t.isTape || (t.id !in group && t.holds.none { it in group })) continue
                    if (group.add(t.id)) grew = true
                    for (h in t.holds) if (group.add(h)) grew = true
                }
            }
            return group
        }

        /** The piece [id] and what is taped to it changed by [how], given each of them and the piece [id] as it lies. */
        private fun Collage.moved(id: String, how: (Piece, Piece) -> Piece): Collage {
            val taken = pieces.firstOrNull { it.id == id } ?: return this
            val group = heldWith(id)
            return copy(pieces = pieces.map { if (it.id in group) how(it, taken) else it })
        }

        private fun Piece.shifted(dx: Float, dy: Float) = copy(x = x + dx, y = y + dy)

        /** Turned clockwise by [degrees] about where [about] lies. */
        private fun Piece.turnedAbout(about: Piece, degrees: Float): Piece {
            val a = Math.toRadians(degrees.toDouble())
            val cs = cos(a).toFloat()
            val sn = sin(a).toFloat()
            val ux = x - about.x
            val uy = y - about.y
            return copy(x = about.x + ux * cs - uy * sn, y = about.y + ux * sn + uy * cs, turn = normalised(turn + degrees))
        }

        /** Grown by [k] about where [about] lies. */
        private fun Piece.grownAbout(about: Piece, k: Float) = copy(x = about.x + (x - about.x) * k, y = about.y + (y - about.y) * k, scale = (scale * k).coerceAtLeast(MIN_SCALE))

        /** Turned over left to right about the line down through where [about] lies. */
        private fun Piece.flippedAbout(about: Piece) = copy(x = 2 * about.x - x, turn = normalised(-turn), flipped = !flipped)
    }
}
