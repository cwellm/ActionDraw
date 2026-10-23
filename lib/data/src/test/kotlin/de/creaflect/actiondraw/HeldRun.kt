package de.creaflect.actiondraw

import de.creaflect.actiondraw.lifecycle.LibRun
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * A run whose work is held back until [release]: whatever is handed to its writes stays pending
 * until then, so a test sees what happens before a write lands — and whether a read waits for it.
 */
class HeldRun : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor()
    private val gate = CountDownLatch(1)
    val run = LibRun(executor.asCoroutineDispatcher())

    init {
        // The run's one thread is busy until the gate opens; its writes queue up behind it.
        executor.execute { gate.await() }
    }

    fun release() = gate.countDown()

    /** Lets the writes go a moment after the caller has started waiting for them. */
    fun releaseSoon() {
        thread {
            Thread.sleep(150)
            release()
        }
    }

    override fun close() {
        release()
        run.close()
        executor.shutdown()
    }
}
