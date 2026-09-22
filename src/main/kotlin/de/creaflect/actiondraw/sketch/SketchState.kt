package de.creaflect.actiondraw.sketch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import de.creaflect.actiondraw.Settings
import de.creaflect.actiondraw.sketch.input.PenSample
import de.creaflect.actiondraw.sketch.input.PenSource
import de.creaflect.actiondraw.sketch.input.PenSources
import de.creaflect.sketch.Brush
import de.creaflect.sketch.InputSample
import de.creaflect.sketch.Lead
import de.creaflect.sketch.PageSize
import de.creaflect.sketch.SketchDocument
import de.creaflect.sketch.SketchSession
import java.awt.EventQueue
import java.awt.Window
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.min

/** Which Live Sketch dialog is open. */
sealed class SketchEditor {
    data object NewSketch : SketchEditor()
    data object SaveAs : SketchEditor()
    data object ToBoard : SketchEditor()
    data object ToConcept : SketchEditor()
    data object Colour : SketchEditor()
    /** Recent sketches, and a way to browse for one. */
    data object Open : SketchEditor()
    /** The sketch has unsaved strokes and something is about to replace it or close the app. */
    data class Confirm(val then: () -> Unit) : SketchEditor()
}

/**
 * Live Sketch's state: a sketch session from the engine, the brush, the view over the page
 * (zoom and pan), the pen source and its readouts, and saving — to a file, into a board, into a
 * concept. Samples arrive on whatever thread the pen source uses and are handed to the event
 * dispatch thread; a test calls [onPen] and the mouse functions directly.
 *
 * The sketch lives while the app runs: leaving the screen keeps it, coming back shows it again.
 * Only replacing it (a new sketch, opening another) or closing the app asks about unsaved strokes.
 */
class SketchState(
    private val settings: Settings,
    private val host: SketchHost,
    private val timestamp: () -> String = { LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm")) },
) {
    // ---- The sketch ----

    var session by mutableStateOf<SketchSession?>(null)
        private set
    /** The `.sketch.json` this sketch was saved to or opened from, or null while it has none. */
    var file by mutableStateOf<File?>(null)
        private set
    var title by mutableStateOf("Untitled")
        private set
    /** The page's size in words, for the toolbar: "A4 150 dpi", "1600 × 1200 px". */
    var pageName by mutableStateOf("")
        private set
    var brush by mutableStateOf(Brush(Lead.MEDIUM, size = 8f))
        private set
    var eraser by mutableStateOf(false)
        private set
    var recentColors by mutableStateOf(listOf(0xFF1A1A1A.toInt(), 0xFF8B2F1E.toInt(), 0xFF1F4E8C.toInt()))
        private set
    /** Bumped whenever the page changed, so the screen draws it again. */
    var tick by mutableStateOf(0)
        private set
    /** Bumped when the history changed (a stroke kept, undo, redo, save, a new page), so the toolbar follows. */
    var version by mutableStateOf(0)
        private set
    var editor by mutableStateOf<SketchEditor?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
    /** A picture kept in view while sketching — the reference from a session. */
    var reference by mutableStateOf<File?>(null)
    var showPenPanel by mutableStateOf(false)
    var showTunables by mutableStateOf(false)

    val dirty: Boolean get() { version; return session?.dirty == true }
    val canUndo: Boolean get() { version; return session?.canUndo == true }
    val canRedo: Boolean get() { version; return session?.canRedo == true }

    /** What the next new page is, unless chosen otherwise: the last one chosen. */
    private var lastSize: PageSize = PageSize.a4(150)
    private var lastPaper: Int = 0xFFFFFFFF.toInt()

    // ---- The view: where the page is on screen ----

    var zoom by mutableStateOf(1f)
        private set
    /** The page's top-left corner in view pixels. */
    var panX by mutableStateOf(0f)
        private set
    var panY by mutableStateOf(0f)
        private set
    var viewSize: IntSize = IntSize.Zero
        private set
    /** Where the view sits in the window, in pixels; pen samples are window-relative. */
    var viewOrigin: Offset = Offset.Zero
    var spaceHeld by mutableStateOf(false)

    // ---- The pen ----

    var sourceName by mutableStateOf<String?>(null)
        private set
    var penStatus by mutableStateOf("Mouse only — no pen source on this machine.")
        private set
    var last by mutableStateOf<PenSample?>(null)
        private set
    var sampleCount by mutableStateOf(0)
        private set
    var rateHz by mutableStateOf(0)
        private set
    var recording by mutableStateOf(false)
        private set
    var recordedTo by mutableStateOf<File?>(null)
        private set

    private var source: PenSource? = null
    private val recentTimes = ArrayDeque<Long>()
    private var recordFile: File? = null
    private var penDrawing = false
    private var mouseDrawing = false
    private var penSeen = false
    private var lastPenNanos = 0L

    // ---- Entering, new, open ----

    /** The screen was entered: hook the pen, and have a page to draw on at once. */
    fun onEnter(window: Window) {
        attach(window)
        ensurePage()
    }

    /** A page to draw on: the one there is, or a new one of the last size chosen. */
    fun ensurePage() {
        if (session == null) newSketch(lastSize, lastPaper)
    }

    fun sketchesHome(): File = settings.sketchesHome()

    fun setSketchesHomeDir(dir: File) = settings.setSketchesHome(dir)

    /** Sketches saved or opened lately, anywhere, and those in the sketches home; newest first. */
    fun recentSketches(): List<File> {
        val inHome = sketchesHome().listFiles().orEmpty().filter { it.isFile && it.name.endsWith(SketchDocument.FILE_SUFFIX) }
        return (settings.recentSketches() + inHome)
            .filter { it.isFile }
            .distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
            .sortedByDescending { it.lastModified() }
    }

    fun newSketch(size: PageSize = lastSize, paper: Int = lastPaper) = guardUnsaved {
        session?.close()
        session = SketchSession(size.width, size.height, size.dpi, paper)
        lastSize = size
        lastPaper = paper
        file = null
        title = "Untitled"
        pageName = size.name
        penDrawing = false
        mouseDrawing = false
        version++
        tick++
        fit()
    }

    /** Opens a `.sketch.json`: the page and every stroke, drawn again; undo goes on from there. */
    fun open(target: File): Boolean {
        val document = runCatching { SketchDocument.fromJson(target.readText()) }.getOrNull() ?: run {
            notice = "Couldn't read ${target.name}."
            return false
        }
        var opened = false
        guardUnsaved {
            session?.close()
            session = SketchSession.fromDocument(document)
            file = target
            title = target.name.removeSuffix(SketchDocument.FILE_SUFFIX)
            pageName = "${document.width} × ${document.height} px · ${document.dpi} dpi"
            penDrawing = false
            mouseDrawing = false
            settings.addRecentSketch(target)
            version++
            tick++
            fit()
            opened = true
        }
        return opened
    }

    /** Runs [action] now, or after the user agrees to leave unsaved strokes behind. */
    fun guardUnsaved(action: () -> Unit) {
        if (dirty) editor = SketchEditor.Confirm(action) else action()
    }

    /** From a session: the picture on screen comes along as the reference. */
    fun openFromSession(picture: File?) {
        reference = picture
        ensurePage()
    }

    // ---- The brush ----

    fun setLead(lead: Lead) {
        brush = brush.copy(lead = lead)
        eraser = false
    }

    fun setSize(size: Float) {
        brush = brush.copy(size = size.coerceIn(1f, 80f))
    }

    fun setColor(color: Int) {
        val opaque = color or 0xFF000000.toInt()
        brush = brush.copy(color = opaque)
        eraser = false
        recentColors = (listOf(opaque) + recentColors.filter { it != opaque }).take(8)
    }

    fun toggleEraser() {
        eraser = !eraser
    }

    // ---- Drawing ----

    /** Page coordinates of a point given in view pixels. */
    fun toPage(viewX: Float, viewY: Float): Offset = Offset((viewX - panX) / zoom, (viewY - panY) / zoom)

    /**
     * A pen reading, window-relative pixels: draws while the tip touches, ends when it lifts.
     * Windows also turns the pen into mouse events; if one of those began a stroke first, it is
     * taken back — the pen's own stroke, with its pressure, is the one that counts.
     */
    fun onPen(sample: PenSample) {
        last = sample
        penSeen = true
        lastPenNanos = sample.timeNanos
        sampleCount++
        note(sample.timeNanos)
        record(sample)
        val s = session ?: return
        if (sample.contact) {
            if (!penDrawing) {
                if (mouseDrawing) {
                    s.cancel()
                    mouseDrawing = false
                }
                s.begin(brush, eraser)
                penDrawing = true
            }
            val page = toPage(sample.x - viewOrigin.x, sample.y - viewOrigin.y)
            s.add(InputSample(page.x, page.y, sample.pressure, sample.tiltX.toFloat(), sample.tiltY.toFloat(), sample.timeNanos, InputSample.Source.PEN))
            tick++
        } else if (penDrawing) {
            penDrawing = false
            s.end()
            version++
            tick++
        }
    }

    /**
     * Is a pen on or near the page? While it is, mouse input is the pen seen twice and is left
     * alone: the pen sends samples while it hovers, so this is true before the tip touches.
     */
    private fun penNearby(timeNanos: Long): Boolean =
        penDrawing || (penSeen && abs(timeNanos - lastPenNanos) < PEN_GRACE_NANOS)

    /** The mouse in view pixels, at pressure 1. A press with no movement leaves a dot. */
    fun mouseDown(viewX: Float, viewY: Float, timeNanos: Long = System.nanoTime()) {
        if (penNearby(timeNanos)) return
        val s = session ?: return
        s.begin(brush, eraser)
        mouseDrawing = true
        mouseMove(viewX, viewY, timeNanos)
    }

    fun mouseMove(viewX: Float, viewY: Float, timeNanos: Long = System.nanoTime()) {
        if (!mouseDrawing || penDrawing) return
        val s = session ?: return
        val page = toPage(viewX, viewY)
        s.add(InputSample(page.x, page.y, 1f, timeNanos = timeNanos, source = InputSample.Source.MOUSE))
        tick++
    }

    fun mouseUp() {
        if (!mouseDrawing) return
        mouseDrawing = false
        session?.end()
        version++
        tick++
    }

    fun undo() {
        session?.undo()
        version++
        tick++
    }

    fun redo() {
        session?.redo()
        version++
        tick++
    }

    // ---- The view ----

    fun viewResized(size: IntSize) {
        val first = viewSize == IntSize.Zero
        viewSize = size
        if (first) fit()
    }

    /** The whole page in the view, centred, with a margin. */
    fun fit() {
        val s = session ?: return
        if (viewSize.width <= 0 || viewSize.height <= 0) return
        val margin = 24f
        zoom = min((viewSize.width - 2 * margin) / s.width, (viewSize.height - 2 * margin) / s.height).coerceIn(MIN_ZOOM, MAX_ZOOM)
        panX = (viewSize.width - s.width * zoom) / 2f
        panY = (viewSize.height - s.height * zoom) / 2f
    }

    /** Zooms by [factor] about the view point ([aboutX], [aboutY]): what is under it stays put. */
    fun zoomBy(factor: Float, aboutX: Float, aboutY: Float) {
        val next = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val page = toPage(aboutX, aboutY)
        zoom = next
        panX = aboutX - page.x * next
        panY = aboutY - page.y * next
    }

    /** `+` / `−`: a step in or out about the middle of the view. */
    fun zoomStep(zoomIn: Boolean) = zoomBy(if (zoomIn) KEY_ZOOM else 1f / KEY_ZOOM, viewSize.width / 2f, viewSize.height / 2f)

    fun pan(dx: Float, dy: Float) {
        panX += dx
        panY += dy
    }

    // ---- Saving ----

    /** Ctrl+S: into the file this sketch has, or ask for one. */
    fun save(): String? {
        val target = file ?: run {
            editor = SketchEditor.SaveAs
            return null
        }
        return writeTo(target.parentFile ?: sketchesHome(), target.name.removeSuffix(SketchDocument.FILE_SUFFIX))
    }

    /** What a sketch with no name yet is saved as: "Sketch 2026-09-22 14.05". */
    fun suggestedName(): String = if (file != null) title else "Sketch " + timestamp()

    /**
     * Writes `<name>.png` and `<name>.sketch.json` into [dir]; the sketch is that file from now
     * on. A name some other sketch already has there is refused rather than overwritten.
     */
    fun saveAs(name: String, dir: File = sketchesHome()): String? {
        val clean = sanitize(name)
        if (clean.isEmpty()) return "Give the sketch a name."
        val json = File(dir, clean + SketchDocument.FILE_SUFFIX)
        val own = file?.let { sameFile(it, json) } == true
        if (!own && (json.exists() || File(dir, "$clean.png").exists())) {
            return "There is already a “$clean” in that folder — pick another name."
        }
        return writeTo(dir, clean)
    }

    /** Into a board's folder, and onto the board. */
    fun saveToBoard(dir: File): String {
        val name = nameIn(dir)
        val problem = writeTo(dir, name)
        if (problem != null) return problem
        val line = host.addToBoard(dir, listOf(File(dir, "$name.png")))
        notice = "Saved $name. $line"
        return line
    }

    /** Into a concept's folder, and into the concept. */
    fun saveToConcept(id: String): String {
        val dir = host.conceptDir(id) ?: return "That concept cannot be found."
        val name = nameIn(dir)
        val problem = writeTo(dir, name)
        if (problem != null) return problem
        val ok = host.addToConcept(id, listOf(File(dir, "$name.png")))
        val line = if (ok) "$name is in the concept now." else "Couldn't add to the concept."
        notice = line
        return line
    }

    /** The name to save under in [dir]: the sketch's own if it already lives there, else a free one. */
    private fun nameIn(dir: File): String {
        val current = file
        if (current != null && current.parentFile?.let { sameFile(it, dir) } == true) return title
        return uniqueName(dir, suggestedName())
    }

    private fun writeTo(dir: File, name: String): String? {
        val s = session ?: return "Nothing to save."
        return runCatching {
            dir.mkdirs()
            File(dir, "$name.png").writeBytes(s.exportPng())
            val json = File(dir, name + SketchDocument.FILE_SUFFIX)
            json.writeText(s.document().toJson())
            s.markSaved()
            file = json
            title = name
            settings.addRecentSketch(json)
            notice = "Saved $name to ${dir.name}."
            version++
            null
        }.getOrElse { "Couldn't save: ${it.message}" }
    }

    private fun uniqueName(dir: File, wanted: String): String {
        val base = sanitize(wanted).ifEmpty { "Sketch" }
        fun taken(name: String) = File(dir, "$name.png").exists() || File(dir, name + SketchDocument.FILE_SUFFIX).exists()
        if (!taken(base)) return base
        var n = 2
        while (taken("$base ($n)")) n++
        return "$base ($n)"
    }

    private fun sameFile(a: File, b: File): Boolean =
        runCatching { a.canonicalFile == b.canonicalFile }.getOrDefault(a.absoluteFile == b.absoluteFile)

    fun boards(): List<Pair<String, File>> = host.boards()
    fun concepts() = host.concepts()

    // ---- Leaving ----

    /** Back to where Live Sketch was opened from; the sketch stays for next time. */
    fun leave() {
        mouseUp()
        detach()
        host.leaveSketch()
    }

    fun openEditor(next: SketchEditor) {
        editor = next
    }

    fun closeEditor() {
        editor = null
    }

    // ---- The pen source ----

    fun attach(window: Window) {
        if (source != null) return
        val candidate = PenSources.forWindow(window)
        if (candidate == null) {
            penStatus = "Mouse only — no native pen route on this platform yet."
            return
        }
        val started = runCatching { candidate.start { sample -> EventQueue.invokeLater { onPen(sample) } } }.getOrDefault(false)
        if (started) {
            source = candidate
            sourceName = candidate.name
            penStatus = "Listening on ${candidate.name}."
        } else {
            penStatus = "${candidate.name} could not hook the window — mouse only."
        }
    }

    fun detach() {
        source?.stop()
        source = null
        sourceName = null
        if (penDrawing) {
            penDrawing = false
            session?.end()
            version++
        }
    }

    private fun note(timeNanos: Long) {
        recentTimes.addLast(timeNanos)
        while (recentTimes.isNotEmpty() && timeNanos - recentTimes.first() > 500_000_000L) recentTimes.removeFirst()
        rateHz = if (recentTimes.size >= 2) recentTimes.size * 2 else 0
    }

    fun toggleRecording(target: File = File(System.getProperty("user.home"), ".actiondraw/pen-samples.csv")) {
        if (recording) {
            recording = false
            return
        }
        runCatching {
            target.parentFile?.mkdirs()
            if (!target.exists()) target.writeText("timeNanos,x,y,pressure,tiltX,tiltY,rotation,contact,barrel,eraser,pointerType\n")
            recordFile = target
            recordedTo = target
            recording = true
        }
    }

    private fun record(s: PenSample) {
        if (!recording) return
        val f = recordFile ?: return
        runCatching {
            f.appendText("${s.timeNanos},${s.x},${s.y},${s.pressure},${s.tiltX},${s.tiltY},${s.rotation},${s.contact},${s.barrel},${s.eraser},${s.pointerType}\n")
        }
    }

    companion object {
        const val MIN_ZOOM = 0.05f
        const val MAX_ZOOM = 8f
        const val KEY_ZOOM = 1.25f
        /** How long after a pen sample mouse input still counts as that pen: hover to touch. */
        const val PEN_GRACE_NANOS = 300_000_000L

        fun sanitize(name: String): String = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().trimEnd('.')

        /** A zoom factor for one step of the wheel or the dial. */
        fun wheelZoom(delta: Float): Float = if (delta < 0) 1.15f else 1 / 1.15f
    }
}
