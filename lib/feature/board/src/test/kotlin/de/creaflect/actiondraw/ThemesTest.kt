package de.creaflect.actiondraw

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import de.creaflect.actiondraw.board.BoardThemes
import de.creaflect.actiondraw.board.ui.Themes
import de.creaflect.actiondraw.ui.Atelier
import org.jetbrains.skia.RuntimeEffect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The texture SkSL compiles and produces an actual (non-constant) tile; plain stays untextured. */
class ThemesTest {
    @Test
    fun textureShadersCompile() {
        RuntimeEffect.makeForShader(Themes.CORK_SKSL).close()
        RuntimeEffect.makeForShader(Themes.PAPYRUS_SKSL).close()
    }

    @Test
    fun corkAndPapyrusRenderVariedTiles() {
        for (theme in listOf(BoardThemes.CORK, BoardThemes.PAPYRUS)) {
            val tile = Themes.tile(theme)
            assertNotNull(tile, "no tile for $theme")
            assertEquals(256, tile.width)
            val pixels = tile.toPixelMap()
            val samples = buildSet {
                for (x in 0 until 256 step 32) for (y in 0 until 256 step 32) add(pixels[x, y])
            }
            assertTrue(samples.size > 4, "$theme tile looks constant (${samples.size} distinct colours)")
        }
    }

    @Test
    fun plainHasNoTile() {
        assertNull(Themes.tile(BoardThemes.PLAIN))
    }

    /** A paper board's palette, made from the atelier's paper and ink, reads as text (WCAG 2: 4.5:1). */
    @Test
    fun aPaperBoardsPaletteReads() {
        fun contrast(a: Color, b: Color): Double {
            val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
            return (hi + 0.05) / (lo + 0.05)
        }
        val paper = Themes.paperColors(Atelier.colors)
        for ((what, fg, bg) in listOf(
            Triple("on surface", paper.onSurface, paper.surface),
            Triple("on background", paper.onBackground, paper.background),
            Triple("primary on surface", paper.primary, paper.surface),
        )) {
            assertTrue(contrast(fg, bg) >= 4.5, "paper: $what is %.2f:1".format(contrast(fg, bg)))
        }
    }
}
