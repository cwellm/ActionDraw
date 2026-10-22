package de.creaflect.actiondraw.board

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.SessionSetup
import de.creaflect.actiondraw.Settings
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.board.ui.BoardScreen
import de.creaflect.actiondraw.image.ThumbCache
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The grid shows a subgroup as a section under its parent, folds it with the parent, and the
 * section's name selects the whole tree. Layout facts, so they go through the real screen.
 */
class SubgroupGridTest {
    @get:Rule
    val rule = createComposeRule()

    private val home: File = Files.createTempDirectory("sg-grid").toFile()
    private val config: File = Files.createTempDirectory("sg-grid-cfg").toFile()
    private val host = object : BoardHost {
        override fun startSession(root: File, images: List<File>, setup: SessionSetup?) = Unit
        override fun showBoard() = Unit
        override fun showBoardList() = Unit
        override fun leaveBoard() = Unit
        override fun currentSetup() = SessionSetup(null, 120, true, ViewMode.NONE, GridMode.OFF)
    }

    @After
    fun cleanup() {
        home.deleteRecursively()
        config.deleteRecursively()
    }

    /** Flügel (two cards) holding Membran (one card), shown in grid mode. */
    private fun shownTree(): Triple<BoardState, String, String> {
        val state = BoardState(Settings(config), host)
        state.createBoard(home, "Drachen")
        val root = state.root!!
        state.importExternal(listOf("a.jpg", "b.jpg", "c.jpg").map { File(root, it).apply { createNewFile() } })
        val ids = state.board!!.items.map { it.id }
        state.clearSelection(); ids.take(2).forEach { state.clickItem(it, ctrl = true, shift = false) }
        val wings = state.groupSelection("Flügel")!!
        state.clearSelection(); state.clickItem(ids[2], ctrl = false, shift = false)
        val membrane = state.groupSelection("Membran", parentId = wings)!!
        state.clearSelection()
        rule.setContent { BoardScreen(state, ThumbCache(config), isFullscreen = false, setFullscreen = {}) }
        rule.waitForIdle()
        return Triple(state, wings, membrane)
    }

    @Test
    fun aSubgroupHasItsOwnSectionUnderItsParent() {
        val (_, wings, membrane) = shownTree()
        val parent = rule.onNodeWithTag("group-header-$wings", useUnmergedTree = true).fetchSemanticsNode()
        val child = rule.onNodeWithTag("group-header-$membrane", useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(child.boundsInRoot.top > parent.boundsInRoot.top, "the subgroup's section comes after its parent's")
        assertTrue(child.boundsInRoot.left > parent.boundsInRoot.left, "and sits indented under it")
    }

    @Test
    fun collapsingTheParentHidesTheSubgroupsSection() {
        val (state, wings, membrane) = shownTree()
        rule.onNodeWithTag("group-header-$membrane", useUnmergedTree = true).assertIsDisplayed()

        state.toggleCollapsed(wings)
        rule.waitForIdle()

        rule.onNodeWithTag("group-header-$membrane", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theParentsNameSelectsItsSubgroupsCardsToo() {
        val (state, wings, _) = shownTree()
        rule.onNodeWithTag("group-header-$wings", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        assertEquals(3, state.selection.size, "two of its own and the one in Membran")
    }
}
