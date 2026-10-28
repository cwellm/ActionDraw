package de.creaflect.actiondraw.lens

import androidx.compose.ui.input.key.Key
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.lens.ui.handleLensRoomKey
import de.creaflect.actiondraw.platform.SystemClipboard
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Lens room's keys, pressed: its own, and the lens's while there is a picture. */
class LensRoomKeysTest {
    private val fixture = LensRoomFixture()

    @AfterTest
    fun cleanup() = fixture.cleanup()

    private fun press(key: Key, state: LensRoomState, ctrl: Boolean = false) = handleLensRoomKey(key, ctrl, state)

    @Test
    fun ctrlOpensPastesAndSaves() {
        val state = fixture.state()
        fixture.chosen = fixture.picture("pose")
        assertTrue(press(Key.O, state, ctrl = true))
        assertEquals("pose", state.picture?.name)
        fixture.pasted = SystemClipboard.Pasted.Files(listOf(fixture.picture("other")))
        assertTrue(press(Key.V, state, ctrl = true))
        assertEquals("other", state.picture?.name)
        fixture.saveTo = File(fixture.root, "view.png")
        assertTrue(press(Key.S, state, ctrl = true))
        assertEquals(LensExport.Save(fixture.saveTo!!), state.export)
        assertFalse(press(Key.Q, state, ctrl = true))
    }

    @Test
    fun theLensKeysWorkTheChosenLensOnceThereIsAPicture() {
        val state = fixture.state()
        assertFalse(press(Key.Nine, state), "nothing to look at yet")
        assertEquals(ViewMode.NONE, state.first.viewMode)
        state.open(fixture.picture("pose"))
        assertTrue(press(Key.Nine, state))
        assertEquals(ViewMode.NOTAN, state.first.viewMode)

        state.arrange(LensLayout.TWO)
        assertTrue(press(Key.Tab, state), "Tab: the other lens")
        assertTrue(press(Key.Two, state))
        assertEquals(ViewMode.GRAYSCALE, state.second.viewMode, "the second, chosen")
        assertEquals(ViewMode.NOTAN, state.first.viewMode, "the first left as it was")
    }

    @Test
    fun tabIsNobodysWithOneLens() {
        val state = fixture.state()
        state.open(fixture.picture("pose"))
        assertFalse(press(Key.Tab, state))
    }

    @Test
    fun escapeLiftsTheGlassPutsItAwayAndThenLeaves() {
        val state = fixture.state()
        val pose = fixture.picture("pose")
        state.open(pose)
        state.first.glass.setDown(pose, 0.5f, 0.5f)
        assertTrue(press(Key.Escape, state))
        assertNull(state.first.glass.focus, "first the whole picture again")
        assertTrue(state.first.glass.inHand)
        press(Key.Escape, state)
        assertFalse(state.first.glass.inHand, "then the glass put away")
        assertEquals(0, fixture.left)
        press(Key.Escape, state)
        assertEquals(1, fixture.left, "and only then out of the room")
    }
}
