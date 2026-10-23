package de.creaflect.sketch

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/**
 * The sketch document (`.sketch.json`): the page and every stroke as it was made — brush and
 * samples with pressure and time — so a sketch can be re-rendered, continued, undone, or drawn
 * again at another size. The PNG beside it is the picture; this is the history.
 */
@Serializable
data class SketchDocument(
    val version: Int = 1,
    val width: Int,
    val height: Int,
    val dpi: Int = 150,
    /** Paper colour, ARGB. */
    val paper: Int = 0xFFFFFFFF.toInt(),
    /** The paper's tooth, a [Paper] by name; a document without one is on medium paper. */
    val tooth: String = Paper.MEDIUM.name,
    val strokes: List<StrokeRecord> = emptyList(),
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

        fun fromJson(text: String): SketchDocument = json.decodeFromString(serializer(), text.removePrefix("﻿"))

        const val FILE_SUFFIX = ".sketch.json"
    }
}

/** One stroke: the brush it was made with and its raw samples, times relative to its first. */
@Serializable
data class StrokeRecord(
    val lead: String,
    val color: Int,
    val size: Float,
    val eraser: Boolean = false,
    /** How much of the graphite an eraser stroke lifts per pass: 1 all of it, less a real rubber's share. */
    val eraserStrength: Float = 1f,
    val samples: List<SampleRecord>,
) {
    val brush: Brush get() = Brush(Lead.entries.firstOrNull { it.name == lead } ?: Lead.MEDIUM, color, size)
}

/** One sample: page position, pressure, nanoseconds since the stroke began, and the tilt in degrees as the pen gave it. */
@Serializable
data class SampleRecord(val x: Float, val y: Float, val p: Float, val t: Long, val tx: Float = 0f, val ty: Float = 0f)

/** The page sizes on offer: paper at a resolution, or plain pixels. */
data class PageSize(val name: String, val width: Int, val height: Int, val dpi: Int) {
    val landscape: PageSize get() = copy(name = "$name landscape", width = height, height = width)

    companion object {
        private const val MM_PER_INCH = 25.4f

        fun paper(name: String, widthMm: Float, heightMm: Float, dpi: Int): PageSize =
            PageSize(name, (widthMm / MM_PER_INCH * dpi).roundToInt(), (heightMm / MM_PER_INCH * dpi).roundToInt(), dpi)

        fun a5(dpi: Int) = paper("A5 $dpi dpi", 148f, 210f, dpi)
        fun a4(dpi: Int) = paper("A4 $dpi dpi", 210f, 297f, dpi)
        fun a3(dpi: Int) = paper("A3 $dpi dpi", 297f, 420f, dpi)

        fun pixels(width: Int, height: Int) = PageSize("$width × $height px", width, height, 96)

        /** What the size dialog offers; a custom W × H comes from [pixels]. */
        val STANDARD: List<PageSize> = listOf(a5(150), a4(150), a3(150), a5(300), a4(300), a3(300))
    }
}
