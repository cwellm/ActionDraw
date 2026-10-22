package de.creaflect.actiondraw.board

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import de.creaflect.actiondraw.AppState
import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.SessionSetup
import de.creaflect.actiondraw.Settings
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.board.ui.MenuExtras
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
    fun settingsOpenFromTheMenuAndChangeThePreference() {
        val settings = Settings(config)
        val boards = BoardState(settings, host)
        rule.setContent { MenuExtras(AppState(settings), boards) }
        rule.waitForIdle()

        rule.onNodeWithTag("menu-settings").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("settings-snap").performClick()
        rule.waitForIdle()

        assertTrue(boards.snapping, "the checkbox is the preference")
        assertTrue(BoardState(Settings(config), host).snapping, "and it is remembered")
    }

    @Test
    fun hotkeysOpenFromTheMenu() {
        val settings = Settings(config)
        rule.setContent { MenuExtras(AppState(settings), BoardState(settings, host)) }
        rule.waitForIdle()

        rule.onNodeWithTag("menu-hotkeys").performClick()
        rule.waitForIdle()

        rule.onNode(hasText("Idea Board")).assertIsDisplayed()
        rule.onNode(hasText("Drawing session")).assertIsDisplayed()
    }
}
