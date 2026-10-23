package de.creaflect.actiondraw.io

import de.creaflect.actiondraw.isInside
import de.creaflect.actiondraw.samePathAs
import java.io.File

/**
 * Folders and the names in them: the next name that is still free, and moving or deleting a
 * whole folder without leaving it in halves. Boards and concepts are folders, so this is what
 * their lifecycle stands on. (What a name may hold is the domain's rule, `safeFileName`.)
 */
object Folders {
    /** How [move] went. */
    enum class Move {
        /** The folder is at its new place, and only there. */
        MOVED,

        /** It moved, but the old folder could not be cleared away (something in it is open). */
        MOVED_OLD_LEFT,

        /** Nothing happened to it: it could not be moved, or the destination was taken. */
        FAILED,

        /** Refused: the destination is the folder itself or lies inside it. */
        INTO_ITSELF,
    }

    /** `Test` → `Test (2)` → `Test (3)` …: the first folder name free beside [wanted]. Never splits off a dot. */
    fun freeFolder(wanted: File): File {
        if (!wanted.exists()) return wanted
        var n = 2
        while (true) {
            val candidate = File(wanted.parentFile, wanted.name + " (" + n + ")")
            if (!candidate.exists()) return candidate
            n++
        }
    }

    /** `foo.jpg` → `foo (2).jpg` → `foo (3).jpg` …: the first file name free beside [wanted]. */
    fun freeFile(wanted: File): File {
        if (!wanted.exists()) return wanted
        val base = wanted.nameWithoutExtension
        val ext = wanted.extension
        var n = 2
        while (true) {
            val name = if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext"
            val candidate = File(wanted.parentFile, name)
            if (!candidate.exists()) return candidate
            n++
        }
    }

    /**
     * Moves [from] to [to], which must not exist yet: a rename where the filesystem allows it,
     * copy-then-delete where it does not (another drive). A copy that fails part-way is cleaned
     * up, so the folder is never left in two halves — either it moved or nothing happened to it.
     */
    fun move(from: File, to: File): Move {
        // Never copy a folder into its own subtree, whatever the caller believed: it would copy
        // itself into itself until the filesystem gave out.
        if (to.samePathAs(from) || to.isInside(from)) return Move.INTO_ITSELF
        // The clean-up below deletes the destination, so it must be one this move created.
        if (to.exists()) return Move.FAILED
        if (from.renameTo(to)) return Move.MOVED
        val copied = runCatching { from.copyRecursively(to, overwrite = false) }.isSuccess
        if (!copied) {
            to.deleteRecursively()
            return Move.FAILED
        }
        return if (from.deleteRecursively()) Move.MOVED else Move.MOVED_OLD_LEFT
    }

    /**
     * Folders nobody means to erase whole, whoever asks: a drive root, and the user's home.
     * Compared canonically, so a junction or a differently-cased path is no way around it.
     */
    fun isProtected(dir: File, dirs: AppDirs = AppDirs.current): Boolean {
        val target = runCatching { dir.canonicalFile }.getOrNull() ?: return true
        if (target.parentFile == null) return true
        val home = runCatching { dirs.userHome.canonicalFile }.getOrNull()
        return home != null && target.samePathAs(home)
    }

    /**
     * Deletes [dir] and everything in it — the one genuinely destructive thing the app does, so
     * a [protected][isProtected] folder is refused outright. False when it was refused or
     * something in it could not be deleted.
     */
    fun deleteTree(dir: File, dirs: AppDirs = AppDirs.current): Boolean {
        if (isProtected(dir, dirs)) return false
        return dir.deleteRecursively()
    }
}
