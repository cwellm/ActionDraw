package de.creaflect.actiondraw

import de.creaflect.actiondraw.ui.DEFRACTION_SKSL
import de.creaflect.actiondraw.ui.EDGE_SKSL
import de.creaflect.actiondraw.ui.INVERT_SKSL
import de.creaflect.actiondraw.ui.NOTAN_SKSL
import de.creaflect.actiondraw.ui.PIXELATE_SKSL
import de.creaflect.actiondraw.ui.POSTERIZE_SKSL
import de.creaflect.actiondraw.ui.SILHOUETTE_SKSL
import de.creaflect.actiondraw.ui.TEMPERATURE_SKSL
import de.creaflect.actiondraw.ui.defractionRenderEffect
import de.creaflect.actiondraw.ui.edgeRenderEffect
import de.creaflect.actiondraw.ui.grayscaleFilter
import de.creaflect.actiondraw.ui.invertRenderEffect
import de.creaflect.actiondraw.ui.notanRenderEffect
import de.creaflect.actiondraw.ui.pixelateRenderEffect
import de.creaflect.actiondraw.ui.posterizeRenderEffect
import de.creaflect.actiondraw.ui.sepiaFilter
import de.creaflect.actiondraw.ui.silhouetteRenderEffect
import de.creaflect.actiondraw.ui.squintFilter
import de.creaflect.actiondraw.ui.temperatureRenderEffect
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
        val shaders = listOf(
            EDGE_SKSL, SILHOUETTE_SKSL, POSTERIZE_SKSL, PIXELATE_SKSL,
            NOTAN_SKSL, DEFRACTION_SKSL, INVERT_SKSL, TEMPERATURE_SKSL,
        )
        for (sksl in shaders) {
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
    fun renderEffectsBuildAcrossTheParameterRanges() {
        // Uniform binding must hold for every slider extreme.
        assertNotNull(posterizeRenderEffect(levels = 2))
        assertNotNull(posterizeRenderEffect(levels = 8))
        assertNotNull(pixelateRenderEffect(block = 4))
        assertNotNull(pixelateRenderEffect(block = 48))
        assertNotNull(silhouetteRenderEffect(threshold = 0.05f))
        assertNotNull(silhouetteRenderEffect(threshold = 0.95f))
        assertNotNull(notanRenderEffect(bands = 2, threshold = 0.05f))
        assertNotNull(notanRenderEffect(bands = 3, threshold = 0.95f))
        assertNotNull(defractionRenderEffect(seed = 0f, block = 32, strength = 0.1f))
        assertNotNull(defractionRenderEffect(seed = 999f, block = 192, strength = 1f))
        assertNotNull(invertRenderEffect())
        // White balance across its whole range, including the values the slider clamps to.
        assertNotNull(temperatureRenderEffect(-1f))
        assertNotNull(temperatureRenderEffect(0f))
        assertNotNull(temperatureRenderEffect(1f))
        assertNotNull(temperatureRenderEffect(-4f), "out-of-range values are coerced, not rejected")
        assertNotNull(temperatureRenderEffect(4f))
    }

    @Test
    fun colorMatrixFiltersBuild() {
        assertNotNull(grayscaleFilter())
        assertNotNull(squintFilter())
        assertNotNull(sepiaFilter())
    }
}
