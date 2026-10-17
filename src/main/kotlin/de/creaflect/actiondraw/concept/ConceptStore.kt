package de.creaflect.actiondraw.concept

import de.creaflect.actiondraw.board.ImageItem
import de.creaflect.actiondraw.io.SafeFile
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Reads and writes the concept sidecar the way `BoardStore` does the board's: an atomic save with
 * a backup beside it, a parser that tolerates a byte-order mark, and a validation pass that drops
 * what has vanished from disk rather than showing a card with nothing behind it.
 */
object ConceptStore {
    const val FILE_NAME = ".actiondraw_concept.json"

    /** Where a concept keeps its documents; relative paths in the sidecar start with this. */
    const val DOCS_DIR = "_docs"

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    sealed class LoadResult {
        data object None : LoadResult()
        data class Loaded(val concept: ConceptFile, val fromBackup: Boolean) : LoadResult()
        data object Failed : LoadResult()
    }

    private fun sidecar(root: File) = SafeFile(File(root, FILE_NAME))

    fun exists(root: File): Boolean = sidecar(root).exists()

    /** The sidecar as written, without validation — for listings that must not touch the folder. */
    fun peek(root: File): ConceptFile? = sidecar(root).load(::decode)?.value

    fun load(root: File): LoadResult {
        val sidecar = sidecar(root)
        if (!sidecar.exists()) return LoadResult.None
        val loaded = sidecar.load(::decode) ?: return LoadResult.Failed
        return LoadResult.Loaded(validate(loaded.value, root), loaded.fromBackup)
    }

    /** Removes the sidecar and its backup: the folder stops being a concept, its files stay. */
    fun forget(root: File): Boolean = sidecar(root).delete()

    private fun decode(text: String): ConceptFile = json.decodeFromString(ConceptFile.serializer(), text)

    /** Drops pictures and documents whose files are gone; notes and links are never dropped. */
    fun validate(concept: ConceptFile, root: File): ConceptFile = concept.copy(
        items = concept.items.filter { it !is ImageItem || File(root, it.path).isFile },
        documents = concept.documents.filter { File(root, it).isFile },
    )

    fun save(root: File, concept: ConceptFile): Boolean =
        runCatching { sidecar(root).write(json.encodeToString(ConceptFile.serializer(), concept)) }.getOrDefault(false)
}
