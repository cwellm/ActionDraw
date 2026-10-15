package de.creaflect.actiondraw.board

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.rightClick
import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.SessionSetup
import de.creaflect.actiondraw.Settings
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.board.ui.BoardCanvas
import de.creaflect.actiondraw.concept.ConceptHost
import de.creaflect.actiondraw.concept.ConceptState
import de.creaflect.actiondraw.image.ThumbCache
import de.creaflect.actiondraw.ui.AtelierTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The ring menu (F9.5): a right click on a card opens six ways into the rooms round it, the
 * collage's dry, the full menu behind the middle; each does what the board already does, and the
 * ring is gone round with the keys.
 */
@OptIn(ExperimentalTestApi::class)
class RingMenuTest {
    @get:Rule
    val rule = createComposeRule()

    private val home: File = Files.createTempDirectory("ring-home").toFile()
    private val conceptsHome: File = Files.createTempDirectory("ring-concepts").toFile()
    private val config: File = Files.createTempDirectory("ring-cfg").toFile()
    private var drawn: List<File> = emptyList()
    private var sketchedOver: File? = null

    private val host = object : BoardHost {
        override fun startSession(root: File, images: List<File>, setup: SessionSetup?) { drawn = images }
        override fun showBoard() = Unit
        override fun showBoardList() = Unit
        override fun leaveBoard() = Unit
        override fun currentSetup() = SessionSetup(null, 120, true, ViewMode.NONE, GridMode.OFF)
        override fun sketchOver(picture: File) { sketchedOver = picture }
    }
    private val concepts = ConceptState(Settings(config), object : ConceptHost {
        override fun showConcepts() = Unit
        override fun showConcept() = Unit
        override fun leaveConcepts() = Unit
    }).also { it.setConceptsHomeDir(conceptsHome) }

    @After
    fun cleanup() {
        listOf(home, conceptsHome, config).forEach { it.deleteRecursively() }
    }

    /** A free board of two pictures, and a concept that is not linked to it yet. */
    private fun board(): Pair<BoardState, List<String>> {
        concepts.createConcept("Drache", "creature")
        val state = BoardState(Settings(config), host, concepts = concepts)
        state.createBoard(home, "Ring")
        val root = state.root!!
        state.importExternal(listOf("a.jpg", "b.jpg").map { File(root, it).apply { createNewFile() } })
        state.setLayout(BoardLayouts.FREE)
        state.clearSelection()
        rule.setContent { AtelierTheme { BoardCanvas(state, ThumbCache(config), textured = false, modifier = Modifier.fillMaxSize()) } }
        rule.waitForIdle()
        val canvas = rule.onNodeWithTag("canvas").fetchSemanticsNode().size
        state.fitAll(canvas.width.toFloat(), canvas.height.toFloat())
        rule.waitForIdle()
        return state to state.board!!.items.map { it.id }
    }

    private fun ring(id: String) {
        rule.onNodeWithTag("card-$id").performMouseInput { rightClick(center) }
        rule.waitForIdle()
        rule.onNodeWithTag("ring-menu").assertExists()
    }

    @Test
    fun aRightClickOpensTheRingAndCollageIsADryWell() {
        val (state, ids) = board()
        ring(ids[0])
        assertEquals(setOf(ids[0]), state.selection, "the right click took the card")
        listOf("ring-draw", "ring-sketch", "ring-concept", "ring-tag", "ring-group", "ring-more").forEach {
            rule.onNodeWithTag(it).assertIsEnabled()
        }
        rule.onNodeWithTag("ring-collage").assertIsNotEnabled()
    }

    @Test
    fun drawSketchAndGroupDoWhatTheBoardDoes() {
        val (state, ids) = board()
        ring(ids[0])
        rule.onNodeWithTag("ring-draw").performClick()
        rule.waitForIdle()
        assertEquals(listOf("a.jpg"), drawn.map { it.name }, "drawn: the picture right-clicked")
        rule.onNodeWithTag("ring-menu").assertDoesNotExist()

        ring(ids[1])
        rule.onNodeWithTag("ring-sketch").performClick()
        rule.waitForIdle()
        assertEquals("b.jpg", sketchedOver?.name, "into Sketch with it beside the page")

        ring(ids[0])
        rule.onNodeWithTag("ring-group").performClick()
        rule.waitForIdle()
        assertTrue(state.editor is BoardEditor.GroupSelection, "naming the group: ${state.editor}")
    }

    @Test
    fun toAConceptLinksItAndGivesTheCardToIt() {
        val (state, ids) = board()
        ring(ids[0])
        rule.onNodeWithTag("ring-concept").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("ring-concept-Drache").performClick()
        rule.waitForIdle()
        assertTrue(concepts.concept!!.id in state.linkedConcepts, "linked onto the board")
        assertEquals(1, state.board!!.items.count { state.isBorrowed(it) }, "the card is the concept's now")
        assertTrue(state.board!!.items.none { it.id == ids[0] }, "and the board's own card made way for it")
    }

    @Test
    fun theMiddleOpensTheFullMenu() {
        val (_, ids) = board()
        ring(ids[0])
        rule.onNodeWithTag("ring-more").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("ring-menu").assertDoesNotExist()
        rule.onNode(hasText("Bring forward")).assertExists()
    }

    @Test
    fun theKeysGoRoundTheRing() {
        val (state, ids) = board()
        ring(ids[1])
        rule.onNodeWithTag("ring-draw").assertIsFocused()
        rule.onNodeWithTag("ring-menu").performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        rule.onNodeWithTag("ring-sketch").assertIsFocused()
        rule.onNodeWithTag("ring-menu").performKeyInput { pressKey(Key.Enter) }
        rule.waitForIdle()
        assertEquals("b.jpg", sketchedOver?.name, "Enter takes the button in focus")
        rule.onNodeWithTag("ring-menu").assertDoesNotExist()
        assertEquals(setOf(ids[1]), state.selection)

        ring(ids[0])
        rule.onNodeWithTag("ring-menu").performKeyInput { pressKey(Key.Escape) }
        rule.waitForIdle()
        rule.onNodeWithTag("ring-menu").assertDoesNotExist()
    }
}
