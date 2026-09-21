package de.creaflect.actiondraw.board.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import java.io.File

/**
 * A diagnostic, off unless the environment variable `ACTIONDRAW_POINTER_LOG` is set: every
 * pointer event that reaches the canvas, and every decision a canvas gesture takes, is appended
 * to `~/.actiondraw/pointer.log`. It exists because a drag that every test moves correctly still
 * panned on one machine — the events a real mouse or pen delivers are the only thing the tests
 * cannot inject, and this shows them as they arrive.
 */
object PointerLog {
    val enabled: Boolean = System.getenv("ACTIONDRAW_POINTER_LOG") != null

    private val file: File by lazy {
        File(System.getProperty("user.home"), ".actiondraw/pointer.log").apply { parentFile?.mkdirs() }
    }

    fun log(line: String) {
        if (!enabled) return
        runCatching { file.appendText("${System.currentTimeMillis() % 1_000_000} $line\n") }
    }

    fun describe(event: PointerEvent, pass: PointerEventPass): String {
        val changes = event.changes.joinToString(" | ") { c ->
            "id=${c.id.value} at=(${c.position.x.toInt()},${c.position.y.toInt()}) pressed=${c.pressed}<-${c.previousPressed}" +
                " consumed=${c.isConsumed} type=${c.type}"
        }
        return "${event.type} $pass [$changes] primary=${event.buttons.isPrimaryPressed} secondary=${event.buttons.isSecondaryPressed}"
    }
}

/** Logs every event in the Initial and Final passes as it goes through this node. */
fun Modifier.pointerLogging(tag: String): Modifier =
    if (!PointerLog.enabled) this
    else pointerInput(tag) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                PointerLog.log("$tag ${PointerLog.describe(event, PointerEventPass.Initial)}")
                awaitPointerEvent(PointerEventPass.Final)
                PointerLog.log("$tag ${PointerLog.describe(event, PointerEventPass.Final)}")
            }
        }
    }
