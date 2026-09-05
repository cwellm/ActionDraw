package de.creaflect.actiondraw.board

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Which folder each board lives in.
 *
 * Boards used to be *found* by scanning the boards home, which quietly tied a board's identity to
 * its location: point the home somewhere else and yesterday's boards were gone from the list, and
 * a board's name had to be its folder's name, so a leftover folder could block its own name. The
 * registry records the mapping instead — a board keeps its folder wherever the home points, and
 * deleting a board deletes exactly the folder that board was in.
 *
 * Lives next to the settings file, and like it, all IO is best-effort: an unreadable registry
 * means the scan below is all we know, not a broken app.
 */
class BoardRegistry(private val dir: File) {
    private val file: File get() = File(dir, FILE_NAME)

    fun entries(): List<BoardEntry> = runCatching {
        file.takeIf { it.isFile }
            ?.readText()
            ?.removePrefix("\uFEFF")
            ?.let { json.decodeFromString(ListSerializer(BoardEntry.serializer()), it) }
            .orEmpty()
    }.getOrDefault(emptyList())

    fun entryFor(folder: File): BoardEntry? = entries().firstOrNull { it.isAt(folder) }

    /** Records [folder] as a board, or updates the name of one already recorded there. */
    fun register(name: String, folder: File, ownsFolder: Boolean): BoardEntry {
        val existing = entryFor(folder)
        val entry = existing?.copy(name = name)
            ?: BoardEntry(
                id = Importer.newId(),
                name = name,
                path = folder.absolutePath,
                ownsFolder = ownsFolder,
            )
        save(entries().filterNot { it.isAt(folder) } + entry)
        return entry
    }

    fun forget(folder: File) = save(entries().filterNot { it.isAt(folder) })

    /** Drops entries whose folder has vanished — deleted in Explorer, or on a drive now offline. */
    fun prune() {
        val live = entries().filter { it.dir.isDirectory }
        if (live.size != entries().size) save(live)
    }

    private fun save(list: List<BoardEntry>) {
        runCatching {
            dir.mkdirs()
            file.writeText(json.encodeToString(ListSerializer(BoardEntry.serializer()), list))
        }
    }

    companion object {
        const val FILE_NAME = "boards.json"
        private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    }
}

/** One board's entry in the registry. */
@Serializable
data class BoardEntry(
    val id: String,
    val name: String,
    val path: String,
    /**
     * True when ActionDraw made this folder for the board; false when an existing folder was
     * adopted (*Explore…*, or a drop of someone's picture library). It decides what deleting the
     * board means: a folder the app created is the board and goes with it, while a folder that
     * was already yours stays yours and merely stops being a board.
     */
    val ownsFolder: Boolean = false,
) {
    val dir: File get() = File(path)

    /** Windows paths differ only in case, so compare that way rather than by string identity. */
    fun isAt(folder: File): Boolean = path.equals(folder.absolutePath, ignoreCase = true)
}
