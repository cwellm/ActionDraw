package de.creaflect.actiondraw.lens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.lens.ui.LensRoomScreen
import de.creaflect.actiondraw.ui.Atelier
import de.creaflect.actiondraw.ui.ProvideAtelier
import de.creaflect.actiondraw.ui.Room
import org.jetbrains.skia.Image
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The Lens room on screen: the invitation while nothing is open, the picture alone, beside the
 * original and through two lenses, and the view saved and pinned for real — drawn off screen, as
 * a click asks for it, and written where it was to go.
 */
@OptIn(ExperimentalTestApi::class)
class LensRoomScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val fixture = LensRoomFixture()

    @After
    fun cleanup() = fixture.cleanup()

    private fun show(state: LensRoomState) {
        rule.setContent { ProvideAtelier { LensRoomScreen(state, onHome = {}) } }
        rule.waitForIdle()
    }

    /** The picture is decoded and a view drawn off the UI thread: wait for [done], in real time. */
    private fun waitFor(what: String, done: () -> Boolean) {
        repeat(100) {
            rule.waitForIdle()
            if (done()) return
            Thread.sleep(100)
        }
        error("never: $what")
    }

    @Test
    fun withNothingOpenTheRoomInvitesAPictureIn() {
        val state = fixture.state()
        show(state)
        rule.onNodeWithTag("lens-invitation").assertIsDisplayed()
        rule.onNodeWithTag("reference").assertDoesNotExist()
        fixture.chosen = fixture.picture("pose")
        rule.onNodeWithTag("lens-invite-open").performClick()
        waitFor("the picture shown") { rule.onAllNodesWithTag("reference").fetchSemanticsNodes().size == 1 }
        rule.onNodeWithTag("lens-invitation").assertDoesNotExist()
        rule.onNodeWithTag("lens-title").assertTextEquals("pose")
        rule.onNodeWithTag("lens-chrome").assertIsDisplayed()
    }

    @Test
    fun besideTheOriginalAndThroughTwoLensesThePictureIsShownTwice() {
        val state = fixture.state()
        state.open(fixture.picture("pose"))
        show(state)
        rule.onNodeWithTag("lens-layout-beside").performClick()
        waitFor("two pictures") { rule.onAllNodesWithTag("reference").fetchSemanticsNodes().size == 2 }
        rule.onNodeWithTag("lens-side-original-label").assertTextContains("The original")

        rule.onNodeWithTag("lens-layout-two").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("lens-side-second-label").assertTextContains("2 · Edge")
        rule.onNodeWithTag("lens-side-second-label").performClick()
        rule.waitForIdle()
        assertSame(state.second, state.lens, "the label chooses the lens the tray works")
        rule.onNodeWithTag("lens-notan").performClick()
        rule.waitForIdle()
        assertEquals(ViewMode.NOTAN, state.second.viewMode, "the tray turns the second lens")
        assertEquals(ViewMode.NONE, state.first.viewMode, "and leaves the first")
    }

    @Test
    fun theRoomIsLitInItsOwnPigment() {
        val state = fixture.state()
        state.open(fixture.picture("pose"))
        state.first.blur = true
        show(state)
        waitFor("the picture shown") { rule.onAllNodesWithTag("reference").fetchSemanticsNodes().size == 1 }
        val glow = Atelier.pigmentOf(Room.LENS).glow
        fun lit(c: Color) = abs(c.red - glow.red) < 0.04f && abs(c.green - glow.green) < 0.04f && abs(c.blue - glow.blue) < 0.04f
        val d = rule.density.density
        // The blur knob is on: its round, beside the glyph in its middle.
        val knob = rule.onNodeWithTag("knob-b").captureToImage().toPixelMap()
        assertTrue(lit(knob[(9 * d).toInt(), (20 * d).toInt()]), "the tray's knob in Payne's grey")
        // The layout chosen is a Material button: Material's colours are the room's too.
        val chip = rule.onNodeWithTag("lens-layout-alone").captureToImage().toPixelMap()
        assertTrue(lit(chip[(6 * d).toInt(), chip.height / 2]), "and the layout chosen")
    }

    @Test
    fun theViewIsSavedAsAPictureOfItsOwnSize() {
        val state = fixture.state()
        state.open(fixture.picture("pose", 400, 200))
        state.first.viewMode = ViewMode.SILHOUETTE
        val target = File(fixture.root, "saved/view.png")
        fixture.saveTo = target
        show(state)
        waitFor("the picture shown") { rule.onAllNodesWithTag("reference").fetchSemanticsNodes().size == 1 }
        rule.onNodeWithTag("lens-save").performClick()
        waitFor("the view saved") { target.isFile && state.export == null }
        val saved = assertNotNull(Image.makeFromEncoded(target.readBytes()))
        assertEquals(400 to 200, saved.width to saved.height, "the picture's own size")
        rule.onNodeWithTag("lens-notice").assertTextEquals("Saved as view.png")
    }

    @Test
    fun theViewIsPinnedToTheBoardChosenBesideItsOriginal() {
        val state = fixture.state()
        val pose = fixture.picture("pose")
        state.open(pose)
        show(state)
        waitFor("the picture shown") { rule.onAllNodesWithTag("reference").fetchSemanticsNodes().size == 1 }
        rule.onNodeWithTag("lens-pin").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("lens-pin-Buch").performClick()
        waitFor("the view pinned") { fixture.pinned.isNotEmpty() }
        val (dir, pictures) = fixture.pinned.single()
        assertEquals(fixture.board, dir)
        assertEquals(listOf(pose, File(fixture.board, "pose - seen.png")), pictures)
        assertTrue(pictures.last().isFile)
    }
}
