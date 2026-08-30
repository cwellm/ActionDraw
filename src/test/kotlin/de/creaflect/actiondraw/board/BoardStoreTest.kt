package de.creaflect.actiondraw.board

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BoardStoreTest {
    private val root: File = Files.createTempDirectory("actiondraw-board").toFile()

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
    }

    private fun sampleBoard(): BoardFile {
        File(root, "wings").mkdirs()
        File(root, "wings/a.jpg").createNewFile()
        return BoardFile(
            name = "Drachenbuch",
            theme = BoardThemes.PAPYRUS,
            groups = listOf(BoardGroup(id = "g1", name = "Flügel", color = "#80CBC4", order = 1, collapsed = true)),
            items = listOf(
                ImageItem(
                    id = "i1", path = "wings/a.jpg", groups = listOf("g1"),
                    caption = "membrane folds", starred = true, tags = listOf("wing", "anatomy"),
                ),
                NoteItem(id = "n1", text = "Mehr ¾-Ansichten sammeln!", groups = listOf("g1")),
            ),
        )
    }

    @Test
    fun roundTripPreservesEverything() {
        val board = sampleBoard()
        assertTrue(BoardStore.save(root, board))
        val loaded = BoardStore.load(root)
        assertIs<BoardStore.LoadResult.Loaded>(loaded)
        assertEquals(board, loaded.board)
        assertEquals(false, loaded.fromBackup)
    }

    @Test
    fun corruptMainFallsBackToTheBackup() {
        val v1 = sampleBoard()
        BoardStore.save(root, v1)
        BoardStore.save(root, v1.copy(name = "v2")) // v1 becomes the .bak
        File(root, BoardStore.FILE_NAME).writeText("{ not json")

        val loaded = BoardStore.load(root)
        assertIs<BoardStore.LoadResult.Loaded>(loaded)
        assertEquals("Drachenbuch", loaded.board.name)
        assertTrue(loaded.fromBackup)
    }

    @Test
    fun failsWhenMainAndBackupAreCorrupt() {
        BoardStore.save(root, sampleBoard())
        File(root, BoardStore.FILE_NAME).writeText("{ not json")
        File(root, "${BoardStore.FILE_NAME}.bak").writeText("also broken")
        assertIs<BoardStore.LoadResult.Failed>(BoardStore.load(root))
    }

    @Test
    fun missingImageFilesAreDroppedButNotesNever() {
        val board = sampleBoard().let {
            it.copy(items = it.items + ImageItem(id = "gone", path = "vanished.jpg"))
        }
        BoardStore.save(root, board)
        val loaded = BoardStore.load(root)
        assertIs<BoardStore.LoadResult.Loaded>(loaded)
        assertEquals(listOf("i1", "n1"), loaded.board.items.map { it.id })
    }

    @Test
    fun unknownKeysFromLaterVersionsAreIgnored() {
        File(root, BoardStore.FILE_NAME).writeText(
            """
            {"version": 7, "name": "Future", "futureField": 42, "groups": [],
             "items": [{"type": "note", "id": "n1", "text": "hi", "groups": [], "pos": {"x": 1, "y": 2}}]}
            """.trimIndent(),
        )
        val loaded = BoardStore.load(root)
        assertIs<BoardStore.LoadResult.Loaded>(loaded)
        assertEquals("Future", loaded.board.name)
        assertEquals(listOf("n1"), loaded.board.items.map { it.id })
    }

    @Test
    fun aFolderWithoutASidecarIsNone() {
        assertIs<BoardStore.LoadResult.None>(BoardStore.load(root))
    }
}
