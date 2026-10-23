package de.creaflect.actiondraw.lens

import de.creaflect.actiondraw.FileLib
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.platform.SystemClipboard
import de.creaflect.actiondraw.ui.Optic
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The Lens room without a window: what it opens, how it lays the picture out, and every way out. */
class LensRoomStateTest {
    private val fixture = LensRoomFixture()

    @AfterTest
    fun cleanup() = fixture.cleanup()

    // ---- The picture ----

    @Test
    fun aPictureComesFromAFileOrTheClipboard() {
        val state = fixture.state()
        assertEquals(false, state.chooseFile(), "nothing chosen, nothing open")
        assertNull(state.picture)

        val pose = fixture.picture("pose")
        fixture.chosen = pose
        assertTrue(state.chooseFile())
        assertEquals(LensPicture.OnDisk(pose), state.picture)
        assertEquals(pose, state.file)

        val other = fixture.picture("other")
        fixture.pasted = SystemClipboard.Pasted.Files(listOf(File(fixture.pictures, "notes.txt").apply { writeText("x") }, other))
        assertTrue(state.paste())
        assertEquals(other, state.file, "the first picture among the files pasted")

        fixture.pasted = SystemClipboard.Pasted.Bitmap(fixture.pngBytes(40, 20))
        assertTrue(state.paste())
        val pasted = assertIs<LensPicture.Pasted>(state.picture)
        assertNull(state.file, "a pasted picture is no file")
        assertEquals(40, assertNotNull(state.decode(pasted)).width, "but decodes as one")

        fixture.pasted = null
        assertEquals(false, state.paste())
        assertSame(pasted, state.picture, "nothing pasted, the picture stays")
        assertEquals("There is no picture on the clipboard", state.notice)
    }

    @Test
    fun anotherPictureStartsWhole() {
        val state = fixture.state()
        val pose = fixture.picture("pose")
        state.open(pose)
        state.first.glass.setDown(pose, 0.3f, 0.3f)
        state.open(fixture.picture("other"))
        assertNull(state.first.glass.focus)
    }

    // ---- How it is laid out ----

    @Test
    fun twoLensesStartAsTwoAndTheTrayWorksTheOneChosen() {
        val state = fixture.state()
        state.open(fixture.picture("pose"))
        state.choose(LensSide.SECOND)
        assertSame(state.first, state.lens, "alone, there is only the one lens")

        state.arrange(LensLayout.TWO)
        assertEquals(ViewMode.EDGE, state.second.viewMode, "Edge beside the first, until it is turned")
        state.choose(LensSide.SECOND)
        assertSame(state.second, state.lens)
        assertTrue(state.otherLens())
        assertSame(state.first, state.lens, "Tab: the other one")

        state.choose(LensSide.SECOND)
        state.arrange(LensLayout.BESIDE)
        assertSame(state.first, state.lens, "beside the original, the lens is the first")
        assertEquals(false, state.otherLens())

        val fresh = fixture.state()
        fresh.first.viewMode = ViewMode.EDGE
        fresh.arrange(LensLayout.TWO)
        assertEquals(ViewMode.NOTAN, fresh.second.viewMode, "never the same lens twice")
    }

    // ---- Out of the room ----

    @Test
    fun theViewIsSavedWhereThePersonChose() {
        val state = fixture.state()
        state.save()
        assertNull(state.export, "nothing open, nothing to save")

        state.open(fixture.picture("pose"))
        state.first.viewMode = ViewMode.NOTAN
        state.save()
        assertEquals("pose - notan.png", fixture.suggested, "named after the picture and its lens")
        assertNull(state.export, "the dialog cancelled")

        val target = File(fixture.root, "out/view.png")
        fixture.saveTo = target
        state.save()
        assertEquals(LensExport.Save(target), state.export, "waiting for the screen to draw it")
        val png = fixture.pngBytes()
        state.finish(png)
        assertTrue(png.contentEquals(target.readBytes()))
        assertEquals("Saved as view.png", state.notice)
        assertNull(state.export)
    }

    @Test
    fun aViewIsPinnedToABoardBesideItsOriginal() {
        val state = fixture.state()
        val pose = fixture.picture("pose")
        state.open(pose)
        state.pinTo(fixture.board)
        assertEquals(LensExport.Pin(fixture.board), state.export)
        state.finish(fixture.pngBytes())
        val view = File(fixture.board, "pose - seen.png")
        assertTrue(view.isFile, "written into the board's folder")
        assertEquals(listOf(fixture.board to listOf(pose, view)), fixture.pinned, "and pinned there with its original")
        assertEquals("Pinned 2 to Buch.", state.notice)

        state.pinTo(fixture.board)
        state.finish(fixture.pngBytes())
        assertEquals(File(fixture.board, "pose - seen (2).png"), fixture.pinned.last().second.last(), "a second one beside the first")

        state.openPng(fixture.pngBytes(), "Pasted picture")
        state.pinTo(fixture.board)
        state.finish(fixture.pngBytes())
        assertEquals(listOf(File(fixture.board, "Pasted picture - seen.png")), fixture.pinned.last().second, "a pasted picture has no original to pin")
    }

    @Test
    fun aViewThatCouldNotBeDrawnIsSaidSo() {
        val state = fixture.state()
        state.open(fixture.picture("pose"))
        state.pinTo(fixture.board)
        state.finish(null)
        assertEquals("The view could not be drawn", state.notice)
        assertTrue(fixture.pinned.isEmpty())
        assertTrue(fixture.board.listFiles()!!.isEmpty(), "and nothing written")
    }

    @Test
    fun theGlassIsAsItWasLeftInTheLastRun() {
        val state = fixture.state()
        state.first.glass.choose(Optic.PRISM)
        assertEquals(Optic.PRISM, LensRoomState(FileLib(fixture.home)).first.glass.optic)
    }

    @Test
    fun leavingGoesBackThroughTheHost() {
        fixture.state().leave()
        assertEquals(1, fixture.left)
    }
}
