package de.creaflect.actiondraw

import de.creaflect.actiondraw.io.AppDirs
import java.io.File
import java.util.Properties

/**
 * The [Preferences], kept in `~/.actiondraw/settings.properties` — unlike the per-folder seen and
 * redo files they belong to the installation. All IO is best-effort: an unreadable or unwritable
 * file simply means "no memory".
 *
 * [dirs] is injectable so tests never touch the real user's.
 */
internal class Settings(private val dirs: AppDirs = AppDirs.current) : Preferences {
    private val file: File get() = File(dirs.config, FILE_NAME)

    override fun lastFolder(): File? = runCatching {
        read().getProperty(KEY_LAST_FOLDER)
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.isDirectory } // moved or deleted since last time -> forget it
    }.getOrNull()

    override fun setLastFolder(folder: File) = set(KEY_LAST_FOLDER, folder.absolutePath)

    override fun reducedMotion(): Boolean? = runCatching { read().getProperty(KEY_REDUCED_MOTION)?.toBooleanStrictOrNull() }.getOrNull()

    override fun setReducedMotion(on: Boolean) = set(KEY_REDUCED_MOTION, on.toString())

    override fun practiceBoard(): File? = runCatching { read().getProperty(KEY_PRACTICE_BOARD)?.takeIf { it.isNotBlank() }?.let(::File) }.getOrNull()

    override fun setPracticeBoard(dir: File?) {
        val props = read()
        if (dir == null) props.remove(KEY_PRACTICE_BOARD) else props.setProperty(KEY_PRACTICE_BOARD, dir.absolutePath)
        write(props)
    }

    override fun tiltShading(): Boolean = runCatching { read().getProperty(KEY_TILT_SHADING)?.toBooleanStrictOrNull() }.getOrNull() ?: true

    override fun setTiltShading(on: Boolean) = set(KEY_TILT_SHADING, on.toString())

    override fun lastRoom(): String? = runCatching { read().getProperty(KEY_LAST_ROOM)?.takeIf { it.isNotBlank() } }.getOrNull()

    override fun setLastRoom(room: String) = set(KEY_LAST_ROOM, room)

    override fun conceptsHome(): File = folder(KEY_CONCEPTS_HOME) ?: dirs.defaultConceptsHome

    override fun setConceptsHome(dir: File) = set(KEY_CONCEPTS_HOME, dir.absolutePath)

    override fun sketchesHome(): File = folder(KEY_SKETCHES_HOME) ?: dirs.defaultSketchesHome

    override fun setSketchesHome(dir: File) = set(KEY_SKETCHES_HOME, dir.absolutePath)

    override fun collagesHome(): File = folder(KEY_COLLAGES_HOME) ?: dirs.defaultCollagesHome

    override fun setCollagesHome(dir: File) = set(KEY_COLLAGES_HOME, dir.absolutePath)

    override fun snapByDefault(): Boolean = runCatching { read().getProperty(KEY_SNAP)?.toBoolean() }.getOrNull() ?: false

    override fun setSnapByDefault(on: Boolean) = set(KEY_SNAP, on.toString())

    override fun boardsHome(): File = folder(KEY_BOARDS_HOME) ?: dirs.defaultBoardsHome

    override fun setBoardsHome(dir: File) = set(KEY_BOARDS_HOME, dir.absolutePath)

    override fun recentBoards(): List<File> = runCatching {
        val props = read()
        (0 until MAX_RECENT_BOARDS)
            .mapNotNull { props.getProperty("$KEY_RECENT_BOARD.$it")?.takeIf { p -> p.isNotBlank() } }
            .map(::File)
            .filter { it.isDirectory }
    }.getOrDefault(emptyList())

    override fun addRecentBoard(dir: File) =
        setRecentBoards(listOf(dir.absoluteFile) + recentBoards().filter { !it.samePathAs(dir) })

    override fun removeRecentBoard(dir: File) = setRecentBoards(recentBoards().filter { !it.samePathAs(dir) })

    private fun setRecentBoards(boards: List<File>) {
        val props = read()
        (0 until MAX_RECENT_BOARDS).forEach { props.remove("$KEY_RECENT_BOARD.$it") }
        boards.take(MAX_RECENT_BOARDS).forEachIndexed { i, f -> props.setProperty("$KEY_RECENT_BOARD.$i", f.absolutePath) }
        write(props)
    }

    override fun recentSketches(): List<File> = runCatching {
        val props = read()
        (0 until MAX_RECENT_SKETCHES)
            .mapNotNull { props.getProperty("$KEY_RECENT_SKETCH.$it")?.takeIf { p -> p.isNotBlank() } }
            .map(::File)
            .filter { it.isFile }
    }.getOrDefault(emptyList())

    override fun addRecentSketch(file: File) {
        val next = (listOf(file.absoluteFile) + recentSketches().filter { it.absoluteFile != file.absoluteFile })
            .take(MAX_RECENT_SKETCHES)
        val props = read()
        (0 until MAX_RECENT_SKETCHES).forEach { props.remove("$KEY_RECENT_SKETCH.$it") }
        next.forEachIndexed { i, f -> props.setProperty("$KEY_RECENT_SKETCH.$i", f.absolutePath) }
        write(props)
    }

    override fun recentCollages(): List<File> = runCatching {
        val props = read()
        (0 until MAX_RECENT_COLLAGES)
            .mapNotNull { props.getProperty("$KEY_RECENT_COLLAGE.$it")?.takeIf { p -> p.isNotBlank() } }
            .map(::File)
            .filter { it.isFile }
    }.getOrDefault(emptyList())

    override fun addRecentCollage(file: File) {
        val next = (listOf(file.absoluteFile) + recentCollages().filter { !it.samePathAs(file) }).take(MAX_RECENT_COLLAGES)
        val props = read()
        (0 until MAX_RECENT_COLLAGES).forEach { props.remove("$KEY_RECENT_COLLAGE.$it") }
        next.forEachIndexed { i, f -> props.setProperty("$KEY_RECENT_COLLAGE.$i", f.absolutePath) }
        write(props)
    }

    override fun leadPresetsJson(): String? = runCatching { read().getProperty(KEY_LEAD_PRESETS)?.takeIf { it.isNotBlank() } }.getOrNull()

    override fun setLeadPresetsJson(json: String) = set(KEY_LEAD_PRESETS, json)

    override fun glass(): GlassSetting? = runCatching {
        val props = read()
        val optic = props.getProperty(KEY_GLASS_OPTIC)?.takeIf { it.isNotBlank() } ?: return null
        GlassSetting(
            optic,
            props.getProperty(KEY_GLASS_MAGNIFICATION).toFloat(),
            props.getProperty(KEY_GLASS_RADIUS).toFloat(),
            props.getProperty(KEY_GLASS_BEND)?.toFloatOrNull() ?: 1f,
        )
    }.getOrNull()

    override fun setGlass(setting: GlassSetting) {
        val props = read()
        props.setProperty(KEY_GLASS_OPTIC, setting.optic)
        props.setProperty(KEY_GLASS_MAGNIFICATION, setting.magnification.toString())
        props.setProperty(KEY_GLASS_RADIUS, setting.radius.toString())
        props.setProperty(KEY_GLASS_BEND, setting.bend.toString())
        write(props)
    }

    private fun folder(key: String): File? = runCatching { read().getProperty(key)?.takeIf { it.isNotBlank() }?.let(::File) }.getOrNull()

    private fun set(key: String, value: String) {
        val props = read()
        props.setProperty(key, value)
        write(props)
    }

    private fun read(): Properties = Properties().also { props ->
        runCatching { file.takeIf { it.isFile }?.inputStream()?.use(props::load) }
    }

    private fun write(props: Properties) {
        runCatching {
            dirs.config.mkdirs()
            file.outputStream().use { props.store(it, "ActionDraw settings") }
        }
    }

    companion object {
        const val FILE_NAME = "settings.properties"
        private const val KEY_LAST_FOLDER = "lastFolder"
        private const val KEY_LAST_ROOM = "lastRoom"
        private const val KEY_TILT_SHADING = "tiltShading"
        private const val KEY_PRACTICE_BOARD = "practiceBoard"
        private const val KEY_REDUCED_MOTION = "reducedMotion"
        private const val KEY_BOARDS_HOME = "boardsHome"
        private const val KEY_SNAP = "snapByDefault"
        private const val KEY_CONCEPTS_HOME = "conceptsHome"
        private const val KEY_SKETCHES_HOME = "sketchesHome"
        private const val KEY_RECENT_SKETCH = "recentSketch"
        private const val MAX_RECENT_SKETCHES = 12
        private const val KEY_COLLAGES_HOME = "collagesHome"
        private const val KEY_RECENT_COLLAGE = "recentCollage"
        private const val MAX_RECENT_COLLAGES = 12
        private const val KEY_LEAD_PRESETS = "leadPresets"
        private const val KEY_RECENT_BOARD = "recentBoard"
        private const val MAX_RECENT_BOARDS = 5
        private const val KEY_GLASS_OPTIC = "glassOptic"
        private const val KEY_GLASS_MAGNIFICATION = "glassMagnification"
        private const val KEY_GLASS_RADIUS = "glassRadius"
        private const val KEY_GLASS_BEND = "glassBend"
    }
}
