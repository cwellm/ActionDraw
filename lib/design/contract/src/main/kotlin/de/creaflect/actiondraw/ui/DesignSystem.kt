package de.creaflect.actiondraw.ui

import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * What a screen may ask of the design it is drawn in. A screen never names a design system: it
 * asks [Design] — `Design.colors.ink`, `Design.materials`, `room.pigment` — and draws with what it
 * gets, so another design system restyles it without a line of it changing. The atelier is the
 * default; the app chooses one and hands it to [DesignTheme].
 */
interface DesignSystem {
    /** The colours of the ground and of what lies on it. */
    val colors: DesignColors

    /** [room]'s pigment. */
    fun pigmentOf(room: Room): Pigment

    /** Material's palette, made from [colors], for the Material components the screens use. */
    val materialColors: Colors

    /** How everything the app says is set. */
    val typography: Typography

    /** The pencil's voice: notes, tags, a group's name. Never a button, never a number to read exactly. */
    val hand: TextStyle

    /** The grain, the lamp's shadows, and the things on the table. */
    val materials: Materials

    /** What every room has in common: the way home, the room's dab and line, its header. */
    val chrome: Chrome
}

/**
 * The colours a design paints with. The ground is what every room stands on — trays and drawers
 * and what is lifted off it are a shade or two lighter — and paper is anything drawn on or pinned,
 * with the ink on it.
 */
@Immutable
data class DesignColors(
    /** The ground of every room. */
    val ground: Color,
    /** A board, a tray, a drawer: a shade lighter than the ground. */
    val board: Color,
    /** What is lifted off it. */
    val raised: Color,
    /** A rule between things. */
    val line: Color,
    /** Text on the ground. */
    val text: Color,
    val textSoft: Color,
    val muted: Color,
    /** Paper, and its shade. */
    val paper: Color,
    val paperShade: Color,
    /** Ink on paper. */
    val ink: Color,
    val inkQuiet: Color,
    /** Text on a glow fill — a selected chip, the Start button. */
    val onGlow: Color,
)

/** The design system the content is drawn in; there is none until a [DesignTheme] gives one. */
val LocalDesign = staticCompositionLocalOf<DesignSystem> {
    error("No design system here: draw the content inside a DesignTheme (the app's, or a test's).")
}

/** The design system in use, as a screen asks for it. */
object Design {
    val colors: DesignColors
        @Composable
        @ReadOnlyComposable
        get() = LocalDesign.current.colors

    val typography: Typography
        @Composable
        @ReadOnlyComposable
        get() = LocalDesign.current.typography

    val hand: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalDesign.current.hand

    val materials: Materials
        @Composable
        @ReadOnlyComposable
        get() = LocalDesign.current.materials

    val chrome: Chrome
        @Composable
        @ReadOnlyComposable
        get() = LocalDesign.current.chrome
}

/**
 * [design] around [content]: its colours, its type, its materials and chrome, and how much the
 * content may move ([reducedMotion]). Material's components take the design's palette and type.
 */
@Composable
fun DesignTheme(design: DesignSystem, reducedMotion: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDesign provides design, LocalReducedMotion provides reducedMotion) {
        MaterialTheme(colors = design.materialColors, typography = design.typography, content = content)
    }
}
