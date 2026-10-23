package de.creaflect.actiondraw.board

import de.creaflect.actiondraw.FileLib
import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.SessionSetup
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.board.ui.cardMenuItems
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A picture Live Sketch saved has its strokes beside it (`name.sketch.json` next to `name.png`);
 * from its card on a board it can be taken up again where it was left (a concept's page: ConceptTest).
 */
class SketchCardTest {
    private val home: File = Files.createTempDirectory("sketch-card").toFile()
    private val config: File = Files.createTempDirectory("sketch-card-cfg").toFile()
    private var opened: File? = null

    private val boardHost = object : BoardHost {
        override fun startSession(root: File, images: List<File>, setup: SessionSetup?) = Unit
        override fun showBoard() = Unit
        override fun showBoardList() = Unit
        override fun leaveBoard() = Unit
        override fun currentSetup() = SessionSetup(null, 120, true, ViewMode.NONE, GridMode.OFF)
        override fun openSketch(file: File) { opened = file }
    }

    @AfterTest
    fun cleanup() {
        listOf(home, config).forEach { it.deleteRecursively() }
    }

    @Test
    fun aSketchedPictureOnABoardCanBeContinuedAPhotoCannot() {
        val state = BoardState(FileLib(config), boardHost)
        state.createBoard(home, "Buch")
        val root = state.root!!
        val png = File(root, "Sketch 1.png").apply { createNewFile() }
        val doc = File(root, "Sketch 1.sketch.json").apply { writeText("{}") }
        val photo = File(root, "photo.jpg").apply { createNewFile() }
        state.importExternal(listOf(png, photo))
        val sketched = state.board!!.items.filterIsInstance<ImageItem>().first { it.path == png.name }
        val plain = state.board!!.items.filterIsInstance<ImageItem>().first { it.path == photo.name }

        assertEquals(doc, state.sketchOf(sketched))
        assertNull(state.sketchOf(plain))
        assertTrue(cardMenuItems(state, sketched).any { it.label == "Continue in Live Sketch" })
        assertFalse(cardMenuItems(state, plain).any { it.label == "Continue in Live Sketch" })

        state.openSketch(sketched)
        assertEquals(doc, opened, "handed to Live Sketch through the host")
    }
}
