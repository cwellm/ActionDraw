package de.creaflect.actiondraw

import de.creaflect.actiondraw.ui.EDGE_SKSL
import de.creaflect.actiondraw.ui.SILHOUETTE_SKSL
import de.creaflect.actiondraw.ui.edgeRenderEffect
import de.creaflect.actiondraw.ui.silhouetteRenderEffect
import org.jetbrains.skia.RuntimeEffect
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * SkSL is compiled lazily at runtime, so a typo would only surface when the filter is first used.
 * These tests compile the shaders up front; [RuntimeEffect.makeForShader] throws on invalid SkSL.
 */
class FiltersShaderTest {
    @Test
    fun edgeShaderCompiles() {
        RuntimeEffect.makeForShader(EDGE_SKSL).close()
    }

    @Test
    fun silhouetteShaderCompiles() {
        RuntimeEffect.makeForShader(SILHOUETTE_SKSL).close()
    }

    @Test
    fun renderEffectsBuild() {
        // Exercises ImageFilter.makeRuntimeShader + asComposeRenderEffect end to end.
        assertNotNull(edgeRenderEffect())
        assertNotNull(silhouetteRenderEffect())
    }
}
