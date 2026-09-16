package de.creaflect.actiondraw

import androidx.compose.ui.input.key.Key
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
 */
class SessionKeysTest {
    private val settingsDir: File = Files.createTempDirectory("actiondraw-keys").toFile()

    @AfterTest
    fun cleanup() {
        settingsDir.deleteRecursively()
    }

    private fun state() = AppState(Settings(settingsDir))

    private fun press(
        key: Key,
        state: AppState,
        isFullscreen: Boolean = false,
        setFullscreen: (Boolean) -> Unit = {},
    ) = handleSessionShortcut(key, state, isFullscreen, setFullscreen)

    // ---- Light ----

    @Test
    fun commaCoolsAndPeriodWarms() {
        val state = state()
        assertTrue(press(Key.Period, state))
        assertEquals(0.1f, state.temperature, 0.0001f)
        press(Key.Period, state)
        assertEquals(0.2f, state.temperature, 0.0001f)
        press(Key.Comma, state)
        press(Key.Comma, state)
        press(Key.Comma, state)
        assertEquals(-0.1f, state.temperature, 0.0001f, "past neutral and into the cool half")
    }

    @Test
    fun theLightNeverLeavesTheSliderRange() {
        val state = state()
        repeat(30) { press(Key.Period, state) }
        assertEquals(1f, state.temperature, 0.0001f)
        repeat(60) { press(Key.Comma, state) }
        assertEquals(-1f, state.temperature, 0.0001f)
    }

    @Test
    fun zeroPutsTheLightBackToNeutral() {
        val state = state()
        repeat(4) { press(Key.Period, state) }
        assertTrue(press(Key.Zero, state))
        assertEquals(0f, state.temperature, 0.0001f)
    }

    @Test
    fun theLightIsIndependentOfTheViewMode() {
        val state = state()
        press(Key.Period, state)
        press(Key.Nine, state) // Notan
        assertEquals(ViewMode.NOTAN, state.viewMode)
        assertEquals(0.1f, state.temperature, 0.0001f, "a Notan study can still be lit warm")
    }

    // ---- The number row, now that Warm/Cool are gone ----

    @Test
    fun oneThroughNineSelectTheNineViewModesInOrder() {
        val keys = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
        assertEquals(ViewMode.entries.size, keys.size, "every view mode has a key, and no key is spare")
        val state = state()
        keys.forEachIndexed { index, key ->
            assertTrue(press(key, state))
            assertEquals(ViewMode.entries[index], state.viewMode)
        }
    }

    // ---- Unchanged neighbours, so the remap did not disturb them ----

    @Test
    fun theOtherSessionKeysStillDoTheirJob() {
        val state = state()
        val auto = state.autoAdvance
        press(Key.A, state)
        assertEquals(!auto, state.autoAdvance)
        press(Key.I, state)
        assertTrue(state.invert)
        press(Key.B, state)
        assertTrue(state.blur)
        assertFalse(press(Key.Q, state), "an unbound key is left for someone else")
    }

    @Test
    fun escapeLeavesFullscreenBeforeItEndsTheSession() {
        val state = state()
        var fullscreen = true
        press(Key.Escape, state, isFullscreen = true) { fullscreen = it }
        assertFalse(fullscreen, "the first Esc only restores the window")
    }
}
