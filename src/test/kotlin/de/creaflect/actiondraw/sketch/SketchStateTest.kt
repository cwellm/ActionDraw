package de.creaflect.actiondraw.sketch

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import de.creaflect.actiondraw.sketch.input.PenSample
import de.creaflect.actiondraw.sketch.ui.SketchScreen
import de.creaflect.sketch.Lead
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The probe's state without a pen: samples fed in as Windows would deliver them, and what the
 * page, the readouts and the recorder make of them.
 */
class SketchStateTest {
    @get:Rule
    val rule = createComposeRule()

    private fun pen(x: Float, y: Float, pressure: Float, contact: Boolean, ms: Long) = PenSample(
        x = x, y = y, pressure = pressure, tiltX = 5, tiltY = -3, rotation = 0,
        contact = contact, barrel = false, eraser = false,
        pointerType = PenSample.PointerKind.PEN, timeNanos = ms * 1_000_000L,
    )

    @Test
    fun penSamplesInContactDrawOnThePageAndAHoverEndsTheStroke() {
        val state = SketchState()
        state.ensurePage(300, 200)
        state.setLead(Lead.SOFT)
        var t = 0L
        for (x in 20..280 step 4) {
            state.onPen(pen(x.toFloat(), 100f, 1f, contact = true, ms = t))
            t += 5
        }
        val page = assertNotNull(state.page)
        assertTrue(page.darkness(150, 100) > 0.8f, "the line is there: ${page.darkness(150, 100)}")
        assertTrue(page.darkness(150, 40) < 0.02f, "and nothing above it")
        assertEquals(66, state.sampleCount)
        assertTrue(state.rateHz > 100, "200 Hz of samples read as ${state.rateHz} Hz")

        state.onPen(pen(280f, 100f, 0f, contact = false, ms = t))
        state.onPen(pen(20f, 150f, 1f, contact = true, ms = t + 5))
        // A new stroke starts exactly where the pen came down: a fresh builder, a fresh filter,
        // a dot at the point. Were the old stroke still open, the filter would lag the jump and
        // leave a short segment by the old point instead — nothing here.
        assertTrue(page.darkness(20, 150) > 0.5f, "the new stroke starts under the pen: ${page.darkness(20, 150)}")
    }

    @Test
    fun theMouseDrawsAtFullPressureUnlessAPenIsOnThePage() {
        val state = SketchState()
        state.ensurePage(300, 200)
        state.onMouse(20f, 100f, 0L)
        state.onMouse(280f, 100f, 20_000_000L)
        state.endStroke()
        assertTrue(state.page!!.darkness(150, 100) > 0.5f, "the mouse draws")

        state.onPen(pen(20f, 50f, 1f, contact = true, ms = 100))
        state.onMouse(280f, 50f, 120_000_000L) // the same movement, promoted to a mouse event: ignored
        assertTrue(state.page!!.darkness(150, 50) < 0.02f, "a pen in contact owns the stroke")
    }

    @Test
    fun samplesAreWindowRelativeAndThePageKnowsWhereItSits() {
        val state = SketchState()
        state.ensurePage(300, 200)
        state.pageOrigin = Offset(100f, 50f)
        var t = 0L
        for (x in 120..380 step 4) {
            state.onPen(pen(x.toFloat(), 150f, 1f, contact = true, ms = t))
            t += 5
        }
        assertTrue(state.page!!.darkness(150, 100) > 0.5f, "drawn at the page's own coordinates")
    }

    @Test
    fun recordingWritesEverySampleToTheFile() {
        val dir = Files.createTempDirectory("pen").toFile()
        try {
            val state = SketchState()
            state.ensurePage(100, 100)
            val file = File(dir, "samples.csv")
            state.toggleRecording(file)
            state.onPen(pen(10f, 10f, 0.5f, contact = true, ms = 0))
            state.onPen(pen(12f, 10f, 0.6f, contact = true, ms = 5))
            state.toggleRecording(file)
            state.onPen(pen(14f, 10f, 0.7f, contact = true, ms = 10)) // not recorded
            val lines = file.readLines()
            assertEquals(3, lines.size, "a header and two samples: $lines")
            assertTrue(lines[1].startsWith("0,10.0,10.0,0.5,5,-3,0,true"), lines[1])
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun theScreenShowsTheStatusAndGivesThePageItsSize() {
        val state = SketchState()
        rule.setContent { SketchScreen(state, onBack = {}) }
        rule.waitForIdle()
        rule.onNodeWithTag("sketch-status").assertExists()
        rule.onNodeWithTag("sketch-readout").assertExists()
        rule.onNodeWithTag("sketch-page").assertExists()
        val page = assertNotNull(state.page, "the page took the area's size")
        assertTrue(page.width > 100 && page.height > 100)
    }
}
