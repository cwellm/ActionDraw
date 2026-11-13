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
import androidx.compose.ui.test.performMultiModalInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import de.creaflect.actiondraw.Room
import de.creaflect.actiondraw.collage.ui.CollageScreen
import de.creaflect.actiondraw.collage.ui.MatView
import de.creaflect.actiondraw.handoff.Handed
import de.creaflect.actiondraw.ui.ProvideAtelier
import org.junit.After
import org.junit.Test
import javax.imageio.ImageIO
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

    /** Naples yellow, the path being cut. */
    private fun naples(c: Color) = c.red > 0.8f && c.green > 0.65f && c.blue < 0.6f

    /** Masking tape over the green mat. */
    private fun tape(c: Color) = c.red > 0.6f && c.green > 0.6f && c.blue > 0.5f

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
    fun theScissorsCutWithADragAndRoundAPartWithClicks() = room {
        val state = fixture.state()
        val piece = assertNotNull(state.addPicture(fixture.picture("cliff"), Spot(950f, 650f)))
        show(state)
        onNodeWithTag("rail-scissors").performClick()
        waitForIdle()
        assertEquals(Tool.SCISSORS, state.tool)

        // A drag on its own down through the piece: cut as it lets go.
        val top = at(piece.toMat(Spot(300f, -30f)))
        drag(top, at(piece.toMat(Spot(300f, 230f))) - top)
        assertEquals(listOf("cliff", "cliff, cut"), state.collage.pieces.map { it.name })

        // Corners clicked on the bare mat: the path drawn between them, in Naples yellow.
        onNodeWithTag("collage-mat").performMouseInput {
            click(at(Spot(200f, 1100f)))
            click(at(Spot(500f, 1100f)))
        }
        waitForIdle()
        assertEquals(2, state.path.size, "a corner each click")
        assertTrue((0..20).any { naples(pixelAt(Spot(220f + it * 13f, 1100f))) }, "the path drawn where it goes")
        state.stepBack()
        waitForIdle()
        assertTrue(state.path.isEmpty(), "Esc drops it")

        // Corners clicked round a part of the larger piece, and the first one clicked again.
        val corners = listOf(Spot(50f, 50f), Spot(150f, 50f), Spot(150f, 150f), Spot(50f, 150f)).map { at(piece.toMat(it)) }
        onNodeWithTag("collage-mat").performMouseInput { corners.forEach { click(it) } }
        waitForIdle()
        assertEquals(4, state.path.size)
        onNodeWithTag("collage-mat").performMouseInput { click(corners.first()) }
        waitForIdle()
        assertTrue(state.path.isEmpty())
        assertEquals(listOf("cliff", "cliff, cut 2", "cliff, cut"), state.collage.pieces.map { it.name })
        assertEquals("cliff, cut 2", state.selectedPiece?.name, "the part cut out chosen")
        assertEquals(10000f, areaOf(state.selectedPiece!!.outline), 400f)
    }

    @Test
    fun aDragWithShiftCutsStraightWhereverThePointerWanders() = room {
        val state = fixture.state()
        val piece = assertNotNull(state.addPicture(fixture.picture("cliff"), Spot(950f, 650f)))
        show(state)
        onNodeWithTag("rail-scissors").performClick()
        waitForIdle()
        val top = at(piece.toMat(Spot(200f, -30f)))
        val down = (at(piece.toMat(Spot(200f, 230f))) - top).y / 40f
        onNodeWithTag("collage-mat").performMultiModalInput {
            key { keyDown(Key.ShiftLeft) }
            mouse {
                moveTo(top)
                press()
                repeat(40) { moveBy(Offset(if (it % 2 == 0) 14f else -14f, down)) }
                release()
            }
            key { keyUp(Key.ShiftLeft) }
        }
        waitForIdle()
        assertEquals(listOf(4, 4), state.collage.pieces.map { it.outline.size }, "parted along one straight line, not the wandering")
    }

    @Test
    fun theTearTearsAlongADragAndTheTapeIsLaidAcrossPieces() = room {
        val state = fixture.state()
        assertNotNull(state.addPicture(fixture.picture("a"), Spot(600f, 650f)))
        val b = assertNotNull(state.addPicture(fixture.picture("b"), Spot(1400f, 650f)))
        show(state)
        onNodeWithTag("rail-tear").performClick()
        waitForIdle()
        val from = at(Spot(600f, 400f))
        drag(from, at(Spot(600f, 900f)) - from)
        assertEquals(listOf("a", "a, torn", "b"), state.collage.pieces.map { it.name })

        onNodeWithTag("rail-tape").performClick()
        waitForIdle()
        val start = at(Spot(900f, 650f))
        drag(start, at(Spot(1100f, 650f)) - start)
        val strip = state.collage.pieces.last()
        assertTrue(strip.isTape, "laid on top")
        assertEquals(2, strip.holds.size, "across a's torn half and b")
        assertTrue(b.id in strip.holds)
        // Between the two pieces it lies on the bare mat.
        waitFor("the tape drawn") { tape(pixelAt(Spot(1000f, 650f))) }
    }

    @Test
    fun allLayersOnTheRailCutsThroughEveryPictureUnderTheCut() = room {
        val state = fixture.state()
        assertNotNull(state.addPicture(fixture.picture("under"), Spot(800f, 650f)))
        assertNotNull(state.addPicture(fixture.picture("over"), Spot(1100f, 650f)))
        show(state)
        onNodeWithTag("rail-all-layers").performClick()
        onNodeWithTag("rail-scissors").performClick()
        waitForIdle()
        assertTrue(state.throughAll)
        val top = at(Spot(950f, 400f))
        drag(top, at(Spot(950f, 900f)) - top)
        assertEquals(listOf("under", "under, cut", "over", "over, cut"), state.collage.pieces.map { it.name })
    }

    @Test
    fun theDrawerLaysAPictureWithAClickAndWhereverItIsDraggedOnto() = room {
        fixture.boardPictures = listOf(fixture.picture("sea"), fixture.picture("rock"))
        val state = fixture.state()
        show(state)
        onNodeWithTag("drawer-boards").assertIsDisplayed()
        onNodeWithTag("drawer-picture-1").performClick()
        waitForIdle()
        assertEquals(listOf("rock"), state.collage.pieces.map { it.name }, "a click lays it on the mat")

        // Dragged from the drawer and let go over the mat, it lies where it was let go.
        val spot = Spot(450f, 350f)
        val print = onNodeWithTag("drawer-picture-0").fetchSemanticsNode()
        val mat = onNodeWithTag("collage-mat").fetchSemanticsNode()
        val from = Offset(print.size.width / 2f, print.size.height / 2f)
        val to = mat.positionInRoot + at(spot) - print.positionInRoot
        onNodeWithTag("drawer-picture-0").performMouseInput {
            moveTo(from)
            press()
            repeat(40) { moveBy((to - from) / 40f) }
            release()
        }
        waitForIdle()
        val laid = state.collage.pieces.last()
        assertEquals("sea", laid.name)
        val scale = view().scale
        assertEquals(spot.x, laid.x, 2f / scale)
        assertEquals(spot.y, laid.y, 2f / scale)

        onNodeWithTag("drawer-concepts").performClick()
        waitForIdle()
        onNodeWithTag("drawer-concepts-Wing").performClick()
        waitForIdle()
        assertEquals("Wing", state.source?.name)
    }

    @Test
    fun theWaysOutDrawTheCollageAndTakeItOnSaveItAndPinIt() = room {
        val state = fixture.state()
        assertNotNull(state.addPicture(fixture.picture("cliff")))
        show(state)
        onNodeWithTag("collage-to-lens").performClick()
        waitFor("drawn and taken into the Lens") { fixture.sent.isNotEmpty() }
        val (what, to) = fixture.sent.single()
        assertEquals(Room.LENS, to)
        val picture = (what as Handed.Picture).file
        assertEquals(state.pictureFile(), picture)
        // The picture, 400 × 200 laid 760 × 380 on the mat, drawn a pixel to a mat unit with a margin round it.
        val png = ImageIO.read(picture)
        assertEquals(806, png.width, "the pieces and a little margin: ${png.width}")
        assertEquals(426, png.height)
        fun at(fx: Float) = java.awt.Color(png.getRGB((png.width * fx).toInt(), png.height / 2)).let { Color(it.red / 255f, it.green / 255f, it.blue / 255f) }
        assertTrue(orange(at(0.25f)) && blue(at(0.75f)), "the picture drawn as it lies")
        val margin = java.awt.Color(png.getRGB(3, 3))
        assertTrue(margin.red > 200 && margin.green > 190, "on paper round it")

        onNodeWithTag("collage-pin").performClick()
        waitForIdle()
        onNodeWithTag("collage-pin-Drachenbuch").performClick()
        waitFor("pinned") { fixture.pinned.isNotEmpty() }
        assertEquals(listOf(fixture.board to listOf(picture)), fixture.pinned)

        picture.delete()
        onNodeWithTag("collage-menu").performClick()
        waitForIdle()
        onNodeWithTag("collage-save").performClick()
        waitFor("saved") { picture.isFile }
        waitForIdle()
        assertEquals("Saved as Untitled collage.png, beside the collage", state.notice)
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
