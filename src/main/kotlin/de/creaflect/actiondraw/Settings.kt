package de.creaflect.actiondraw

import java.io.File
import java.util.Properties

/**
 * App-level preferences — unlike the per-folder seen/redo files these belong to the installation,
 * so they live in `~/.actiondraw/settings.properties`. Used to reopen the folder you drew from
 * last. All IO is best-effort: an unreadable or unwritable config simply means "no memory".
 *
 * [dir] is injectable so tests never touch the real user config.
 */
class Settings(private val dir: File = defaultDir()) {
    private val file: File get() = File(dir, FILE_NAME)

    /** The folder from the previous run, or null if unknown or no longer a directory. */
    fun lastFolder(): File? = runCatching {
        read().getProperty(KEY_LAST_FOLDER)
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.isDirectory } // moved or deleted since last time -> forget it
    }.getOrNull()

    fun setLastFolder(folder: File) {
        val props = read()
        props.setProperty(KEY_LAST_FOLDER, folder.absolutePath)
        write(props)
    }

    private fun read(): Properties = Properties().also { props ->
        runCatching { file.takeIf { it.isFile }?.inputStream()?.use(props::load) }
    }

    private fun write(props: Properties) {
        runCatching {
            dir.mkdirs()
            file.outputStream().use { props.store(it, "ActionDraw settings") }
        }
    }

    companion object {
        const val FILE_NAME = "settings.properties"
        private const val KEY_LAST_FOLDER = "lastFolder"

        private fun defaultDir(): File =
            File(System.getProperty("user.home") ?: ".", ".actiondraw")
    }
}
