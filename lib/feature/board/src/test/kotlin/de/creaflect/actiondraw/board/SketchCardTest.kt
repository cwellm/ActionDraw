package de.creaflect.actiondraw.board

import de.creaflect.actiondraw.FileLib
import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.Room
import de.creaflect.actiondraw.SessionSetup
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.board.ui.cardMenuItems
import de.creaflect.actiondraw.handoff.Handed
import de.creaflect.actiondraw.handoff.Handoff
import de.creaflect.actiondraw.handoff.Route
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A card's picture leaves the board along the hand-off seam's routes: the card's menu lists the
 * ways the seam has, and taking one hands the card's own file on. Which room takes what — and that
 * Sketch goes on with a sketched picture — is the seam's to say, not the board's.
 */
class SketchCardTest {
    private val home: File = Files.createTempDirectory("sketch-card").toFile()
    private val config: File = Files.createTempDirectory("sketch-card-cfg").toFile()
    private val sent = mutableListOf<Pair<Handed, Room>>()

    private val boardHost = object : BoardHost {
        override fun startSession(root: File, images: List<File>, setup: SessionSetup?) = Unit
        override fun showBoard() = Unit
        override fun showBoardList() = Unit
        override fun leaveBoard() = Unit
        override fun currentSetup() = SessionSetup(null, 120, true, ViewMode.NONE, GridMode.OFF)
    }

    private val handoff = object : Handoff {
        override fun routes(what: Handed, from: Room) =
            if (from == Room.BOARDS) listOf(Route(Room.SKETCH, "Sketch over"), Route(Room.LENS, "Check the values")) else emptyList()
        override fun send(what: Handed, to: Room) {
            sent += what to to
        }
    }

    @AfterTest
    fun cleanup() {
        listOf(home, config).forEach { it.deleteRecursively() }
    }

    @Test
    fun aCardsPictureLeavesAlongTheSeamsRoutes() {
        val state = BoardState(FileLib(config), boardHost, handoff = handoff)
        state.createBoard(home, "Buch")
        val photo = File(state.root!!, "photo.jpg").apply { createNewFile() }
        state.importExternal(listOf(photo))
        val card = state.board!!.items.filterIsInstance<ImageItem>().single()

        assertEquals(listOf(Route(Room.SKETCH, "Sketch over"), Route(Room.LENS, "Check the values")), state.routes(card))
        val menu = cardMenuItems(state, card)
        assertEquals(listOf("Sketch over", "Check the values"), menu.map { it.label }.filter { it == "Sketch over" || it == "Check the values" }, "in the card's menu")
        menu.single { it.label == "Check the values" }.onClick()
        assertEquals(listOf<Pair<Handed, Room>>(Handed.Picture(photo) to Room.LENS), sent, "the card's own file, handed on")
    }

    @Test
    fun aSketchedPictureWearsTheTapeOfLiveSketch() {
        val state = BoardState(FileLib(config), boardHost, handoff = handoff)
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
    }
}
