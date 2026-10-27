package de.creaflect.actiondraw

import androidx.compose.ui.input.key.Key
import de.creaflect.actiondraw.lens.LensState
import de.creaflect.actiondraw.ui.Optic
import de.creaflect.actiondraw.ui.handleLensKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The lens's key table is pure wiring, and wiring is exactly what unit tests of the state class
 * cannot catch — a key pointing at the wrong mode compiles perfectly. These press the keys.
 */
class LensKeysTest {
    private fun press(key: Key, lens: LensState) = handleLensKey(key, lens)

    // ---- Light ----

    @Test
    fun commaCoolsAndPeriodWarms() {
        val lens = LensState()
        assertTrue(press(Key.Period, lens))
        assertEquals(0.1f, lens.temperature, 0.0001f)
        press(Key.Period, lens)
        assertEquals(0.2f, lens.temperature, 0.0001f)
        press(Key.Comma, lens)
        press(Key.Comma, lens)
        press(Key.Comma, lens)
        assertEquals(-0.1f, lens.temperature, 0.0001f, "past neutral and into the cool half")
    }

    @Test
    fun theLightNeverLeavesTheSliderRange() {
        val lens = LensState()
        repeat(30) { press(Key.Period, lens) }
        assertEquals(1f, lens.temperature, 0.0001f)
        repeat(60) { press(Key.Comma, lens) }
        assertEquals(-1f, lens.temperature, 0.0001f)
    }

    @Test
    fun zeroPutsTheLightBackToNeutral() {
        val lens = LensState()
        repeat(4) { press(Key.Period, lens) }
        assertTrue(press(Key.Zero, lens))
        assertEquals(0f, lens.temperature, 0.0001f)
    }

    @Test
    fun theLightIsIndependentOfTheViewMode() {
        val lens = LensState()
        press(Key.Period, lens)
        press(Key.Nine, lens) // Notan
        assertEquals(ViewMode.NOTAN, lens.viewMode)
        assertEquals(0.1f, lens.temperature, 0.0001f, "a Notan study can still be lit warm")
    }

    // ---- The number row, now that Warm/Cool are gone ----

    @Test
    fun oneThroughNineSelectTheNineViewModesInOrder() {
        val keys = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
        assertEquals(ViewMode.entries.size, keys.size, "every view mode has a key, and no key is spare")
        val lens = LensState()
        keys.forEachIndexed { index, key ->
            assertTrue(press(key, lens))
            assertEquals(ViewMode.entries[index], lens.viewMode)
        }
    }

    // ---- The knobs ----

    @Test
    fun eachKnobHasItsKey() {
        val lens = LensState()
        press(Key.B, lens)
        assertTrue(lens.blur)
        press(Key.I, lens)
        assertTrue(lens.invert)
        press(Key.D, lens)
        assertTrue(lens.defraction)
        press(Key.M, lens)
        assertTrue(lens.mirror)
        press(Key.U, lens)
        assertTrue(lens.upsideDown)
        press(Key.G, lens)
        assertEquals(GridMode.THIRDS, lens.gridMode)
        press(Key.N, lens)
        assertEquals(ViewMode.NOTAN, lens.viewMode)
        press(Key.B, lens)
        assertFalse(lens.blur, "and the same key turns it off again")
    }

    @Test
    fun escapeAndUnboundKeysAreNotTheLenss() {
        val lens = LensState()
        assertFalse(press(Key.Escape, lens), "what Esc steps back from goes beyond the lens")
        assertFalse(press(Key.Q, lens))
        assertFalse(press(Key.Spacebar, lens))
    }

    // ---- The glass ----

    @Test
    fun lTakesTheGlassUpAndPutsItAwayAndOTurnsTheOptic() {
        val lens = LensState()
        assertTrue(press(Key.L, lens))
        assertTrue(lens.glass.inHand)
        assertTrue(press(Key.O, lens))
        assertEquals(Optic.CONVEX, lens.glass.optic)
        val m = lens.glass.magnification
        assertTrue(press(Key.Plus, lens))
        assertTrue(lens.glass.magnification > m, "+ brings the glass nearer")
        press(Key.Minus, lens)
        press(Key.Minus, lens)
        assertTrue(lens.glass.magnification < m)
        press(Key.L, lens)
        assertFalse(lens.glass.inHand)
        assertFalse(press(Key.Plus, lens), "without the glass, + is nobody's")
    }

    @Test
    fun wLaysTheGlassOverTheWholePictureAndTakesItBack() {
        val lens = LensState()
        assertTrue(press(Key.W, lens))
        assertNotNull(lens.glass.spread)
        assertTrue(lens.glass.inHand)
        assertTrue(press(Key.W, lens))
        assertNull(lens.glass.spread)
        assertTrue(lens.glass.inHand, "back in the hand")
    }
}
