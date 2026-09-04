package de.creaflect.actiondraw.board

import androidx.compose.ui.input.key.Key
import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.SessionSetup
import de.creaflect.actiondraw.Settings
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.board.ui.handleBoardShortcut
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The board's key mapping. What matters here is *which* command a key runs: plain `G` used to
 * make an empty group even with cards selected, so grouping silently did nothing to them — a
 * mistake no test of the commands themselves could catch.
 */
class BoardKeysTest {
    private val home: File = Files.createTempDirectory("keys-home").toFile()
    private val config: File = Files.createTempDirectory("keys-cfg").toFile()
    private val host = object : BoardHost {
        override fun startSession(root: File, images: List<File>, setup: SessionSetup?) = Unit
        override fun showBoard() = Unit
        override fun showBoardList() = Unit
        override fun leaveBoard() = Unit
        override fun currentSetup() = SessionSetup(null, 120, true, ViewMode.NONE, GridMode.OFF)
    }

    @AfterTest
    fun cleanup() {
        home.deleteRecursively()
        config.deleteRecursively()
    }

    private val board: BoardState by lazy { boardWithTwoCards() }

    private fun boardWithTwoCards(): BoardState {
        val state = BoardState(Settings(config), host)
        state.createBoard(home, "Keys")
        val files = listOf("a.jpg", "b.jpg").map { File(state.root!!, it).apply { createNewFile() } }
        state.importExternal(files)
        state.clearSelection()
        return state
    }

    private fun press(key: Key, ctrl: Boolean = false, shift: Boolean = false): Boolean =
        handleBoardShortcut(key, ctrl, shift, state = board, isFullscreen = false, setFullscreen = {})

    @Test
    fun gGroupsTheSelectionWhenThereIsOne() {
        board.selectAll()

        press(Key.G)

        assertIs<BoardEditor.GroupSelection>(
            board.editor,
            "G with cards selected must offer to group them, not make an empty group",
        )
        board.groupSelection("Wings")
        assertEquals(2, board.itemsIn(board.sortedGroups.single().id).size)
    }

    @Test
    fun gStartsAnEmptyGroupWhenNothingIsSelected() {
        press(Key.G)
        assertIs<BoardEditor.NewGroup>(board.editor)
    }

    @Test
    fun ctrlGDoesTheSameAsG() {
        board.selectAll()
        press(Key.G, ctrl = true)
        assertIs<BoardEditor.GroupSelection>(board.editor)
    }

    @Test
    fun ctrlShiftGUngroups() {
        board.selectAll()
        board.groupSelection("Wings")
        board.selectAll()

        press(Key.G, ctrl = true, shift = true)

        assertEquals(emptyList(), board.sortedGroups, "the emptied group is tidied away")
        assertEquals(2, board.itemsIn(null).size, "and its cards are back in the Inbox")
    }
}
