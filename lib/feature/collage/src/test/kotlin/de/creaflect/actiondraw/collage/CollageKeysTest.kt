package de.creaflect.actiondraw.collage

import androidx.compose.ui.input.key.Key
import de.creaflect.actiondraw.collage.ui.handleCollageKey
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Collage room's keys: the chosen piece's, the collage's own, and Esc. */
class CollageKeysTest {
    private val fixture = CollageFixture()

    @AfterTest
    fun cleanup() = fixture.cleanup()

    @Test
    fun theKeysWorkTheChosenPiece() {
        val state = fixture.state()
        val a = assertNotNull(state.addPicture(fixture.picture("a")))
        val b = assertNotNull(state.addPicture(fixture.picture("b")))
        state.select(a.id)
        fun key(k: Key, ctrl: Boolean = false, shift: Boolean = false) = assertTrue(handleCollageKey(k, ctrl, shift, state), "$k is the room's")
        key(Key.DirectionRight)
        key(Key.DirectionDown, shift = true)
        assertEquals(a.x + 2f, state.selectedPiece!!.x)
        assertEquals(a.y + 25f, state.selectedPiece!!.y)
        key(Key.E)
        key(Key.Q, shift = true)
        assertEquals(346f, state.selectedPiece!!.turn, "a degree one way, fifteen the other")
        key(Key.F)
        assertTrue(state.selectedPiece!!.flipped)
        key(Key.PageUp)
        assertEquals(listOf(b.id, a.id), state.collage.pieces.map { it.id })
        key(Key.LeftBracket)
        assertEquals(listOf(a.id, b.id), state.collage.pieces.map { it.id })
        key(Key.Z, ctrl = true)
        assertEquals(listOf(b.id, a.id), state.collage.pieces.map { it.id }, "Ctrl+Z")
        key(Key.Y, ctrl = true)
        key(Key.Delete)
        assertEquals(listOf(b.id), state.collage.pieces.map { it.id })
        assertFalse(handleCollageKey(Key.A, false, false, state))
    }

    @Test
    fun theToolsAndTheCutBeingMadeAreOnKeys() {
        val state = fixture.state()
        val piece = assertNotNull(state.addPicture(fixture.picture("cliff"), Spot(950f, 650f)))
        fun key(k: Key) = assertTrue(handleCollageKey(k, false, false, state), "$k is the room's")
        key(Key.S)
        assertEquals(Tool.SCISSORS, state.tool)
        key(Key.R)
        assertEquals(Tool.TEAR, state.tool)
        key(Key.T)
        assertEquals(Tool.TAPE, state.tool)
        key(Key.M)
        assertEquals(Tool.MOVE, state.tool)
        key(Key.S)
        assertFalse(handleCollageKey(Key.Enter, false, false, state), "no cut being made: Enter is not the room's")
        state.trace(listOf(piece.toMat(Spot(300f, -20f)), piece.toMat(Spot(300f, 220f))))
        key(Key.Enter)
        assertEquals(listOf("cliff", "cliff, cut"), state.collage.pieces.map { it.name }, "Enter cuts along the corners so far")
        assertTrue(state.path.isEmpty())
        key(Key.L)
        assertTrue(state.throughAll, "L: through every layer")
        key(Key.L)
        assertFalse(state.throughAll, "and through one again")
        assertTrue(handleCollageKey(Key.S, true, false, state))
        assertEquals(CollageExport.Save, state.export, "Ctrl+S: saved as a picture, once drawn")
    }

    @Test
    fun ctrlKeysAddAndBeginAndEscLetsGoThenLeaves() {
        val state = fixture.state()
        fixture.chosen = fixture.picture("wing")
        assertTrue(handleCollageKey(Key.O, true, false, state))
        assertEquals("wing", state.collage.pieces.single().name)
        assertTrue(handleCollageKey(Key.Escape, false, false, state))
        assertNull(state.selected)
        handleCollageKey(Key.Escape, false, false, state)
        assertEquals(1, fixture.left)
        assertTrue(handleCollageKey(Key.N, true, false, state))
        assertTrue(state.collage.pieces.isEmpty(), "Ctrl+N: a new collage")
    }
}
