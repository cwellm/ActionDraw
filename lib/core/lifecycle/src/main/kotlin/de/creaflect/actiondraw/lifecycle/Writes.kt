package de.creaflect.actiondraw.lifecycle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * The writes that must reach the disk, done off the caller's thread. Each belongs to a key — the
 * file it writes, say — and a write handed over for a key whose last one has not started yet
 * replaces it: the newest content wins, and a burst of edits costs one write, not one each.
 * Writes run one at a time, in the order their keys first came in.
 *
 * A write that throws is reported to [onError] and does not stop the others. Inline writes —
 * the inline run's, and every write after [close] — happen at once, on the caller's thread.
 */
class Writes internal constructor(private val scope: CoroutineScope, private val inline: Boolean) {
    private val lock = Object()
    private val pending = LinkedHashMap<Any, () -> Unit>()
    private var running: Any? = null
    private var working = false
    private val outstanding = MutableStateFlow(0)

    @Volatile private var closed = false

    /** Told of every write that threw; the write is dropped, the others go on. */
    @Volatile var onError: (Throwable) -> Unit = {}

    /**
     * Hands over a write for [key]. One for the same key that has not started yet is replaced by
     * this one; one under way finishes, and this one follows it.
     */
    fun write(key: Any, block: () -> Unit) {
        if (inline || closed) {
            run(block)
            return
        }
        synchronized(lock) {
            pending[key] = block
            count()
            if (!working) {
                working = true
                scope.launch { drain() }
            }
        }
    }

    /** Whether a write for [key] is waiting or under way. */
    fun isPending(key: Any): Boolean = synchronized(lock) { key in pending || running == key }

    /**
     * Waits until no write for [key] is waiting or under way — before reading back what it
     * writes. False when [timeout] ran out first.
     */
    fun awaitKey(key: Any, timeout: Duration): Boolean = waitUntil(timeout) { key !in pending && running != key }

    /** Waits until every write handed over so far has happened. False when [timeout] ran out first. */
    fun flush(timeout: Duration): Boolean = waitUntil(timeout) { pending.isEmpty() && running == null }

    /** [flush] for a caller that can wait without blocking a thread. */
    suspend fun flush() {
        outstanding.first { it == 0 }
    }

    /** No more writes off the caller's thread: waits for the pending ones, up to [timeout]. */
    internal fun close(timeout: Duration): Boolean {
        val done = flush(timeout)
        closed = true
        return done
    }

    private fun drain() {
        while (true) {
            val block = synchronized(lock) {
                val next = pending.entries.firstOrNull()
                if (next == null) {
                    working = false
                    running = null
                    count()
                    lock.notifyAll()
                    return
                }
                pending.remove(next.key)
                running = next.key
                next.value
            }
            run(block)
            synchronized(lock) {
                running = null
                count()
                lock.notifyAll()
            }
        }
    }

    private fun run(block: () -> Unit) {
        runCatching(block).onFailure { onError(it) }
    }

    private fun count() {
        outstanding.value = pending.size + (if (running != null) 1 else 0)
    }

    private fun waitUntil(timeout: Duration, done: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + timeout.inWholeNanoseconds
        synchronized(lock) {
            while (!done()) {
                val left = deadline - System.nanoTime()
                if (left <= 0) return false
                lock.wait(left / 1_000_000, (left % 1_000_000).toInt())
            }
            return true
        }
    }
}
