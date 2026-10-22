package de.creaflect.actiondraw.board

import de.creaflect.actiondraw.LoadResult
import de.creaflect.actiondraw.SETTLE
import de.creaflect.actiondraw.Settings
import de.creaflect.actiondraw.deleteFolder
import de.creaflect.actiondraw.io.AppDirs
import de.creaflect.actiondraw.io.Folders
import de.creaflect.actiondraw.isInside
import de.creaflect.actiondraw.isProtected
import de.creaflect.actiondraw.keyOf
import de.creaflect.actiondraw.lifecycle.Writes
import de.creaflect.actiondraw.safeFileName
import de.creaflect.actiondraw.samePathAs
import de.creaflect.actiondraw.settle
import java.io.File
import java.io.IOException

/**
 * The [Boards] over the files: each board's sidecar ([BoardStore]), the registry of which folder
 * each board lives in ([BoardRegistry]), and the settings for the boards home and the recent ones.
 */
internal class FileBoards(
    private val settings: Settings,
    private val registry: BoardRegistry,
    private val writes: Writes,
    private val dirs: AppDirs,
) : Boards {
    // ---- One board ----

    override fun isBoard(dir: File): Boolean {
        writes.settle(dir)
        return BoardStore.exists(dir)
    }

    override fun peek(dir: File): Board? {
        writes.settle(dir)
        return BoardStore.peek(dir)
    }

    override fun load(dir: File): LoadResult<Board> {
        writes.settle(dir)
        return BoardStore.load(dir)
    }

    override fun save(dir: File, board: Board) = writes.write(keyOf(dir)) {
        if (!BoardStore.save(dir, board)) throw IOException("Couldn't write the board in $dir")
    }

    override fun saveNow(dir: File, board: Board): Boolean {
        writes.settle(dir)
        return BoardStore.save(dir, board)
    }

    // ---- Which boards there are ----

    override fun entryFor(dir: File): BoardEntry? = registry.entryFor(dir)

    override fun rename(dir: File, name: String) {
        registry.register(name, dir, ownsFolder = registry.entryFor(dir)?.ownsFolder ?: false)
    }

    override fun last(): BoardRef? {
        val dir = settings.recentBoards().firstOrNull { isBoard(it) } ?: return null
        return BoardRef(nameOf(dir), dir)
    }

    override fun available(): List<BoardRef> {
        registry.prune()
        val found = LinkedHashMap<String, File>()
        registry.entries()
            .filter { isBoard(it.dir) }
            .forEach { found.putIfAbsent(it.path.lowercase(), it.dir) }

        // Because the registry holds absolute paths, pointing the boards home somewhere else adds
        // a place to look; it does not take the boards already recorded away.
        val seen = settings.recentBoards() +
            settings.boardsHome().listFiles().orEmpty().sortedBy { it.name.lowercase() }
        seen.filter { it.isDirectory && isBoard(it) }.forEach { dir ->
            if (found.putIfAbsent(dir.absolutePath.lowercase(), dir.absoluteFile) == null) adopt(dir)
        }

        return found.values.map { BoardRef(nameOf(it), it) }
    }

    override fun tree(): List<BoardNode> {
        val all = available()
        val known = all.map { it.dir.absolutePath.lowercase() }.toSet()
        fun parentPathOf(dir: File): String? =
            registry.parentOf(dir)?.path?.lowercase()?.takeIf { it in known }

        val out = mutableListOf<BoardNode>()
        fun walk(under: String?, depth: Int, parentName: String?) {
            all.filter { parentPathOf(it.dir) == under }
                .sortedBy { it.name.lowercase() }
                .forEach { (name, dir) ->
                    out += BoardNode(name, dir, depth, parentName)
                    walk(dir.absolutePath.lowercase(), depth + 1, name)
                }
        }
        walk(null, 0, null)
        return out
    }

    override fun parentOf(dir: File): BoardEntry? = registry.parentOf(dir)?.takeIf { isBoard(it.dir) }

    override fun childrenOf(dir: File): List<BoardEntry> =
        registry.descendantsOf(dir)
            .filter { registry.parentOf(it.dir)?.isAt(dir) == true && isBoard(it.dir) }
            .sortedBy { it.name.lowercase() }

    override fun countInside(dir: File): Int = registry.descendantsOf(dir).count { isBoard(it.dir) }

    override fun opened(dir: File) {
        settings.addRecentBoard(dir)
        if (registry.entryFor(dir) == null) adopt(dir)
    }

    /** The name a board goes by in a list: its own, else its folder's. */
    private fun nameOf(dir: File): String = peek(dir)?.name?.takeIf { it.isNotBlank() } ?: dir.name

    /**
     * Records a board folder nobody told us about. A direct child of the boards home is one the
     * app almost certainly made (that is where a new board goes), so it counts as the board's
     * own; anything else was already the person's before ActionDraw saw it.
     */
    private fun adopt(dir: File) {
        val underHome = runCatching {
            dir.canonicalFile.parentFile == settings.boardsHome().canonicalFile
        }.getOrDefault(false)
        registry.register(nameOf(dir), dir, ownsFolder = underHome)
    }

    // ---- Making, deleting, moving ----

    override fun create(parent: File, board: Board): BoardCreation {
        // "Is there a board called this already?" is a question for the registry: a board's name
        // and its folder's name are not the same thing.
        val known = registry.entries().firstOrNull {
            it.name.equals(board.name, ignoreCase = true) &&
                it.dir.parentFile?.samePathAs(parent) == true &&
                isBoard(it.dir)
        }
        if (known != null) return BoardCreation.Existing(known.dir)
        val wanted = File(parent, safeFileName(board.name))
        if (isBoard(wanted)) return BoardCreation.Existing(wanted) // one the registry has not met yet
        // A leftover folder keeps the name it has and the new board gets one beside it — deleting
        // a board must never make its name unusable.
        val dir = if (wanted.exists() && !wanted.listFiles().isNullOrEmpty()) Folders.freeFolder(wanted) else wanted
        if (!dir.isDirectory && !dir.mkdirs()) return BoardCreation.CouldNotCreate(dir)
        // A board made inside another one is nesting, not a choice of where new boards go.
        if (registry.parentOf(dir) == null) settings.setBoardsHome(parent)
        if (!saveNow(dir, board)) return BoardCreation.CouldNotWrite(dir)
        registry.register(board.name, dir, ownsFolder = true)
        return BoardCreation.Created(dir)
    }

    override fun delete(dir: File, how: Deletion): BoardDeletion {
        if (!dir.isDirectory) return BoardDeletion.GONE_ALREADY
        if (how == Deletion.DELETE_FOLDER && !safeToDeleteFolder(dir)) return BoardDeletion.REFUSED
        // Nothing still on its way to the disk may land in the folder after it is gone.
        writes.flush(SETTLE)
        val outcome = when (how) {
            Deletion.FORGET -> if (BoardStore.forget(dir)) BoardDeletion.FORGOTTEN else BoardDeletion.NOT_FORGOTTEN
            Deletion.DELETE_FOLDER -> if (deleteFolder(dir, dirs)) BoardDeletion.DELETED else BoardDeletion.NOT_DELETED
        }
        // A deleted folder takes any board nested inside it, so their records go too.
        if (how == Deletion.DELETE_FOLDER) registry.descendantsOf(dir).forEach { registry.forget(it.dir) }
        registry.forget(dir)
        settings.removeRecentBoard(dir)
        return outcome
    }

    /** Guards the destructive path against folders nobody means to erase. */
    private fun safeToDeleteFolder(dir: File): Boolean {
        val target = runCatching { dir.canonicalFile }.getOrNull() ?: return false
        if (isProtected(target, dirs)) return false // a drive root, or the home
        val boardsHome = runCatching { settings.boardsHome().canonicalFile }.getOrNull()
        if (target == boardsHome) return false
        // Only a folder ActionDraw actually knows as a board may be removed wholesale.
        return isBoard(target)
    }

    override fun move(dir: File, into: File?): BoardMove {
        if (!isBoard(dir)) return BoardMove.NotABoard
        val target = (into ?: settings.boardsHome()).absoluteFile
        // Compared canonically: a junction or symlink pointing back into the board would slip
        // past a plain string test, and moving a folder into itself copies it into itself until
        // the filesystem gives out. This guard is the only thing between that and the board.
        val realDir = runCatching { dir.canonicalFile }.getOrDefault(dir.absoluteFile)
        val realTarget = runCatching { target.canonicalFile }.getOrDefault(target)
        if (realTarget.samePathAs(realDir)) return BoardMove.IntoItself
        if (realTarget.isInside(realDir)) return BoardMove.IntoItsOwnSubBoard
        if (!target.isDirectory && !target.mkdirs()) return BoardMove.CannotOpen(target)
        if (dir.parentFile?.samePathAs(target) == true) return BoardMove.AlreadyThere

        val destination = Folders.freeFolder(File(target, dir.name))
        // What is still to be written into the folder is written before it moves, not after.
        writes.flush(SETTLE)
        val oldLeft = when (Folders.move(dir, destination)) {
            Folders.Move.MOVED -> false
            // It moved; the original just could not be cleared away.
            Folders.Move.MOVED_OLD_LEFT -> true
            Folders.Move.INTO_ITSELF -> return BoardMove.RefusedIntoItself
            Folders.Move.FAILED -> return BoardMove.Failed
        }
        registry.repath(dir, destination)
        settings.removeRecentBoard(dir)
        settings.addRecentBoard(destination)
        return BoardMove.Moved(destination, oldLeft)
    }

    // ---- The board's own copies ----

    override fun copyWallpaper(dir: File, picture: File): String? {
        val folder = File(dir, BoardStore.WALLPAPER_DIR).apply { mkdirs() }
        val target = Folders.freeFile(File(folder, picture.name))
        runCatching { picture.copyTo(target) }.onFailure { return null }
        return BoardStore.WALLPAPER_DIR + "/" + target.name
    }

    override fun deleteWallpaper(dir: File, path: String) {
        // Only ever a copy the board made: a hand-edited board file must not aim this elsewhere.
        val file = runCatching { File(dir, path).canonicalFile }.getOrNull() ?: return
        val folder = runCatching { File(dir, BoardStore.WALLPAPER_DIR).canonicalFile }.getOrNull() ?: return
        if (file.isInside(folder)) file.delete()
    }
}
