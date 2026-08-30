package de.creaflect.actiondraw

import de.creaflect.actiondraw.image.ImageScanner
import de.creaflect.actiondraw.image.relKey
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageScannerTest {
    private val dir: File = Files.createTempDirectory("actiondraw-scan").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    @Test
    fun picksOnlyTopLevelImageFiles() {
        File(dir, "a.jpg").writeText("x")
        File(dir, "b.PNG").writeText("x") // case-insensitive extension
        File(dir, "c.txt").writeText("x") // not an image
        File(dir, ".actiondraw_seen.txt").writeText("x") // bookkeeping file, ignored
        File(dir, "sub").apply { mkdir() }
            .let { File(it, "d.jpg").writeText("x") } // nested, ignored (top-level only)

        val names = ImageScanner.scan(dir).map { it.name }.toSet()
        assertEquals(setOf("a.jpg", "b.PNG"), names)
    }

    @Test
    fun scanTreeFindsNestedImagesAndSkipsDotDirectories() {
        File(dir, "a.jpg").writeText("x")
        File(dir, "wings").apply { mkdir() }.let { File(it, "b.png").writeText("x") }
        File(dir, "wings/deep").apply { mkdirs() }.let { File(it, "c.webp").writeText("x") }
        File(dir, ".thumbs").apply { mkdir() }.let { File(it, "hidden.jpg").writeText("x") }
        File(dir, "notes.txt").writeText("x")

        val keys = ImageScanner.scanTree(dir).map { relKey(dir, it) }
        assertEquals(listOf("a.jpg", "wings/b.png", "wings/deep/c.webp"), keys)
    }

    @Test
    fun relKeyIsSlashSeparatedAndEqualsTheNameAtTopLevel() {
        val top = File(dir, "a.jpg")
        val nested = File(File(dir, "wings"), "b.png")
        assertEquals("a.jpg", relKey(dir, top))
        assertEquals("wings/b.png", relKey(dir, nested))
    }
}
