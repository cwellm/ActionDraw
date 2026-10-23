package de.creaflect.actiondraw.concept

import de.creaflect.actiondraw.LoadResult
import de.creaflect.actiondraw.SETTLE
import de.creaflect.actiondraw.Settings
import de.creaflect.actiondraw.deleteFolder
import de.creaflect.actiondraw.io.AppDirs
import de.creaflect.actiondraw.io.Folders
import de.creaflect.actiondraw.isInside
import de.creaflect.actiondraw.keyOf
import de.creaflect.actiondraw.lifecycle.Writes
import de.creaflect.actiondraw.newId
import de.creaflect.actiondraw.safeFileName
import de.creaflect.actiondraw.settle
import java.io.File
import java.io.IOException

/**
 * The [Concepts] over the files: each concept's sidecar ([ConceptStore]), the registry of which
 * folder each concept id lives in ([ConceptRegistry]), and the settings for the concepts home.
 */
internal class FileConcepts(
    private val settings: Settings,
    private val registry: ConceptRegistry,
    private val writes: Writes,
    private val dirs: AppDirs,
) : Concepts {
    // ---- One concept ----

    override fun isConcept(dir: File): Boolean {
        writes.settle(dir)
        return ConceptStore.exists(dir)
    }

    override fun peek(dir: File): Concept? {
        writes.settle(dir)
        return ConceptStore.peek(dir)
    }

    override fun load(dir: File): LoadResult<Concept> {
        writes.settle(dir)
        return ConceptStore.load(dir)
    }

    override fun save(dir: File, concept: Concept) = writes.write(keyOf(dir)) {
        if (!ConceptStore.save(dir, concept)) throw IOException("Couldn't write the concept in $dir")
    }

    override fun saveNow(dir: File, concept: Concept): Boolean {
        writes.settle(dir)
        return ConceptStore.save(dir, concept)
    }

    // ---- Which concepts there are ----

    override fun entryFor(dir: File): ConceptEntry? = registry.entryFor(dir)

    override fun byId(id: String): ConceptEntry? = registry.byId(id)

    override fun register(dir: File, concept: Concept) {
        registry.register(concept.id, concept.name, concept.kind, dir)
    }

    override fun available(): List<ConceptEntry> {
        registry.prune()
        val known = registry.entries().filter { isConcept(it.dir) }.toMutableList()
        settings.conceptsHome().listFiles().orEmpty()
            .filter { it.isDirectory && isConcept(it) && known.none { k -> k.isAt(it) } }
            .forEach { dir ->
                val found = peek(dir) ?: return@forEach
                val id = found.id.ifBlank { newId() }
                if (found.id.isBlank()) saveNow(dir, found.copy(id = id))
                known += registry.register(id, found.name.ifBlank { dir.name }, found.kind, dir)
            }
        return known.sortedWith(compareBy({ it.kind.lowercase() }, { it.name.lowercase() }))
    }

    // ---- Making and deleting ----

    override fun create(concept: Concept): ConceptCreation {
        val home = settings.conceptsHome().apply { mkdirs() }
        val dir = Folders.freeFolder(File(home, safeFileName(concept.name)))
        if (!dir.mkdirs()) return ConceptCreation.CouldNotCreate(dir)
        if (!saveNow(dir, concept)) return ConceptCreation.CouldNotWrite(dir)
        registry.register(concept.id, concept.name, concept.kind, dir)
        return ConceptCreation.Created(dir)
    }

    override fun delete(dir: File, alsoFolder: Boolean): Boolean {
        // Nothing still on its way to the disk may land in the folder after it is gone.
        writes.flush(SETTLE)
        val done = if (alsoFolder) {
            deleteFolder(dir, dirs)
        } else {
            ConceptStore.forget(dir)
            true
        }
        registry.forget(dir)
        return done
    }

    // ---- Documents ----

    override fun readDocument(dir: File, path: String): String? =
        File(dir, path).takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }

    override fun writeDocument(dir: File, path: String?, title: String, text: String): String? {
        val target = if (path != null) {
            File(dir, path).takeIf { it.isWithin(dir) } ?: return null
        } else {
            val docs = File(dir, ConceptStore.DOCS_DIR).apply { mkdirs() }
            Folders.freeFile(File(docs, safeFileName(title).ifBlank { "document" } + ".md"))
        }
        runCatching { target.writeText(text) }.onFailure { return null }
        return target.relativeTo(dir).path.replace(File.separatorChar, '/')
    }

    override fun deleteDocument(dir: File, path: String) {
        File(dir, path).takeIf { it.isWithin(dir) }?.delete()
    }

    /** Whether this lies inside [dir]: a hand-edited concept file must not aim a write or a delete elsewhere. */
    private fun File.isWithin(dir: File): Boolean = runCatching { canonicalFile.isInside(dir.canonicalFile) }.getOrDefault(false)
}
