package de.creaflect.actiondraw.ui

import androidx.compose.material.Colors
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
import de.creaflect.actiondraw.Room

/**
 * The atelier (CONCEPT.md, *The design language*): a graphite table, paper with ink on it, and six
 * pigments, one per [Room] — the default design system. Screens never name it: they ask [Design],
 * and the app hands this one to [DesignTheme] ([AtelierTheme]).
 */
object Atelier : DesignSystem {
    override val colors = DesignColors(
        // The table: the ground of every room, trays and drawers, and what is lifted off it.
        ground = Color(0xFF171513),
        board = Color(0xFF211E1B),
        raised = Color(0xFF2C2824),
        line = Color(0xFF3A342E),
        text = Color(0xFFEEE7DB),
        textSoft = Color(0xFFD6CEC1),
        muted = Color(0xFFA89E90),
        // Paper: anything drawn on or pinned, and the ink on it.
        paper = Color(0xFFF3EDE2),
        paperShade = Color(0xFFE7DECF),
        ink = Color(0xFF27221D),
        inkQuiet = Color(0xFF6A6054),
        onGlow = Color(0xFF1E1A16),
    )

    private val pigments = mapOf(
        Room.PRACTICE to Pigment("Cadmium orange", Color(0xFFE0782F), Color(0xFFF5A25E), colors.ink),
        Room.LENS to Pigment("Payne's grey", Color(0xFF46525F), Color(0xFFA3B0BE), colors.paper),
        Room.BOARDS to Pigment("Ultramarine", Color(0xFF3446B8), Color(0xFF8E9EF4), colors.paper),
        // A shade deeper than the design canvas's #1F7F6E: paper text on that reached only 4.2:1.
        Room.CONCEPTS to Pigment("Viridian", Color(0xFF1A6F60), Color(0xFF5CC4AE), colors.paper),
        Room.SKETCH to Pigment("Madder rose", Color(0xFFB8324A), Color(0xFFEE8094), colors.paper),
        Room.COLLAGE to Pigment("Naples yellow", Color(0xFFD9A43A), Color(0xFFF0CB72), colors.ink),
    )

    override fun pigmentOf(room: Room): Pigment = pigments.getValue(room)

    /**
     * Material's palette, derived from the atelier, so every screen still reading
     * `MaterialTheme.colors` takes the atelier's colours. Primary stays the amber family it always
     * was (Practice's cadmium), secondary the teal family (Concepts' viridian).
     */
    override val materialColors: Colors = darkColors(
        primary = pigmentOf(Room.PRACTICE).glow,
        primaryVariant = pigmentOf(Room.PRACTICE).mass,
        secondary = pigmentOf(Room.CONCEPTS).glow,
        secondaryVariant = pigmentOf(Room.CONCEPTS).mass,
        background = colors.ground,
        surface = colors.board,
        onPrimary = colors.onGlow,
        onSecondary = colors.onGlow,
        onBackground = colors.text,
        onSurface = colors.text,
    )

    override val typography: Typography get() = AtelierType.typography

    override val hand: TextStyle get() = AtelierType.Hand

    override val materials: Materials get() = AtelierMaterials

    override val chrome: Chrome get() = AtelierChrome
}

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
fun AtelierTheme(reducedMotion: Boolean = false, content: @Composable () -> Unit) = DesignTheme(Atelier, reducedMotion, content)

/**
 * The atelier for [content] without Material's theme around it: what a test of one screen wants
 * when its layout should be the one it always had.
 */
@Composable
fun ProvideAtelier(content: @Composable () -> Unit) = CompositionLocalProvider(LocalDesign provides Atelier, content = content)
