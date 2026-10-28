package de.creaflect.actiondraw

import de.creaflect.actiondraw.collage.Collage
import de.creaflect.actiondraw.collage.Collages
import de.creaflect.actiondraw.collage.Piece
import de.creaflect.actiondraw.collage.Spot
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The collages as files: `<name>.collage.json`, its pieces' pictures kept where they belong, renamed with its picture. */
class FileCollagesTest {
    private val home: File = Files.createTempDirectory("collages").toFile()

    @AfterTest
    fun cleanup() {
        home.deleteRecursively()
    }

    private fun piece(id: String, source: File) = Piece(
        id = id,
        name = source.nameWithoutExtension,
        source = source,
        outline = listOf(Spot(0f, 0f), Spot(400f, 0f), Spot(380f, 300f), Spot(10f, 290f)),
        x = 950f,
        y = 640f,
        turn = 12.5f,
        scale = 0.75f,
        flipped = true,
    )

    @Test
    fun aCollageIsKeptAsItWasLaidOutAndItsPicturesWhereTheyAre() {
        val lib = FileLib(home)
        val collagesHome = lib.preferences.collagesHome()
        assertEquals(File(home, "ActionDraw Collages"), collagesHome, "the collages start out beside the boards")
        val beside = File(collagesHome, "cut/sky.png")
        val board = File(home, "ActionDraw Boards/Drachen/cliff.jpg")
        val collage = Collage(listOf(piece("a", beside), piece("b", board)))

        val file = lib.collages.freeFile(collagesHome, "Cliff at dusk")
        assertEquals(File(collagesHome, "Cliff at dusk.collage.json"), file)
        assertTrue(lib.collages.write(file, collage))
        assertEquals(collage, lib.collages.read(file), "read back as it was laid out, piece for piece")

        val text = file.readText()
        assertTrue("\"cut/sky.png\"" in text, "a picture beside the collage kept relative to it: it moves with it")
        assertTrue(board.absolutePath.replace("\\", "\\\\") in text, "one elsewhere kept where it is")
        assertEquals(listOf(file), lib.collages.recent(), "and it is among the recent ones")

        val elsewhere = File(home, "Elsewhere/Cliff.collage.json")
        assertTrue(lib.collages.write(elsewhere, Collage()))
        assertTrue(elsewhere in lib.collages.recent(), "one kept outside the collages home is too")
    }

    @Test
    fun aNewCollagesNameIsOneNoOtherCollageThereHas() {
        val lib = FileLib(home)
        val dir = File(home, "c").apply { mkdirs() }
        File(dir, "Untitled collage.collage.json").writeText("{}")
        File(dir, "Untitled collage (2).png").writeBytes(byteArrayOf(1))
        assertEquals(File(dir, "Untitled collage (3).collage.json"), lib.collages.freeFile(dir, "Untitled collage"))
    }

    @Test
    fun aCollageIsRenamedWithItsPictureUnlessTheNameIsTaken() {
        val lib = FileLib(home)
        val dir = File(home, "c").apply { mkdirs() }
        val file = File(dir, "first.collage.json")
        assertTrue(lib.collages.write(file, Collage()))
        File(dir, "first.png").writeBytes(byteArrayOf(7))
        File(dir, "taken.collage.json").writeText("{}")

        assertNull(lib.collages.rename(file, "taken"), "a name another collage has")
        val renamed = assertNotNull(lib.collages.rename(file, "second"))
        assertEquals(File(dir, "second.collage.json"), renamed)
        assertFalse(file.exists())
        assertTrue(File(dir, "second.png").isFile, "its picture renamed with it")
        assertEquals("second", Collages.nameOf(renamed))
        assertEquals(Collage(), lib.collages.read(renamed))
    }

    @Test
    fun aDocumentThatCannotBeReadIsNoCollage() {
        val lib = FileLib(home)
        val file = File(home, "broken.collage.json").apply { writeText("not a collage") }
        assertNull(lib.collages.read(file))
    }
}
