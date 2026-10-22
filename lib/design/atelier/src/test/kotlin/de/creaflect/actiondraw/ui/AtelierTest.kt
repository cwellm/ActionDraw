package de.creaflect.actiondraw.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The atelier's colours read as text wherever the design puts text on them (WCAG 2: 4.5:1), and
 * the bundled fonts are the cuts they claim to be.
 */
class AtelierTest {
    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun hex(c: Color) = "#%06X".format(c.value.toLong().ushr(32).toInt() and 0xFFFFFF)

    /** Every (text, ground) pair at 4.5:1 or better; the message names each one that is not. */
    private fun assertReadable(pairs: List<Triple<String, Color, Color>>) {
        val failing = pairs.map { (what, fg, bg) -> Triple(what, "${hex(fg)} on ${hex(bg)}", contrast(fg, bg)) }
            .filter { it.third < 4.5 }
        assertTrue(failing.isEmpty(), failing.joinToString("\n") { "${it.first}: ${it.second} = %.2f:1".format(it.third) })
    }

    private val table = listOf(Atelier.colors.ground, Atelier.colors.board, Atelier.colors.raised)

    @Test
    fun textReadsOnTheTable() = assertReadable(
        listOf(Atelier.colors.text, Atelier.colors.textSoft, Atelier.colors.muted).flatMap { fg -> table.map { Triple("text", fg, it) } },
    )

    @Test
    fun inkReadsOnPaper() = assertReadable(
        listOf(Atelier.colors.ink, Atelier.colors.inkQuiet).flatMap { fg ->
            listOf(Atelier.colors.paper, Atelier.colors.paperShade).map { Triple("ink", fg, it) }
        },
    )

    @Test
    fun everyGlowReadsOnTheTable() = assertReadable(
        Room.entries.flatMap { room -> table.map { Triple("${room.label} glow", Atelier.pigmentOf(room).glow, it) } },
    )

    @Test
    fun textReadsOnEveryGlowAndMass() = assertReadable(
        Room.entries.flatMap { room ->
            listOf(
                Triple("on ${room.label} glow", Atelier.colors.onGlow, Atelier.pigmentOf(room).glow),
                Triple("on ${room.label} mass", Atelier.pigmentOf(room).onMass, Atelier.pigmentOf(room).mass),
            )
        },
    )

    @Test
    fun materialPalettesRead() = assertReadable(
        listOf(
            Triple("on surface", Atelier.materialColors.onSurface, Atelier.materialColors.surface),
            Triple("on background", Atelier.materialColors.onBackground, Atelier.materialColors.background),
            Triple("on primary", Atelier.materialColors.onPrimary, Atelier.materialColors.primary),
            Triple("on secondary", Atelier.materialColors.onSecondary, Atelier.materialColors.secondary),
            Triple("primary on surface", Atelier.materialColors.primary, Atelier.materialColors.surface),
        ),
    )

    /** The static cuts load, and each one carries the weight it is filed under. */
    @Test
    fun bundledFontsAreTheCutsTheyClaim() {
        val cuts = mapOf(
            "BricolageGrotesque-Regular.ttf" to FontWeight.Normal,
            "BricolageGrotesque-Medium.ttf" to FontWeight.Medium,
            "BricolageGrotesque-SemiBold.ttf" to FontWeight.SemiBold,
            "BricolageGrotesque-Bold.ttf" to FontWeight.Bold,
            "BricolageGrotesque-Display-ExtraBold.ttf" to FontWeight.ExtraBold,
            "Caveat-Medium.ttf" to FontWeight.Medium,
            "Caveat-Bold.ttf" to FontWeight.Bold,
        )
        for ((file, weight) in cuts) {
            val bytes = javaClass.classLoader.getResourceAsStream("fonts/$file")!!.use { it.readBytes() }
            val face = FontMgr.default.makeFromData(Data.makeFromBytes(bytes))!!
            assertEquals(weight.weight, face.fontStyle.weight, "$file is filed as ${weight.weight}")
            assertTrue(face.variationAxes.isNullOrEmpty(), "$file is still variable")
        }
        // The families are what the typography uses.
        assertEquals(AtelierType.Bricolage, AtelierType.typography.body1.fontFamily)
        assertEquals(AtelierType.BricolageDisplay, AtelierType.typography.h3.fontFamily)
    }
}
