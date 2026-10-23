package de.creaflect.actiondraw

import androidx.compose.ui.input.key.Key
import de.creaflect.actiondraw.practice.PracticePage
import de.creaflect.actiondraw.practice.PracticeState
import de.creaflect.actiondraw.ui.handleSessionShortcut
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The session shortcut table is pure wiring, and wiring is exactly what unit tests of the state
 * class cannot catch — a key pointing at the wrong mode compiles perfectly. These press the keys.
 * The lens's own keys are the lens kit's (`LensKeysTest`); here they only have to reach it.
 */
class SessionKeysTest {
    private val settingsDir: File = Files.createTempDirectory("actiondraw-keys").toFile()

    @AfterTest
    fun cleanup() {
        settingsDir.deleteRecursively()
    }

    private fun state() = PracticeState(FileLib(settingsDir))

    private fun press(
        key: Key,
        state: PracticeState,
        isFullscreen: Boolean = false,
        setFullscreen: (Boolean) -> Unit = {},
    ) = handleSessionShortcut(key, state, isFullscreen, setFullscreen)

    @Test
    fun theLensKeysReachTheSessionsLens() {
        val state = state()
        assertTrue(press(Key.Nine, state))
        assertEquals(ViewMode.NOTAN, state.lens.viewMode)
        assertTrue(press(Key.Period, state))
        assertEquals(0.1f, state.lens.temperature, 0.0001f)
        assertTrue(press(Key.L, state))
        assertTrue(state.lens.glass.inHand)
    }

    @Test
    fun theOtherSessionKeysStillDoTheirJob() {
        val state = state()
        val auto = state.autoAdvance
        press(Key.A, state)
        assertEquals(!auto, state.autoAdvance)
        press(Key.I, state)
        assertTrue(state.lens.invert)
        press(Key.B, state)
        assertTrue(state.lens.blur)
        assertFalse(press(Key.Q, state), "an unbound key is left for someone else")
    }

    @Test
    fun escapeLeavesFullscreenBeforeItEndsTheSession() {
        val state = state()
        var fullscreen = true
        press(Key.Escape, state, isFullscreen = true) { fullscreen = it }
        assertFalse(fullscreen, "the first Esc only restores the window")
    }

    @Test
    fun escapeLiftsTheGlassThenPutsItAwayBeforeAnythingElse() {
        val state = state()
        val screen = state.page
        var fullscreen = true
        state.lens.glass.setDown(state.currentImage, 0.3f, 0.3f)
        press(Key.Escape, state, isFullscreen = true) { fullscreen = it }
        assertEquals(null, state.lens.glass.focus, "first the whole picture again")
        assertTrue(state.lens.glass.inHand)
        assertTrue(fullscreen)
        press(Key.Escape, state, isFullscreen = true) { fullscreen = it }
        assertFalse(state.lens.glass.inHand, "then the glass put away")
        assertTrue(fullscreen)
        press(Key.Escape, state, isFullscreen = true) { fullscreen = it }
        assertFalse(fullscreen, "then the window")
        assertEquals(screen, state.page, "and the session is still on")
        press(Key.Escape, state, isFullscreen = false) { fullscreen = it }
        assertEquals(PracticePage.Summary, state.page, "and only then the session ended")
    }

    @Test
    fun hCoversAndUncoversTheReference() {
        val state = state()
        assertTrue(press(Key.H, state))
        assertTrue(state.referenceFlipped)
        press(Key.H, state)
        assertFalse(state.referenceFlipped)
    }
}
