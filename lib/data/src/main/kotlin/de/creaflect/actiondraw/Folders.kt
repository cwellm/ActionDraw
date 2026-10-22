package de.creaflect.actiondraw

import de.creaflect.actiondraw.io.AppDirs
import de.creaflect.actiondraw.io.Folders
import de.creaflect.actiondraw.lifecycle.Writes
import java.io.File
import kotlin.time.Duration.Companion.seconds

// What the repositories share: how a folder is named to the writes, how long a read waits for
// them, and what may never be erased whole.

/** How long a read waits for its folder's pending write before reading what is there. */
internal val SETTLE = 5.seconds

/**
 * The key a folder's writes go by: the same folder, however its path was spelled, so a newer
 * write replaces an older one and a read waits for the right one.
 */
internal fun keyOf(dir: File): String = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)

/** Waits until nothing is still to be written into [dir]. */
internal fun Writes.settle(dir: File) {
    awaitKey(keyOf(dir), SETTLE)
}

/**
 * A folder nobody means to erase whole: a drive root, and the home — the lib's own ([dirs]) and
 * the person's, which differ when a test stands the lib up in a temp folder.
 */
internal fun isProtected(dir: File, dirs: AppDirs): Boolean = Folders.isProtected(dir, dirs) || Folders.isProtected(dir)

/** Deletes [dir] and everything in it, unless it [is protected][isProtected]. */
internal fun deleteFolder(dir: File, dirs: AppDirs): Boolean = !isProtected(dir, dirs) && Folders.deleteTree(dir, dirs)
