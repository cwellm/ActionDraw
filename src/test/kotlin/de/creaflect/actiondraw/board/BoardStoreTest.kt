package de.creaflect.actiondraw.board

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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

    /** Loading fills in content ids, so compare what the author actually wrote. */
    private fun BoardFile.withoutContentIds() =
        copy(items = items.map { if (it is ImageItem) it.copy(contentId = null) else it })

    @Test
    fun roundTripPreservesEverything() {
        val board = sampleBoard()
        assertTrue(BoardStore.save(root, board))
        val loaded = BoardStore.load(root)
        assertIs<BoardStore.LoadResult.Loaded>(loaded)
        assertEquals(board, loaded.board.withoutContentIds())
        assertEquals(false, loaded.fromBackup)
    }

    @Test
    fun loadingGivesEveryPictureAContentId() {
        BoardStore.save(root, sampleBoard())
        val loaded = BoardStore.load(root)
        assertIs<BoardStore.LoadResult.Loaded>(loaded)
        val image = loaded.board.items.filterIsInstance<ImageItem>().single()
        assertNotNull(image.contentId, "cards need an identity to survive a rename")
    }

    @Test
    fun aRenamedPictureKeepsItsCardAndItsMetadata() {
        // Give the file some content so its identity is not just its length.
        File(root, "wings").mkdirs()
        File(root, "wings/a.jpg").writeText("a real picture would go here")
        BoardStore.save(root, sampleBoard())
        BoardStore.load(root) // first load records the content id
        val withIds = BoardStore.peek(root)!!.let { BoardStore.validate(it, root) }
        BoardStore.save(root, withIds)

        // Rename it outside the app, into another folder for good measure.
        File(root, "renamed").mkdirs()
        assertTrue(File(root, "wings/a.jpg").renameTo(File(root, "renamed/b.jpg")))

        val loaded = BoardStore.load(root)
        assertIs<BoardStore.LoadResult.Loaded>(loaded)
        val image = loaded.board.items.filterIsInstance<ImageItem>().single()
        assertEquals("renamed/b.jpg", image.path, "the card follows the file")
        assertEquals("membrane folds", image.caption, "and keeps everything it knew")
        assertEquals(listOf("wing", "anatomy"), image.tags)
        assertEquals(listOf("g1"), image.groups)
    }

    @Test
    fun aPictureThatIsReallyGoneStillDisappears() {
        File(root, "wings").mkdirs()
        File(root, "wings/a.jpg").writeText("content")
        BoardStore.save(root, BoardStore.validate(sampleBoard(), root))
        assertTrue(File(root, "wings/a.jpg").delete())

        val loaded = BoardStore.load(root)
        assertIs<BoardStore.LoadResult.Loaded>(loaded)
        assertEquals(listOf("n1"), loaded.board.items.map { it.id }, "only the note is left")
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

    @Test
    fun freeformFieldsRoundTrip() {
        val board = sampleBoard().let {
            it.copy(
                layout = BoardLayouts.FREE,
                camera = Camera(x = 12f, y = -30f, zoom = 1.5f),
                items = it.items.map { item ->
                    item.withPos(ItemPos(x = 100f, y = 50f, scale = 1.4f, rotation = -12f))
                },
            )
        }
        BoardStore.save(root, board)
        val loaded = BoardStore.load(root)
        assertIs<BoardStore.LoadResult.Loaded>(loaded)
        assertEquals(board, loaded.board.withoutContentIds())
    }

    @Test
    fun phaseOneSidecarsWithoutFreeformFieldsStillLoad() {
        File(root, "a.jpg").createNewFile()
        File(root, BoardStore.FILE_NAME).writeText(
            """
            {"version": 1, "name": "Old", "theme": "cork", "groups": [],
             "items": [{"type": "image", "id": "i1", "path": "a.jpg", "groups": [],
                        "caption": null, "starred": false, "tags": []}]}
            """.trimIndent(),
        )
        val loaded = BoardStore.load(root)
        assertIs<BoardStore.LoadResult.Loaded>(loaded)
        assertEquals(BoardLayouts.GRID, loaded.board.layout)
        assertEquals(null, loaded.board.camera)
        assertEquals(null, loaded.board.items.single().pos)
    }
}
