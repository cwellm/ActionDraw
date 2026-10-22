package de.creaflect.actiondraw

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import de.creaflect.actiondraw.board.ui.Themes
import de.creaflect.actiondraw.ui.Atelier
import de.creaflect.actiondraw.ui.AtelierColors
import de.creaflect.actiondraw.ui.AtelierType
import de.creaflect.actiondraw.ui.Room
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

    private val table = listOf(Atelier.Ground, Atelier.Board, Atelier.Raised)

    @Test
    fun textReadsOnTheTable() = assertReadable(
        listOf(Atelier.Text, Atelier.TextSoft, Atelier.Muted).flatMap { fg -> table.map { Triple("text", fg, it) } },
    )

    @Test
    fun inkReadsOnPaper() = assertReadable(
        listOf(Atelier.Ink, Atelier.InkQuiet).flatMap { fg ->
            listOf(Atelier.Paper, Atelier.PaperShade).map { Triple("ink", fg, it) }
        },
    )

    @Test
    fun everyGlowReadsOnTheTable() = assertReadable(
        Room.entries.flatMap { room -> table.map { Triple("${room.label} glow", room.pigment.glow, it) } },
    )

    @Test
    fun textReadsOnEveryGlowAndMass() = assertReadable(
        Room.entries.flatMap { room ->
            listOf(
                Triple("on ${room.label} glow", Atelier.OnGlow, room.pigment.glow),
                Triple("on ${room.label} mass", room.pigment.onMass, room.pigment.mass),
            )
        },
    )

    @Test
    fun materialPalettesRead() = assertReadable(
        listOf(
            Triple("on surface", AtelierColors.onSurface, AtelierColors.surface),
            Triple("on background", AtelierColors.onBackground, AtelierColors.background),
            Triple("on primary", AtelierColors.onPrimary, AtelierColors.primary),
            Triple("on secondary", AtelierColors.onSecondary, AtelierColors.secondary),
            Triple("primary on surface", AtelierColors.primary, AtelierColors.surface),
            Triple("paper: on surface", Themes.paperColors.onSurface, Themes.paperColors.surface),
            Triple("paper: on background", Themes.paperColors.onBackground, Themes.paperColors.background),
            Triple("paper: primary on surface", Themes.paperColors.primary, Themes.paperColors.surface),
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
