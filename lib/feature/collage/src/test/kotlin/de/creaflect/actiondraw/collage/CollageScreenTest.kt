package de.creaflect.actiondraw.collage

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import de.creaflect.actiondraw.collage.ui.CollageScreen
import de.creaflect.actiondraw.collage.ui.MatView
import de.creaflect.actiondraw.ui.ProvideAtelier
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Collage room on screen, as wide as the app opens: the invitation on the bare mat, a picture
 * laid on it and drawn where it lies, a piece moved, turned and grown by the mouse, chosen and let go
 * of, and worked from the pieces list, the rail and the header.
 */
@OptIn(ExperimentalTestApi::class)
class CollageScreenTest {
    private val fixture = CollageFixture()

    @After
    fun cleanup() = fixture.cleanup()

    private fun room(block: ComposeUiTest.() -> Unit) = runDesktopComposeUiTest(width = 1120, height = 720) { block() }

    private fun ComposeUiTest.show(state: CollageState) {
        setContent { ProvideAtelier { CollageScreen(state, onHome = {}) } }
        waitForIdle()
    }

    /** How the mat lies on screen, as the room lays it out. */
    private fun ComposeUiTest.view(): MatView {
        val size = onNodeWithTag("collage-mat").fetchSemanticsNode().size
        return MatView.fit(Size(size.width.toFloat(), size.height.toFloat()), 14 * density.density)
    }

    private fun ComposeUiTest.at(m: Spot): Offset = view().toScreen(m)

    /** A drag with the mouse from [from] by [by], in steps a mouse makes. */
    private fun ComposeUiTest.drag(from: Offset, by: Offset) {
        val steps = 40
        onNodeWithTag("collage-mat").performMouseInput {
            moveTo(from)
            press()
            repeat(steps) { moveBy(by / steps.toFloat()) }
            release()
        }
        waitForIdle()
    }

    /** The mat's pixel at [m], once the pieces' pictures are read — they are read off the UI thread. */
    private fun ComposeUiTest.pixelAt(m: Spot): Color {
        val p = at(m)
        return onNodeWithTag("collage-mat").captureToImage().toPixelMap()[p.x.toInt(), p.y.toInt()]
    }

    private fun ComposeUiTest.waitFor(what: String, done: () -> Boolean) {
        repeat(100) {
            waitForIdle()
            if (done()) return
            Thread.sleep(50)
        }
        error("never: $what")
    }

    private fun orange(c: Color) = c.red > 0.7f && c.blue < 0.4f
    private fun blue(c: Color) = c.blue > 0.6f && c.red < 0.4f

    @Test
    fun theBareMatInvitesAPictureAndOneLaidOnItIsDrawnWhereItLies() = room {
        val state = fixture.state()
        show(state)
        onNodeWithTag("collage-invitation").assertIsDisplayed()
        onNodeWithTag("rail-flip").assertIsNotEnabled()
        fixture.chosen = fixture.picture("cliff")
        onNodeWithTag("collage-invite-add").performClick()
        waitForIdle()
        onNodeWithTag("collage-invitation").assertDoesNotExist()
        val piece = state.collage.pieces.single()
        onNodeWithTag("piece-${piece.id}").assertIsDisplayed()
        // The picture is orange on its left half and blue on its right.
        val left = Spot(piece.x - 150f, piece.y)
        val right = Spot(piece.x + 150f, piece.y)
        waitFor("the picture drawn") { orange(pixelAt(left)) }
        assertTrue(blue(pixelAt(right)), "its right half where it lies")
        state.flip()
        waitForIdle()
        assertTrue(blue(pixelAt(left)) && orange(pixelAt(right)), "flipped over, its halves swap")
    }

    @Test
    fun aPieceIsDraggedTurnedByItsHandleAndGrownByItsCorner() = room {
        val state = fixture.state()
        val piece = assertNotNull(state.addPicture(fixture.picture("cliff")))
        state.select(null)
        show(state)
        val scale = view().scale

        drag(at(Spot(piece.x, piece.y)), Offset(60f, 30f))
        val moved = state.collage.pieces.single()
        assertEquals(piece.id, state.selected, "the press took it")
        assertEquals(piece.x + 60f / scale, moved.x, 1.5f / scale)
        assertEquals(piece.y + 30f / scale, moved.y, 1.5f / scale)
        assertTrue(state.canUndo)

        // Its turning handle sits out from the middle of its top edge.
        val b = moved.bounds()
        val edge = at(moved.toMat(Spot((b[0] + b[2]) / 2f, b[1])))
        val handle = edge - Offset(0f, 26 * density.density)
        drag(handle, Offset(80f, 0f))
        val turned = state.collage.pieces.single()
        assertTrue(turned.turn in 5f..90f, "dragged to the right, it turns clockwise: ${turned.turn}")

        state.turnTo(piece.id, 0f)
        val corner = at(turned.copy(turn = 0f).toMat(Spot(b[2], b[3])))
        drag(corner, Offset(40f, 20f))
        assertTrue(state.collage.pieces.single().scale > piece.scale * 1.05f, "pulled out at its corner, it grows")
    }

    @Test
    fun aPressOnTheBareMatLetsGoAndTheTopmostPieceIsTaken() = room {
        val state = fixture.state()
        val under = assertNotNull(state.addPicture(fixture.picture("under"), Spot(500f, 500f)))
        val over = assertNotNull(state.addPicture(fixture.picture("over"), Spot(600f, 500f)))
        show(state)
        onNodeWithTag("collage-mat").performMouseInput { click(at(Spot(550f, 500f))) }
        waitForIdle()
        assertEquals(over.id, state.selected)
        onNodeWithTag("collage-mat").performMouseInput { click(at(Spot(150f, 500f))) }
        waitForIdle()
        assertEquals(under.id, state.selected)
        onNodeWithTag("collage-mat").performMouseInput { click(at(Spot(1700f, 1200f))) }
        waitForIdle()
        assertNull(state.selected, "the bare mat lets go")
    }

    @Test
    fun thePiecesListTheRailAndTheHeaderWorkTheCollage() = room {
        val state = fixture.state()
        val a = assertNotNull(state.addPicture(fixture.picture("cliff")))
        val b = assertNotNull(state.addPicture(fixture.picture("sea")))
        state.select(null)
        show(state)

        onNodeWithTag("piece-${a.id}").performClick()
        waitForIdle()
        assertEquals(a.id, state.selected, "a row chooses its piece")
        onNodeWithTag("rail-forward").performClick()
        waitForIdle()
        assertEquals(listOf(b.id, a.id), state.collage.pieces.map { it.id }, "Forward: up the stack")
        onNodeWithTag("rail-flip").performClick()
        waitForIdle()
        assertTrue(state.selectedPiece!!.flipped)

        onNodeWithTag("piece-${b.id}").performMouseInput { doubleClick() }
        waitForIdle()
        onNodeWithTag("piece-name-field").performTextInput("Sea at dusk")
        onNodeWithTag("piece-name-field").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals("Sea at dusk", state.collage.pieces.first { it.id == b.id }.name, "renamed in the list")
        onNodeWithTag("piece-remove").performClick()
        waitForIdle()
        assertEquals(listOf(a.id), state.collage.pieces.map { it.id }, "and taken away")

        onNodeWithTag("collage-name").performClick()
        waitForIdle()
        onNodeWithTag("collage-name-field").performTextInput("Cliff study")
        onNodeWithTag("collage-name-field").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals("Cliff study", state.name)
        assertEquals("Cliff study.collage.json", state.file?.name)

        onNodeWithTag("collage-menu").performClick()
        waitForIdle()
        onNodeWithTag("collage-new").performClick()
        waitForIdle()
        assertTrue(state.collage.pieces.isEmpty())
        onNodeWithTag("collage-invitation").assertIsDisplayed()
        onNodeWithTag("collage-menu").performClick()
        waitForIdle()
        onNodeWithTag("collage-open-Cliff study").performClick()
        waitForIdle()
        assertEquals("Cliff study", state.name, "opened again from the menu")
        assertFalse(state.collage.pieces.isEmpty())
    }
}
