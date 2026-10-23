package de.creaflect.actiondraw

import java.io.File

/**
 * The lib's logs: what the pen and the pointer really deliver, written down as it arrives, for
 * the cases a test cannot reproduce. Nothing is logged unless someone starts it.
 */
interface Diagnostics {
    enum class Log {
        /** Every pointer event the canvas sees, while `ACTIONDRAW_POINTER_LOG` is set. */
        POINTER,

        /** The pen's raw samples, while the Pen panel records them. */
        PEN_SAMPLES,
    }

    /** Where [log] is written, to tell the person. */
    fun fileOf(log: Log): File

    /** Begins [log] with [header] unless it has begun already. False when it cannot be written. */
    fun begin(log: Log, header: String): Boolean

    /** Adds [text] to the end of [log]; a log that cannot be written is skipped. */
    fun append(log: Log, text: String)
}
