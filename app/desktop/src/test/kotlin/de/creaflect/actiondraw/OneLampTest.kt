package de.creaflect.actiondraw

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * One lamp (CONCEPT.md, *Materials*; F9.2): every shadow in the app is the lamp's — `Lift` and
 * `lampShadow`, cast to the bottom right — never Material's elevation or `Modifier.shadow`, which
 * falls straight down and would put a second light in the room. Read off the sources — the app's
 * and every module of the lib that draws (the design system, the features) — so a new screen
 * cannot bring its own shadow back in unnoticed.
 */
class OneLampTest {
    @Test
    fun everyShadowIsTheLamps() {
        // The test runs in app/desktop; the repository is two folders up.
        val repo = File("../..").canonicalFile
        val sources = listOf(File(repo, "app"), File(repo, "lib"))
            .flatMap { it.walk().filter { f -> f.isFile && f.extension == "kt" && f.path.contains("src${File.separator}main${File.separator}kotlin") }.toList() }
        assertTrue(sources.size > 50, "the sources were found: ${sources.size}")
        assertTrue(sources.any { it.name == "AtelierMaterials.kt" }, "the design system's among them")
        val elevation = Regex("""elevation\s*=\s*(?!0\.dp)[0-9]""")
        val shadow = Regex("""\.shadow\(""")
        val offenders = sources.flatMap { file ->
            file.readLines().mapIndexedNotNull { i, line ->
                if (elevation.containsMatchIn(line) || shadow.containsMatchIn(line)) "${file.name}:${i + 1}: ${line.trim()}" else null
            }
        }
        assertTrue(offenders.isEmpty(), "a shadow that is not the lamp's:\n" + offenders.joinToString("\n"))
    }
}
