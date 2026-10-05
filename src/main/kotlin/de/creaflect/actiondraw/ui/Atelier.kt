package de.creaflect.actiondraw.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.sp

/**
 * The atelier's colours (CONCEPT.md, *The design language*): a graphite table, paper with ink on
 * it, and six pigments, one per [Room]. Every colour the app draws with should come from here.
 */
object Atelier {
    // The table: the ground of every room, trays and drawers, and what is lifted off it.
    val Ground = Color(0xFF171513)
    val Board = Color(0xFF211E1B)
    val Raised = Color(0xFF2C2824)
    val Line = Color(0xFF3A342E)
    val Text = Color(0xFFEEE7DB)
    val TextSoft = Color(0xFFD6CEC1)
    val Muted = Color(0xFFA89E90)

    // Paper: anything drawn on or pinned, and the ink on it.
    val Paper = Color(0xFFF3EDE2)
    val PaperShade = Color(0xFFE7DECF)
    val Ink = Color(0xFF27221D)
    val InkQuiet = Color(0xFF6A6054)

    /** Text on a glow fill — a selected chip, the Start button. */
    val OnGlow = Color(0xFF1E1A16)
}

/**
 * A pigment: [mass] is the paint on paper and porcelain, [glow] the same pigment on graphite,
 * light enough to be read as text there; [onMass] is the text colour for a label on the mass.
 */
data class Pigment(val name: String, val mass: Color, val glow: Color, val onMass: Color)

/**
 * The six rooms of the palette. A room's pigment marks what is held, selected or running in it —
 * sprinkled, never a whole surface — and a button leading into a room carries its dab.
 */
enum class Room(val label: String, val pigment: Pigment) {
    PRACTICE("Practice", Pigment("Cadmium orange", Color(0xFFE0782F), Color(0xFFF5A25E), Atelier.Ink)),
    LENS("Lens", Pigment("Payne's grey", Color(0xFF46525F), Color(0xFFA3B0BE), Atelier.Paper)),
    BOARDS("Boards", Pigment("Ultramarine", Color(0xFF3446B8), Color(0xFF8E9EF4), Atelier.Paper)),
    // A shade deeper than the design canvas's #1F7F6E: paper text on that reached only 4.2:1.
    CONCEPTS("Concepts", Pigment("Viridian", Color(0xFF1A6F60), Color(0xFF5CC4AE), Atelier.Paper)),
    SKETCH("Sketch", Pigment("Madder rose", Color(0xFFB8324A), Color(0xFFEE8094), Atelier.Paper)),
    COLLAGE("Collage", Pigment("Naples yellow", Color(0xFFD9A43A), Color(0xFFF0CB72), Atelier.Ink)),
}

/**
 * Material's palette, derived from the atelier, so every screen still reading
 * `MaterialTheme.colors` takes the new colours at once. Primary stays the amber family it always
 * was (Practice's cadmium), secondary the teal family (Concepts' viridian).
 */
val AtelierColors = darkColors(
    primary = Room.PRACTICE.pigment.glow,
    primaryVariant = Room.PRACTICE.pigment.mass,
    secondary = Room.CONCEPTS.pigment.glow,
    secondaryVariant = Room.CONCEPTS.pigment.mass,
    background = Atelier.Ground,
    surface = Atelier.Board,
    onPrimary = Atelier.OnGlow,
    onSecondary = Atelier.OnGlow,
    onBackground = Atelier.Text,
    onSurface = Atelier.Text,
)

/**
 * One typeface and one hand. Bricolage Grotesque sets everything the app says; its display cut
 * (a high optical size, extra bold) is for large titles only. Caveat letters what the person
 * wrote. The files are static cuts of the variable fonts in `art/fonts/` (see the script there):
 * Compose Desktop cannot set a variable font's axes.
 */
object AtelierType {
    val Bricolage = FontFamily(
        Font("fonts/BricolageGrotesque-Regular.ttf", FontWeight.Normal),
        Font("fonts/BricolageGrotesque-Medium.ttf", FontWeight.Medium),
        Font("fonts/BricolageGrotesque-SemiBold.ttf", FontWeight.SemiBold),
        Font("fonts/BricolageGrotesque-Bold.ttf", FontWeight.Bold),
    )
    val BricolageDisplay = FontFamily(Font("fonts/BricolageGrotesque-Display-ExtraBold.ttf", FontWeight.ExtraBold))
    val Caveat = FontFamily(
        Font("fonts/Caveat-Medium.ttf", FontWeight.Medium),
        Font("fonts/Caveat-Bold.ttf", FontWeight.Bold),
    )

    val typography: Typography = Typography(defaultFontFamily = Bricolage).let { t ->
        val display = TextStyle(fontFamily = BricolageDisplay, fontWeight = FontWeight.ExtraBold)
        t.copy(
            h1 = t.h1.merge(display).copy(letterSpacing = (-1.5).sp),
            h2 = t.h2.merge(display).copy(letterSpacing = (-1).sp),
            h3 = t.h3.merge(display).copy(letterSpacing = (-0.5).sp),
            h4 = t.h4.merge(display).copy(letterSpacing = (-0.25).sp),
            h5 = t.h5.copy(fontWeight = FontWeight.Bold),
            h6 = t.h6.copy(fontWeight = FontWeight.SemiBold),
            subtitle2 = t.subtitle2.copy(fontWeight = FontWeight.SemiBold),
            // Material's 1.25 sp tracking is for upper-case Roboto; on Bricolage in sentence case
            // it spread the menu's session chips past the row's width.
            button = t.button.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp),
        )
    }

    /** The pencil's voice: notes, tags, a group's name. Never a button, never a number to read exactly. */
    val Hand = TextStyle(fontFamily = Caveat, fontWeight = FontWeight.Medium, fontSize = 22.sp)
}

/** The atelier around a window's content: its colours, its type, and how much it may move. */
@Composable
fun AtelierTheme(reducedMotion: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        MaterialTheme(colors = AtelierColors, typography = AtelierType.typography, content = content)
    }
}
