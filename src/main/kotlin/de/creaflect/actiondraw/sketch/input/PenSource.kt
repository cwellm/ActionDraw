package de.creaflect.actiondraw.sketch.input

import com.sun.jna.Platform
import java.awt.Window

/**
 * One reading from the pen as the operating system reports it, before the engine sees it:
 * position in the window's client pixels, pressure 0..1, tilt and rotation in degrees, whether
 * the tip touches, which buttons are down, and what kind of pointer it was. Everything the probe
 * shows and records comes from here.
 */
data class PenSample(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val tiltX: Int,
    val tiltY: Int,
    val rotation: Int,
    val contact: Boolean,
    val barrel: Boolean,
    val eraser: Boolean,
    val pointerType: PointerKind,
    val timeNanos: Long,
) {
    enum class PointerKind { PEN, TOUCH, MOUSE, OTHER }
}

/**
 * Where pen samples come from. Compose Desktop delivers none (LEARNINGS L1), so a source hooks
 * the window natively. [start] returns false when this machine has no such route; the app then
 * draws with the mouse at pressure 1.
 */
interface PenSource {
    val name: String

    /** Begins delivering samples; the callback may arrive on any thread. */
    fun start(onSample: (PenSample) -> Unit): Boolean

    fun stop()
}

object PenSources {
    /** The native route for this platform, or null where there is none yet. */
    fun forWindow(window: Window): PenSource? =
        if (Platform.isWindows()) WindowsPointerSource(window) else null
}
