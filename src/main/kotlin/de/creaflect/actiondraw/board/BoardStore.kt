package de.creaflect.actiondraw.board

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

    fun load(root: File): LoadResult {
        if (!exists(root)) return LoadResult.None
        parse(File(root, FILE_NAME))?.let { return LoadResult.Loaded(validate(it, root), fromBackup = false) }
        parse(File(root, BAK_NAME))?.let { return LoadResult.Loaded(validate(it, root), fromBackup = true) }
        return LoadResult.Failed
    }

    private fun parse(file: File): BoardFile? = runCatching {
        file.takeIf { it.isFile }?.readText()?.let { json.decodeFromString(BoardFile.serializer(), it) }
    }.getOrNull()

    /**
     * Drops image items whose file no longer exists — the accepted Phase-1 answer to external
     * renames/deletes (shaping A4). Notes are never dropped. The drop becomes permanent with the
     * next save.
     */
    fun validate(board: BoardFile, root: File): BoardFile =
        board.copy(items = board.items.filter { it !is ImageItem || File(root, it.path).isFile })

    fun save(root: File, board: BoardFile): Boolean = runCatching {
        val main = File(root, FILE_NAME)
        val tmp = File(root, TMP_NAME)
        tmp.writeText(json.encodeToString(BoardFile.serializer(), board))
        if (main.isFile) main.copyTo(File(root, BAK_NAME), overwrite = true)
        Files.move(tmp.toPath(), main.toPath(), StandardCopyOption.REPLACE_EXISTING)
        true
    }.getOrDefault(false)
}
