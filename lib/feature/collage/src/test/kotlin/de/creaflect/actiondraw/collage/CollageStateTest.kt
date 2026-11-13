package de.creaflect.actiondraw.collage

import de.creaflect.actiondraw.Room
import de.creaflect.actiondraw.handoff.Handed
import de.creaflect.actiondraw.platform.SystemClipboard
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Collage room without a window: what comes onto the mat, how the pieces are moved, and what is kept. */
class CollageStateTest {
    private val fixture = CollageFixture()

    @AfterTest
    fun cleanup() = fixture.cleanup()

    /** The collage as its document on disk has it. */
    private fun kept(state: CollageState): Collage = assertNotNull(fixture.lib.collages.read(assertNotNull(state.file)))

    // ---- Pictures in ----

    @Test
    fun aPictureLiesOnTheMatWholeInItsMiddleAndTheCollageIsKeptFromThen() {
        val state = fixture.state()
        assertNull(state.file, "nothing on the mat, nothing kept")
        val piece = assertNotNull(state.addPicture(fixture.picture("cliff", 800, 400)))
        assertEquals("cliff", piece.name)
        assertEquals(listOf(Spot(0f, 0f), Spot(800f, 0f), Spot(800f, 400f), Spot(0f, 400f)), piece.outline, "whole, to cut from")
        assertEquals(Spot(Collage.MAT_WIDTH / 2, Collage.MAT_HEIGHT / 2), Spot(piece.x, piece.y))
        assertEquals(Collage.MAT_WIDTH * CollageState.PIECE_SHARE / 800f, piece.scale, 0.0001f, "as large as fits a good part of the mat")
        assertEquals(piece.id, state.selected, "chosen, to work on")
        assertEquals(File(fixture.lib.preferences.collagesHome(), "Untitled collage.collage.json"), state.file, "kept in the collages home from the first piece")
        assertEquals(state.collage, kept(state))

        val next = assertNotNull(state.addPicture(fixture.picture("sea")))
        assertTrue(next.x > piece.x && next.y > piece.y, "a second one a little apart, not hidden under the first")
        assertEquals(listOf("cliff", "sea"), state.collage.pieces.map { it.name }, "and on top of it")
    }

    @Test
    fun aPictureComesFromAFileTheClipboardOrAPasteOfItsOwn() {
        val state = fixture.state()
        assertFalse(state.chooseFile(), "nothing chosen, nothing added")
        fixture.chosen = fixture.picture("wing")
        assertTrue(state.chooseFile())
        assertEquals("wing", state.collage.pieces.last().name)

        fixture.pasted = SystemClipboard.Pasted.Files(listOf(fixture.picture("rock")))
        assertTrue(state.paste())
        assertEquals("rock", state.collage.pieces.last().name)

        fixture.pasted = SystemClipboard.Pasted.Bitmap(fixture.pngBytes())
        assertTrue(state.paste())
        val pasted = state.collage.pieces.last()
        assertEquals(assertNotNull(state.file).parentFile, pasted.source?.parentFile, "a pasted picture kept beside the collage")
        assertTrue(pasted.source?.isFile == true)

        fixture.pasted = null
        assertFalse(state.paste())
        assertEquals("There is no picture on the clipboard", state.notice)
        assertNull(state.addPicture(File(fixture.pictures, "missing.png")))
        assertEquals("Could not read missing.png", state.notice)
    }

    @Test
    fun anyPictureHandedHereIsLaidOnTheCollageKeptLast() {
        val earlier = fixture.state()
        earlier.addPicture(fixture.picture("sky"))
        val keptAs = assertNotNull(earlier.file)

        val state = fixture.state()
        assertEquals(Room.COLLAGE, state.room)
        assertEquals("To a collage", state.offer(Handed.Picture(fixture.picture("cliff"))))
        state.take(Handed.Picture(fixture.picture("cliff")))
        assertEquals(keptAs, state.file, "onto the collage kept last")
        assertEquals(listOf("sky", "cliff"), state.collage.pieces.map { it.name })

        state.take(Handed.Drawn("Sketch 3") { fixture.pngBytes() })
        val drawn = state.collage.pieces.last()
        assertEquals("Sketch 3", drawn.name, "a sketch's page, written beside the collage and laid on it")
        assertEquals(keptAs.parentFile, drawn.source?.parentFile)
    }

    // ---- Pieces moved ----

    @Test
    fun aDragIsOneChangeKeptAndUndoneWhole() {
        val state = fixture.state()
        val piece = assertNotNull(state.addPicture(fixture.picture("cliff")))
        state.beginDrag()
        state.moveTo(piece.id, 300f, 200f)
        state.moveTo(piece.id, 320f, 210f)
        state.turnTo(piece.id, -30f)
        state.scaleTo(piece.id, 2f)
        assertEquals(Spot(piece.x, piece.y), kept(state).pieces.single().let { Spot(it.x, it.y) }, "not kept until the drag ends")
        state.endDrag()
        val moved = state.collage.pieces.single()
        assertEquals(Spot(320f, 210f), Spot(moved.x, moved.y))
        assertEquals(330f, moved.turn, "turned back past nothing: 330°")
        assertEquals(2f, moved.scale)
        assertEquals(state.collage, kept(state), "kept as the drag ended")

        state.undo()
        assertEquals(piece, state.collage.pieces.single(), "the whole drag undone at once")
        assertEquals(state.collage, kept(state))
        state.redo()
        assertEquals(moved, state.collage.pieces.single())
        state.undo()
        state.undo()
        assertTrue(state.collage.pieces.isEmpty(), "and the picture laid in before it")
        assertFalse(state.canUndo)
    }

    @Test
    fun theChosenPieceIsNudgedTurnedFlippedRestackedAndTakenAway() {
        val state = fixture.state()
        val (a, b, c) = listOf("a", "b", "c").map { assertNotNull(state.addPicture(fixture.picture(it))) }
        state.select(a.id)
        state.nudge(5f, -2f)
        state.turnBy(15f)
        state.flip()
        val moved = state.selectedPiece!!
        assertEquals(Spot(a.x + 5f, a.y - 2f), Spot(moved.x, moved.y))
        assertEquals(345f, moved.turn, "turned over, a piece turned one way lies turned the other")
        assertTrue(moved.flipped)

        state.forward()
        assertEquals(listOf(b.id, a.id, c.id), state.collage.pieces.map { it.id }, "one up the stack")
        state.forward()
        state.forward()
        assertEquals(listOf(b.id, c.id, a.id), state.collage.pieces.map { it.id }, "and no further than the top")
        state.backward()
        state.reorder(c.id, 0)
        assertEquals(listOf(c.id, b.id, a.id), state.collage.pieces.map { it.id })

        state.renamePiece(b.id, "  Sea  ")
        assertEquals("Sea", state.collage.pieces[1].name)
        state.remove()
        assertNull(state.selected)
        assertEquals(listOf(c.id, b.id), state.collage.pieces.map { it.id })
        assertEquals(state.collage, kept(state), "every change kept")
        state.undo()
        assertEquals(3, state.collage.pieces.size, "the piece taken away comes back")
    }

    @Test
    fun aClickTakesTheTopmostPieceUnderIt() {
        val state = fixture.state()
        val under = assertNotNull(state.addPicture(fixture.picture("under"), Spot(500f, 500f)))
        val over = assertNotNull(state.addPicture(fixture.picture("over"), Spot(600f, 500f)))
        assertEquals(over.id, state.pieceAt(Spot(550f, 500f))?.id, "where both are: the one on top")
        // Each lies 760 units wide: the one under from 120 to 880, the one over from 220.
        assertEquals(under.id, state.pieceAt(Spot(150f, 500f))?.id, "where only the one under is")
        assertNull(state.pieceAt(Spot(50f, 50f)))
    }

    // ---- The collage ----

    @Test
    fun aCollageIsRenamedNewAndOpenedAgain() {
        val state = fixture.state()
        assertTrue(state.rename("Cliff at dusk"), "named before anything is kept")
        state.addPicture(fixture.picture("cliff"))
        val first = assertNotNull(state.file)
        assertEquals("Cliff at dusk.collage.json", first.name)
        assertTrue(state.rename("Cliff, evening"))
        assertEquals("Cliff, evening.collage.json", state.file?.name)
        val renamed = assertNotNull(state.file)

        state.newCollage()
        assertNull(state.file)
        assertEquals(CollageState.UNTITLED, state.name)
        assertTrue(state.collage.pieces.isEmpty())
        state.addPicture(fixture.picture("sea"))
        assertFalse(state.rename("Cliff, evening"), "a name another collage has")
        assertEquals("There is a collage called Cliff, evening already", state.notice)

        assertTrue(state.open(renamed))
        assertEquals("Cliff, evening", state.name)
        assertEquals(listOf("cliff"), state.collage.pieces.map { it.name })
        assertFalse(state.canUndo, "a collage opened starts with nothing to undo")
        assertFalse(state.open(File(fixture.root, "nowhere.collage.json")))
    }

    @Test
    fun comingInOpensTheCollageKeptLastAndEscLetsGoThenLeaves() {
        fixture.state().apply { addPicture(fixture.picture("cliff")) }
        val state = fixture.state()
        state.onEnter()
        assertEquals(listOf("cliff"), state.collage.pieces.map { it.name })
        state.select(state.collage.pieces.single().id)
        state.stepBack()
        assertNull(state.selected)
        assertEquals(0, fixture.left)
        state.stepBack()
        assertEquals(1, fixture.left, "and then back to where the room was entered from")
    }
}
