package de.creaflect.actiondraw

import de.creaflect.actiondraw.image.SeenStore
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Behavioural tests for AppState against a real temp folder (empty files pass ImageScanner). */
class AppStateTest {
    private val dir: File = Files.createTempDirectory("actiondraw-state").toFile()

    private fun makeImages(vararg names: String) {
        names.forEach { File(dir, it).createNewFile() }
    }

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    @Test
    fun pickerSelectionLimitsThePool() {
        makeImages("a.jpg", "b.jpg", "c.jpg")
        val state = AppState()
        state.selectFolder(dir)
        state.toggleSelected("b.jpg") // deselect b -> selection = {a, c}
        assertEquals(2, state.selectedCount)

        state.start()
        assertEquals(setOf("a.jpg", "c.jpg"), state.pool.map { it.name }.toSet())
    }

    @Test
    fun togglingEverythingBackOnRestoresAllMode() {
        makeImages("a.jpg", "b.jpg")
        val state = AppState()
        state.selectFolder(dir)
        state.toggleSelected("a.jpg")
        assertEquals(1, state.selectedCount)
        state.toggleSelected("a.jpg") // back to the full set -> "all images" (null selection)
        assertEquals(null, state.selection)
        assertEquals(2, state.selectedCount)
    }

    @Test
    fun manualModeCountsOvertimeInsteadOfSwitching() {
        makeImages("a.jpg", "b.jpg")
        val state = AppState()
        state.selectFolder(dir)
        state.intervalSeconds = 2
        state.autoAdvance = false
        state.start()

        val first = state.currentImage?.name
        repeat(5) { state.tick() }
        assertEquals(first, state.currentImage?.name) // no auto-switch
        assertEquals(1, state.sessionPoses)
        assertEquals(0, state.remainingSeconds)
        assertEquals(3, state.overtimeSeconds) // 5 elapsed - 2 interval
        assertEquals(5, state.sessionSeconds) // drawing time keeps counting
    }

    @Test
    fun autoAdvanceSwitchesAtTheInterval() {
        makeImages("a.jpg", "b.jpg")
        val state = AppState()
        state.selectFolder(dir)
        state.intervalSeconds = 2
        state.start()

        repeat(2) { state.tick() }
        assertEquals(2, state.sessionPoses) // advanced exactly once
        assertEquals(0, state.elapsedSeconds)
        assertEquals(0, state.overtimeSeconds)
    }

    @Test
    fun freshCycleForgetsOnlyTheSelectedImagesSeenState() {
        makeImages("a.jpg", "b.jpg", "c.jpg")
        // b was seen in some earlier session and is NOT part of the selection.
        SeenStore.write(dir, setOf("b.jpg"))

        val state = AppState()
        state.selectFolder(dir)
        state.toggleSelected("b.jpg") // selection = {a, c}
        state.start()
        assertEquals(2, state.pool.size)

        // Draw through the whole selection -> triggers a fresh cycle for {a, c} only.
        state.next()
        state.next()

        val seen = SeenStore.read(dir)
        assertTrue("b.jpg" in seen, "unselected image must keep its seen state")
        assertTrue("a.jpg" !in seen && "c.jpg" !in seen, "selected images were reset for the new cycle")
        assertEquals(setOf("a.jpg", "c.jpg"), state.pool.map { it.name }.toSet())
    }
}
