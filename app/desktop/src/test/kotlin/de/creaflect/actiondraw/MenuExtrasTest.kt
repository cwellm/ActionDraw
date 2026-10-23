package de.creaflect.actiondraw

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import de.creaflect.actiondraw.board.BoardHost
import de.creaflect.actiondraw.board.BoardState
import de.creaflect.actiondraw.practice.PracticeState
import de.creaflect.actiondraw.ui.PaletteScreen
import de.creaflect.actiondraw.ui.ProvideAtelier
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertTrue

/** Settings and the hotkeys reachable from the menu, and the settings really settings. */
class MenuExtrasTest {
    @get:Rule
    val rule = createComposeRule()

    private val config: File = Files.createTempDirectory("extras-cfg").toFile()
    private val host = object : BoardHost {
        override fun startSession(root: File, images: List<File>, setup: SessionSetup?) = Unit
        override fun showBoard() = Unit
        override fun showBoardList() = Unit
        override fun leaveBoard() = Unit
        override fun currentSetup() = SessionSetup(null, 120, true, ViewMode.NONE, GridMode.OFF)
    }

    @After
    fun cleanup() {
        config.deleteRecursively()
    }

    @Test
    fun theStartMenuShowsSettingsAndHotkeys() {
        // The start menu is the palette now; Settings and Hotkeys sit in its corner.
        val lib = FileLib(config)
        val app = AppState(lib)
        val boards = BoardState(lib, host)
        val wells = paletteWells(lastBoard = null, sketchOpen = false)
        rule.setContent {
            ProvideAtelier {
                PaletteScreen(wells, app.paletteRoom, app::selectRoom, onOpen = {}, corner = { MenuExtras(app, PracticeState(lib), boards) })
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("menu-settings").assertIsDisplayed()
        rule.onNodeWithTag("menu-hotkeys").assertIsDisplayed()
    }

    @Test
    fun settingsOpenFromTheMenuAndChangeThePreference() {
        val lib = FileLib(config)
        val boards = BoardState(lib, host)
        rule.setContent { ProvideAtelier { MenuExtras(AppState(lib), PracticeState(lib), boards) } }
        rule.waitForIdle()

        rule.onNodeWithTag("menu-settings").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("settings-snap").performClick()
        rule.waitForIdle()

        assertTrue(boards.snapping, "the checkbox is the preference")
        assertTrue(BoardState(FileLib(config), host).snapping, "and it is remembered")
    }

    @Test
    fun hotkeysOpenFromTheMenu() {
        val lib = FileLib(config)
        rule.setContent { ProvideAtelier { MenuExtras(AppState(lib), PracticeState(lib), BoardState(lib, host)) } }
        rule.waitForIdle()

        rule.onNodeWithTag("menu-hotkeys").performClick()
        rule.waitForIdle()

        rule.onNode(hasText("Idea Board")).assertIsDisplayed()
        rule.onNode(hasText("Drawing session")).assertIsDisplayed()
    }
}
