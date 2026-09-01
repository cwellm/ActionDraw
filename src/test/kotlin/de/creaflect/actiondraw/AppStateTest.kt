package de.creaflect.actiondraw

import de.creaflect.actiondraw.image.SeenStore
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Behavioural tests for AppState against a real temp folder (empty files pass ImageScanner). */
class AppStateTest {
    private val dir: File = Files.createTempDirectory("actiondraw-state").toFile()

    /** Isolated config dir, so tests never read or clobber the real ~/.actiondraw. */
    private val settingsDir: File = Files.createTempDirectory("actiondraw-config").toFile()

    private fun newState() = AppState(Settings(settingsDir))

    private fun makeImages(vararg names: String) {
        names.forEach { File(dir, it).createNewFile() }
    }

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
        settingsDir.deleteRecursively()
    }

    @Test
    fun pickerSelectionLimitsThePool() {
        makeImages("a.jpg", "b.jpg", "c.jpg")
        val state = newState()
        state.selectFolder(dir)
        state.toggleSelected("b.jpg") // deselect b -> selection = {a, c}
        assertEquals(2, state.selectedCount)

        state.start()
        assertEquals(setOf("a.jpg", "c.jpg"), state.pool.map { it.name }.toSet())
    }

    @Test
    fun togglingEverythingBackOnRestoresAllMode() {
        makeImages("a.jpg", "b.jpg")
        val state = newState()
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
        val state = newState()
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
        val state = newState()
        state.selectFolder(dir)
        state.intervalSeconds = 2
        state.start()

        repeat(2) { state.tick() }
        assertEquals(2, state.sessionPoses) // advanced exactly once
        assertEquals(0, state.elapsedSeconds)
        assertEquals(0, state.overtimeSeconds)
    }

    @Test
    fun defractionRollsAFreshSeedOnEverySwitchOn() {
        val state = newState()
        val seeds = mutableSetOf<Float>()
        repeat(4) {
            state.toggleDefraction() // on -> rolls a seed
            assertTrue(state.defraction)
            seeds += state.defractionSeed
            state.toggleDefraction() // off
        }
        assertTrue(seeds.size > 1, "seed must differ between activations (got $seeds)")
    }

    @Test
    fun freshCycleForgetsOnlyTheSelectedImagesSeenState() {
        makeImages("a.jpg", "b.jpg", "c.jpg")
        // b was seen in some earlier session and is NOT part of the selection.
        SeenStore.write(dir, setOf("b.jpg"))

        val state = newState()
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

    @Test
    fun boardSessionUsesTheExplicitPoolAndRestoresPracticeAfterwards() {
        makeImages("a.jpg", "b.jpg")
        val state = newState()
        state.selectFolder(dir)

        val boardRoot = Files.createTempDirectory("actiondraw-boardroot").toFile()
        try {
            val sub = File(boardRoot, "wings").apply { mkdirs() }
            val w1 = File(sub, "w1.jpg").apply { createNewFile() }
            val w2 = File(sub, "w2.jpg").apply { createNewFile() }

            state.startBoardSession(boardRoot, listOf(w1, w2))
            // Board sessions run in their own window; the main screen is left alone.
            assertEquals(Screen.Session, state.boardWindowScreen)
            assertEquals(Screen.Menu, state.screen)
            assertEquals(setOf("w1.jpg", "w2.jpg"), state.pool.map { it.name }.toSet())

            state.next() // marks the first pose seen -> key must be the relative path
            val seen = SeenStore.read(boardRoot)
            assertTrue(seen.isNotEmpty() && seen.all { it.startsWith("wings/") }, "expected relative keys, got $seen")

            state.stop()
            assertEquals(Screen.Summary, state.boardWindowScreen)

            state.start() // "Go again" replays the board pool, not the practice folder
            assertEquals(Screen.Session, state.boardWindowScreen)
            assertEquals(setOf("w1.jpg", "w2.jpg"), state.pool.map { it.name }.toSet())
            state.stop()

            state.backToMenu() // closes the session window and restores practice state
            assertEquals(null, state.boardWindowScreen)
            assertEquals(dir.absolutePath, state.folder?.absolutePath, "practice folder restored")
        } finally {
            boardRoot.deleteRecursively()
        }
    }

    @Test
    fun lastFolderIsRememberedAcrossRestarts() {
        makeImages("a.jpg", "b.jpg")
        newState().selectFolder(dir) // "previous run"

        val restarted = newState() // fresh app start, same config dir
        assertEquals(dir.absolutePath, restarted.folder?.absolutePath)
        assertEquals(2, restarted.totalCount) // images were scanned, so Start is usable right away
    }

    @Test
    fun aLastFolderThatNoLongerExistsIsIgnored() {
        Settings(settingsDir).setLastFolder(File(dir, "deleted-since-last-run"))

        val restarted = newState()
        assertNull(restarted.folder)
        assertEquals(0, restarted.totalCount)
    }
}
