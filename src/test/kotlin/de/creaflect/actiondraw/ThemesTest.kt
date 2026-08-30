package de.creaflect.actiondraw

import androidx.compose.ui.graphics.toPixelMap
import de.creaflect.actiondraw.board.BoardThemes
import de.creaflect.actiondraw.board.ui.Themes
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
}
