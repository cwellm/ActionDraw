package de.creaflect.actiondraw.collage

import de.creaflect.actiondraw.Room
import de.creaflect.actiondraw.board.BoardRef
import de.creaflect.actiondraw.handoff.Handed
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Collage room's ways in and out without a window: the source drawer drawing from a board or a
 * concept, and the collage saved as a picture beside it, pinned to a board and taken into the other
 * rooms — each once the screen has drawn it.
 */
class WaysInAndOutTest {
    private val fixture = CollageFixture()

    @AfterTest
    fun cleanup() = fixture.cleanup()

    @Test
    fun theDrawerDrawsFromTheFirstBoardOrAConceptAsOftenAsYouLike() {
        fixture.boardPictures = listOf(fixture.picture("sea"), fixture.picture("rock"))
        File(fixture.concept, "wing.png").writeBytes(fixture.pngBytes())
        fixture.conceptPictures = listOf("wing.png", "notes.txt")
        val state = fixture.state()
        state.onEnter()
        assertEquals(DrawerSource.Board(BoardRef("Drachenbuch", fixture.board)), state.source, "the first board, to begin with")
        assertEquals(fixture.boardPictures, state.sourcePictures)

        val sea = state.sourcePictures.first()
        state.addPicture(sea)
        state.addPicture(sea)
        assertEquals(listOf("sea", "sea"), state.collage.pieces.map { it.name }, "as often as you like, each a piece of its own")

        state.drawFrom(DrawerSource.Concept(state.concepts().single()))
        assertEquals(listOf(File(fixture.concept, "wing.png")), state.sourcePictures, "a concept's pictures, and nothing else of it")
        state.onEnter()
        assertEquals("Wing", state.source?.name, "coming in again keeps what it draws from")

        fixture.boardPictures = fixture.boardPictures + fixture.picture("sky")
        state.drawFrom(DrawerSource.Board(state.boards().single()))
        assertEquals(3, state.sourcePictures.size, "read as the board is now")

        val alone = CollageState(fixture.lib)
        alone.onEnter()
        assertNull(alone.source, "no boards and no concepts: nothing to draw from")
        assertTrue(alone.sourcePictures.isEmpty())
    }

    @Test
    fun theCollageIsSavedAsAPictureBesideItPinnedAndTakenOn() {
        val state = fixture.state()
        assertTrue(state.routes().isEmpty(), "nothing on the mat, nowhere to take it")
        state.saveAsPicture()
        assertNull(state.export)
        assertEquals("There is nothing on the mat yet", state.notice)

        state.addPicture(fixture.picture("cliff"))
        val beside = File(state.file!!.parentFile, "Untitled collage.png")
        assertEquals(beside, state.pictureFile())
        assertEquals(listOf(Room.SKETCH, Room.LENS), state.routes().map { it.to })

        state.saveAsPicture()
        assertEquals(CollageExport.Save, state.export, "waiting for the screen to draw it")
        state.finish(fixture.pngBytes(10, 10))
        assertNull(state.export)
        assertTrue(beside.isFile)
        assertEquals("Saved as Untitled collage.png, beside the collage", state.notice)

        state.pinTo(fixture.board)
        state.finish(fixture.pngBytes(20, 10))
        assertEquals(listOf(fixture.board to listOf(beside)), fixture.pinned, "pinned as it is saved")
        assertEquals("Pinned to Drachenbuch", state.notice)
        assertEquals(20, ImageIO.read(beside).width, "saved again as it is now, over the one before")

        state.handOff(Room.LENS)
        assertTrue(fixture.sent.isEmpty(), "not before it is drawn")
        state.finish(fixture.pngBytes())
        assertEquals(listOf<Pair<Handed, Room>>(Handed.Picture(beside) to Room.LENS), fixture.sent)

        state.saveAsPicture()
        state.finish(null)
        assertEquals("The collage could not be drawn", state.notice)

        assertTrue(state.rename("Cliff study"))
        val renamed = File(beside.parentFile, "Cliff study.png")
        assertTrue(renamed.isFile, "renamed with its picture")
        assertEquals(renamed, state.pictureFile())
    }
}
