package de.creaflect.actiondraw

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.lens.LensState
import de.creaflect.actiondraw.ui.Atelier
import de.creaflect.actiondraw.ui.LensSettings
import de.creaflect.actiondraw.ui.LensTray
import de.creaflect.actiondraw.ui.LensView
import de.creaflect.actiondraw.ui.ProvideAtelier
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The lens kit on its own, with no room around it: the picture seen through the lens and the
 * knobs, a veil in its place, and the tray lit in the pigment of the room it lies in.
 */
@OptIn(ExperimentalTestApi::class)
class LensViewTest {
    @get:Rule
    val rule = createComposeRule()

    /** A 200 × 100 picture, black at its left edge to white at its right. */
    private fun ramp(): ImageBitmap {
        val image = BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until 200) {
            val v = x * 255 / 199
            for (y in 0 until 100) image.setRGB(x, y, (v shl 16) or (v shl 8) or v)
        }
        return image.toComposeImageBitmap()
    }

    private fun show(lens: LensState, veil: (@Composable () -> Unit)? = null) {
        val bitmap = ramp()
        rule.setContent {
            ProvideAtelier {
                Box(Modifier.size(400.dp, 200.dp)) { LensView(lens, bitmap, File("ramp.png"), Modifier.fillMaxSize(), veil) }
            }
        }
        rule.waitForIdle()
    }

    /** The picture as shown, along its middle row, from its left to its right. */
    private fun middleRow(): List<Color> {
        rule.waitForIdle()
        val shot = rule.onNodeWithTag("reference").captureToImage().toPixelMap()
        val y = shot.height / 2
        return (shot.width / 10 until shot.width * 9 / 10 step 8).map { shot[it, y] }
    }

    @Test
    fun theLensIsWhatThePictureIsSeenThrough() {
        val lens = LensState()
        show(lens)
        assertTrue(middleRow().count { it.red in 0.3f..0.7f } > 5, "without a lens: the ramp's greys")
        lens.viewMode = ViewMode.SILHOUETTE
        assertTrue(middleRow().all { it.red < 0.05f || it.red > 0.95f }, "a silhouette: black and white, nothing between")
    }

    @Test
    fun theKnobsWorkOnThePicture() {
        val lens = LensState()
        show(lens)
        assertTrue(middleRow().first().red < 0.2f, "dark at the left")
        lens.invert = true
        assertTrue(middleRow().first().red > 0.8f, "inverted: light at the left")
        lens.invert = false
        lens.mirror = true
        assertTrue(middleRow().first().red > 0.8f, "mirrored: the light end on the left")
    }

    @Test
    fun aVeilStandsInThePicturesPlaceAndTheGlassFindsNothingUnderIt() {
        val lens = LensState()
        lens.glass.takeUp()
        show(lens, veil = { Text("veiled", Modifier.testTag("veil")) })
        rule.onNodeWithTag("reference").assertDoesNotExist()
        rule.onNodeWithTag("veil").performMouseInput { click(center) }
        rule.waitForIdle()
        assertNull(lens.glass.focus, "nothing under the veil to set the glass down on")
    }

    @Test
    fun theTrayIsLitInThePigmentOfTheRoomItLiesIn() {
        val lens = LensState().apply { blur = true }
        rule.setContent {
            ProvideAtelier { LensTray(lens, null, remember { mutableStateOf<LensSettings?>(null) }, Room.LENS) { _, _ -> } }
        }
        rule.waitForIdle()
        // The blur knob is on: its round, beside the glyph in its middle.
        val shot = rule.onNodeWithTag("knob-b").captureToImage().toPixelMap()
        val d = rule.density.density
        val lit = shot[(9 * d).toInt(), (20 * d).toInt()]
        val glow = Atelier.pigmentOf(Room.LENS).glow
        assertNotEquals(Atelier.pigmentOf(Room.PRACTICE).glow, glow)
        assertTrue(abs(lit.red - glow.red) < 0.04f && abs(lit.green - glow.green) < 0.04f && abs(lit.blue - glow.blue) < 0.04f, "the Lens room's pigment: $lit, not $glow")

        rule.onNodeWithTag("lens-notan").performClick()
        rule.waitForIdle()
        assertEquals(ViewMode.NOTAN, lens.viewMode, "and a slide chooses its lens")
    }
}
