package de.creaflect.actiondraw.collage

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The scissors, the tear and the tape without a window: a piece cut across or round, where its parts
 * lie, what they are called and which is chosen; a tear; and tape that holds pieces so they move as
 * one.
 */
class CuttingTest {
    private val fixture = CollageFixture()

    @AfterTest
    fun cleanup() = fixture.cleanup()

    /** The collage as its document on disk has it. */
    private fun kept(state: CollageState): Collage = assertNotNull(fixture.lib.collages.read(assertNotNull(state.file)))

    private fun assertNear(want: Spot, got: Spot, what: String) {
        assertEquals(want.x, got.x, 0.05f, what)
        assertEquals(want.y, got.y, 0.05f, what)
    }

    /** A 400 × 200 picture laid with its middle at [at]: 760 × 380 on the mat. */
    private fun CollageState.lay(name: String, at: Spot): Piece = assertNotNull(addPicture(fixture.picture(name), at))

    @Test
    fun aCutAcrossAPiecePartsItWhereItLiesAndTheCutOffPartIsChosen() {
        val state = fixture.state()
        state.lay("cliff", Spot(950f, 650f))
        state.turnBy(30f)
        state.flip()
        val piece = state.collage.pieces.single()
        // Across the picture three quarters of the way along it, however it is turned and flipped.
        assertTrue(state.cut(listOf(piece.toMat(Spot(300f, -20f)), piece.toMat(Spot(300f, 220f))), closed = false))
        val (kept, cut) = state.collage.pieces
        assertEquals(piece.id, kept.id, "the larger part is the piece still")
        assertEquals("cliff", kept.name)
        assertEquals("cliff, cut", cut.name)
        assertEquals(cut.id, state.selected, "the part cut off chosen")
        assertEquals(60000f, areaOf(kept.outline), 1f)
        assertEquals(20000f, areaOf(cut.outline), 1f)
        assertNear(piece.toMat(Spot(0f, 0f)), kept.toMat(Spot(0f, 0f)), "the piece lies where it lay")
        assertNear(piece.toMat(Spot(400f, 200f)), cut.toMat(Spot(400f, 200f)), "and so does the part cut off")
        assertEquals(state.collage, kept(state), "kept")

        state.undo()
        assertEquals(listOf(piece), state.collage.pieces, "one change to undo")
    }

    @Test
    fun aCutRoundAPartCutsItOutAndLeavesAHole() {
        val state = fixture.state()
        val piece = state.lay("cliff", Spot(950f, 650f))
        val ring = listOf(Spot(50f, 50f), Spot(150f, 50f), Spot(150f, 150f), Spot(50f, 150f)).map(piece::toMat)
        assertTrue(state.cut(ring, closed = true))
        val (left, out) = state.collage.pieces
        assertEquals(piece.id, left.id)
        assertEquals("cliff, cut", out.name)
        assertEquals(out.id, state.selected, "the part cut out chosen, on top")
        assertEquals(10000f, areaOf(out.outline), 1f)
        assertEquals(70000f, areaOf(left.outline), 1f)
        val middle = piece.toMat(Spot(100f, 100f))
        assertEquals(out.id, state.pieceAt(middle)?.id)
        state.remove(out.id)
        assertNull(state.pieceAt(middle), "a hole where it was")
        assertEquals(piece.id, state.pieceAt(piece.toMat(Spot(300f, 100f)))?.id)
    }

    @Test
    fun aCutRoundTheLargerPartStillCutsThatPartOut() {
        val state = fixture.state()
        val piece = state.lay("cliff", Spot(950f, 650f))
        // Round the left three quarters, over three of its edges.
        val ring = listOf(Spot(-20f, -20f), Spot(300f, -20f), Spot(300f, 220f), Spot(-20f, 220f)).map(piece::toMat)
        assertTrue(state.cut(ring, closed = true))
        val (left, out) = state.collage.pieces
        assertEquals(piece.id, left.id, "what the cut went round is cut out, however large")
        assertEquals(20000f, areaOf(left.outline), 1f)
        assertEquals(60000f, areaOf(out.outline), 1f)
        assertEquals(out.id, state.selected)
    }

    @Test
    fun aCutThroughNothingSaysSoAndChangesNothing() {
        val state = fixture.state()
        state.lay("cliff", Spot(950f, 650f))
        val before = state.collage
        assertFalse(state.cut(listOf(Spot(100f, 100f), Spot(300f, 100f)), closed = false))
        assertEquals("Cut across a piece from edge to edge, or all the way round a part of it", state.notice)
        assertEquals(before, state.collage)
        state.undo()
        assertTrue(state.collage.pieces.isEmpty(), "nothing to undo but the picture laid")
    }

    @Test
    fun theChosenPieceIsCutElseTheTopmostTheCutGoesThrough() {
        val state = fixture.state()
        val under = state.lay("under", Spot(800f, 650f))
        val over = state.lay("over", Spot(1100f, 650f))
        val across = listOf(Spot(950f, 400f), Spot(950f, 900f))
        state.select(null)
        assertTrue(state.cut(across, closed = false))
        assertEquals(listOf("under", "over", "over, cut"), state.collage.pieces.map { it.name }, "the one on top")
        state.undo()
        state.select(under.id)
        assertTrue(state.cut(across, closed = false))
        assertEquals(listOf("under", "under, cut", "over"), state.collage.pieces.map { it.name }, "the chosen one, under the other")
        assertEquals(over, state.collage.pieces.last())
    }

    @Test
    fun throughAllLayersEveryPictureUnderTheCutIsCutAsOneChange() {
        val state = fixture.state()
        val under = state.lay("under", Spot(800f, 650f))
        state.lay("over", Spot(1100f, 650f))
        state.lay("loose", Spot(1500f, 1100f))
        val tape = state.layTape(Spot(900f, 700f), Spot(1000f, 700f))
        state.cutThroughAll(true)
        // Chosen or not, every picture under the cut is cut; the one on top is the one gone on with.
        state.select(under.id)
        val across = listOf(Spot(950f, 400f), Spot(950f, 900f))
        assertTrue(state.cut(across, closed = false))
        assertEquals(listOf("under", "under, cut", "over", "over, cut", "loose", "Tape"), state.collage.pieces.map { it.name }, "both it went through, each part above its piece")
        assertEquals("over, cut", state.selectedPiece?.name, "the topmost one's part cut off chosen")
        assertEquals(tape.outline, state.collage.pieces.last().outline, "tape is not cut")
        assertEquals(4, state.collage.pieces.last().holds.size, "and holds the parts of both under it")
        state.undo()
        assertEquals(listOf("under", "over", "loose", "Tape"), state.collage.pieces.map { it.name }, "one change to undo")

        assertTrue(state.tear(across))
        assertEquals(listOf("under", "under, torn", "over", "over, torn", "loose", "Tape"), state.collage.pieces.map { it.name }, "torn through all of them too")
        state.undo()
        state.cutThroughAll(false)
        assertTrue(state.cut(across, closed = false))
        assertEquals(5, state.collage.pieces.size, "and through one alone again")
    }

    @Test
    fun aPartCutAgainIsCalledAfterThePiece() {
        val state = fixture.state()
        val piece = state.lay("cliff", Spot(950f, 650f))
        state.cut(listOf(piece.toMat(Spot(300f, -20f)), piece.toMat(Spot(300f, 220f))), closed = false)
        // Through the part cut off, still chosen: its own part lies just above it.
        state.cut(listOf(piece.toMat(Spot(350f, -20f)), piece.toMat(Spot(350f, 220f))), closed = false)
        assertEquals(listOf("cliff", "cliff, cut", "cliff, cut 2"), state.collage.pieces.map { it.name })
    }

    @Test
    fun aTearPartsAPieceAlongARaggedEdge() {
        val state = fixture.state()
        val piece = state.lay("cliff", Spot(950f, 650f))
        assertTrue(state.tear(listOf(Spot(1000f, 400f), Spot(1000f, 900f))))
        val (kept, torn) = state.collage.pieces
        assertEquals(piece.id, kept.id)
        assertEquals("cliff, torn", torn.name)
        assertTrue(torn.outline.size > 20, "ragged along the tear: ${torn.outline.size} corners")
        assertEquals(80000f, areaOf(kept.outline) + areaOf(torn.outline), 2f, "nothing lost but the tear")

        state.undo()
        state.choose(Tool.TEAR)
        state.trace(listOf(Spot(1000f, 400f), Spot(1000f, 900f)))
        assertTrue(state.cutAlongPath(closed = false))
        assertEquals("cliff, torn", state.collage.pieces.last().name, "the path drawn with the tear in hand is torn")
        assertTrue(state.path.isEmpty())
    }

    @Test
    fun tapeLaidAcrossPiecesHoldsThemSoTheyMoveAsOne() {
        val state = fixture.state()
        val a = state.lay("a", Spot(600f, 650f))
        val b = state.lay("b", Spot(1400f, 650f))
        val loose = state.lay("loose", Spot(1000f, 1200f))
        val tape = state.layTape(Spot(900f, 650f), Spot(1100f, 650f))
        assertEquals(setOf(a.id, b.id), tape.holds.toSet(), "the two it lies across, not the one below")
        assertEquals(tape.id, state.selected)
        assertEquals(tape, state.collage.pieces.last(), "on top of everything")
        assertEquals(200f, tape.outline.maxOf { it.x }, "as long as it was drawn")
        assertEquals(setOf(a.id, b.id, tape.id), state.heldWith(a.id))

        state.select(a.id)
        state.nudge(10f, 0f)
        fun at(id: String) = state.collage.pieces.first { it.id == id }
        assertEquals(1410f, at(b.id).x, "moved with it")
        assertEquals(1010f, at(tape.id).x)
        assertEquals(loose.x, at(loose.id).x, "what is not taped stays")

        state.turnBy(90f)
        assertNear(Spot(610f, 650f + 800f), Spot(at(b.id).x, at(b.id).y), "turned about the piece taken hold of")
        assertEquals(90f, at(b.id).turn)
        state.undo()
        state.flip()
        assertNear(Spot(2 * 610f - 1410f, 650f), Spot(at(b.id).x, at(b.id).y), "turned over about it")
        assertTrue(at(b.id).flipped)
        state.undo()

        state.select(tape.id)
        state.remove()
        state.select(a.id)
        state.nudge(10f, 0f)
        assertEquals(1410f, at(b.id).x, "untaped, it stays")

        val dab = state.layTape(Spot(100f, 100f), Spot(102f, 101f))
        assertEquals(CollageState.TAPE_LENGTH, dab.outline.maxOf { it.x }, "a click lays a short strip")
        assertEquals(0f, dab.turn)
        assertTrue(dab.holds.isEmpty(), "on the bare mat it holds nothing")
    }

    @Test
    fun tapeAcrossAPieceCutApartHoldsThePartUnderIt() {
        val state = fixture.state()
        val piece = state.lay("cliff", Spot(950f, 650f))
        val tape = state.layTape(Spot(1250f, 500f), Spot(1250f, 800f))
        state.select(piece.id)
        // The smaller part, on the right, is the one the tape lies on.
        state.cut(listOf(Spot(1100f, 400f), Spot(1100f, 900f)), closed = false)
        // Beside the tape, on the part of the piece under it.
        val right = assertNotNull(state.pieceAt(Spot(1200f, 650f)))
        assertFalse(right.isTape)
        assertEquals(listOf(right.id), state.collage.pieces.first { it.id == tape.id }.holds, "the part under the tape, not the other")
    }

    @Test
    fun tapeIsNotCutAndLetsGoOfWhatIsTakenAway() {
        val state = fixture.state()
        val piece = state.lay("cliff", Spot(950f, 650f))
        val tape = state.layTape(Spot(800f, 650f), Spot(1100f, 650f))
        state.select(null)
        assertTrue(state.cut(listOf(Spot(950f, 400f), Spot(950f, 900f)), closed = false))
        val after = state.collage.pieces.single { it.isTape }
        assertEquals(tape.outline, after.outline, "the picture under it cut, not the tape")
        assertEquals(state.collage.pieces.filter { !it.isTape }.map { it.id }.toSet(), after.holds.toSet(), "which holds both its parts now")
        state.undo()
        state.remove(piece.id)
        assertTrue(state.collage.pieces.single().holds.isEmpty(), "a piece taken away is held no more")
    }

    @Test
    fun escDropsTheCutThenLetsGoThenPutsMoveInHandThenLeaves() {
        val state = fixture.state()
        val piece = state.lay("cliff", Spot(950f, 650f))
        state.choose(Tool.SCISSORS)
        state.trace(listOf(Spot(1f, 1f), Spot(2f, 2f)))
        state.choose(Tool.SCISSORS)
        assertTrue(state.path.isEmpty(), "a tool taken up again drops the path")
        state.trace(listOf(Spot(1f, 1f), Spot(2f, 2f)))
        state.stepBack()
        assertTrue(state.path.isEmpty())
        assertEquals(piece.id, state.selected)
        state.stepBack()
        assertNull(state.selected)
        assertEquals(Tool.SCISSORS, state.tool)
        state.stepBack()
        assertEquals(Tool.MOVE, state.tool)
        assertEquals(0, fixture.left)
        state.stepBack()
        assertEquals(1, fixture.left)
    }
}
