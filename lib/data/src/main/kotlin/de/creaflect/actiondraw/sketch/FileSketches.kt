package de.creaflect.actiondraw.sketch

import de.creaflect.actiondraw.Settings
import de.creaflect.sketch.SketchDocument
import java.io.File

/** The [Sketches] as files: `<name>.png` beside `<name>.sketch.json`, wherever they were saved. */
internal class FileSketches(private val settings: Settings) : Sketches {
    override fun recent(): List<File> {
        val inHome = settings.sketchesHome().listFiles().orEmpty().filter { it.isFile && it.name.endsWith(SketchDocument.FILE_SUFFIX) }
        return (settings.recentSketches() + inHome)
            .filter { it.isFile }
            .distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
            .sortedByDescending { it.lastModified() }
    }

    override fun read(file: File): String? = runCatching { file.readText() }.getOrNull()

    override fun opened(file: File) = settings.addRecentSketch(file)

    override fun isTaken(dir: File, name: String): Boolean =
        File(dir, "$name.png").exists() || File(dir, name + SketchDocument.FILE_SUFFIX).exists()

    override fun write(dir: File, name: String, png: ByteArray, document: String): Result<File> = runCatching {
        dir.mkdirs()
        File(dir, "$name.png").writeBytes(png)
        val json = File(dir, name + SketchDocument.FILE_SUFFIX)
        json.writeText(document)
        settings.addRecentSketch(json)
        json
    }
}
