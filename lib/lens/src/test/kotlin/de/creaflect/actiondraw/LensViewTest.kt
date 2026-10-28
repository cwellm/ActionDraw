package de.creaflect.actiondraw

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.lens.LensState
import de.creaflect.actiondraw.platform.FileDialogs
import de.creaflect.actiondraw.ui.Atelier
import de.creaflect.actiondraw.ui.Block
import de.creaflect.actiondraw.ui.Form
import de.creaflect.actiondraw.ui.Heights
import de.creaflect.actiondraw.ui.HeightsSource
import de.creaflect.actiondraw.ui.LensControls
import de.creaflect.actiondraw.ui.LensSettings
import de.creaflect.actiondraw.ui.LensTray
import de.creaflect.actiondraw.ui.LensView
import de.creaflect.actiondraw.ui.LocalDrawnOnProcessor
import de.creaflect.actiondraw.ui.Optic
import de.creaflect.actiondraw.ui.ProvideAtelier
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
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

    /** [lens] over the ramp — drawn, as every frame of a test is, on the processor. */
    private fun show(lens: LensState, veil: (@Composable () -> Unit)? = null) {
        val bitmap = ramp()
        rule.setContent {
            ProvideAtelier {
                CompositionLocalProvider(LocalDrawnOnProcessor provides true) {
                    Box(Modifier.size(400.dp, 200.dp)) { LensView(lens, bitmap, File("ramp.png"), Modifier.fillMaxSize(), veil) }
                }
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

    @Test
    fun theGlassOverTheWholePictureBendsItAboutItsCentreWhichADragMoves() {
        val lens = LensState()
        show(lens)
        val plain = middleRow()
        lens.glass.choose(Optic.CONVEX)
        lens.glass.spreadOver(0.5f, 0.5f)
        val bent = middleRow()
        // A convex glass swells its middle: left of the centre, the ramp is seen nearer the middle — lighter.
        val left = plain.size / 4
        assertTrue(bent[left].red > plain[left].red + 0.05f, "bent about the middle: ${plain[left]} -> ${bent[left]}")

        val area = rule.onNodeWithTag("reference").fetchSemanticsNode().size
        rule.onNodeWithTag("reference").performMouseInput {
            moveTo(Offset(area.width * 0.5f, area.height * 0.5f))
            press()
            repeat(20) { moveBy(Offset(-area.width * 0.25f / 20f, 0f)) }
            release()
        }
        rule.waitForIdle()
        assertEquals(0.25f, assertNotNull(lens.glass.spread).x, 0.02f, "dragged, its centre follows")
        rule.onNodeWithTag("reference").performMouseInput { rightClick(Offset(10f, 10f)) }
        rule.waitForIdle()
        assertNull(lens.glass.spread, "a right click takes it back into the hand")
        assertNull(lens.glass.focus, "and sets it down nowhere")
    }

    @Test
    fun theGlassSettingsSayWhereItIsHowLargeAndHowMuchItBends() {
        val lens = LensState()
        rule.setContent {
            ProvideAtelier { Box(Modifier.size(1100.dp, 600.dp)) { LensControls(lens, null, Room.LENS, remember { mutableStateOf(0) }) } }
        }
        rule.onNodeWithTag("glass").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("settings-glass").assertExists()
        rule.onNode(hasText("Size: 110 dp")).assertExists()
        rule.onNodeWithTag("glass-over-whole").performClick()
        rule.waitForIdle()
        assertNotNull(lens.glass.spread, "over the whole picture")
        rule.onNode(hasText("Bend: 100%")).assertExists()
        rule.onNode(hasText("Size: 110 dp")).assertDoesNotExist()
        rule.onNodeWithTag("glass-in-hand").performClick()
        rule.waitForIdle()
        assertNull(lens.glass.spread, "and back in hand")
    }

    @Test
    fun thePictureOnAFormIsDrawnOnItAndADragTurnsIt() {
        val lens = LensState()
        show(lens)
        lens.form.choose(Form.SPHERE)
        lens.form.lit = false
        rule.waitForIdle()
        val shot = rule.onNodeWithTag("reference").captureToImage().toPixelMap()
        // The top right corner: the ramp at its lightest, flat — round the ball, nothing.
        assertEquals(0f, shot[shot.width - 4, 3].red, 0.02f, "round the ball, nothing: the view's own black")
        assertTrue(shot[shot.width / 2, shot.height / 2].red > 0.05f, "on it, the picture")
        val yaw = lens.form.yaw
        val area = rule.onNodeWithTag("reference").fetchSemanticsNode().size
        rule.onNodeWithTag("reference").performMouseInput {
            moveTo(Offset(area.width * 0.4f, area.height * 0.5f))
            press()
            repeat(20) { moveBy(Offset(2f, 0f)) }
            release()
        }
        rule.waitForIdle()
        assertTrue(lens.form.yaw < yaw - 0.2f, "dragged right, its front turns right: $yaw -> ${lens.form.yaw}")
        assertNull(lens.glass.focus, "and no glass is set down by it")
    }

    @Test
    fun theFormSettingsChooseTheFormItsLightAndStraightenIt() {
        val lens = LensState()
        rule.setContent {
            ProvideAtelier { Box(Modifier.size(1100.dp, 600.dp)) { LensControls(lens, null, Room.LENS, remember { mutableStateOf(0) }) } }
        }
        rule.onNodeWithTag("knob-p").performClick()
        rule.waitForIdle()
        assertEquals(Form.SPHERE, lens.form.form, "the knob puts the picture on a ball")
        rule.onNodeWithTag("settings-form").assertExists()
        rule.onNodeWithTag("form-cone").performClick()
        rule.waitForIdle()
        assertEquals(Form.CONE, lens.form.form)
        rule.onNodeWithTag("form-lit").performClick()
        rule.waitForIdle()
        assertFalse(lens.form.lit, "the lamp off")
        val straight = lens.form.yaw to lens.form.pitch
        lens.form.turn(0.7f, 0.3f)
        rule.onNodeWithTag("form-straighten").performClick()
        rule.waitForIdle()
        assertEquals(straight, lens.form.yaw to lens.form.pitch, "and straight again")
        rule.onNodeWithTag("knob-p").performClick()
        rule.waitForIdle()
        assertNull(lens.form.form, "the knob again: the picture flat")
    }

    @Test
    fun aFormIsDrawnAsItsSettingsSay() {
        val lens = LensState()
        show(lens)
        fun shot() = rule.onNodeWithTag("reference").captureToImage().toPixelMap().let { map -> (0 until map.width step 3).flatMap { x -> (0 until map.height step 3).map { y -> map[x, y] } } }
        fun changes(what: String, change: () -> Unit) {
            val before = shot()
            change()
            rule.waitForIdle()
            val after = shot()
            assertTrue(before.indices.count { before[it] != after[it] } > 50, "$what changes what is drawn")
        }
        lens.form.choose(Form.BLOCK)
        rule.waitForIdle()
        changes("the block's shape") { lens.form.block = Block(ends = 0f, around = -1f, height = 1.6f) }
        lens.form.choose(Form.RELIEF)
        rule.waitForIdle()
        changes("the relief's depth") { lens.form.depth = 0.6f }
        val flat = ImageBitmap(40, 40).asSkiaBitmap().let { org.jetbrains.skia.Image.makeFromBitmap(it) }
        changes("the heights chosen for it") { lens.form.useHeights(Heights("flat", flat)) }
    }

    @Test
    fun aFormOnAWindowDrawnOnTheProcessorIsDrawnInBandsJustAlike() {
        val lens = LensState().apply {
            form.choose(Form.SPHERE)
            invert = true
            temperature = 0.5f
        }
        val processor = mutableStateOf(false)
        val bitmap = ramp()
        rule.setContent {
            ProvideAtelier {
                // Small: drawn at once, on the processor, every frame takes its time.
                CompositionLocalProvider(LocalDrawnOnProcessor provides processor.value) {
                    Box(Modifier.size(160.dp, 100.dp)) { LensView(lens, bitmap, File("ramp.png"), Modifier.fillMaxSize()) }
                }
            }
        }
        rule.waitForIdle()
        val once = rule.onNodeWithTag("reference").captureToImage().toPixelMap()
        processor.value = true
        rule.waitForIdle()
        val banded = rule.onNodeWithTag("reference").captureToImage().toPixelMap()
        var differ = 0
        for (y in 0 until once.height) for (x in 0 until once.width) if (once[x, y] != banded[x, y]) differ++
        assertEquals(0, differ, "every pixel alike, the lens's colours on it too")
        assertTrue(banded[banded.width / 2, banded.height / 2].red > 0.05f, "and the picture on its form")
    }

    private fun controls(lens: LensState) {
        rule.setContent {
            ProvideAtelier { Box(Modifier.size(1100.dp, 700.dp)) { LensControls(lens, null, Room.LENS, remember { mutableStateOf(0) }) } }
        }
        rule.waitForIdle()
    }

    /** The settings' sliders, from the top. */
    private fun slider(index: Int) = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))[index]

    @Test
    fun aBlocksSlidersShapeItAndAReliefIsRaisedByThePicturesOwnValues() {
        val lens = LensState()
        controls(lens)
        rule.onNodeWithTag("knob-p").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("form-block").performClick()
        rule.waitForIdle()
        assertEquals(Form.BLOCK, lens.form.form)
        rule.onNode(hasText("Ends: square 70%")).assertExists()
        slider(0).performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        slider(1).performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        slider(2).performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        slider(3).performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        rule.waitForIdle()
        assertEquals(Block(ends = 0.5f, around = 0f, height = 2f, depth = 0.5f), lens.form.block, "the four sliders: the ends, the round, the height, the depth")
        rule.onNode(hasText("Ends: pointed 50%")).assertExists()
        rule.onNode(hasText("Around: round")).assertExists()

        rule.onNodeWithTag("form-relief").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("relief-own").assertExists()
        rule.onNodeWithTag("relief-picture").assertDoesNotExist()
        slider(0).performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        rule.waitForIdle()
        assertEquals(0.5f, lens.form.depth, "and the relief's depth")
        rule.onNode(hasText("Depth: 50%")).assertExists()
    }

    @Test
    fun aReliefsHeightsCanBeAPictureChosenForThem() {
        val root = Files.createTempDirectory("relief").toFile()
        try {
            val knee = File(root, "knee.png").also { ImageIO.write(BufferedImage(60, 40, BufferedImage.TYPE_INT_RGB), "png", it) }
            val dialogs = object : FileDialogs by FileDialogs.None {
                override fun chooseFile(start: File?, title: String, description: String, accepts: (File) -> Boolean): File? = knee.takeIf(accepts)
            }
            val lens = LensState()
            lens.form.heightsFrom = HeightsSource(dialogs, FileLib(File(root, "lib")).pictures)
            lens.form.choose(Form.RELIEF)
            controls(lens)
            rule.onNodeWithTag("knob-p").performClick()
            rule.waitForIdle()
            rule.onNodeWithTag("knob-p").performClick()
            rule.waitForIdle()
            assertEquals(Form.SPHERE, lens.form.form, "the knob off and on again, onto the ball")
            rule.onNodeWithTag("form-relief").performClick()
            rule.waitForIdle()
            rule.onNodeWithTag("relief-picture").performClick()
            repeat(50) {
                rule.waitForIdle()
                if (lens.form.heights != null) return@repeat
                Thread.sleep(50)
            }
            val heights = assertNotNull(lens.form.heights, "the heights decoded off the UI thread")
            assertEquals("knee", heights.name)
            assertTrue(heights.image.width <= 384, "and made small and soft: ${heights.image.width}")
            rule.onNode(hasText("From knee")).assertExists()
            rule.onNodeWithTag("relief-own").performClick()
            rule.waitForIdle()
            assertNull(lens.form.heights, "back to the picture's own values")
        } finally {
            root.deleteRecursively()
        }
    }
}
