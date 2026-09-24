package de.creaflect.actiondraw.sketch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import de.creaflect.actiondraw.sketch.ui.ColorPicker
import androidx.compose.ui.unit.IntSize
import de.creaflect.actiondraw.Settings
import de.creaflect.actiondraw.board.ConceptRef
import de.creaflect.actiondraw.image.ThumbCache
import de.creaflect.actiondraw.sketch.input.PenSample
import de.creaflect.actiondraw.sketch.ui.SketchDialogs
import de.creaflect.actiondraw.sketch.ui.SketchScreen
import de.creaflect.sketch.Lead
import de.creaflect.sketch.PageSize
import de.creaflect.sketch.Paper
import de.creaflect.sketch.Pencils
import de.creaflect.sketch.SketchDocument
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Live Sketch's state without a window: the page and the view, drawing by mouse and by pen
 * samples fed in as Windows would deliver them, undo, the keys, and every way of saving and
 * opening again — a file, a board's folder, a concept's folder.
 */
class SketchStateTest {
    @get:Rule
    val rule = createComposeRule()

    private val home: File = Files.createTempDirectory("sketch-home").toFile()
    private val config: File = Files.createTempDirectory("sketch-cfg").toFile()
    private val boardDir: File = Files.createTempDirectory("sketch-board").toFile()
    private val conceptDir: File = Files.createTempDirectory("sketch-concept").toFile()
    private val added = mutableListOf<Pair<String, List<File>>>()
    private var left = 0

    private val host = object : SketchHost {
        override fun boards() = listOf("Buch" to boardDir)
        override fun addToBoard(dir: File, pictures: List<File>): String {
            added += "board:" + dir.name to pictures
            return "Pinned ${pictures.size} to Buch."
        }
        override fun concepts() = listOf(ConceptRef("c1", "Drache", "creature"))
        override fun conceptDir(id: String) = if (id == "c1") conceptDir else null
        override fun addToConcept(id: String, pictures: List<File>): Boolean {
            added += "concept:$id" to pictures
            return true
        }
        override fun leaveSketch() { left++ }
    }

    @After
    fun cleanup() {
        listOf(home, config, boardDir, conceptDir).forEach { it.deleteRecursively() }
    }

    private fun newState(): SketchState =
        SketchState(Settings(config), host, timestamp = { "2026-09-22 14.05" }).also { it.setSketchesHomeDir(home) }

    /** A state with a small page, seen through a 400 × 300 view at zoom 1 with the page at (20, 20). */
    private fun ready(): SketchState {
        val state = newState()
        state.newSketch(PageSize.pixels(300, 200))
        state.viewResized(IntSize(400, 300))
        state.zoomBy(1f / state.zoom, 0f, 0f) // zoom 1
        state.pan(20f - state.panX, 20f - state.panY)
        return state
    }

    private fun SketchState.darkness(x: Int, y: Int) = session!!.surface.darkness(x, y)

    /**
     * A mouse line across the page at [y]. The mouse presses at one fixed, middling pressure, so
     * this is a medium-grey line, not the lead's darkest: `> 0.3` is a line, `< 0.02` is paper.
     */
    private fun SketchState.mouseLine(y: Float, startNanos: Long = 10_000_000_000L) {
        var t = startNanos
        mouseDown(20f + 10f, 20f + y, t)
        for (x in 10..290 step 4) {
            t += 5_000_000L
            mouseMove(20f + x, 20f + y, t)
        }
        mouseUp()
    }

    private fun pen(x: Float, y: Float, contact: Boolean, ms: Long, origin: Offset = Offset.Zero) = PenSample(
        x = origin.x + 20f + x, y = origin.y + 20f + y, pressure = 0.9f, tiltX = 0, tiltY = 0, rotation = 0,
        contact = contact, barrel = false, eraser = false, pointerType = PenSample.PointerKind.PEN, timeNanos = ms * 1_000_000L,
    )

    // ---- The page and the view ----

    @Test
    fun enteringGivesAPageAtOnceFittedIntoTheView() {
        val state = newState()
        state.viewResized(IntSize(800, 600))
        state.ensurePage()
        val s = assertNotNull(state.session, "a page without asking")
        assertEquals(1240 to 1754, s.width to s.height, "A4 at 150 dpi")
        assertEquals("A4 150 dpi", state.pageName)
        assertTrue(state.zoom < 1f && state.zoom > 0.2f, "fitted: ${state.zoom}")
        assertTrue(state.panX >= 0f && state.panY >= 0f, "and centred: ${state.panX}, ${state.panY}")
        assertFalse(state.dirty)

        state.newSketch(PageSize.a5(300).landscape)
        state.newSketch() // nothing drawn, so nothing to ask about
        assertEquals(PageSize.a5(300).landscape.width, state.session!!.width, "a new page is the size chosen last")
    }

    @Test
    fun theMouseDrawsInPageCoordinatesAndATapLeavesADot() {
        val state = ready()
        state.mouseLine(100f)
        assertTrue(state.darkness(150, 100) > 0.3f, "a line at page y=100: ${state.darkness(150, 100)}")
        assertTrue(state.dirty)

        state.mouseDown(20f + 60f, 20f + 160f)
        state.mouseUp()
        assertTrue(state.darkness(60, 160) > 0.1f, "a press without a move is a dot: ${state.darkness(60, 160)}")
        assertEquals(2, state.session!!.strokeCount)

        state.zoomBy(2f, 20f, 20f)
        assertEquals(Offset(50f, 50f), state.toPage(120f, 120f), "zoomed about the page corner")
    }

    @Test
    fun penSamplesDrawWhileInContactInPageSpace() {
        val state = ready()
        val origin = Offset(100f, 50f)
        state.viewOrigin = origin // the view sits inside the window; samples are window-relative
        var t = 0L
        for (x in 10..290 step 4) {
            state.onPen(pen(x.toFloat(), 60f, contact = true, ms = t, origin = origin))
            t += 5
        }
        assertTrue(state.darkness(150, 60) > 0.5f, "drawn where the pen was: ${state.darkness(150, 60)}")
        state.onPen(pen(290f, 60f, contact = false, ms = t, origin = origin))
        assertEquals(1, state.session!!.strokeCount, "lifting the pen ends the stroke")
    }

    @Test
    fun aPenOnOrNearThePageSilencesTheMouseItAlsoBecomes() {
        val state = ready()
        // The pen hovers: Windows sends samples before the tip touches.
        state.onPen(pen(150f, 150f, contact = false, ms = 1_000))
        state.mouseDown(20f + 10f, 20f + 150f, 1_050_000_000L)
        state.mouseMove(20f + 250f, 20f + 150f, 1_100_000_000L)
        state.mouseUp()
        assertTrue(state.darkness(150, 150) < 0.02f, "the promoted mouse events drew nothing")
        assertEquals(0, state.session!!.strokeCount)

        // A second later the pen is gone and the mouse is the mouse again.
        state.mouseLine(100f, startNanos = 3_000_000_000L)
        assertTrue(state.darkness(150, 100) > 0.3f)
    }

    @Test
    fun aMouseStrokeBegunBeforeThePensContactIsTakenBack() {
        val state = ready()
        state.mouseLine(40f) // a real stroke, kept
        // The promoted mouse press arrives first, then the pen's own contact.
        state.mouseDown(20f + 10f, 20f + 120f, 20_000_000_000L)
        state.mouseMove(20f + 200f, 20f + 120f, 20_010_000_000L)
        assertTrue(state.darkness(100, 120) > 0.1f, "on the page for a moment: ${state.darkness(100, 120)}")
        var t = 20_020L
        for (x in 10..100 step 4) {
            state.onPen(pen(x.toFloat(), 160f, contact = true, ms = t))
            t += 5
        }
        state.onPen(pen(100f, 160f, contact = false, ms = t))
        assertTrue(state.darkness(150, 120) < 0.02f, "the mouse's version is gone: ${state.darkness(150, 120)}")
        assertTrue(state.darkness(50, 160) > 0.3f, "the pen's is there")
        assertTrue(state.darkness(150, 40) > 0.3f, "and the earlier stroke untouched")
        assertEquals(2, state.session!!.strokeCount)
    }

    @Test
    fun undoAndRedoAndTheBrush() {
        val state = ready()
        state.mouseLine(100f)
        assertTrue(state.canUndo)
        state.undo()
        assertTrue(state.darkness(150, 100) < 0.02f)
        assertTrue(state.canRedo)
        state.redo()
        assertTrue(state.darkness(150, 100) > 0.3f)

        state.setLead(Lead.SOFT)
        state.toggleEraser()
        assertTrue(state.eraser)
        state.setLead(Lead.HARD)
        assertFalse(state.eraser, "picking a lead puts the eraser down")
        state.setColor(0x123456)
        assertEquals(0xFF123456.toInt(), state.brush.color)
        assertEquals(0xFF123456.toInt(), state.recentColors.first())
        state.setSize(200f)
        assertEquals(80f, state.brush.size, "sizes are bounded")
    }

    // ---- Saving and opening ----

    @Test
    fun saveAsWritesThePictureAndTheDocumentAndOpenBringsItBack() {
        val state = ready()
        state.mouseLine(100f)
        assertEquals("Sketch 2026-09-22 14.05", state.suggestedName(), "a first save is offered a dated name")
        assertNull(state.saveAs("Drache Skizze"))
        val png = File(home, "Drache Skizze.png")
        val json = File(home, "Drache Skizze" + SketchDocument.FILE_SUFFIX)
        assertTrue(png.isFile && json.isFile, "both files in the sketches home")
        assertFalse(state.dirty, "saved")
        assertEquals("Drache Skizze", state.title)
        assertEquals(listOf(json), state.recentSketches())

        state.mouseLine(150f)
        assertNull(state.save(), "Ctrl+S saves to the same file")
        assertEquals(2, SketchDocument.fromJson(json.readText()).strokes.size)

        val fresh = newState()
        fresh.viewResized(IntSize(400, 300))
        assertTrue(fresh.open(json))
        assertEquals(2, fresh.session!!.strokeCount)
        assertEquals("Drache Skizze", fresh.title)
        assertTrue(fresh.session!!.surface.darkness(150, 100) > 0.3f, "the picture is back")
        assertTrue(fresh.canUndo, "and its strokes can be undone")
    }

    @Test
    fun saveAsRefusesToOverwriteAnotherSketch() {
        val state = ready()
        state.mouseLine(100f)
        assertNull(state.saveAs("Eins"))
        state.newSketch(PageSize.pixels(300, 200))
        state.mouseLine(50f)
        val problem = state.saveAs("Eins")
        assertNotNull(problem, "another sketch's name is not taken over")
        assertEquals(1, SketchDocument.fromJson(File(home, "Eins" + SketchDocument.FILE_SUFFIX).readText()).strokes.size, "the first is intact")
        assertNull(state.saveAs("Zwei"))
        assertNull(state.saveAs("Zwei"), "its own name again is fine")
    }

    @Test
    fun withoutAFileSaveAsksForOne() {
        val state = ready()
        state.mouseLine(100f)
        assertNull(state.save())
        assertEquals(SketchEditor.SaveAs, state.editor)
    }

    @Test
    fun savingToABoardPutsTheFilesInItsFolderAndCtrlSKeepsToThatFile() {
        val state = ready()
        state.mouseLine(100f)
        val line = state.saveToBoard(boardDir)
        assertTrue(line.startsWith("Pinned"), line)
        val name = "Sketch 2026-09-22 14.05"
        val png = File(boardDir, "$name.png")
        assertTrue(png.isFile && File(boardDir, name + SketchDocument.FILE_SUFFIX).isFile)
        assertEquals(listOf("board:" + boardDir.name to listOf(png)), added)
        assertEquals(name, state.title)
        assertFalse(state.dirty)

        // Drawing on and saving: the same files on the board, not a stray "Untitled".
        state.mouseLine(150f)
        assertNull(state.save())
        assertEquals(2, SketchDocument.fromJson(File(boardDir, name + SketchDocument.FILE_SUFFIX).readText()).strokes.size)
        assertEquals(2, boardDir.listFiles()!!.size, "one picture, one document")

        // To the same board again: saved in place, not copied.
        state.saveToBoard(boardDir)
        assertEquals(2, boardDir.listFiles()!!.size)

        // A new sketch to the same board gets a name of its own.
        state.newSketch(PageSize.pixels(300, 200))
        state.mouseLine(60f)
        state.saveToBoard(boardDir)
        assertTrue(File(boardDir, "$name (2).png").isFile, "never overwritten")
        assertTrue(state.recentSketches().any { it.parentFile == boardDir }, "and remembered for Open")
    }

    @Test
    fun savingToAConceptGoesIntoItsFolder() {
        val state = ready()
        state.mouseLine(100f)
        val line = state.saveToConcept("c1")
        assertTrue(line.contains("in the concept"), line)
        val png = File(conceptDir, "Sketch 2026-09-22 14.05.png")
        assertTrue(png.isFile)
        assertEquals("concept:c1" to listOf(png), added.single())
        assertTrue(state.saveToConcept("nope").contains("cannot be found"))
    }

    // ---- Leaving, replacing, keys ----

    @Test
    fun leavingKeepsTheSketchAndReplacingItAsksFirst() {
        val state = ready()
        state.mouseLine(100f)
        state.leave()
        assertEquals(1, left, "leaving never asks: the sketch stays for next time")
        assertTrue(state.dirty)
        assertTrue(state.darkness(150, 100) > 0.3f)

        state.newSketch(PageSize.pixels(100, 100))
        val confirm = state.editor as SketchEditor.Confirm
        assertEquals(300, state.session!!.width, "not replaced yet")
        state.closeEditor()
        confirm.then()
        assertEquals(100, state.session!!.width, "after agreeing")
        assertFalse(state.dirty)
    }

    @Test
    fun theKeysDoWhatTheToolbarDoes() {
        val state = ready()
        assertTrue(handleSketchShortcut(Key.One, ctrl = false, state = state))
        assertEquals(Lead.HARD, state.brush.lead)
        handleSketchShortcut(Key.Three, ctrl = false, state = state)
        assertEquals(Lead.SOFT, state.brush.lead)
        handleSketchShortcut(Key.E, ctrl = false, state = state)
        assertTrue(state.eraser)
        state.mouseLine(100f)
        assertTrue(handleSketchShortcut(Key.Z, ctrl = true, state = state))
        assertFalse(state.canUndo)
        assertTrue(handleSketchShortcut(Key.Y, ctrl = true, state = state))
        assertTrue(state.canUndo)
        assertTrue(handleSketchShortcut(Key.S, ctrl = true, state = state))
        assertEquals(SketchEditor.SaveAs, state.editor, "Ctrl+S with no file asks for one")
        assertTrue(handleSketchShortcut(Key.Escape, ctrl = false, state = state))
        assertNull(state.editor, "Esc closes the dialog first")
        assertTrue(handleSketchShortcut(Key.O, ctrl = true, state = state))
        assertEquals(SketchEditor.Open, state.editor)
        state.closeEditor()
        assertFalse(handleSketchShortcut(Key.Q, ctrl = false, state = state), "an unbound key is nobody's")
    }

    @Test
    fun sizeAndZoomGoByTheCharacterTypedWhateverTheLayout() {
        val state = ready()
        val size = state.brush.size
        assertTrue(handleSketchChar('[', state))
        assertEquals(size - 1f, state.brush.size)
        assertTrue(handleSketchChar(']', state))
        assertTrue(handleSketchChar(']', state))
        assertEquals(size + 1f, state.brush.size)
        val zoom = state.zoom
        assertTrue(handleSketchChar('+', state))
        assertEquals(zoom * SketchState.KEY_ZOOM, state.zoom, 0.001f)
        assertTrue(handleSketchChar('-', state))
        assertEquals(zoom, state.zoom, 0.001f)
        assertFalse(handleSketchChar('x', state))
        state.openEditor(SketchEditor.Colour)
        assertFalse(handleSketchChar('[', state), "a dialog's text field gets its characters")
    }

    // ---- After the first hands-on: the dial, the eraser, the pen that never spoke ----

    @Test
    fun theWheelZoomsWithOrWithoutCtrlAndShiftSizesTheLead() {
        val state = ready()
        val zoom = state.zoom
        state.wheel(-1f, shift = false, aboutX = 100f, aboutY = 100f)
        assertTrue(state.zoom > zoom, "a wheel step in")
        // The XPPen's dial in its zoom setting sends Ctrl+wheel; that is still a zoom, so the
        // caller does not even pass Ctrl. Shift+wheel is the lead's size.
        val size = state.brush.size
        state.wheel(-1f, shift = true, aboutX = 100f, aboutY = 100f)
        assertEquals(size + 1f, state.brush.size)
        val z = state.zoom
        state.wheel(0f, shift = false, aboutX = 100f, aboutY = 100f)
        assertEquals(z, state.zoom, "a zero step is nothing")
    }

    @Test
    fun theEraserIsSoftUnlessAskedToBeHard() {
        val state = ready()
        assertTrue(state.eraserSoft)
        assertEquals(SketchState.SOFT_ERASER, state.eraserStrength)
        state.toggleEraserMode()
        assertEquals(1f, state.eraserStrength)
        // And the strength goes into the strokes: a soft pass leaves some, a hard one none.
        state.setLead(Lead.SOFT)
        state.setSize(12f)
        state.mouseLine(100f)
        val before = state.darkness(150, 100)
        state.toggleEraser()
        state.toggleEraserMode() // soft again
        state.setSize(24f)
        state.mouseLine(100f, startNanos = 20_000_000_000L)
        val afterSoft = state.darkness(150, 100)
        assertTrue(afterSoft > before * 0.2f && afterSoft < before * 0.85f, "soft: $before -> $afterSoft")
        assertEquals(0.45f, state.session!!.document().strokes.last().eraserStrength)
    }

    @Test
    fun withoutAPenSampleTheStrokesFromTheMouseAreExplained() {
        val state = ready()
        assertNull(state.penHint, "nothing drawn, nothing to explain")
        state.mouseLine(100f)
        val hint = assertNotNull(state.penHint, "mouse strokes and no pen sample: say so")
        assertTrue(hint.contains("Windows Ink"), hint)
        state.onPen(pen(50f, 50f, contact = false, ms = 100_000))
        assertNull(state.penHint, "the pen spoke: nothing to explain")
    }

    @Test
    fun theToolbarWrapsInsteadOfClippingItsTail() {
        val state = newState()
        rule.setContent {
            Box(Modifier.width(900.dp)) { SketchScreen(state, ThumbCache(config)) }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("sketch-back").assertIsDisplayed()
        rule.onNodeWithTag("sketch-save").assertIsDisplayed()
    }

    /**
     * A compact button's fixed height once left 14 dp for a 19-dp line of text, and Text clips
     * what overflows its box: every toolbar label lost the bottom of its letters. Every text on
     * the screen, panels open, must fit its box in height.
     */
    @Test
    fun noLabelOnTheSketchScreenIsCutOffAtTheBottom() {
        val state = newState()
        rule.setContent { SketchScreen(state, ThumbCache(config)) }
        rule.waitForIdle()
        state.newSketch(PageSize.pixels(300, 200))
        state.showPenPanel = true
        state.showTunables = true
        rule.waitForIdle()
        val texts = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(texts.size > 10, "the screen's labels: ${texts.size}")
        val cut = texts.mapNotNull { node ->
            val results = ArrayList<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            val layout = results.firstOrNull() ?: return@mapNotNull null
            if (layout.didOverflowHeight) layout.layoutInput.text.text else null
        }
        assertTrue(cut.isEmpty(), "cut off at the bottom: $cut")
    }

    // ---- The paper, and presets ----

    @Test
    fun thePaperIsChosenForANewSketchAndCanBeChangedUnderTheStrokes() {
        val state = newState()
        state.newSketch(PageSize.pixels(300, 200), tooth = Paper.ROUGH)
        state.viewResized(IntSize(400, 300))
        state.zoomBy(1f / state.zoom, 0f, 0f)
        state.pan(20f - state.panX, 20f - state.panY)
        assertEquals(Paper.ROUGH, state.tooth)
        state.mouseLine(100f)
        state.setTooth(Paper.SMOOTH)
        assertEquals(Paper.SMOOTH, state.tooth)
        assertEquals("SMOOTH", state.session!!.document().tooth)
        assertTrue(state.darkness(150, 100) > 0.3f, "the line is drawn again on the new paper: ${state.darkness(150, 100)}")
        assertTrue(state.dirty, "changing the paper is a change")
        assertTrue(state.canUndo, "and the strokes are still strokes")
        state.saveAs("on smooth")
        val json = File(home, "on smooth" + SketchDocument.FILE_SUFFIX)
        assertTrue(json.isFile)
        val fresh = newState()
        fresh.viewResized(IntSize(400, 300))
        assertTrue(fresh.open(json))
        assertEquals(Paper.SMOOTH, fresh.tooth, "the paper comes back with the sketch")
    }

    @Test
    fun aPresetKeepsATunedLeadByNameAcrossStates() {
        try {
            val state = ready()
            state.setLead(Lead.SOFT)
            Pencils.set(Lead.SOFT, Pencils.SOFT.copy(maxWidth = 1.9f))
            assertNotNull(state.savePreset("   "), "a blank name is refused")
            assertNull(state.savePreset("my 4B"))
            assertEquals(listOf("my 4B"), state.presets.map { it.name })
            assertEquals("my 4B", state.activePreset)
            Pencils.reset(Lead.SOFT)

            val fresh = newState()
            assertEquals(1, fresh.presets.size, "kept in the settings")
            fresh.applyPreset(fresh.presets.single())
            assertEquals(Lead.SOFT, fresh.brush.lead)
            assertEquals(1.9f, Pencils.of(Lead.SOFT).maxWidth, "the lead is tuned as the preset says")
            assertEquals("my 4B", fresh.activePreset)
            fresh.markTuned()
            assertNull(fresh.activePreset, "a knob moved: no longer the preset")
            fresh.deletePreset("my 4B")
            assertTrue(fresh.presets.isEmpty())
            assertTrue(newState().presets.isEmpty(), "gone from the settings too")
        } finally {
            Pencils.reset(Lead.SOFT)
        }
    }

    @Test
    fun theTunePanelOffersThePaperAndPresetsAndTheToolbarShowsThePreset() {
        try {
            val state = newState()
            rule.setContent {
                Box(Modifier.fillMaxSize()) {
                    SketchScreen(state, ThumbCache(config))
                    SketchDialogs(state)
                }
            }
            rule.waitForIdle()
            state.newSketch(PageSize.pixels(300, 200))
            state.showTunables = true
            rule.waitForIdle()
            rule.onNodeWithTag("sketch-paper-ROUGH").performClick()
            rule.waitForIdle()
            assertEquals(Paper.ROUGH, state.tooth)
            rule.onNodeWithTag("sketch-save-preset").performClick()
            rule.waitForIdle()
            rule.onNodeWithTag("sketch-preset-name").performTextInput("mine")
            rule.onNodeWithTag("sketch-preset-save").performClick()
            rule.waitForIdle()
            assertEquals(listOf("mine"), state.presets.map { it.name })
            assertNull(state.editor)
            rule.onNodeWithTag("sketch-preset-mine").assertIsDisplayed()
        } finally {
            Pencils.reset(Lead.MEDIUM)
        }
    }

    // ---- The colour picker ----

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun thePickerKeepsItsHueThroughAGrey() {
        var colour by mutableStateOf(0xFF2040C0.toInt()) // a blue
        rule.setContent { ColorPicker(current = colour, recent = emptyList(), onPick = { colour = it }) }
        rule.waitForIdle()
        val square = rule.onNodeWithTag("colour-square")
        val size = square.fetchSemanticsNode().size
        // All the way to the left: no saturation at all, a true grey — which has no hue of its own.
        square.performMouseInput { click(Offset(0f, size.height / 3f)) }
        rule.waitForIdle()
        val grey = colour
        assertTrue(kotlin.math.abs(((grey shr 16) and 0xFF) - (grey and 0xFF)) <= 3, "a grey: ${Integer.toHexString(grey)}")
        // Back to full saturation: the hue is still blue, not the red a grey's hue would reset to.
        square.performMouseInput { click(Offset(size.width - 2f, size.height / 3f)) }
        rule.waitForIdle()
        val back = colour
        assertTrue((back and 0xFF) > ((back shr 16) and 0xFF), "blue again: ${Integer.toHexString(back)}")
    }

    // ---- On screen ----

    @Test
    fun theScreenDrawsTheStrokesThroughTheTiles() {
        val state = newState()
        rule.setContent { SketchScreen(state, ThumbCache(config)) }
        rule.waitForIdle()
        rule.onNodeWithTag("sketch-undo").assertIsNotEnabled()
        state.newSketch(PageSize.pixels(600, 300))
        rule.waitForIdle()
        state.fit()
        rule.waitForIdle()
        val bare = rule.onNodeWithTag("sketch-page").captureToImage().toPixelMap()
        // A thick line across the page's middle, crossing a tile border.
        state.setLead(Lead.SOFT)
        state.setSize(30f)
        var t = 10_000_000_000L
        val y = state.panY + 150f * state.zoom
        state.mouseDown(state.panX + 40f * state.zoom, y, t)
        for (x in 40..560 step 4) {
            t += 5_000_000L
            state.mouseMove(state.panX + x * state.zoom, y, t)
        }
        state.mouseUp()
        rule.waitForIdle()

        val shot = rule.onNodeWithTag("sketch-page").captureToImage().toPixelMap()
        fun dark(x: Float, yy: Float, map: PixelMap = shot): Float {
            val c = map[x.toInt(), yy.toInt()]
            return 1f - (c.red + c.green + c.blue) / 3f
        }
        val onLine = listOf(100f, 256f, 400f).map { dark(state.panX + it * state.zoom, y) }
        assertTrue(onLine.all { it > 0.3f }, "the line is on screen, across the tile border: $onLine")
        // Bare paper is white with the faint shade of its tooth: light, and the same as before the stroke.
        val px = state.panX + 300f * state.zoom
        val py = state.panY + 40f * state.zoom
        assertTrue(dark(px, py, bare) < 0.12f, "bare paper is light: ${dark(px, py, bare)}")
        assertEquals(bare[px.toInt(), py.toInt()], shot[px.toInt(), py.toInt()], "and the paper around the line is paper")
        rule.onNodeWithTag("sketch-undo").performClick()
        rule.waitForIdle()
        val after = rule.onNodeWithTag("sketch-page").captureToImage().toPixelMap()
        val lx = (state.panX + 256f * state.zoom).toInt()
        assertEquals(bare[lx, y.toInt()], after[lx, y.toInt()], "undo takes it off the screen too")
    }
}
