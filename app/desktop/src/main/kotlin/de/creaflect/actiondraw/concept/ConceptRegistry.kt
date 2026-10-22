package de.creaflect.actiondraw.concept

import de.creaflect.actiondraw.isInside
import de.creaflect.actiondraw.samePathAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Which folder each concept lives in, keyed by the concept's id — because boards refer to a
 * concept by id and must still find it after its folder has moved. The same shape as
 * `BoardRegistry`, kept beside the settings file, all IO best-effort.
 */
class ConceptRegistry(private val dir: File) {
    private val file: File get() = File(dir, FILE_NAME)

    fun entries(): List<ConceptEntry> = runCatching {
        file.takeIf { it.isFile }
            ?.readText()
            ?.removePrefix("﻿")
            ?.let { json.decodeFromString(ListSerializer(ConceptEntry.serializer()), it) }
            .orEmpty()
    }.getOrDefault(emptyList())

    fun byId(id: String): ConceptEntry? = entries().firstOrNull { it.id == id }

    fun entryFor(folder: File): ConceptEntry? = entries().firstOrNull { it.isAt(folder) }

    /** Records [folder] as the concept [id], or refreshes the name and kind of one already there. */
    fun register(id: String, name: String, kind: String, folder: File): ConceptEntry {
        val entry = ConceptEntry(id = id, name = name, kind = kind, path = folder.absolutePath)
        save(entries().filterNot { it.isAt(folder) || it.id == id } + entry)
        return entry
    }

    fun forget(folder: File) = save(entries().filterNot { it.isAt(folder) })

    /** Follows a folder that has moved. */
    fun repath(from: File, to: File) {
        val cut = from.absolutePath.trimEnd(File.separatorChar).length
        save(
            entries().map { entry ->
                when {
                    entry.isAt(from) -> entry.copy(path = to.absolutePath)
                    entry.dir.isInside(from) -> entry.copy(path = to.absolutePath + entry.path.substring(cut))
                    else -> entry
                }
            },
        )
    }

    /** Drops entries whose folder has vanished. */
    fun prune() {
        val live = entries().filter { it.dir.isDirectory }
        if (live.size != entries().size) save(live)
    }

    private fun save(list: List<ConceptEntry>) {
        runCatching {
            dir.mkdirs()
            file.writeText(json.encodeToString(ListSerializer(ConceptEntry.serializer()), list))
        }
    }

    companion object {
        const val FILE_NAME = "concepts.json"
        private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    }
}

/** One concept's entry in the registry. */
@Serializable
data class ConceptEntry(
    val id: String,
    val name: String,
    val kind: String = "",
    val path: String,
) {
    val dir: File get() = File(path)
    fun isAt(folder: File): Boolean = dir.samePathAs(folder)
}
