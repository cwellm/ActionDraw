package de.creaflect.actiondraw

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import de.creaflect.actiondraw.board.BoardHost
import de.creaflect.actiondraw.board.BoardLayouts
import de.creaflect.actiondraw.board.BoardState
import de.creaflect.actiondraw.board.NoteKind
import de.creaflect.actiondraw.concept.ConceptHost
import de.creaflect.actiondraw.concept.ConceptState
import de.creaflect.actiondraw.image.ThumbCache
import de.creaflect.actiondraw.sketch.SketchHost
import de.creaflect.actiondraw.sketch.SketchState
import de.creaflect.actiondraw.ui.Optic
import de.creaflect.actiondraw.ui.Room
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
import kotlin.test.AfterTest
import kotlin.test.Test
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

    /** The app's states wired as `main` wires them, on folders of their own. */
    private inner class World {
        val config = File(root, "config").apply { mkdirs() }
        val settings = Settings(config)
        val app = AppState(settings)
        lateinit var boards: BoardState
        lateinit var sketch: SketchState
        val concepts = ConceptState(settings, object : ConceptHost {
            override fun showConcepts() = app.showConcepts()
            override fun showConcept() = app.showConcept()
            override fun leaveConcepts() = app.leaveConcepts()
        }).also { it.setConceptsHomeDir(File(root, "concepts")) }

        init {
            boards = BoardState(settings, object : BoardHost {
                override fun startSession(root: File, images: List<File>, setup: SessionSetup?) = app.startBoardSession(root, images, setup)
                override fun showBoard() = app.showBoard()
                override fun showBoardList() = app.showBoardList()
                override fun leaveBoard() = app.leaveBoard()
                override fun currentSetup(): SessionSetup = app.currentSetup()
                override fun showConcept(id: String) {
                    concepts.openById(id)
                }
                override fun showConcepts() = concepts.openList()
            }, concepts = concepts)
            boards.setBoardsHomeDir(File(root, "boards").apply { mkdirs() })
            sketch = SketchState(settings, object : SketchHost {
                override fun boards() = boards.availableBoards()
                override fun addToBoard(dir: File, pictures: List<File>) = boards.pinTo(dir, pictures)
                override fun concepts() = concepts.available()
                override fun conceptDir(id: String) = concepts.entryById(id)?.dir
                override fun addToConcept(id: String, pictures: List<File>) = concepts.addToConcept(id, pictures, emptyList()) != null
                override fun leaveSketch() = app.leaveSketch()
            }).also { it.setSketchesHomeDir(File(root, "sketches")) }
        }

        val thumbs = ThumbCache(config)
        val pinTargets = PinTargets(boards = { boards.availableBoards() }, pin = boards::pinTo)

        fun show(test: ComposeUiTest) = test.setContent {
            App(app, boards, concepts, sketch, thumbs, pinTargets, isFullscreen = false, onToggleFullscreen = {}, setFullscreen = {})
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
        world.app.selectFolder(pictures)
        world.app.showPractice()
        world.show(this)
        mainClock.advanceTimeBy(500)
        shoot("03-practice", Room.PRACTICE)
        world.app.start()
        mainClock.autoAdvance = false // a running pose ticks for ever
        mainClock.advanceTimeBy(1500)
        shoot("04-session", null)
        world.app.glass.choose(Optic.CONVEX)
        val reference = onNodeWithTag("reference")
        val size = reference.fetchSemanticsNode().size
        reference.performMouseInput { moveTo(Offset(size.width * 0.5f, size.height * 0.35f)) }
        mainClock.advanceTimeBy(200)
        shoot("05-session-glass-in-hand", null)
        reference.performMouseInput { click(Offset(size.width * 0.5f, size.height * 0.35f)) }
        repeat(5) { mainClock.advanceTimeByFrame() }
        mainClock.advanceTimeBy(1000)
        shoot("06-session-glass-down", null)
        world.app.stop()
        mainClock.advanceTimeBy(500)
        shoot("07-summary", Room.PRACTICE)
    }

    @Test
    fun boardsListedAndOpen() = window {
        val world = World()
        val home = File(root, "boards")
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
        world.app.showSketch()
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
}
