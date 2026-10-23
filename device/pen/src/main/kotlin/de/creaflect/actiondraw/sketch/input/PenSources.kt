package de.creaflect.actiondraw.sketch.input

import com.sun.jna.Platform
import java.awt.Window

/** The pen's native routes, by platform. */
object PenSources {
    /** The native route for this platform, or null where there is none yet. */
    fun forWindow(window: Window): PenSource? =
        if (Platform.isWindows()) WindowsPointerSource(window) else null
}
