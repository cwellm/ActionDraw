package de.creaflect.actiondraw

import de.creaflect.actiondraw.ui.EDGE_SKSL
import de.creaflect.actiondraw.ui.PIXELATE_SKSL
import de.creaflect.actiondraw.ui.POSTERIZE_SKSL
import de.creaflect.actiondraw.ui.SILHOUETTE_SKSL
import de.creaflect.actiondraw.ui.coolFilter
import de.creaflect.actiondraw.ui.edgeRenderEffect
import de.creaflect.actiondraw.ui.grayscaleFilter
import de.creaflect.actiondraw.ui.pixelateRenderEffect
import de.creaflect.actiondraw.ui.posterizeRenderEffect
import de.creaflect.actiondraw.ui.sepiaFilter
import de.creaflect.actiondraw.ui.silhouetteRenderEffect
import de.creaflect.actiondraw.ui.squintFilter
import de.creaflect.actiondraw.ui.warmFilter
import org.jetbrains.skia.RuntimeEffect
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * SkSL is compiled lazily at runtime, so a typo would only surface when the filter is first used.
 * These tests compile the shaders up front; [RuntimeEffect.makeForShader] throws on invalid SkSL.
 */
class FiltersShaderTest {
    @Test
    fun allShadersCompile() {
        for (sksl in listOf(EDGE_SKSL, SILHOUETTE_SKSL, POSTERIZE_SKSL, PIXELATE_SKSL)) {
            RuntimeEffect.makeForShader(sksl).close()
        }
    }

    @Test
    fun renderEffectsBuild() {
        // Exercises ImageFilter.makeRuntimeShader + asComposeRenderEffect end to end.
        assertNotNull(edgeRenderEffect())
        assertNotNull(silhouetteRenderEffect())
        assertNotNull(posterizeRenderEffect())
        assertNotNull(pixelateRenderEffect())
    }

    @Test
    fun colorMatrixFiltersBuild() {
        assertNotNull(grayscaleFilter())
        assertNotNull(squintFilter())
        assertNotNull(sepiaFilter())
        assertNotNull(warmFilter())
        assertNotNull(coolFilter())
    }
}
