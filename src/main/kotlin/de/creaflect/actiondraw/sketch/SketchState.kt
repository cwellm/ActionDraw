package de.creaflect.actiondraw.sketch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import de.creaflect.actiondraw.sketch.input.PenSample
import de.creaflect.actiondraw.sketch.input.PenSource
import de.creaflect.actiondraw.sketch.input.PenSources
import de.creaflect.sketch.Brush
import de.creaflect.sketch.InputSample
import de.creaflect.sketch.Lead
import de.creaflect.sketch.SketchSurface
import de.creaflect.sketch.StrokeBuilder
import java.awt.EventQueue
import java.awt.Window
import java.io.File

/**
 * The Live Sketch probe's state: a pen source if the machine has one, the readouts the probe
 * shows, a page drawn into through the engine, and a recorder that writes every sample to a
 * file for LEARNINGS. Samples arrive on whatever thread the source uses and are handed to the
 * event dispatch thread, where the page is drawn; a test may call [onPen] directly.
 */
class SketchState {
    var sourceName by mutableStateOf<String?>(null)
        private set
    var status by mutableStateOf("Mouse only — no pen source on this machine.")
        private set
    var last by mutableStateOf<PenSample?>(null)
        private set
    var sampleCount by mutableStateOf(0)
        private set
    /** Samples per second over the last half second, while the pen is moving. */
    var rateHz by mutableStateOf(0)
        private set
    var brush by mutableStateOf(Brush(Lead.MEDIUM, size = 10f))
        private set
    /** Bumped whenever the page changed, so the screen takes a new snapshot. */
    var tick by mutableStateOf(0)
        private set
    var recording by mutableStateOf(false)
        private set
    var recordedTo by mutableStateOf<File?>(null)
        private set
    /** Where the page sits in the window, in pixels; pen samples are window-relative. */
    var pageOrigin: Offset = Offset.Zero

    var page: SketchSurface? = null
        private set
    private var builder: StrokeBuilder? = null
    private var source: PenSource? = null
    private val recentTimes = ArrayDeque<Long>()
    private var recordFile: File? = null

    // ---- The source ----

    /** Hooks the window for pen input, once it is on screen. Safe to call again. */
    fun attach(window: Window) {
        if (source != null) return
        val candidate = PenSources.forWindow(window)
        if (candidate == null) {
            status = "Mouse only — no native pen route on this platform yet."
            return
        }
        val started = runCatching { candidate.start { sample -> EventQueue.invokeLater { onPen(sample) } } }.getOrDefault(false)
        if (started) {
            source = candidate
            sourceName = candidate.name
            status = "Listening on ${candidate.name}. Put the pen to the page."
        } else {
            status = "${candidate.name} could not hook the window — mouse only."
        }
    }

    fun detach() {
        source?.stop()
        source = null
        sourceName = null
        endStroke()
    }

    // ---- The page ----

    /** The page takes the size of the area it is shown in, once known; a new size starts afresh. */
    fun ensurePage(width: Int, height: Int) {
        val current = page
        if (current != null && current.width == width && current.height == height) return
        if (width <= 0 || height <= 0) return
        current?.close()
        page = SketchSurface(width, height)
        builder = null
        tick++
    }

    fun clear() {
        page?.clear()
        builder = null
        tick++
    }

    fun setLead(lead: Lead) {
        brush = brush.copy(lead = lead)
    }

    fun setSize(size: Float) {
        brush = brush.copy(size = size.coerceIn(1f, 60f))
    }

    // ---- Samples ----

    /** A pen reading; window-relative pixels. Drawn while the tip touches, ended when it lifts. */
    fun onPen(sample: PenSample) {
        last = sample
        sampleCount++
        note(sample.timeNanos)
        record(sample)
        if (sample.contact) {
            stroke(
                InputSample(
                    x = sample.x - pageOrigin.x,
                    y = sample.y - pageOrigin.y,
                    pressure = sample.pressure,
                    tiltX = sample.tiltX.toFloat(),
                    tiltY = sample.tiltY.toFloat(),
                    timeNanos = sample.timeNanos,
                    source = InputSample.Source.PEN,
                ),
            )
        } else {
            endStroke()
        }
    }

    /** The mouse, at pressure 1 — so the page works without a tablet, just not like a pencil. */
    fun onMouse(x: Float, y: Float, timeNanos: Long = System.nanoTime()) {
        // While a pen is in contact its samples own the stroke; the promoted mouse events are
        // the same movement seen twice.
        if (last?.contact == true) return
        stroke(InputSample(x, y, 1f, timeNanos = timeNanos, source = InputSample.Source.MOUSE))
    }

    fun endStroke() {
        builder = null
    }

    private fun stroke(sample: InputSample) {
        val surface = page ?: return
        val current = builder ?: StrokeBuilder(brush).also { builder = it }
        val previous = current.current.lastOrNull()
        val point = current.add(sample)
        if (previous == null) surface.drawDot(brush, point) else surface.drawSegment(brush, previous, point)
        tick++
    }

    private fun note(timeNanos: Long) {
        recentTimes.addLast(timeNanos)
        while (recentTimes.isNotEmpty() && timeNanos - recentTimes.first() > 500_000_000L) recentTimes.removeFirst()
        rateHz = if (recentTimes.size >= 2) (recentTimes.size * 2) else 0
    }

    // ---- Recording, for LEARNINGS ----

    fun toggleRecording(file: File = File(System.getProperty("user.home"), ".actiondraw/pen-samples.csv")) {
        if (recording) {
            recording = false
            return
        }
        runCatching {
            file.parentFile?.mkdirs()
            if (!file.exists()) file.writeText("timeNanos,x,y,pressure,tiltX,tiltY,rotation,contact,barrel,eraser,pointerType\n")
            recordFile = file
            recordedTo = file
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
}
