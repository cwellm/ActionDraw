package de.creaflect.actiondraw.board

import de.creaflect.actiondraw.LoadResult
import de.creaflect.actiondraw.image.ImageScanner
import de.creaflect.actiondraw.image.relKey
import de.creaflect.actiondraw.io.SafeFile
import de.creaflect.actiondraw.model.BoardJson
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Loads and saves the board sidecar. Writes are atomic (tmp file + rename) and keep a `.bak` of
 * the previous version; a sidecar that fails to parse never crashes the app — the backup is tried,
 * and failing that the caller gets [LoadResult.Failed] to report.
 */
internal object BoardStore {
    const val FILE_NAME = ".actiondraw_board.json"

    /** Where a board keeps the copy of its background picture. */
    const val WALLPAPER_DIR = "_wallpaper"

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private fun sidecar(root: File) = SafeFile(File(root, FILE_NAME))

    fun exists(root: File): Boolean = sidecar(root).exists()

    /** Cheap look at a board (name etc.) without validation — for board lists. */
    fun peek(root: File): Board? = sidecar(root).load(::decode)?.value

    fun load(root: File): LoadResult<Board> {
        val sidecar = sidecar(root)
        if (!sidecar.exists()) return LoadResult.None
        val loaded = sidecar.load(::decode) ?: return LoadResult.Failed
        return LoadResult.Loaded(validate(loaded.value, root), loaded.fromBackup)
    }

    /** Removes the sidecar and its backup: the folder stops being a board, its files stay. */
    fun forget(root: File): Boolean = sidecar(root).delete()

    private fun decode(text: String): Board = json.decodeFromString(BoardJson.serializer(), text).toBoard()

    /**
     * Reconciles the board with what is on disk. A card whose file is gone is not dropped
     * immediately: if a file with the same content ([ContentId]) turns up elsewhere in the board
     * folder, the card follows it — so renaming or moving a picture outside the app keeps its
     * caption, tags, groups and place. Only cards whose picture is really gone disappear, and
     * cards still missing a content id get one so they are recoverable next time. Notes are never
     * dropped. Changes become permanent with the next save.
     */
    fun validate(board: Board, root: File): Board {
        // Borrowed pictures (a linked concept's) are checked against the concept, not this folder.
        val (present, missing) = board.items.filterIsInstance<ImageItem>()
            .filterNot { ConceptLink.isBorrowedPath(it.path) }
            .partition { File(root, it.path).isFile }
        // Index the folder by content only when something actually has to be found again.
        val candidates: Map<String, String> =
            if (missing.isEmpty()) emptyMap()
            else {
                val taken = present.mapTo(mutableSetOf()) { it.path }
                ImageScanner.scanTree(root)
                    .map { relKey(root, it) }
                    .filter { it !in taken && !it.startsWith(WALLPAPER_DIR + "/") }
                    .mapNotNull { path -> ContentId.of(File(root, path))?.let { it to path } }
                    .toMap()
            }
        val recovered = missing.mapNotNull { item ->
            item.contentId?.let { candidates[it] }?.let { item.copy(path = it) }
        }.associateBy { it.id }
        val lost = missing.filter { it.id !in recovered }.mapTo(mutableSetOf()) { it.id }

        return board.copy(
            groups = oneLevelDeep(board.groups),
            items = board.items.mapNotNull { item ->
                when {
                    item !is ImageItem -> item
                    ConceptLink.isBorrowedPath(item.path) -> item
                    item.id in lost -> null
                    else -> recovered[item.id] ?: item.withContentId(root)
                }
            },
        )
    }

    /** Fills in a missing content id from the file on disk (cheap: a length + sampled hash). */
    private fun ImageItem.withContentId(root: File): ImageItem =
        if (contentId != null) this else copy(contentId = ContentId.of(File(root, path)))

    fun save(root: File, board: Board): Boolean =
        runCatching { sidecar(root).write(json.encodeToString(BoardJson.serializer(), board.toJson())) }.getOrDefault(false)
}
