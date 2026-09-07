package de.creaflect.actiondraw.board

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.SessionSetup
import de.creaflect.actiondraw.Settings
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.board.ui.BoardViewer
import de.creaflect.actiondraw.image.ThumbCache
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The wheel over the large view — which is what a tablet's dial sends — must zoom. That is a
 * pointer-routing fact, and the canvas has taught us that those only show up when a real event
 * is dispatched through the real composable.
 */
@OptIn(ExperimentalTestApi::class)
class ViewerWheelTest {
    @get:Rule
    val rule = createComposeRule()

    private val home: File = Files.createTempDirectory("wheel-home").toFile()
    private val config: File = Files.createTempDirectory("wheel-cfg").toFile()
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

    private fun viewerOverTwoPictures(): BoardState {
        val state = BoardState(Settings(config), host)
        state.createBoard(home, "Wheel")
        state.importExternal(listOf("a.jpg", "b.jpg").map { File(state.root!!, it).apply { createNewFile() } })
        state.selectAll()
        state.openViewer()
        return state
    }

    @Test
    fun wheelUpOverTheViewerZoomsIn() {
        val state = viewerOverTwoPictures()
        rule.setContent { BoardViewer(state, ThumbCache()) }

        rule.onNodeWithTag("viewer").performMouseInput {
            moveTo(center)
            scroll(-1f)
        }
        rule.waitForIdle()

        assertTrue(state.viewerZoom > 1f, "zoom is ${state.viewerZoom}")
        assertEquals(0, state.viewerIndex, "and the wheel no longer flips to the next picture")
    }

    @Test
    fun wheelDownAtFittedSizeStaysFitted() {
        val state = viewerOverTwoPictures()
        rule.setContent { BoardViewer(state, ThumbCache()) }

        rule.onNodeWithTag("viewer").performMouseInput {
            moveTo(center)
            scroll(1f)
        }
        rule.waitForIdle()

        assertEquals(1f, state.viewerZoom)
    }
}
