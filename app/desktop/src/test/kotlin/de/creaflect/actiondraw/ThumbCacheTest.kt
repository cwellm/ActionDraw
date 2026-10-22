package de.creaflect.actiondraw

import de.creaflect.actiondraw.image.ThumbCache
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ThumbCacheTest {
    private val dir: File = Files.createTempDirectory("actiondraw-thumbsrc").toFile()
    private val cacheDir: File = Files.createTempDirectory("actiondraw-thumbcache").toFile()
    private val cache = ThumbCache(cacheDir)

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
        cacheDir.deleteRecursively()
    }

    /** Writes a real [size]×[size] PNG so Skia can decode it. */
    private fun writePng(file: File, size: Int) {
        val surface = Surface.makeRasterN32Premul(size, size)
        surface.canvas.clear(Color.makeRGB(200, 60, 40))
        val data = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!
        file.writeBytes(data.bytes)
    }

    @Test
    fun thumbnailsLandInTheCacheAndAreServedFromIt() {
        val source = File(dir, "big.png").also { writePng(it, 64) }
        assertNotNull(cache.load(source, maxSize = 16))
        val cached = cacheDir.listFiles()!!.single()
        assertTrue(cached.name.endsWith(".png"))

        // Replace the cached file with a recognisably different (8 px) image; the source is
        // unchanged, so the next load must come from the cache and show 8 px.
        writePng(cached, 8)
        val second = cache.load(source, maxSize = 16)
        assertEquals(8, second!!.width, "expected the cached file to be served")
    }

    @Test
    fun cacheKeyDependsOnRequestedSize() {
        val source = File(dir, "big.png").also { writePng(it, 64) }
        assertNotEquals(cache.cacheName(source, 16), cache.cacheName(source, 192))
    }
}
