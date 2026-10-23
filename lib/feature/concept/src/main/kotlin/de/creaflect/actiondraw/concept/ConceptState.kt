package de.creaflect.actiondraw.concept

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.creaflect.actiondraw.Lib
import de.creaflect.actiondraw.LoadResult
import de.creaflect.actiondraw.board.Board
import de.creaflect.actiondraw.board.BoardItem
import de.creaflect.actiondraw.board.BoardLayouts
import de.creaflect.actiondraw.board.BoardLink
import de.creaflect.actiondraw.board.Camera
import de.creaflect.actiondraw.board.CardLayout
import de.creaflect.actiondraw.board.ConceptRef
import de.creaflect.actiondraw.board.ConceptSnapshot
import de.creaflect.actiondraw.board.ConceptSource
import de.creaflect.actiondraw.board.ImageItem
import de.creaflect.actiondraw.board.LinkItem
import de.creaflect.actiondraw.board.NoteItem
import de.creaflect.actiondraw.board.NoteKind
import de.creaflect.actiondraw.newId
import de.creaflect.actiondraw.platform.Browser
import de.creaflect.actiondraw.platform.FileDialogs
import de.creaflect.actiondraw.samePathAs
import java.io.File

/**
 * Everything the concept module is allowed to ask of the rest of the app — the same kind of seam
 * as `BoardHost`. The app shell implements it; nothing else knows about concepts.
 */
interface ConceptHost {
    fun showConcepts()
    fun showConcept()
    fun leaveConcepts()

    /** Every board, and whether it links the concept — the boards side answers this. */
    fun boardsFor(conceptId: String): List<BoardLink> = emptyList()

    /** Links or unlinks the concept on one board. */
    fun setLinked(conceptId: String, board: File, linked: Boolean) {}

    /** Takes the concept off every board that links it; returns their names. */
    fun unlinkEverywhere(conceptId: String): List<String> = emptyList()

    /** Continue the sketch saved in [file] (a `.sketch.json`) in Live Sketch. */
    fun openSketch(file: File) {}
}

/** Which dialog is open over the concept screens. */
sealed class ConceptEditor {
    data object NewConcept : ConceptEditor()
    data object Rename : ConceptEditor()
    data class EditNote(val itemId: String?) : ConceptEditor()
    data class EditLink(val itemId: String?) : ConceptEditor()

    /** `path == null` starts a new document. */
    data class EditDocument(val path: String?) : ConceptEditor()
    data class DeleteConcept(val dir: File, val name: String) : ConceptEditor()

    /** Which boards the open concept is linked onto — ticks to set and clear. */
    data object LinkToBoards : ConceptEditor()
}

/**
 * The concepts side: a thing that lives once — this character, this landscape — in a folder of
 * its own under the concepts home, with a sidecar, pictures, notes, links and documents. Mirrors
 * `BoardState` where the shape is the same and stays much smaller where it is not: a concept has
 * no layout, no groups, no session recipe. Linking onto boards is the board's business
 * (`BoardState`), by id.
 */
class ConceptState(
    private val lib: Lib,
    private val host: ConceptHost,
    /** How an address in a document leaves the app: the host's browser. */
    private val browser: Browser = Browser.None,
    /** The host's file dialogs, for the concept's screens to ask with. */
    val dialogs: FileDialogs = FileDialogs.None,
) : ConceptSource {
    private val concepts get() = lib.concepts

    var root by mutableStateOf<File?>(null)
        private set
    var concept by mutableStateOf<Concept?>(null)
        private set
    var editor by mutableStateOf<ConceptEditor?>(null)
        private set
    var openFailed by mutableStateOf(false)
        private set
    var notice by mutableStateOf<String?>(null)

    /** Bumped when the list of concepts should be re-read. */
    var listTick by mutableStateOf(0)
        private set
    var selection by mutableStateOf<Set<String>>(emptySet())
        private set

    // The free layout's viewport — the concept's own, never a board's.
    var camX by mutableStateOf(0f)
        private set
    var camY by mutableStateOf(0f)
        private set
    var zoom by mutableStateOf(1f)
        private set

    val isOpen: Boolean get() = root != null && concept != null

    // ---- Home and listing ----

    fun conceptsHome(): File = lib.preferences.conceptsHome()

    fun setConceptsHomeDir(dir: File) {
        lib.preferences.setConceptsHome(dir)
        listTick++
    }

    fun entryFor(dir: File): ConceptEntry? = concepts.entryFor(dir)
    fun entryById(id: String): ConceptEntry? = concepts.byId(id)

    /**
     * Every concept recorded, plus any concept folder under the home not recorded yet (recorded
     * as found), by kind then name.
     */
    fun availableConcepts(): List<ConceptEntry> = concepts.available()

    /** The concept in [dir] as its file holds it, for the list: nothing is checked or changed. */
    fun peek(dir: File): Concept? = concepts.peek(dir)

    /** The picture formats this build reads: what a file chooser offers. */
    val pictureExtensions: Set<String> get() = lib.pictures.extensions

    // ---- Lifecycle ----

    fun createConcept(name: String, kind: String = ""): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "Give the concept a name."
        val created = Concept(id = newId(), name = trimmed, kind = kind.trim())
        val dir = when (val made = concepts.create(created)) {
            is ConceptCreation.CouldNotCreate -> return "Couldn't create:\n${made.dir}"
            is ConceptCreation.CouldNotWrite -> return "Couldn't write the concept file in:\n${made.dir}"
            is ConceptCreation.Created -> made.dir
        }
        root = dir
        concept = created
        openFailed = false
        afterOpen()
        return null
    }

    fun openConcept(dir: File) {
        when (val result = concepts.load(dir)) {
            LoadResult.None -> return
            is LoadResult.Loaded -> {
                root = dir
                concept = result.value
                openFailed = false
                concepts.register(dir, result.value)
            }
            LoadResult.Failed -> {
                openFailed = true
                return
            }
        }
        afterOpen()
    }

    fun openById(id: String): Boolean {
        val entry = concepts.byId(id) ?: return false
        if (!concepts.isConcept(entry.dir)) return false
        openConcept(entry.dir)
        return true
    }

    private fun afterOpen() {
        selection = emptySet()
        editor = null
        val camera = concept?.camera ?: Camera()
        camX = camera.x
        camY = camera.y
        zoom = camera.zoom
        placeMissingIfFree()
        listTick++
        host.showConcept()
    }

    fun closeConcept() {
        commitCamera()
        root = null
        concept = null
        selection = emptySet()
        editor = null
        host.showConcepts()
    }

    fun openList() {
        openFailed = false
        host.showConcepts()
    }

    fun leaveList() {
        openFailed = false
        host.leaveConcepts()
    }

    fun rename(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        update { it.copy(name = trimmed) }
        root?.let { dir -> concept?.let { concepts.register(dir, it) } }
        listTick++
    }

    fun setKind(kind: String) {
        update { it.copy(kind = kind.trim()) }
        root?.let { dir -> concept?.let { concepts.register(dir, it) } }
        listTick++
    }

    fun setNotes(notes: String) = update { it.copy(notes = notes) }

    /**
     * Deletes a concept. It lives once and may be linked onto several boards, so the dialog that
     * leads here names them first; here, the folder goes (or only the sidecar, when [alsoFolder]
     * is false) and the record with it.
     */
    fun deleteConcept(dir: File, alsoFolder: Boolean): String {
        if (!concepts.isConcept(dir)) return "${dir.name} is not a concept."
        // Off every board first: a board must not be left pointing at a concept that is gone.
        val unlinked = concepts.peek(dir)?.id?.let { host.unlinkEverywhere(it) }.orEmpty()
        if (root?.samePathAs(dir) == true) {
            root = null
            concept = null
            selection = emptySet()
        }
        val deleted = concepts.delete(dir, alsoFolder)
        val message = when {
            !alsoFolder -> "Concept removed. The files are still in ${dir.name}."
            deleted -> "Deleted ${dir.name} and everything in it."
            else -> "Couldn't delete ${dir.name}."
        }
        listTick++
        val full = if (unlinked.isEmpty()) message else "$message Unlinked from ${unlinked.joinToString()}."
        notice = full
        return full
    }

    // ---- Boards ----

    fun boardsFor(conceptId: String): List<BoardLink> = host.boardsFor(conceptId)

    fun setLinked(conceptId: String, board: File, linked: Boolean) {
        host.setLinked(conceptId, board, linked)
        listTick++
    }

    // ---- What boards see (ConceptSource) ----

    override fun available(): List<ConceptRef> = availableConcepts().map { ConceptRef(it.id, it.name, it.kind) }

    override fun snapshot(id: String): ConceptSnapshot? {
        val open = concept
        val dir = root
        if (open != null && dir != null && open.id == id) return ConceptSnapshot(id, open.name, dir, open.items)
        val entry = concepts.byId(id) ?: return null
        val file = (concepts.load(entry.dir) as? LoadResult.Loaded<Concept>)?.value ?: return null
        return ConceptSnapshot(id, file.name, entry.dir, file.items)
    }

    override fun addToConcept(id: String, pictures: List<File>, others: List<BoardItem>): List<BoardItem>? {
        val openHere = concept?.id == id && root != null
        val dir = (if (openHere) root else concepts.byId(id)?.dir) ?: return null
        val current = (if (openHere) concept else (concepts.load(dir) as? LoadResult.Loaded<Concept>)?.value) ?: return null
        val existing = current.items.filterIsInstance<ImageItem>().map { it.path }.toSet()
        val outcome = lib.pictures.importInto(dir, pictures, groupId = null, existingPaths = existing)
        val added = outcome.items + others.map { it.withGroups(emptyList()) }
        val next = current.copy(items = current.items + added)
        if (!concepts.saveNow(dir, next)) return null
        if (openHere) {
            concept = next
            placeMissingIfFree()
        }
        return added
    }

    // ---- Layout ----

    val layout: String get() = concept?.layout ?: BoardLayouts.GRID

    fun setLayout(layout: String) {
        if (layout == this.layout) return
        update { it.copy(layout = layout) }
        placeMissingIfFree()
    }

    /** Cards without a place yet go in rows near the camera — the board's rule, reused as it is. */
    private fun placeMissingIfFree() {
        if (layout != BoardLayouts.FREE) return
        if (items.none { it.pos == null }) return
        update { c -> c.copy(items = CardLayout.placeMissing(Board(items = c.items), camX, camY).items) }
    }

    /** Width over height of a card on the canvas: the board's rule, so the two look alike. */
    fun aspectOf(item: BoardItem): Float = when (item) {
        is ImageItem -> (item.aspect ?: 1f).coerceIn(0.2f, 5f)
        is LinkItem -> CardLayout.STRIP_ASPECT
        is NoteItem -> if (item.kind == NoteKind.POSTIT) item.estimatedAspect() else CardLayout.STRIP_ASPECT
    }

    /** A picture's shape once decoded, kept so the layout is stable from then on. */
    fun rememberAspect(id: String, aspect: Float) {
        val item = item(id) as? ImageItem ?: return
        if (item.aspect == aspect) return
        update { c -> c.copy(items = c.items.map { if (it.id == id && it is ImageItem) it.copy(aspect = aspect) else it }) }
    }

    fun pan(dx: Float, dy: Float) {
        camX += dx
        camY += dy
    }

    fun setZoom(newZoom: Float, newCamX: Float, newCamY: Float) {
        zoom = newZoom.coerceIn(0.1f, 5f)
        camX = newCamX
        camY = newCamY
    }

    fun commitCamera() {
        if (!isOpen) return
        val camera = Camera(camX, camY, zoom)
        if (concept?.camera != camera) update { it.copy(camera = camera) }
    }

    /** Moves a card (or the whole selection, if it is part of it); [commitLayout] persists it. */
    fun dragBy(id: String, dx: Float, dy: Float) {
        val ids = if (id in selection) selection else setOf(id)
        concept = concept?.let { c ->
            c.copy(
                items = c.items.map { item ->
                    val pos = item.pos
                    if (item.id in ids && pos != null) item.withPos(pos.copy(x = pos.x + dx, y = pos.y + dy)) else item
                },
            )
        }
    }

    fun resizeBy(id: String, factor: Float) {
        concept = concept?.let { c ->
            c.copy(
                items = c.items.map { item ->
                    val pos = item.pos
                    if (item.id == id && pos != null) item.withPos(pos.copy(scale = (pos.scale * factor).coerceIn(0.3f, 4f))) else item
                },
            )
        }
    }

    fun commitLayout() {
        val dir = root ?: return
        concepts.save(dir, concept ?: return)
    }

    // ---- Cards ----

    val items: List<BoardItem> get() = concept?.items.orEmpty()

    fun item(id: String): BoardItem? = items.find { it.id == id }

    fun fileOf(item: ImageItem): File? = root?.let { File(it, item.path) }

    /** The sketch a picture came from, when Live Sketch saved it: `name.sketch.json` beside `name.png`. */
    fun sketchOf(item: ImageItem): File? = fileOf(item)?.let { lib.sketches.documentOf(it) }

    fun openSketch(item: ImageItem) {
        sketchOf(item)?.let(host::openSketch)
    }

    /** Opens an address — a `[text](url)` inside a document — in the host's browser. */
    fun openUrl(url: String) {
        browser.open(url)
    }

    fun addPictures(files: List<File>) {
        val dir = root ?: return
        val existing = items.filterIsInstance<ImageItem>().map { it.path }.toSet()
        val outcome = lib.pictures.importInto(dir, files, groupId = null, existingPaths = existing)
        if (outcome.items.isNotEmpty()) update { it.copy(items = it.items + outcome.items) }
        placeMissingIfFree()
        notice = when {
            outcome.items.isEmpty() && outcome.duplicates > 0 -> "Already here."
            outcome.duplicates > 0 -> "Added ${outcome.items.size}; ${outcome.duplicates} already here."
            outcome.items.isEmpty() && files.isNotEmpty() -> "Nothing new to add."
            else -> null
        }
    }

    fun saveNote(itemId: String?, text: String, kind: String = NoteKind.DOCUMENT) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (itemId == null) {
            update { it.copy(items = it.items + NoteItem(id = newId(), text = trimmed, kind = kind)) }
            placeMissingIfFree()
        } else {
            update { c -> c.copy(items = c.items.map { if (it is NoteItem && it.id == itemId) it.copy(text = trimmed, kind = kind) else it }) }
        }
    }

    fun saveLink(itemId: String?, url: String, title: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        val name = title.trim()
        if (itemId == null) {
            update { it.copy(items = it.items + LinkItem(id = newId(), url = trimmed, title = name)) }
            placeMissingIfFree()
        } else {
            update { c -> c.copy(items = c.items.map { if (it is LinkItem && it.id == itemId) it.copy(url = trimmed, title = name) else it }) }
        }
    }

    fun removeItems(ids: Set<String>) {
        if (ids.isEmpty()) return
        update { c -> c.copy(items = c.items.filterNot { it.id in ids }) }
        selection = selection - ids
    }

    fun toggleSelected(id: String) {
        selection = if (id in selection) selection - id else selection + id
    }

    fun clearSelection() {
        selection = emptySet()
    }

    // ---- Documents ----

    /** A document's text, or null if its file is gone. */
    fun readDocument(path: String): String? = root?.let { concepts.readDocument(it, path) }

    /**
     * Writes a document. A new one ([path] null) is named from its first heading or line and
     * placed in `_docs/`; an existing one is overwritten in place. Returns the document's path.
     */
    fun saveDocument(path: String?, text: String): String? {
        val dir = root ?: return null
        val rel = concepts.writeDocument(dir, path, NoteItem("", text).title, text) ?: return null
        if (path == null) update { it.copy(documents = it.documents + rel) }
        return rel
    }

    fun deleteDocument(path: String) {
        val dir = root ?: return
        concepts.deleteDocument(dir, path)
        update { it.copy(documents = it.documents - path) }
    }

    /** The document's title: its first heading, else its first line, else its file name. */
    fun documentTitle(path: String): String {
        val text = readDocument(path) ?: return File(path).nameWithoutExtension
        return NoteItem("", text).title.takeIf { it != "Note" } ?: File(path).nameWithoutExtension
    }

    // ---- Dialogs ----

    fun openEditor(next: ConceptEditor) {
        editor = next
    }

    fun closeEditor() {
        editor = null
    }

    // ---- Persistence ----

    private fun update(transform: (Concept) -> Concept) {
        val dir = root ?: return
        val next = transform(concept ?: return)
        concept = next
        concepts.save(dir, next)
    }
}
