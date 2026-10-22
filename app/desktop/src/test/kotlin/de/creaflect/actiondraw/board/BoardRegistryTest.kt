package de.creaflect.actiondraw.board

import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.SessionSetup
import de.creaflect.actiondraw.Settings
import de.creaflect.actiondraw.ViewMode
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A board is a *recorded* mapping from name to folder, not "whatever happens to sit under the
 * boards home". These tests pin the two things that buys: boards survive the home moving, and
 * deleting one acts on exactly the folder that board was in.
 */
class BoardRegistryTest {
    private val home: File = Files.createTempDirectory("registry-home").toFile()
    private val elsewhere: File = Files.createTempDirectory("registry-else").toFile()
    private val config: File = Files.createTempDirectory("registry-cfg").toFile()

    private val host = object : BoardHost {
        override fun startSession(root: File, images: List<File>, setup: SessionSetup?) = Unit
        override fun showBoard() = Unit
        override fun showBoardList() = Unit
        override fun leaveBoard() = Unit
        override fun currentSetup() = SessionSetup(null, 120, true, ViewMode.NONE, GridMode.OFF)
    }

    /**
     * A fresh state on the same config — i.e. the app started again. The boards home is set
     * explicitly: left unset, [Settings] falls back to the real one in the user's home, and the
     * test would scan (and adopt) whatever boards actually live there.
     */
    private fun newState(boardsHome: File = home) =
        BoardState(Settings(config), host).also { it.setBoardsHomeDir(boardsHome) }

    @AfterTest
    fun cleanup() {
        home.deleteRecursively()
        elsewhere.deleteRecursively()
        config.deleteRecursively()
    }

    // ---- (a) moving the boards home leaves the existing boards alone ----

    @Test
    fun boardsSurviveTheBoardsHomeMovingSomewhereElse() {
        val state = newState()
        state.createBoard(home, "Drachenbuch")
        val original = state.root!!

        val newHome = File(elsewhere, "Boards").apply { mkdirs() }
        state.setBoardsHomeDir(newHome)
        // Enough new boards to push the old one out of the five-deep recent list, so that only
        // the registry can still account for it.
        repeat(6) { state.createBoard(newHome, "Later $it") }

        // Restarting with the home where the user moved it — the old board is not under it.
        val listed = newState(newHome).availableBoards()
        assertTrue(
            listed.any { it.first == "Drachenbuch" },
            "the board is still listed although the home now points elsewhere: $listed",
        )
        assertEquals(
            original.absolutePath,
            listed.first { it.first == "Drachenbuch" }.second.absolutePath,
            "and at its own folder",
        )
        assertTrue(original.isDirectory, "which was never moved")
    }

    @Test
    fun aBoardOutsideTheHomeIsRememberedPastTheRecentList() {
        val state = newState()
        val outside = File(elsewhere, "Loose board").apply { mkdirs() }
        state.openBoard(outside)

        // Push it well out of the five-deep recent list.
        repeat(8) { state.createBoard(home, "Filler $it") }

        assertTrue(
            newState().availableBoards().any { it.second.absolutePath == outside.absolutePath },
            "the registry keeps it; the recent list alone would have dropped it",
        )
    }

    // ---- (b) deleting acts on the recorded folder ----

    @Test
    fun deletingABoardDropsItsEntry() {
        val state = newState()
        state.createBoard(home, "Doomed")
        val dir = state.root!!
        assertNotNull(state.entryFor(dir))

        state.deleteBoard(dir, BoardState.Deletion.DELETE_FOLDER)

        assertFalse(dir.exists(), "the recorded folder is the one that went")
        assertNull(newState().entryFor(dir), "and the record went with it")
        assertTrue(newState().availableBoards().isEmpty())
    }

    @Test
    fun theSameNameOpensTheBoardThatAlreadyHasIt() {
        val state = newState()
        state.createBoard(home, "Drachen")
        val first = state.root!!

        state.createBoard(home, "Drachen")

        assertEquals(first.absolutePath, state.root!!.absolutePath, "the existing board, not a second one")
        assertEquals(1, state.availableBoards().size)
    }

    @Test
    fun aLeftoverFolderDoesNotBlockTheName() {
        // Exactly the reported case: a folder from a board that was removed without its folder.
        val state = newState()
        val leftover = File(home, "Test").apply { mkdirs() }
        File(leftover, "orphan.jpg").writeText("picture")

        val error = state.createBoard(home, "Test")

        assertNull(error, "the name is usable: $error")
        assertEquals("Test", state.board!!.name, "the board is called what was asked for")
        assertTrue(
            state.root!!.absolutePath != leftover.absolutePath,
            "in a folder of its own, leaving the orphan alone",
        )
        assertTrue(File(leftover, "orphan.jpg").isFile, "and nothing of the leftover was touched")
    }

    // ---- whose folder is it ----

    @Test
    fun aFolderActionDrawMadeIsTheBoardsOwn() {
        val state = newState()
        state.createBoard(home, "Mine")
        assertEquals(true, state.entryFor(state.root!!)?.ownsFolder)
    }

    @Test
    fun anAdoptedFolderStaysTheUsersOwn() {
        val state = newState()
        val mine = File(elsewhere, "My drawings").apply { mkdirs() }
        File(mine, "a.jpg").writeText("picture")

        state.openBoard(mine) // what Explore… does

        assertEquals(
            false,
            state.entryFor(mine)?.ownsFolder,
            "a folder that was already the user's is not ActionDraw's to delete by default",
        )
    }

    @Test
    fun boardsFromBeforeTheRegistryAreAdoptedOnFirstListing() {
        // A board folder written straight to disk, as an older version would have left it.
        val old = File(home, "Legacy").apply { mkdirs() }
        BoardStore.save(old, BoardFile(name = "Legacy"))

        val state = newState()
        assertNull(state.entryFor(old), "unknown to begin with")

        assertEquals(listOf("Legacy"), state.availableBoards().map { it.first })
        assertEquals(
            true,
            state.entryFor(old)?.ownsFolder,
            "a direct child of the boards home is where New board… puts them",
        )
    }

    @Test
    fun aFolderDeletedInExplorerIsForgottenRatherThanListed() {
        val state = newState()
        state.createBoard(home, "Gone")
        val dir = state.root!!

        dir.deleteRecursively() // behind the app's back

        assertTrue(state.availableBoards().isEmpty())
        assertNull(newState().entryFor(dir), "the stale entry is pruned, not kept forever")
    }
}
