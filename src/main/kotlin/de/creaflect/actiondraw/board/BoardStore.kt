package de.creaflect.actiondraw.board

import de.creaflect.actiondraw.image.ImageScanner
import de.creaflect.actiondraw.image.relKey
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Loads and saves the board sidecar. Writes are atomic (tmp file + rename) and keep a `.bak` of
 * the previous version; a sidecar that fails to parse never crashes the app — the backup is tried,
 * and failing that the caller gets [LoadResult.Failed] to report.
 */
object BoardStore {
    const val FILE_NAME = ".actiondraw_board.json"
    private const val BAK_NAME = "$FILE_NAME.bak"
    private const val TMP_NAME = "$FILE_NAME.tmp"

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    sealed class LoadResult {
        /** No sidecar — the folder isn't a board (yet). */
        data object None : LoadResult()

        data class Loaded(val board: BoardFile, val fromBackup: Boolean) : LoadResult()

        /** Sidecar exists but neither it nor the backup could be parsed. */
        data object Failed : LoadResult()
    }

    fun exists(root: File): Boolean = File(root, FILE_NAME).isFile

    /** Cheap look at a board (name etc.) without validation — for board lists. */
    fun peek(root: File): BoardFile? = parse(File(root, FILE_NAME)) ?: parse(File(root, BAK_NAME))

    fun load(root: File): LoadResult {
        if (!exists(root)) return LoadResult.None
        parse(File(root, FILE_NAME))?.let { return LoadResult.Loaded(validate(it, root), fromBackup = false) }
        parse(File(root, BAK_NAME))?.let { return LoadResult.Loaded(validate(it, root), fromBackup = true) }
        return LoadResult.Failed
    }

    private fun parse(file: File): BoardFile? = runCatching {
        file.takeIf { it.isFile }
            ?.readText()
            // Editors on Windows like to add a byte-order mark; the JSON parser chokes on it.
            ?.removePrefix("﻿")
            ?.let { json.decodeFromString(BoardFile.serializer(), it) }
    }.getOrNull()

    /**
     * Reconciles the board with what is on disk. A card whose file is gone is not dropped
     * immediately: if a file with the same content ([ContentId]) turns up elsewhere in the board
     * folder, the card follows it — so renaming or moving a picture outside the app keeps its
     * caption, tags, groups and place. Only cards whose picture is really gone disappear, and
     * cards still missing a content id get one so they are recoverable next time. Notes are never
     * dropped. Changes become permanent with the next save.
     */
    fun validate(board: BoardFile, root: File): BoardFile {
        val (present, missing) = board.items.filterIsInstance<ImageItem>()
            .partition { File(root, it.path).isFile }
        // Index the folder by content only when something actually has to be found again.
        val candidates: Map<String, String> =
            if (missing.isEmpty()) emptyMap()
            else {
                val taken = present.mapTo(mutableSetOf()) { it.path }
                ImageScanner.scanTree(root)
                    .map { relKey(root, it) }
                    .filter { it !in taken }
                    .mapNotNull { path -> ContentId.of(File(root, path))?.let { it to path } }
                    .toMap()
            }
        val recovered = missing.mapNotNull { item ->
            item.contentId?.let { candidates[it] }?.let { item.copy(path = it) }
        }.associateBy { it.id }
        val lost = missing.filter { it.id !in recovered }.mapTo(mutableSetOf()) { it.id }

        return board.copy(
            items = board.items.mapNotNull { item ->
                when {
                    item !is ImageItem -> item
                    item.id in lost -> null
                    else -> recovered[item.id] ?: item.withContentId(root)
                }
            },
        )
    }

    /** Fills in a missing content id from the file on disk (cheap: a length + sampled hash). */
    private fun ImageItem.withContentId(root: File): ImageItem =
        if (contentId != null) this else copy(contentId = ContentId.of(File(root, path)))

    fun save(root: File, board: BoardFile): Boolean = runCatching {
        val main = File(root, FILE_NAME)
        val tmp = File(root, TMP_NAME)
        tmp.writeText(json.encodeToString(BoardFile.serializer(), board))
        if (main.isFile) main.copyTo(File(root, BAK_NAME), overwrite = true)
        Files.move(tmp.toPath(), main.toPath(), StandardCopyOption.REPLACE_EXISTING)
        true
    }.getOrDefault(false)
}
