package de.creaflect.actiondraw.platform

import de.creaflect.actiondraw.sketch.input.PenSample
import de.creaflect.actiondraw.sketch.input.PenSource
import java.awt.EventQueue

/**
 * [pen] with its samples delivered on the UI thread — AWT's event thread, which Compose Desktop
 * draws on — whatever thread the device reads them on. Live Sketch takes them from there.
 */
fun onUiThread(pen: PenSource): PenSource = object : PenSource by pen {
    override fun start(onSample: (PenSample) -> Unit): Boolean = pen.start { sample -> EventQueue.invokeLater { onSample(sample) } }
}
