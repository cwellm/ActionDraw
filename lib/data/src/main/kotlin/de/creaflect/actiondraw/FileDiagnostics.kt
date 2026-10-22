package de.creaflect.actiondraw

import de.creaflect.actiondraw.Diagnostics.Log
import de.creaflect.actiondraw.io.AppDirs
import java.io.File

/** The [Diagnostics] as files in `~/.actiondraw`, written as the lines arrive. */
internal class FileDiagnostics(private val dirs: AppDirs) : Diagnostics {
    override fun fileOf(log: Log): File = when (log) {
        Log.POINTER -> dirs.pointerLog
        Log.PEN_SAMPLES -> dirs.penSamples
    }

    override fun begin(log: Log, header: String): Boolean = runCatching {
        val file = fileOf(log)
        file.parentFile?.mkdirs()
        if (!file.exists()) file.writeText(header)
        true
    }.getOrDefault(false)

    override fun append(log: Log, text: String) {
        val file = fileOf(log)
        // The folder is made only when the first line finds it missing, not for every line.
        runCatching { file.appendText(text) }.onFailure {
            runCatching {
                file.parentFile?.mkdirs()
                file.appendText(text)
            }
        }
    }
}
