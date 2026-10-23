package de.creaflect.actiondraw

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Motion in the app (F9.4): reduced motion as the person chose it. The session's controls stepping
 * back are tested with the Practice room, the timelines where they live: the receding chrome's in
 * the design contract, the bloom's in the atelier.
 */
@OptIn(ExperimentalTestApi::class)
class MotionTest {
    @get:Rule
    val rule = createComposeRule()

    private val pictures: File = Files.createTempDirectory("motion-pics").toFile()
    private val config: File = Files.createTempDirectory("motion-cfg").toFile()

    @After
    fun cleanup() {
        pictures.deleteRecursively()
        config.deleteRecursively()
    }

    @Test
    fun reducedMotionIsWhatWasChosenOnceItIsChosen() {
        val lib = FileLib(config)
        lib.preferences.setReducedMotion(true)
        assertTrue(AppState(lib).reducedMotion)
        val app = AppState(lib)
        app.setReducedMotionPreference(false)
        assertEquals(false, AppState(FileLib(config)).reducedMotion, "and it is remembered")
    }
}
