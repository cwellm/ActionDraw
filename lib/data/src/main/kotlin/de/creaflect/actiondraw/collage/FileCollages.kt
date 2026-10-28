package de.creaflect.actiondraw.collage

import de.creaflect.actiondraw.Settings
import de.creaflect.actiondraw.io.SafeFile
import de.creaflect.actiondraw.model.CollageJson
import de.creaflect.actiondraw.model.PieceJson
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The [Collages] as files: `<name>.collage.json` wherever a collage was made, written whole or not
 * at all (a [SafeFile]); `<name>.png` beside it once it is saved as a picture. A piece's picture is
 * kept relative to the collage's folder where it lies inside it — the collage and its pictures move
 * together — and absolute where it lies elsewhere, on a board, in a concept.
 */
internal class FileCollages(private val settings: Settings) : Collages {
    override fun recent(): List<File> {
        val inHome = settings.collagesHome().listFiles().orEmpty().filter { it.isFile && it.name.endsWith(Collages.SUFFIX) }
        return (settings.recentCollages() + inHome)
            .filter { it.isFile }
            .distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
            .sortedByDescending { it.lastModified() }
    }

    override fun read(file: File): Collage? =
        SafeFile(file).load { json.decodeFromString(CollageJson.serializer(), it) }?.value?.toCollage(file.absoluteFile.parentFile)

    override fun write(file: File, collage: Collage): Boolean {
        file.absoluteFile.parentFile.mkdirs()
        val written = SafeFile(file).write(json.encodeToString(CollageJson.serializer(), collage.toJson(file.absoluteFile.parentFile)))
        if (written) settings.addRecentCollage(file)
        return written
    }

    override fun freeFile(dir: File, name: String): File = generateSequence(1) { it + 1 }
        .map { n -> if (n == 1) name else "$name ($n)" }
        .first { !taken(dir, it) }
        .let { File(dir, it + Collages.SUFFIX) }

    override fun rename(file: File, name: String): File? {
        val dir = file.absoluteFile.parentFile
        if (name == Collages.nameOf(file)) return file
        if (taken(dir, name)) return null
        val target = File(dir, name + Collages.SUFFIX)
        return runCatching {
            Files.move(file.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            val picture = File(dir, Collages.nameOf(file) + ".png")
            if (picture.isFile) Files.move(picture.toPath(), File(dir, "$name.png").toPath())
            settings.addRecentCollage(target)
            target
        }.getOrNull()
    }

    override fun opened(file: File) = settings.addRecentCollage(file)

    private fun taken(dir: File, name: String) = File(dir, name + Collages.SUFFIX).exists() || File(dir, "$name.png").exists()

    private fun CollageJson.toCollage(dir: File) = Collage(
        pieces.map { p ->
            Piece(
                id = p.id,
                name = p.name,
                source = if (p.tape) null else File(p.source).let { if (it.isAbsolute) it else File(dir, p.source) },
                outline = p.outline.chunked(2).filter { it.size == 2 }.map { (x, y) -> Spot(x, y) },
                x = p.x,
                y = p.y,
                turn = p.turn,
                scale = p.scale,
                flipped = p.flipped,
                holds = p.holds,
            )
        },
    )

    private fun Collage.toJson(dir: File) = CollageJson(
        pieces = pieces.map { p ->
            PieceJson(
                id = p.id,
                name = p.name,
                source = p.source?.let { relativeTo(dir, it) }.orEmpty(),
                outline = p.outline.flatMap { listOf(it.x, it.y) },
                x = p.x,
                y = p.y,
                turn = p.turn,
                scale = p.scale,
                flipped = p.flipped,
                tape = p.isTape,
                holds = p.holds,
            )
        },
    )

    /** [file] as the document keeps it: relative to [dir], `/`-separated, where it lies inside it; else absolute. */
    private fun relativeTo(dir: File, file: File): String {
        val base = dir.absoluteFile.toPath().normalize()
        val path = file.absoluteFile.toPath().normalize()
        return if (path.startsWith(base)) base.relativize(path).joinToString("/") else path.toString()
    }

    private companion object {
        val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
