package de.creaflect.actiondraw.board

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.SessionSetup
import de.creaflect.actiondraw.Settings
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.board.ui.BoardScreen
import de.creaflect.actiondraw.image.ThumbCache
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A board's background picture: copied into the board so it travels with it, one copy at a time,
 * and gone without a trace when removed.
 */
class WallpaperTest {
    @get:Rule
    val rule = createComposeRule()

    private val home: File = Files.createTempDirectory("wall-home").toFile()
    private val elsewhere: File = Files.createTempDirectory("wall-else").toFile()
    private val config: File = Files.createTempDirectory("wall-cfg").toFile()
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
        elsewhere.deleteRecursively()
        config.deleteRecursively()
    }

    private fun png(name: String, color: Color = Color.ORANGE): File {
        val image = BufferedImage(24, 16, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply { paint = color; fillRect(0, 0, 24, 16); dispose() }
        return File(elsewhere, name).also { ImageIO.write(image, "png", it) }
    }

    private fun board(): BoardState = BoardState(Settings(config), host).also { it.createBoard(home, "Wall") }

    // ---- The copy ----

    @Test
    fun aWallpaperIsCopiedIntoTheBoardAndRecordedRelatively() {
        val state = board()
        val picture = png("cork.png")

        assertNull(state.setWallpaper(picture))

        val paper = state.wallpaper!!
        assertEquals("${BoardState.WALLPAPER_DIR}/cork.png", paper.path, "relative, so it moves with the board")
        assertTrue(File(state.root!!, paper.path).isFile, "and really copied")
        assertTrue(picture.isFile, "the original is untouched")
        assertEquals(WallpaperFit.COVER, paper.fit)
    }

    @Test
    fun replacingTheWallpaperLeavesOnlyOneCopyBehind() {
        val state = board()
        state.setWallpaper(png("first.png"))
        val first = state.wallpaperFile!!

        state.setWallpaper(png("second.png"))

        assertFalse(first.exists(), "the old copy is cleared away")
        assertEquals("${BoardState.WALLPAPER_DIR}/second.png", state.wallpaper!!.path)
        assertEquals(1, File(state.root!!, BoardState.WALLPAPER_DIR).listFiles()!!.size)
    }

    @Test
    fun replacingKeepsTheLookYouChose() {
        val state = board()
        state.setWallpaper(png("first.png"))
        state.setWallpaperLook(fit = WallpaperFit.TILE, dim = 0.6f, blur = 0.2f)

        state.setWallpaper(png("second.png"))

        val paper = state.wallpaper!!
        assertEquals(WallpaperFit.TILE, paper.fit)
        assertEquals(0.6f, paper.dim)
        assertEquals(0.2f, paper.blur)
    }

    @Test
    fun removingTheWallpaperRemovesItsFileToo() {
        val state = board()
        state.setWallpaper(png("gone.png"))
        val copy = state.wallpaperFile!!

        state.clearWallpaper()

        assertNull(state.wallpaper)
        assertFalse(copy.exists())
    }

    @Test
    fun theWallpaperSurvivesAReopen() {
        val state = board()
        state.setWallpaper(png("keep.png"))
        state.setWallpaperLook(dim = 0.5f)

        val reopened = BoardState(Settings(config), host).also { it.openBoard(state.root!!) }

        assertEquals(0.5f, reopened.wallpaper!!.dim)
        assertTrue(reopened.wallpaperFile!!.isFile)
    }

    @Test
    fun theLookIsKeptWithinReason() {
        val state = board()
        state.setWallpaper(png("look.png"))
        state.setWallpaperLook(dim = 5f, blur = -1f)
        assertEquals(0.9f, state.wallpaper!!.dim, "never fully black — the cards must stay readable")
        assertEquals(0f, state.wallpaper!!.blur)
    }

    @Test
    fun aCardUsedAsWallpaperIsACopyNotALink() {
        val state = board()
        val onBoard = File(state.root!!, "wing.png").also { png("wing.png").copyTo(it) }
        state.importExternal(listOf(onBoard))
        val card = state.board!!.items.single()

        state.setWallpaper(state.fileOf(card as ImageItem)!!)
        state.removeItems(setOf(card.id))

        assertTrue(state.wallpaperFile!!.isFile, "the card is gone; the background is not")
    }

    @Test
    fun theWallpaperCopyIsNotOfferedBackAsACard() {
        val state = board()
        val onBoard = File(state.root!!, "wing.png").also { png("wing.png").copyTo(it) }
        state.importExternal(listOf(onBoard))
        val card = state.board!!.items.single() as ImageItem
        state.setWallpaper(state.fileOf(card)!!) // a copy with the very same content id
        onBoard.delete() // the card's own file goes missing

        // Recovery by content id must not point the lost card at the wallpaper's copy.
        val recovered = BoardStore.validate(state.board!!, state.root!!)
        assertTrue(recovered.items.none { it is ImageItem && it.path.startsWith(BoardState.WALLPAPER_DIR) })
    }

    // ---- On screen ----

    @Test
    fun theWallpaperIsDrawnWhenSetAndGoneWhenRemoved() {
        val state = board()
        rule.setContent { BoardScreen(state, ThumbCache(config), isFullscreen = false, setFullscreen = {}) }
        rule.waitForIdle()
        rule.onNodeWithTag("wallpaper").assertDoesNotExist()

        state.setWallpaper(png("shown.png"))
        rule.waitForIdle()
        rule.onNodeWithTag("wallpaper").assertIsDisplayed()

        state.clearWallpaper()
        rule.waitForIdle()
        rule.onNodeWithTag("wallpaper").assertDoesNotExist()
    }
}
