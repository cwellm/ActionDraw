package de.creaflect.actiondraw.board

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.SessionSetup
import de.creaflect.actiondraw.Settings
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.board.ui.BoardCanvas
import de.creaflect.actiondraw.image.ThumbCache
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals

/**
 * Pointer behaviour of the freeform canvas. Selection lives in [BoardState], but *what a click
 * selects* is decided by Compose hit-testing and gesture consumption — which only the real
 * composable can answer. This exists because a canvas-wide tap handler once cleared the selection
 * a card click had just made, so multi-select was impossible while every unit test stayed green.
 */
class CanvasSelectionTest {
    @get:Rule
    val rule = createComposeRule()

    private val home: File = Files.createTempDirectory("canvas-sel").toFile()
    private val config: File = Files.createTempDirectory("canvas-cfg").toFile()

    private fun boardWithTwoCards(): BoardState {
        val host = object : BoardHost {
            override fun startSession(root: File, images: List<File>, setup: SessionSetup?) = Unit
            override fun showBoard() = Unit
            override fun showBoardList() = Unit
            override fun leaveBoard() = Unit
            override fun currentSetup() = SessionSetup(null, 120, true, ViewMode.NONE, GridMode.OFF)
        }
        val state = BoardState(Settings(config), host)
        state.createBoard(home, "Canvas")
        val root = state.root!!
        val files = listOf("a.jpg", "b.jpg").map { File(root, it).apply { createNewFile() } }
        state.importExternal(files)
        state.setLayout(BoardLayouts.FREE)
        state.clearSelection()
        return state
    }

    @Test
    fun clickingACardSelectsItAndItStaysSelected() {
        val state = boardWithTwoCards()
        val first = state.board!!.items.first().id
        rule.setContent {
            BoardCanvas(state, ThumbCache(config), textured = false, modifier = Modifier.fillMaxSize())
        }

        rule.onNodeWithTag("card-$first").performClick()
        rule.waitForIdle()

        assertEquals(setOf(first), state.selection, "a click must leave the card selected")
    }

    @Test
    fun clickingASecondCardMovesTheSelectionRatherThanClearingIt() {
        val state = boardWithTwoCards()
        val (first, second) = state.board!!.items.map { it.id }
        rule.setContent {
            BoardCanvas(state, ThumbCache(config), textured = false, modifier = Modifier.fillMaxSize())
        }

        rule.onNodeWithTag("card-$first").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("card-$second").performClick()
        rule.waitForIdle()

        assertEquals(setOf(second), state.selection)
    }
}
