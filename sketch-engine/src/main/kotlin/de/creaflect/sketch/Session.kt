package de.creaflect.sketch

import org.jetbrains.skia.EncodedImageFormat

/**
 * A sketch being made: the page, the strokes so far, the one in progress, and undo/redo.
 *
 * Every stroke goes through the same pipeline whether drawn live or replayed — samples into
 * `StrokeBuilder` (filter, speed, the lead's width and alpha), points into `Resampler` (even
 * dabs along a spline), dabs into `StampRenderer` — so a document loaded from disk renders
 * pixel for pixel as it was drawn, and undo can re-render from a snapshot plus a replay of the
 * strokes after it. Snapshots are taken every [SNAPSHOT_EVERY] strokes and only the last few
 * kept; a snapshot shares every tile the strokes after it have not touched, so it costs only
 * what changed.
 */
class SketchSession(
    val width: Int,
    val height: Int,
    val dpi: Int = 150,
    val paper: Int = 0xFFFFFFFF.toInt(),
    private val stamps: StampRenderer = StampRenderer(),
) : AutoCloseable {
    val surface = SketchSurface(width, height, paper)

    private val strokes = ArrayList<StrokeRecord>()
    private val redoStack = ArrayList<StrokeRecord>()
    private val snapshots = ArrayList<Pair<Int, PageSnapshot>>() // strokes drawn → the layer then
    private var live: LiveStroke? = null

    /** Something changed since the last [markSaved]. */
    var dirty: Boolean = false
        private set

    val strokeCount: Int get() = strokes.size
    val canUndo: Boolean get() = strokes.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val isDrawing: Boolean get() = live != null

    private class LiveStroke(val brush: Brush, val eraser: Boolean, stamps: StampRenderer) {
        val builder = StrokeBuilder(brush, model = if (eraser) Pencils.ERASER else brush.model)
        val resampler = Resampler(stamps::spacing)
        val samples = ArrayList<SampleRecord>()
        var startNanos = 0L
    }

    // ---- Drawing ----

    fun begin(brush: Brush, eraser: Boolean = false) {
        end()
        live = LiveStroke(brush, eraser, stamps)
    }

    /** The next sample of the stroke in progress; dabs are put on the page at once. */
    fun add(sample: InputSample) {
        val stroke = live ?: return
        if (stroke.samples.isEmpty()) stroke.startNanos = sample.timeNanos
        stroke.samples += SampleRecord(sample.x, sample.y, sample.pressure, sample.timeNanos - stroke.startNanos)
        place(stroke, sample)
    }

    /** Ends the stroke in progress; an empty one is dropped. Returns whether one was kept. */
    fun end(): Boolean {
        val stroke = live ?: return false
        live = null
        if (stroke.samples.isEmpty()) return false
        strokes += StrokeRecord(stroke.brush.lead.name, stroke.brush.color, stroke.brush.size, stroke.eraser, stroke.samples.toList())
        redoStack.clear()
        dirty = true
        maybeSnapshot()
        return true
    }

    /**
     * Drops the stroke in progress and takes its marks off the page again — for a stroke that
     * turned out not to be one (the mouse events Windows makes out of a pen, arriving first).
     */
    fun cancel() {
        if (live == null) return
        live = null
        rerender()
    }

    private fun place(stroke: LiveStroke, sample: InputSample) {
        val point = stroke.builder.add(sample)
        stroke.resampler.add(point) { dab -> stamps.dab(surface, stroke.brush, dab, stroke.eraser) }
    }

    // ---- Undo / redo ----

    fun undo(): Boolean {
        end()
        if (strokes.isEmpty()) return false
        redoStack += strokes.removeLast()
        dirty = true
        rerender()
        return true
    }

    fun redo(): Boolean {
        end()
        val record = redoStack.removeLastOrNull() ?: return false
        strokes += record
        dirty = true
        replay(record)
        maybeSnapshot()
        return true
    }

    /** The page as of the last snapshot at or before the current stroke count, plus the rest replayed. */
    private fun rerender() {
        while (snapshots.isNotEmpty() && snapshots.last().first > strokes.size) snapshots.removeLast().second.close()
        val base = snapshots.lastOrNull()
        if (base == null) surface.clear() else surface.restore(base.second)
        val from = base?.first ?: 0
        for (i in from until strokes.size) replay(strokes[i])
    }

    private fun replay(record: StrokeRecord) {
        val stroke = LiveStroke(record.brush, record.eraser, stamps)
        for (s in record.samples) place(stroke, InputSample(s.x, s.y, s.p, timeNanos = s.t))
    }

    private fun maybeSnapshot() {
        if (strokes.size % SNAPSHOT_EVERY != 0) return
        snapshots += strokes.size to surface.snapshot()
        while (snapshots.size > KEEP_SNAPSHOTS) snapshots.removeFirst().second.close()
    }

    // ---- The document ----

    fun document(): SketchDocument = SketchDocument(width = width, height = height, dpi = dpi, paper = paper, strokes = strokes.toList())

    /** The picture: paper and strokes, as PNG bytes. */
    fun exportPng(): ByteArray = surface.compose().use { image ->
        image.encodeToData(EncodedImageFormat.PNG, 100)?.use { it.bytes } ?: error("PNG encoding failed")
    }

    fun markSaved() {
        dirty = false
    }

    override fun close() {
        snapshots.forEach { it.second.close() }
        snapshots.clear()
        surface.close()
    }

    companion object {
        const val SNAPSHOT_EVERY = 12
        const val KEEP_SNAPSHOTS = 3

        /** A session with the document's page and all its strokes drawn again. */
        fun fromDocument(document: SketchDocument, stamps: StampRenderer = StampRenderer()): SketchSession {
            val session = SketchSession(document.width, document.height, document.dpi, document.paper, stamps)
            for (record in document.strokes) {
                session.strokes += record
                session.replay(record)
                session.maybeSnapshot()
            }
            return session
        }
    }
}
