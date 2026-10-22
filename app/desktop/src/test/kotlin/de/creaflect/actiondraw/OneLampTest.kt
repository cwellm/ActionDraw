package de.creaflect.actiondraw

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * One lamp (CONCEPT.md, *Materials*; F9.2): every shadow in the app is the lamp's — `Lift` and
 * `lampShadow`, cast to the bottom right — never Material's elevation or `Modifier.shadow`, which
 * falls straight down and would put a second light in the room. Read off the sources, so a new
 * screen cannot bring its own shadow back in unnoticed.
 */
class OneLampTest {
    @Test
    fun everyShadowIsTheLamps() {
        val sources = File("src/main/kotlin").walk().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(sources.size > 50, "the sources were found: ${sources.size}")
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
