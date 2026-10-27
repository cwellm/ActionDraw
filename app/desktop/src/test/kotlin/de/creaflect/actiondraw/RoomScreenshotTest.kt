package de.creaflect.actiondraw

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import de.creaflect.actiondraw.board.BoardLayouts
import de.creaflect.actiondraw.board.NoteKind
import de.creaflect.actiondraw.lens.LensLayout
import de.creaflect.actiondraw.ui.Atelier
import de.creaflect.actiondraw.ui.Form
import de.creaflect.actiondraw.ui.Optic
import de.creaflect.actiondraw.ui.ProvideAtelier
import de.creaflect.sketch.Lead
import de.creaflect.sketch.PageSize
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.awt.BasicStroke
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every room as the app shows it (F9.9): the whole shell — the room, its pigment line, the
 * dialogs' layer — at the window's starting size, chosen and running. Each is saved to
 * `build/screenshots` to be looked at, and held to what must be true of every screen: no label
 * cut off at the bottom or losing lines without a "…" (Text clips what overflows its box, and a
 * too-tight row did that to every toolbar label once), and the room's line along the top.
 *
 * Pixel-exact goldens are left out on purpose: a font or Skia update would fail them all at once
 * without anything being wrong.
 */
@OptIn(ExperimentalTestApi::class)
class RoomScreenshotTest {
    private val root: File = Files.createTempDirectory("rooms").toFile()
    private val out = File("build/screenshots").apply { mkdirs() }

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
    }

    /** The rooms as `main` puts them together — the same [Rooms] — on folders of their own. */
    private inner class World {
        val rooms = Rooms(FileLib(File(root, "config").apply { mkdirs() }))
        val app = rooms.app
        val practice = rooms.practice
        val boards = rooms.boards
        val concepts = rooms.concepts
        val sketch = rooms.sketch

        init {
            concepts.setConceptsHomeDir(File(root, "concepts"))
            boards.setBoardsHomeDir(File(root, "boards").apply { mkdirs() })
            sketch.setSketchesHomeDir(File(root, "sketches"))
        }

        fun show(test: ComposeUiTest) = test.setContent {
            ProvideAtelier {
                App(rooms, isFullscreen = false, onToggleFullscreen = {}, setFullscreen = {})
            }
        }
    }

    /** A picture to draw from: a figure on a squared ground, so a glass's bending shows. */
    private fun picture(file: File, tint: Int = 0xE8DCC8): File {
        val img = BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB)
        img.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            paint = java.awt.GradientPaint(0f, 0f, java.awt.Color(tint), 0f, 400f, java.awt.Color(0x9C8B74))
            fillRect(0, 0, 600, 400)
            color = java.awt.Color(0x5A4E42)
            for (x in 0..600 step 25) drawLine(x, 0, x, 400)
            for (y in 0..400 step 25) drawLine(0, y, 600, y)
            color = java.awt.Color(0x3B2F28)
            fillOval(275, 60, 50, 60)
            stroke = BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            drawLine(300, 120, 295, 240); drawLine(295, 240, 255, 350); drawLine(295, 240, 345, 340)
            drawLine(298, 150, 240, 200); drawLine(298, 150, 370, 135)
            dispose()
        }
        file.parentFile.mkdirs()
        ImageIO.write(img, "png", file)
        return file
    }

    private fun ComposeUiTest.shoot(name: String, room: Room?) {
        val png = Image.makeFromBitmap(onAllNodes(isRoot()).onFirst().captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!
        File(out, "$name.png").writeBytes(png.bytes)
        val texts = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes()
        val cut = texts.mapNotNull { node ->
            val results = ArrayList<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            val layout = results.firstOrNull() ?: return@mapNotNull null
            // Cut: lower than its box lets it be, or lines past maxLines dropped without a "…" to
            // say so. A line ended in "…" on purpose is not cut.
            val clipped = layout.size.height < layout.multiParagraph.height
            val dropped = layout.multiParagraph.didExceedMaxLines && layout.layoutInput.overflow != TextOverflow.Ellipsis
            if (clipped || dropped) layout.layoutInput.text.text else null
        }
        assertTrue(cut.isEmpty(), "$name: cut off at the bottom: $cut")
        if (room != null) onNodeWithTag("room-line-${room.name.lowercase()}").assertExists("$name: the room's line")
    }

    private fun window(block: ComposeUiTest.() -> Unit) = runDesktopComposeUiTest(width = 1120, height = 770) { block() }

    @Test
    fun thePalette() = window {
        val world = World()
        world.show(this)
        mainClock.advanceTimeBy(1000)
        shoot("01-palette", null)
        world.app.selectRoom(Room.BOARDS)
        mainClock.advanceTimeBy(1000)
        shoot("02-palette-boards", null)
    }

    @Test
    fun practiceChosenAndRunningWithTheGlass() = window {
        val world = World()
        val pictures = File(root, "pictures")
        repeat(3) { picture(File(pictures, "pose$it.png")) }
        world.practice.selectFolder(pictures)
        world.app.showPractice()
        world.show(this)
        mainClock.advanceTimeBy(500)
        shoot("03-practice", Room.PRACTICE)
        world.practice.start()
        mainClock.autoAdvance = false // a running pose ticks for ever
        mainClock.advanceTimeBy(1500)
        shoot("04-session", null)
        world.practice.lens.glass.choose(Optic.CONVEX)
        val reference = onNodeWithTag("reference")
        val size = reference.fetchSemanticsNode().size
        reference.performMouseInput { moveTo(Offset(size.width * 0.5f, size.height * 0.35f)) }
        mainClock.advanceTimeBy(200)
        shoot("05-session-glass-in-hand", null)
        reference.performMouseInput { click(Offset(size.width * 0.5f, size.height * 0.35f)) }
        repeat(5) { mainClock.advanceTimeByFrame() }
        mainClock.advanceTimeBy(1000)
        shoot("06-session-glass-down", null)
        world.practice.stop()
        mainClock.advanceTimeBy(500)
        shoot("07-summary", Room.PRACTICE)
    }

    @Test
    fun boardsListedAndOpen() = window {
        val world = World()
        val home = File(root, "boards")
        // Two more boards first, so the list has a shelf of covers.
        listOf("Hands" to 0xE6C9B8, "Faces" to 0xD8D2E8).forEach { (name, tint) ->
            world.boards.createBoard(home, name)
            world.boards.importExternal(listOf(picture(File(world.boards.root!!, "cover.png"), tint)))
        }
        world.boards.createBoard(home, "Drachenbuch")
        val board = world.boards.root!!
        val files = listOf(0xE8DCC8, 0xC9D8C0, 0xE6C9B8, 0xD8D2E8).mapIndexed { i, tint -> picture(File(board, "p$i.png"), tint) }
        File(board, "p3.sketch.json").writeText("{}")
        world.boards.importExternal(files)
        val items = world.boards.board!!.items
        world.boards.clickItem(items[0].id, ctrl = false, shift = false)
        world.boards.clickItem(items[1].id, ctrl = true, shift = false)
        world.boards.groupSelection("Wings")
        world.boards.saveNote(null, "line of action first", NoteKind.POSTIT)
        world.boards.saveNote(null, "# Wing studies\nFold, spread, glide.", NoteKind.DOCUMENT)
        world.boards.saveLink(null, "https://example.org/wings", "How wings fold")
        // A concept linked onto the board: its cards are borrowed, and wear its tab.
        world.concepts.createConcept("Drache", "creature")
        world.concepts.addPictures(listOf(picture(File(root, "drache.png"), 0xC9D8C0)))
        world.boards.linkConcept(world.concepts.concept!!.id)
        world.app.showBoard()
        world.boards.setTheme("plain")
        world.boards.setLayout(BoardLayouts.FREE)
        world.boards.clickItem(items[2].id, ctrl = false, shift = false)
        world.show(this)
        mainClock.advanceTimeBy(1500)
        shoot("08-board-free", Room.BOARDS)
        // The ring on a right click; Esc puts it away.
        onNodeWithTag("card-${items[2].id}").performMouseInput { rightClick(center) }
        mainClock.advanceTimeBy(300)
        shoot("08-board-ring", Room.BOARDS)
        onNodeWithTag("ring-menu").performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Escape) }
        mainClock.advanceTimeBy(300)
        world.boards.setLayout(BoardLayouts.GRID)
        mainClock.advanceTimeBy(1000)
        shoot("09-board-grid-plain", Room.BOARDS)
        world.boards.setTheme("cork")
        mainClock.advanceTimeBy(1000)
        shoot("09-board-grid-cork", Room.BOARDS)
        world.boards.closeBoard()
        world.boards.openBoardList()
        mainClock.advanceTimeBy(1000)
        // The tiles are read off the disk away from the screen: wait for them, not for the clock.
        repeat(100) {
            if (onAllNodesWithTag("shelf", useUnmergedTree = true).fetchSemanticsNodes().size == 3) return@repeat
            Thread.sleep(50)
            mainClock.advanceTimeBy(50)
        }
        assertEquals(3, onAllNodesWithTag("shelf", useUnmergedTree = true).fetchSemanticsNodes().size, "three boards on the shelf")
        shoot("10-board-list", Room.BOARDS)
    }

    @Test
    fun conceptsListedAndOpen() = window {
        val world = World()
        world.concepts.createConcept("Drache", "creature")
        world.concepts.addPictures(listOf(picture(File(root, "drache.png"), 0xC9D8C0)))
        world.show(this)
        mainClock.advanceTimeBy(1000)
        shoot("11-concept", Room.CONCEPTS)
        world.concepts.closeConcept()
        world.concepts.openList()
        mainClock.advanceTimeBy(1000)
        shoot("12-concept-list", Room.CONCEPTS)
    }

    @Test
    fun sketchAtRestAndDrawing() = window {
        val world = World()
        world.sketch.newSketch(PageSize.pixels(1200, 850))
        world.app.enter(Screen.Sketch)
        world.show(this)
        mainClock.advanceTimeBy(500)
        world.sketch.fit()
        mainClock.advanceTimeBy(200)
        val s = world.sketch
        fun line(x0: Float, y0: Float, x1: Float, y1: Float, lift: Boolean = true) {
            var t = 10_000_000_000L
            s.mouseDown(s.panX + x0 * s.zoom, s.panY + y0 * s.zoom, t)
            for (i in 1..40) {
                t += 6_000_000L
                s.mouseMove(s.panX + (x0 + (x1 - x0) * i / 40) * s.zoom, s.panY + (y0 + (y1 - y0) * i / 40) * s.zoom, t)
            }
            if (lift) s.mouseUp()
        }
        s.setLead(Lead.MEDIUM)
        line(200f, 700f, 1000f, 690f)
        line(500f, 200f, 470f, 680f)
        mainClock.advanceTimeBy(300)
        shoot("13-sketch", Room.SKETCH)
        // A stroke held: the header, the tray and the paper step back.
        line(600f, 300f, 800f, 420f, lift = false)
        mainClock.advanceTimeBy(2500)
        shoot("14-sketch-drawing", Room.SKETCH)
        s.mouseUp()
    }

    @Test
    fun theLensRoomEmptyAloneBesideAndThroughTwoLenses() = window {
        val world = World()
        val lens = world.rooms.lens
        world.app.enter(Screen.Lens)
        world.show(this)
        mainClock.advanceTimeBy(500)
        shoot("15-lens-empty", Room.LENS)
        lens.open(picture(File(root, "pose.png")))
        lens.first.viewMode = ViewMode.NOTAN
        // The picture is decoded off the UI thread: real time, and the clock stepped with it.
        for (i in 0 until 50) {
            if (onAllNodesWithTag("reference").fetchSemanticsNodes().isNotEmpty()) break
            Thread.sleep(100)
            mainClock.advanceTimeBy(100)
        }
        mainClock.advanceTimeBy(500)
        shoot("16-lens-notan", Room.LENS)
        lens.arrange(LensLayout.BESIDE)
        mainClock.advanceTimeBy(500)
        shoot("17-lens-beside", Room.LENS)
        // The layout chosen is one of Material's buttons: in the app, Material's colours are the room's.
        val glow = Atelier.pigmentOf(Room.LENS).glow
        val chip = onNodeWithTag("lens-layout-beside").captureToImage().toPixelMap()
        val lit = chip[(6 * density.density).toInt(), chip.height / 2]
        assertTrue(abs(lit.red - glow.red) < 0.04f && abs(lit.green - glow.green) < 0.04f && abs(lit.blue - glow.blue) < 0.04f, "Payne's grey, not Practice's orange: $lit")
        lens.arrange(LensLayout.TWO)
        lens.choose(de.creaflect.actiondraw.lens.LensSide.SECOND)
        mainClock.advanceTimeBy(500)
        shoot("18-lens-two", Room.LENS)
        // The glass laid over the whole picture, bending it about a point off the middle.
        lens.arrange(LensLayout.ALONE)
        lens.first.viewMode = de.creaflect.actiondraw.ViewMode.NONE
        lens.first.glass.choose(Optic.FISHEYE)
        lens.first.glass.spreadOver(0.4f, 0.35f)
        mainClock.advanceTimeBy(500)
        shoot("19-lens-glass-over-whole", Room.LENS)
    }

    /**
     * The picture on a form (F10.10), in a lower window: in a test every frame is drawn on the
     * processor, four rays a pixel, and the full window takes minutes a form. As wide as the app
     * opens, so the header and the form's settings still have to fit without a label cut off.
     */
    @Test
    fun theLensRoomsForms() = runDesktopComposeUiTest(width = 1120, height = 560) {
        val world = World()
        val lens = world.rooms.lens
        world.app.enter(Screen.Lens)
        lens.open(picture(File(root, "pose.png")))
        world.show(this)
        for (i in 0 until 50) {
            if (onAllNodesWithTag("reference").fetchSemanticsNodes().isNotEmpty()) break
            Thread.sleep(100)
            mainClock.advanceTimeBy(100)
        }
        listOf(Form.SPHERE, Form.CHROME).forEachIndexed { i, form ->
            lens.first.form.choose(form)
            repeat(3) { mainClock.advanceTimeByFrame() }
            shoot("2$i-lens-form-" + form.name.lowercase(), Room.LENS)
        }
    }
}
