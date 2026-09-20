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

/**
 * Everything the concept module is allowed to ask of the rest of the app — the same kind of seam
 * as `BoardHost`. The app shell implements it; nothing else knows about concepts.
 */
interface ConceptHost {
    fun showConcepts()
    fun showConcept()
    fun leaveConcepts()
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
}

/**
 * The concepts side: a thing that lives once — this character, this landscape — in a folder of
 * its own under the concepts home, with a sidecar, pictures, notes, links and documents. Mirrors
 * `BoardState` where the shape is the same and stays much smaller where it is not: a concept has
 * no layout, no groups, no session recipe. Linking onto boards is the board's business
 * (`BoardState`), by id.
 */
class ConceptState(private val settings: Settings, private val host: ConceptHost) {
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
        listTick++
        host.showConcept()
    }

    fun closeConcept() {
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
        notice = message
        return message
    }

    // ---- Cards ----

    val items: List<BoardItem> get() = concept?.items.orEmpty()

    fun item(id: String): BoardItem? = items.find { it.id == id }

    fun fileOf(item: ImageItem): File? = root?.let { File(it, item.path) }

    fun addPictures(files: List<File>) {
        val dir = root ?: return
        val existing = items.filterIsInstance<ImageItem>().map { it.path }.toSet()
        val outcome = Importer.importFiles(dir, files, groupId = null, existingPaths = existing)
        if (outcome.items.isNotEmpty()) update { it.copy(items = it.items + outcome.items) }
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
