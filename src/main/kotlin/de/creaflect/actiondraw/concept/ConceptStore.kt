package de.creaflect.actiondraw.concept

import de.creaflect.actiondraw.board.ImageItem
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Reads and writes the concept sidecar the way `BoardStore` does the board's: an atomic save with
 * a backup beside it, a parser that tolerates a byte-order mark, and a validation pass that drops
 * what has vanished from disk rather than showing a card with nothing behind it.
 */
object ConceptStore {
    const val FILE_NAME = ".actiondraw_concept.json"
    private const val BAK_NAME = "$FILE_NAME.bak"
    private const val TMP_NAME = "$FILE_NAME.tmp"

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

    fun exists(root: File): Boolean = File(root, FILE_NAME).isFile

    /** The sidecar as written, without validation — for listings that must not touch the folder. */
    fun peek(root: File): ConceptFile? = parse(File(root, FILE_NAME)) ?: parse(File(root, BAK_NAME))

    fun load(root: File): LoadResult {
        if (!exists(root)) return LoadResult.None
        parse(File(root, FILE_NAME))?.let { return LoadResult.Loaded(validate(it, root), fromBackup = false) }
        parse(File(root, BAK_NAME))?.let { return LoadResult.Loaded(validate(it, root), fromBackup = true) }
        return LoadResult.Failed
    }

    private fun parse(file: File): ConceptFile? = runCatching {
        file.takeIf { it.isFile }
            ?.readText()
            ?.removePrefix("﻿")
            ?.let { json.decodeFromString(ConceptFile.serializer(), it) }
    }.getOrNull()

    /** Drops pictures and documents whose files are gone; notes and links are never dropped. */
    fun validate(concept: ConceptFile, root: File): ConceptFile = concept.copy(
        items = concept.items.filter { it !is ImageItem || File(root, it.path).isFile },
        documents = concept.documents.filter { File(root, it).isFile },
    )

    fun save(root: File, concept: ConceptFile): Boolean = runCatching {
        val main = File(root, FILE_NAME)
        val tmp = File(root, TMP_NAME)
        tmp.writeText(json.encodeToString(ConceptFile.serializer(), concept))
        if (main.isFile) main.copyTo(File(root, BAK_NAME), overwrite = true)
        Files.move(tmp.toPath(), main.toPath(), StandardCopyOption.REPLACE_EXISTING)
        true
    }.getOrDefault(false)
}
