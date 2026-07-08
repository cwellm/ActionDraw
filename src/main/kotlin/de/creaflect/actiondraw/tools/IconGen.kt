package de.creaflect.actiondraw.tools

import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Renders the master SVG logo into all icon assets using Skiko (the Skia engine already bundled
 * with Compose Desktop) — so icon generation needs no external tools. Run via `./gradlew genIcons`.
 *
 * Produces `actiondraw-<size>.png` for the Linux hicolor sizes and a multi-resolution
 * `actiondraw.ico` (PNG-compressed entries) for Windows.
 */
fun main(args: Array<String>) {
    val svgFile = File(args.getOrElse(0) { "art/actiondraw.svg" })
    val outDir = File(args.getOrElse(1) { "art/icons" }).apply { mkdirs() }
    val svgBytes = svgFile.readBytes()

    val sizes = intArrayOf(16, 24, 32, 48, 64, 128, 256, 512)
    val pngBySize = LinkedHashMap<Int, ByteArray>()
    for (size in sizes) {
        val png = renderSvgToPng(svgBytes, size)
        pngBySize[size] = png
        File(outDir, "actiondraw-$size.png").writeBytes(png)
    }

    // Windows .ico tops out at 256px; pack the standard set as PNG-compressed entries.
    val icoSizes = intArrayOf(16, 24, 32, 48, 64, 128, 256)
    File(outDir, "actiondraw.ico").writeBytes(buildIco(icoSizes.map { it to pngBySize.getValue(it) }))

    println("Wrote ${sizes.size} PNGs + actiondraw.ico to ${outDir.path}")
}

/** The master SVG's coordinate canvas (viewBox 0 0 512 512). */
private const val BASE = 512f

private fun renderSvgToPng(svgBytes: ByteArray, size: Int): ByteArray {
    val dom = SVGDOM(Data.makeFromBytes(svgBytes))
    dom.setContainerSize(BASE, BASE) // render at native SVG size...
    val surface = Surface.makeRasterN32Premul(size, size)
    val canvas = surface.canvas
    canvas.scale(size / BASE, size / BASE) // ...then scale the whole thing into the target surface
    dom.render(canvas)
    val data = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)
        ?: error("PNG encoding failed at ${size}px")
    return data.bytes
}

/** Minimal ICO container with PNG-compressed images (Vista+); width/height byte is 0 for 256. */
private fun buildIco(entries: List<Pair<Int, ByteArray>>): ByteArray {
    val out = ByteArrayOutputStream()
    fun short(v: Int) { out.write(v and 0xFF); out.write((v ushr 8) and 0xFF) }
    fun int(v: Int) { out.write(v and 0xFF); out.write((v ushr 8) and 0xFF); out.write((v ushr 16) and 0xFF); out.write((v ushr 24) and 0xFF) }

    short(0); short(1); short(entries.size) // ICONDIR: reserved, type=icon, count
    var offset = 6 + entries.size * 16
    for ((size, png) in entries) {
        out.write(if (size >= 256) 0 else size) // width
        out.write(if (size >= 256) 0 else size) // height
        out.write(0); out.write(0)              // palette count, reserved
        short(1); short(32)                     // planes, bit depth
        int(png.size); int(offset)              // size, offset
        offset += png.size
    }
    for ((_, png) in entries) out.write(png)
    return out.toByteArray()
}
