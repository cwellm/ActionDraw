package de.creaflect.actiondraw.concept

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.creaflect.actiondraw.Settings
import de.creaflect.actiondraw.board.BoardItem
import de.creaflect.actiondraw.board.ImageItem
import de.creaflect.actiondraw.board.Importer
import de.creaflect.actiondraw.board.LinkItem
import de.creaflect.actiondraw.board.NoteItem
import de.creaflect.actiondraw.board.NoteKind
import de.creaflect.actiondraw.samePathAs
import java.io.File
import de.creaflect.actiondraw.board.BoardLink
import de.creaflect.actiondraw.board.ConceptRef
import de.creaflect.actiondraw.board.ConceptSnapshot
import de.creaflect.actiondraw.board.ConceptSource
import de.creaflect.actiondraw.board.BoardFile
import de.creaflect.actiondraw.board.BoardLayouts
import de.creaflect.actiondraw.board.BoardState
import de.creaflect.actiondraw.board.Camera
import de.creaflect.sketch.SketchDocument

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
class ConceptState(private val settings: Settings, private val host: ConceptHost) : ConceptSource {
    private val registry = ConceptRegistry(settings.configDir)

    var root by mutableStateOf<File?>(null)
        private set
    var concept by mutableStateOf<ConceptFile?>(null)
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

    fun conceptsHome(): File = settings.conceptsHome()

    fun setConceptsHomeDir(dir: File) {
        settings.setConceptsHome(dir)
        listTick++
    }

    fun entryFor(dir: File): ConceptEntry? = registry.entryFor(dir)
    fun entryById(id: String): ConceptEntry? = registry.byId(id)

    /**
     * Every concept the registry knows, plus any concept folder under the home not recorded yet
     * (adopted as found), by kind then name.
     */
    fun availableConcepts(): List<ConceptEntry> {
        registry.prune()
        val known = registry.entries().filter { ConceptStore.exists(it.dir) }.toMutableList()
        conceptsHome().listFiles().orEmpty()
            .filter { it.isDirectory && ConceptStore.exists(it) && known.none { k -> k.isAt(it) } }
            .forEach { dir ->
                val file = ConceptStore.peek(dir) ?: return@forEach
                val id = file.id.ifBlank { Importer.newId() }
                if (file.id.isBlank()) ConceptStore.save(dir, file.copy(id = id))
                known += registry.register(id, file.name.ifBlank { dir.name }, file.kind, dir)
            }
        return known.sortedWith(compareBy({ it.kind.lowercase() }, { it.name.lowercase() }))
    }

    // ---- Lifecycle ----

    fun createConcept(name: String, kind: String = ""): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "Give the concept a name."
        val home = conceptsHome().apply { mkdirs() }
        val dir = freeFolder(File(home, sanitizeName(trimmed)))
        if (!dir.mkdirs()) return "Couldn't create:\n$dir"
        val created = ConceptFile(id = Importer.newId(), name = trimmed, kind = kind.trim())
        if (!ConceptStore.save(dir, created)) return "Couldn't write the concept file in:\n$dir"
        registry.register(created.id, created.name, created.kind, dir)
        root = dir
        concept = created
        openFailed = false
        afterOpen()
        return null
    }

    fun openConcept(dir: File) {
        when (val result = ConceptStore.load(dir)) {
            ConceptStore.LoadResult.None -> return
            is ConceptStore.LoadResult.Loaded -> {
                root = dir
                concept = result.concept
                openFailed = false
                registry.register(result.concept.id, result.concept.name, result.concept.kind, dir)
            }
            ConceptStore.LoadResult.Failed -> {
                openFailed = true
                return
            }
        }
        afterOpen()
    }

    fun openById(id: String): Boolean {
        val entry = registry.byId(id) ?: return false
        if (!ConceptStore.exists(entry.dir)) return false
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
        root?.let { dir -> concept?.let { registry.register(it.id, it.name, it.kind, dir) } }
        listTick++
    }

    fun setKind(kind: String) {
        update { it.copy(kind = kind.trim()) }
        root?.let { dir -> concept?.let { registry.register(it.id, it.name, it.kind, dir) } }
        listTick++
    }

    fun setNotes(notes: String) = update { it.copy(notes = notes) }

    /**
     * Deletes a concept. It lives once and may be linked onto several boards, so the dialog that
     * leads here names them first; here, the folder goes (or only the sidecar, when [alsoFolder]
     * is false) and the record with it.
     */
    fun deleteConcept(dir: File, alsoFolder: Boolean): String {
        if (!ConceptStore.exists(dir)) return "${dir.name} is not a concept."
        // Off every board first: a board must not be left pointing at a concept that is gone.
        val unlinked = ConceptStore.peek(dir)?.id?.let { host.unlinkEverywhere(it) }.orEmpty()
        if (root?.samePathAs(dir) == true) {
            root = null
            concept = null
            selection = emptySet()
        }
        val message = if (alsoFolder) {
            if (dir.deleteRecursively()) "Deleted ${dir.name} and everything in it." else "Couldn't delete ${dir.name}."
        } else {
            File(dir, ConceptStore.FILE_NAME).delete()
            File(dir, ConceptStore.FILE_NAME + ".bak").delete()
            "Concept removed. The files are still in ${dir.name}."
        }
        registry.forget(dir)
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
        val entry = registry.byId(id) ?: return null
        val file = (ConceptStore.load(entry.dir) as? ConceptStore.LoadResult.Loaded)?.concept ?: return null
        return ConceptSnapshot(id, file.name, entry.dir, file.items)
    }

    override fun addToConcept(id: String, pictures: List<File>, others: List<BoardItem>): List<BoardItem>? {
        val openHere = concept?.id == id && root != null
        val dir = (if (openHere) root else registry.byId(id)?.dir) ?: return null
        val current = (if (openHere) concept else (ConceptStore.load(dir) as? ConceptStore.LoadResult.Loaded)?.concept) ?: return null
        val existing = current.items.filterIsInstance<ImageItem>().map { it.path }.toSet()
        val outcome = Importer.importFiles(dir, pictures, groupId = null, existingPaths = existing)
        val added = outcome.items + others.map { it.withGroups(emptyList()) }
        val next = current.copy(items = current.items + added)
        if (!ConceptStore.save(dir, next)) return null
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
        update { c -> c.copy(items = BoardState.placeMissing(BoardFile(items = c.items), camX, camY).items) }
    }

    /** Width over height of a card on the canvas: the board's rule, so the two look alike. */
    fun aspectOf(item: BoardItem): Float = when (item) {
        is ImageItem -> (item.aspect ?: 1f).coerceIn(0.2f, 5f)
        is LinkItem -> BoardState.STRIP_ASPECT
        is NoteItem -> if (item.kind == NoteKind.POSTIT) item.estimatedAspect() else BoardState.STRIP_ASPECT
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
            c.copy(items = c.items.map { item ->
                val pos = item.pos
                if (item.id in ids && pos != null) item.withPos(pos.copy(x = pos.x + dx, y = pos.y + dy)) else item
            })
        }
    }

    fun resizeBy(id: String, factor: Float) {
        concept = concept?.let { c ->
            c.copy(items = c.items.map { item ->
                val pos = item.pos
                if (item.id == id && pos != null) item.withPos(pos.copy(scale = (pos.scale * factor).coerceIn(0.3f, 4f))) else item
            })
        }
    }

    fun commitLayout() {
        val dir = root ?: return
        ConceptStore.save(dir, concept ?: return)
    }

    // ---- Cards ----

    val items: List<BoardItem> get() = concept?.items.orEmpty()

    fun item(id: String): BoardItem? = items.find { it.id == id }

    fun fileOf(item: ImageItem): File? = root?.let { File(it, item.path) }

    /** The sketch a picture came from, when Live Sketch saved it: `name.sketch.json` beside `name.png`. */
    fun sketchOf(item: ImageItem): File? =
        fileOf(item)?.let { File(it.parentFile, it.nameWithoutExtension + SketchDocument.FILE_SUFFIX) }?.takeIf { it.isFile }

    fun openSketch(item: ImageItem) {
        sketchOf(item)?.let(host::openSketch)
    }

    fun addPictures(files: List<File>) {
        val dir = root ?: return
        val existing = items.filterIsInstance<ImageItem>().map { it.path }.toSet()
        val outcome = Importer.importFiles(dir, files, groupId = null, existingPaths = existing)
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
            update { it.copy(items = it.items + NoteItem(id = Importer.newId(), text = trimmed, kind = kind)) }
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
            update { it.copy(items = it.items + LinkItem(id = Importer.newId(), url = trimmed, title = name)) }
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
    fun readDocument(path: String): String? =
        root?.let { File(it, path) }?.takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }

    /**
     * Writes a document. A new one ([path] null) is named from its first heading or line and
     * placed in `_docs/`; an existing one is overwritten in place. Returns the document's path.
     */
    fun saveDocument(path: String?, text: String): String? {
        val dir = root ?: return null
        val target = if (path != null) {
            File(dir, path)
        } else {
            val docs = File(dir, ConceptStore.DOCS_DIR).apply { mkdirs() }
            val title = NoteItem("", text).title
            Importer.collisionFree(File(docs, sanitizeName(title).ifBlank { "document" } + ".md"))
        }
        runCatching { target.writeText(text) }.onFailure { return null }
        val rel = target.relativeTo(dir).path.replace(File.separatorChar, '/')
        if (path == null) update { it.copy(documents = it.documents + rel) }
        return rel
    }

    fun deleteDocument(path: String) {
        val dir = root ?: return
        File(dir, path).delete()
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

    private fun update(transform: (ConceptFile) -> ConceptFile) {
        val dir = root ?: return
        val next = transform(concept ?: return)
        concept = next
        ConceptStore.save(dir, next)
    }

    private fun freeFolder(wanted: File): File {
        if (!wanted.exists()) return wanted
        var n = 2
        while (true) {
            val candidate = File(wanted.parentFile, wanted.name + " (" + n + ")")
            if (!candidate.exists()) return candidate
            n++
        }
    }

    companion object {
        fun sanitizeName(name: String): String =
            name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().trimEnd('.')
    }
}
