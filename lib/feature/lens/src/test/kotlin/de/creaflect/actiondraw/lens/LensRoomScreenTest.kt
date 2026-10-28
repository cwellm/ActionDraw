package de.creaflect.actiondraw.lens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import de.creaflect.actiondraw.Room
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.handoff.Handed
import de.creaflect.actiondraw.lens.ui.LensRoomScreen
import de.creaflect.actiondraw.ui.Atelier
import de.creaflect.actiondraw.ui.ProvideAtelier
import org.jetbrains.skia.Image
import org.junit.After
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The Lens room on screen, as wide as the app opens unless a test says otherwise: the invitation
 * while nothing is open, the picture alone, beside the original and through two lenses, the view
 * saved and pinned for real — drawn off screen, as a click asks for it, and written where it was
 * to go — and the header folding its actions into menus as the window narrows.
 */
@OptIn(ExperimentalTestApi::class)
class LensRoomScreenTest {
    private val fixture = LensRoomFixture()

    @After
    fun cleanup() = fixture.cleanup()

    /** The room on screen in a window [width] dp wide. */
    private fun room(width: Int = 1120, block: ComposeUiTest.() -> Unit) = runDesktopComposeUiTest(width = width, height = 560) { block() }

    private fun ComposeUiTest.show(state: LensRoomState) {
        setContent { ProvideAtelier { LensRoomScreen(state, onHome = {}) } }
        waitForIdle()
    }

    /** The picture is decoded and a view drawn off the UI thread: wait for [done], in real time. */
    private fun ComposeUiTest.waitFor(what: String, done: () -> Boolean) {
        repeat(100) {
            waitForIdle()
            if (done()) return
            Thread.sleep(100)
        }
        error("never: $what")
    }

    private fun ComposeUiTest.pictureShown() = waitFor("the picture shown") { onAllNodesWithTag("reference").fetchSemanticsNodes().size == 1 }

    @Test
    fun withNothingOpenTheRoomInvitesAPictureIn() = room {
        val state = fixture.state()
        show(state)
        onNodeWithTag("lens-invitation").assertIsDisplayed()
        onNodeWithTag("reference").assertDoesNotExist()
        fixture.chosen = fixture.picture("pose")
        onNodeWithTag("lens-invite-open").performClick()
        pictureShown()
        onNodeWithTag("lens-invitation").assertDoesNotExist()
        onNodeWithTag("lens-title").assertTextEquals("pose")
        onNodeWithTag("lens-chrome").assertIsDisplayed()
    }

    @Test
    fun besideTheOriginalAndThroughTwoLensesThePictureIsShownTwice() = room {
        val state = fixture.state()
        state.open(fixture.picture("pose"))
        show(state)
        onNodeWithTag("lens-layout-beside").performClick()
        waitFor("two pictures") { onAllNodesWithTag("reference").fetchSemanticsNodes().size == 2 }
        onNodeWithTag("lens-side-original-label").assertTextContains("The original")

        onNodeWithTag("lens-layout-two").performClick()
        waitForIdle()
        onNodeWithTag("lens-side-second-label").assertTextContains("2 · Edge")
        onNodeWithTag("lens-side-second-label").performClick()
        waitForIdle()
        assertSame(state.second, state.lens, "the label chooses the lens the tray works")
        onNodeWithTag("lens-notan").performClick()
        waitForIdle()
        assertEquals(ViewMode.NOTAN, state.second.viewMode, "the tray turns the second lens")
        assertEquals(ViewMode.NONE, state.first.viewMode, "and leaves the first")
    }

    @Test
    fun theRoomsTrayIsLitInItsOwnPigment() = room {
        val state = fixture.state()
        state.open(fixture.picture("pose"))
        state.first.blur = true
        show(state)
        pictureShown()
        val glow = Atelier.pigmentOf(Room.LENS).glow
        fun lit(c: Color) = abs(c.red - glow.red) < 0.04f && abs(c.green - glow.green) < 0.04f && abs(c.blue - glow.blue) < 0.04f
        val d = density.density
        // The blur knob is on: its round, beside the glyph in its middle.
        val knob = onNodeWithTag("knob-b").captureToImage().toPixelMap()
        assertTrue(lit(knob[(9 * d).toInt(), (20 * d).toInt()]), "the tray's knob in Payne's grey")
    }

    @Test
    fun thePictureGoesOnIntoSketchFromTheHeader() = room {
        val state = fixture.state()
        val pose = fixture.picture("pose")
        state.open(pose)
        show(state)
        onNodeWithTag("lens-to-sketch").performClick()
        waitForIdle()
        assertEquals(listOf<Pair<Handed, Room>>(Handed.Picture(pose) to Room.SKETCH), fixture.sent)
    }

    @Test
    fun theViewIsSavedAsAPictureOfItsOwnSize() = room {
        val state = fixture.state()
        state.open(fixture.picture("pose", 400, 200))
        state.first.viewMode = ViewMode.SILHOUETTE
        val target = File(fixture.root, "saved/view.png")
        fixture.saveTo = target
        show(state)
        pictureShown()
        onNodeWithTag("lens-save").performClick()
        waitFor("the view saved") { target.isFile && state.export == null }
        val saved = assertNotNull(Image.makeFromEncoded(target.readBytes()))
        assertEquals(400 to 200, saved.width to saved.height, "the picture's own size")
        onNodeWithTag("lens-notice").assertTextEquals("Saved as view.png")
    }

    @Test
    fun theViewIsPinnedToTheBoardChosenBesideItsOriginal() = room {
        val state = fixture.state()
        val pose = fixture.picture("pose")
        state.open(pose)
        show(state)
        pictureShown()
        onNodeWithTag("lens-pin").performClick()
        waitForIdle()
        onNodeWithTag("lens-pin-Buch").performClick()
        waitFor("the view pinned") { fixture.pinned.isNotEmpty() }
        val (dir, pictures) = fixture.pinned.single()
        assertEquals(fixture.board, dir)
        assertEquals(listOf(pose, File(fixture.board, "pose - seen.png")), pictures)
        assertTrue(pictures.last().isFile)
    }

    // ---- The header, as the window narrows ----

    /** The header's way back is on screen, wholly inside the window [width] dp wide — nothing pushed past its edge. */
    private fun ComposeUiTest.backInside(width: Int) {
        onNodeWithTag("lens-back").assertIsDisplayed()
        val right = onNodeWithTag("lens-back").fetchSemanticsNode().boundsInRoot.right / density.density
        val window = onAllNodes(isRoot()).onFirst().fetchSemanticsNode().boundsInRoot.right / density.density
        assertTrue(right <= window + 0.5f && window <= width + 0.5f, "the way back inside the window: $right of $window")
    }

    @Test
    fun wideEveryActionIsOutAndAsWideAsTheAppOpensOnlyOpeningFolds() {
        room(width = 1400) {
            val state = fixture.state()
            state.open(fixture.picture("pose"))
            show(state)
            onNodeWithTag("lens-open").assertIsDisplayed()
            onNodeWithTag("lens-paste").assertIsDisplayed()
            onNodeWithTag("lens-open-menu").assertDoesNotExist()
            backInside(1400)
        }
        room {
            val state = fixture.state()
            state.open(fixture.picture("pose"))
            show(state)
            onNodeWithTag("lens-open").assertDoesNotExist()
            onNodeWithTag("lens-layout-two").assertIsDisplayed()
            onNodeWithTag("lens-save").assertIsDisplayed()
            onNodeWithTag("lens-title").assertTextEquals("pose")
            backInside(1120)
            fixture.chosen = fixture.picture("other")
            onNodeWithTag("lens-open-menu").performClick()
            waitForIdle()
            onNodeWithTag("lens-menu-open").performClick()
            waitForIdle()
            assertEquals("other", state.picture?.name, "opened from its menu")
        }
    }

    @Test
    fun thePicturesNameKeepsItsRoomWhateverFolds() = room(width = 1000) {
        val state = fixture.state()
        state.open(fixture.picture("a pose from the long Sunday session at the studio"))
        show(state)
        val title = onNodeWithTag("lens-title").fetchSemanticsNode().boundsInRoot.width / density.density
        assertTrue(title >= 110f, "the name keeps its room, the actions folding first: $title dp")
        backInside(1000)
    }

    @Test
    fun narrowerThePictureAndItsLayoutAreMenus() = room(width = 720) {
        val state = fixture.state()
        state.open(fixture.picture("pose"))
        show(state)
        onNodeWithTag("lens-layout-two").assertDoesNotExist()
        onNodeWithTag("lens-save").assertDoesNotExist()
        onNodeWithTag("lens-to-sketch").assertIsDisplayed()
        backInside(720)
        onNodeWithTag("lens-layout-menu").performClick()
        waitForIdle()
        onNodeWithTag("lens-menu-layout-two").performClick()
        waitForIdle()
        assertEquals(LensLayout.TWO, state.layout, "laid out from the layout's menu")
        onNodeWithTag("lens-layout-menu").assertTextContains("Two lenses ▾")
        onNodeWithTag("lens-picture-menu").performClick()
        waitForIdle()
        onNodeWithTag("lens-menu-pin-Buch").performClick()
        waitFor("the view pinned") { fixture.pinned.isNotEmpty() }
        assertEquals(fixture.board, fixture.pinned.single().first, "and pinned from the picture's menu")
    }

    @Test
    fun narrowestEverythingButTheWayBackIsUnderMore() = room(width = 480) {
        val state = fixture.state()
        val pose = fixture.picture("pose")
        state.open(pose)
        show(state)
        onNodeWithTag("lens-picture-menu").assertDoesNotExist()
        onNodeWithTag("lens-to-sketch").assertDoesNotExist()
        backInside(480)
        onNodeWithTag("lens-more").performClick()
        waitForIdle()
        onNodeWithTag("lens-menu-layout-beside").assertIsDisplayed()
        onNodeWithTag("lens-menu-save").assertIsDisplayed()
        onNodeWithTag("lens-menu-to-sketch").performClick()
        waitForIdle()
        assertEquals(listOf<Pair<Handed, Room>>(Handed.Picture(pose) to Room.SKETCH), fixture.sent, "the way on, from More")
    }
}
