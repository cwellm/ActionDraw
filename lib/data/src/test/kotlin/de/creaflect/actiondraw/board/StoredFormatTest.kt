package de.creaflect.actiondraw.board

import de.creaflect.actiondraw.concept.Concept
import de.creaflect.actiondraw.concept.ConceptRegistry
import de.creaflect.actiondraw.concept.ConceptStore
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The files on disk do not drift: what the stores write, byte for byte, is what the app wrote
 * before the domain's types were split from the stored format (the golden files were captured
 * from that code, F11.7). A board saved today opens in yesterday's app, and the other way round.
 */
class StoredFormatTest {
    private val tmp: File = Files.createTempDirectory("format").toFile()

    @AfterTest
    fun cleanup() {
        tmp.deleteRecursively()
    }

    private fun golden(name: String): String =
        javaClass.getResource("/golden/$name")!!.readText().replace("\r\n", "\n")

    private fun saved(file: File): String = file.readText().replace("\r\n", "\n")

    private val board = Board(
        name = "Drachen",
        theme = "papyrus",
        layout = "free",
        camera = Camera(x = 12.5f, y = -3f, zoom = 1.75f),
        session = SessionRecipe(plan = "Classic gesture", intervalSeconds = 60, autoAdvance = false, viewMode = "NOTAN", grid = "THIRDS", temperature = -0.4f),
        groups = listOf(
            BoardGroup(id = "g1", name = "Heads", color = "#80CBC4", order = 1, collapsed = true),
            BoardGroup(id = "g2", name = "Eyes", order = 2, parentId = "g1"),
            BoardGroup(id = "concept:c1", name = "Wyvern", order = 3, source = "concept:c1"),
        ),
        items = listOf(
            ImageItem(id = "i1", path = "heads/a.png", groups = listOf("g1"), caption = "Front", starred = true, tags = listOf("red", "wing"), pos = ItemPos(10f, 20f, 1.5f, -2.4f), aspect = 0.75f, contentId = "123-abc"),
            ImageItem(id = "i2", path = "b.jpg"),
            NoteItem(id = "n1", text = "# Light\nfrom the left", groups = listOf("g2"), pos = ItemPos(1f, 2f), color = "#FFE082", heading = true, kind = NoteKind.POSTIT),
            NoteItem(id = "n2", text = "Whose colours\n#112233 #445566", kind = NoteKind.PALETTE),
            LinkItem(id = "l1", url = "https://example.com/x", title = "Example", groups = listOf("g1"), pos = ItemPos(5f, 6f, 0.5f), preview = "_previews/Example.png"),
        ),
        wallpaper = Wallpaper(path = "_wallpaper/w.jpg", fit = "tile", dim = 0.5f, blur = 0.25f),
        concepts = listOf("c1"),
    )

    @Test
    fun aBoardIsWrittenAsItAlwaysWas() {
        val full = File(tmp, "full").apply { mkdirs() }
        BoardStore.save(full, board)
        assertEquals(golden("board-full.json"), saved(File(full, BoardStore.FILE_NAME)))
        val empty = File(tmp, "empty").apply { mkdirs() }
        BoardStore.save(empty, Board())
        assertEquals(golden("board-empty.json"), saved(File(empty, BoardStore.FILE_NAME)))
    }

    @Test
    fun aBoardReadsBackAsItWasSaved() {
        val dir = File(tmp, "full").apply { mkdirs() }
        File(dir, BoardStore.FILE_NAME).writeText(golden("board-full.json"))
        val loaded = BoardStore.peek(dir)
        assertEquals(board, loaded, "every field of the golden file arrives in the domain's board")
    }

    @Test
    fun aSidecarThatLeavesFieldsOutReadsAsTheDomainsDefaults() {
        // An older or hand-written file holds only what it must; whatever it leaves out takes the
        // stored format's defaults, and those must be the domain's, field for field.
        val dir = File(tmp, "sparse").apply { mkdirs() }
        File(dir, BoardStore.FILE_NAME).writeText(
            """{"groups":[{"id":"g","name":"G"}],"camera":{},"session":{},"wallpaper":{"path":"w"},""" +
                """"items":[{"type":"image","id":"i","path":"p","pos":{"x":1,"y":2}},{"type":"note","id":"n","text":"t"},{"type":"link","id":"l","url":"u"}]}""",
        )
        assertEquals(
            Board(
                groups = listOf(BoardGroup(id = "g", name = "G")),
                camera = Camera(),
                session = SessionRecipe(),
                wallpaper = Wallpaper(path = "w"),
                items = listOf(ImageItem(id = "i", path = "p", pos = ItemPos(1f, 2f)), NoteItem(id = "n", text = "t"), LinkItem(id = "l", url = "u")),
            ),
            BoardStore.peek(dir),
        )
        val concept = File(tmp, "sparse-concept").apply { mkdirs() }
        File(concept, ConceptStore.FILE_NAME).writeText("""{"camera":{}}""")
        assertEquals(Concept(camera = Camera()), ConceptStore.peek(concept))
    }

    @Test
    fun aConceptIsWrittenAsItAlwaysWas() {
        val concept = Concept(
            id = "c1",
            name = "Wyvern",
            kind = "creature",
            notes = "Two legs, not four.",
            items = listOf(ImageItem(id = "ci1", path = "w.png", caption = "Side"), NoteItem(id = "cn1", text = "scales"), LinkItem(id = "cl1", url = "example.com")),
            documents = listOf("_docs/Anatomy.md"),
            layout = "free",
            camera = Camera(1f, 2f, 3f),
        )
        val dir = File(tmp, "concept").apply { mkdirs() }
        ConceptStore.save(dir, concept)
        assertEquals(golden("concept-full.json"), saved(File(dir, ConceptStore.FILE_NAME)))
        assertEquals(concept, ConceptStore.peek(dir), "and reads back as it was")
        val empty = File(tmp, "concept-empty").apply { mkdirs() }
        ConceptStore.save(empty, Concept())
        assertEquals(golden("concept-empty.json"), saved(File(empty, ConceptStore.FILE_NAME)))
    }

    @Test
    fun theRegistriesAreWrittenAsTheyAlwaysWere() {
        val config = File(tmp, "config")
        BoardRegistry(config).apply {
            register("Drachen", File(tmp, "full"), ownsFolder = true)
            register("Adopted", File(tmp, "empty"), ownsFolder = false)
        }
        val boards = saved(File(config, BoardRegistry.FILE_NAME))
            .replace(File(tmp, "full").absolutePath.replace("\\", "\\\\"), "FULL")
            .replace(File(tmp, "empty").absolutePath.replace("\\", "\\\\"), "EMPTY")
            .replace(Regex("\"id\": \"[0-9a-f-]{36}\""), "\"id\": \"ID\"")
        assertEquals(golden("boards.json"), boards)

        ConceptRegistry(config).register("c1", "Wyvern", "creature", File(tmp, "concept"))
        val concepts = saved(File(config, ConceptRegistry.FILE_NAME))
            .replace(File(tmp, "concept").absolutePath.replace("\\", "\\\\"), "CONCEPT")
        assertEquals(golden("concepts.json"), concepts)
    }
}
