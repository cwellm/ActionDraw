package de.creaflect.actiondraw

import de.creaflect.actiondraw.board.Board
import de.creaflect.actiondraw.board.BoardCreation
import de.creaflect.actiondraw.board.PreviewResult
import de.creaflect.actiondraw.network.Fetched
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The lib as one piece: where it keeps its own files, and that its parts are wired to each other. */
class FileLibTest {
    private val home: File = Files.createTempDirectory("file-lib").toFile()

    @AfterTest
    fun cleanup() {
        home.deleteRecursively()
    }

    @Test
    fun everythingTheLibKeepsOfItsOwnLivesUnderItsHome() {
        val lib = FileLib(home)
        assertEquals(File(home, "ActionDraw Boards"), lib.preferences.boardsHome(), "the boards start out beside it")
        assertEquals(File(home, "ActionDraw Concepts"), lib.preferences.conceptsHome())
        assertEquals(File(home, "ActionDraw Sketches"), lib.preferences.sketchesHome())

        lib.preferences.setSnapByDefault(true)
        assertTrue(File(home, ".actiondraw/settings.properties").isFile, "the settings in ~/.actiondraw")
        assertIs<BoardCreation.Created>(lib.boards.create(lib.preferences.boardsHome(), Board(name = "Drachen")))
        assertTrue(File(home, ".actiondraw/boards.json").isFile, "and the record of which boards there are")
        assertTrue(File(home, "ActionDraw Boards/Drachen/.actiondraw_board.json").isFile, "the board itself in its own folder")
    }

    @Test
    fun theGlassIsKeptFromOneRunToTheNext() {
        assertNull(FileLib(home).preferences.glass(), "nothing kept until it is first settled")
        FileLib(home).preferences.setGlass(GlassSetting("CONVEX", 4f, 150f, bend = 0.4f))
        assertEquals(GlassSetting("CONVEX", 4f, 150f, bend = 0.4f), FileLib(home).preferences.glass())
    }

    @Test
    fun aLinksPreviewIsFetchedUnderItsTitleIntoTheBoard() {
        val asked = mutableListOf<String>()
        val lib = FileLib(home) { url ->
            asked += url
            if (url.endsWith(".png")) {
                Fetched("image/png", byteArrayOf(1, 2, 3), url)
            } else {
                Fetched("text/html", """<meta property="og:image" content="https://cdn.example.com/w.png">""".toByteArray(), url)
            }
        }
        val board = File(home, "Drachen").apply { mkdirs() }

        val saved = assertIs<PreviewResult.Saved>(lib.previews.fetch(board, "https://example.com/wings", "Bat wings"))
        assertEquals("_previews/Bat wings.png", saved.path, "named after the card's title")
        assertTrue(File(board, saved.path).isFile, "and kept in the board, for offline")
        assertEquals(listOf("https://example.com/wings", "https://cdn.example.com/w.png"), asked, "the page, then its picture")

        val untitled = assertIs<PreviewResult.Saved>(lib.previews.fetch(board, "https://example.com/wings", ""))
        assertEquals("_previews/example.com.png", untitled.path, "a card without a title is named after the site")
    }
}
