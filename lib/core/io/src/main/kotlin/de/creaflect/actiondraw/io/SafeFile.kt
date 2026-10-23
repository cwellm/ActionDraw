package de.creaflect.actiondraw.io

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * A file the app writes whole and must never half-write, like a board's or a concept's sidecar.
 * The new text goes to `<name>.tmp` and is renamed over the old file, which is kept first as
 * `<name>.bak`: a crash mid-write leaves the old file, and a file spoiled some other way still has
 * the version before it. Reading never throws — a file that cannot be read is simply not there.
 */
class SafeFile(val file: File) {
    val backup: File get() = File(file.parentFile, file.name + ".bak")
    private val temp: File get() = File(file.parentFile, file.name + ".tmp")

    /** What [load] found, and whether it had to fall back to the backup to find it. */
    data class Loaded<T>(val value: T, val fromBackup: Boolean)

    fun exists(): Boolean = file.isFile

    /** Writes [text] all or nothing; false when it could not, with the old file untouched. */
    fun write(text: String): Boolean = runCatching {
        temp.writeText(text)
        if (file.isFile) file.copyTo(backup, overwrite = true)
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        true
    }.getOrDefault(false)

    /**
     * The file decoded by [decode], or else its backup; null when neither can be read or decoded.
     * Whatever [decode] throws counts as "cannot be decoded".
     */
    fun <T : Any> load(decode: (String) -> T): Loaded<T>? {
        textOf(file)?.let { text -> runCatching { decode(text) }.getOrNull()?.let { return Loaded(it, fromBackup = false) } }
        textOf(backup)?.let { text -> runCatching { decode(text) }.getOrNull()?.let { return Loaded(it, fromBackup = true) } }
        return null
    }

    /** Removes the file and its backup; true when the file itself is gone. */
    fun delete(): Boolean {
        val removed = file.delete()
        backup.delete()
        return removed
    }

    private fun textOf(f: File): String? = runCatching {
        // Editors on Windows like to add a byte-order mark; a JSON parser chokes on it.
        f.takeIf { it.isFile }?.readText()?.removePrefix("\uFEFF")
    }.getOrNull()
}
