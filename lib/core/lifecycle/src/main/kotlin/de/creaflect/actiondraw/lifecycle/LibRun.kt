package de.creaflect.actiondraw.lifecycle

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The lib's run, from loading to closing. One per application: whoever needs to work off the
 * caller's thread, write something that must not be lost, or hold memory while the app runs gets
 * it from here instead of choosing its own dispatcher, its own thread or its own cache.
 *
 * - [scope] is where the lib's long-running work lives; closing cancels whatever is left of it.
 * - [io] is the dispatcher for disk and network work.
 * - [writes] takes the writes that must reach the disk — a board's sidecar after an edit — and
 *   writes them off the caller's thread, the newest of a key winning; closing waits for them.
 * - [cache] hands out memory the lib holds while it runs, given back on [trim] and on close.
 */
class LibRun private constructor(
    /** Where disk and network work runs. */
    val io: CoroutineDispatcher,
    inlineWrites: Boolean,
) : AutoCloseable {
    /** A run whose work goes to [io] — [Dispatchers.IO] unless a caller hands in its own. */
    constructor(io: CoroutineDispatcher = Dispatchers.IO) : this(io, inlineWrites = false)

    /** Where the lib is in its run. */
    enum class Phase {
        /** Built, and still loading what it needs before it is of use. */
        LOADING,

        /** Loaded and at work. */
        RUNNING,

        /** Closing: writing what is pending, giving memory back. */
        CLOSING,

        /** Closed: whatever is written now is written at once, on the caller's thread. */
        CLOSED,
    }

    private val job = SupervisorJob()
    private val phaseNow = MutableStateFlow(Phase.LOADING)
    private val caches = CopyOnWriteArrayList<MemoryCache<*, *>>()

    /** Where the lib is in its run, for whoever wants to show it. */
    val phase: StateFlow<Phase> = phaseNow.asStateFlow()

    /** Where the lib's long-running work lives; closing cancels what is left of it. */
    val scope: CoroutineScope = CoroutineScope(job + io + CoroutineName("actiondraw"))

    /** The writes that must reach the disk. */
    val writes: Writes = Writes(scope, inline = inlineWrites)

    /** Marks the lib as loaded: it is running from now on. */
    fun loaded() {
        phaseNow.compareAndSet(Phase.LOADING, Phase.RUNNING)
    }

    /**
     * Memory the lib may hold while it runs: at most [capacity] entries, the least recently used
     * going first. Given back on [trim] and when the run closes.
     */
    fun <K : Any, V : Any> cache(capacity: Int): MemoryCache<K, V> = MemoryCache<K, V>(capacity).also { caches += it }

    /** Gives back the memory the caches hold — when the app is told memory is short, say. */
    fun trim() {
        caches.forEach { it.clear() }
    }

    /**
     * Closes the run: waits up to [timeout] for the writes still pending, gives the memory back,
     * and cancels whatever work is left. Returns false when a write was still under way when the
     * time ran out. A write handed over after this happens at once, so none is ever dropped.
     */
    fun close(timeout: Duration): Boolean {
        if (phaseNow.value == Phase.CLOSED) return true
        phaseNow.value = Phase.CLOSING
        val written = writes.close(timeout)
        trim()
        caches.clear()
        scope.cancel()
        phaseNow.value = Phase.CLOSED
        return written
    }

    /** Closes with a few seconds for the pending writes — enough for a sidecar, not a hang. */
    override fun close() {
        close(5.seconds)
    }

    companion object {
        /**
         * A run whose writes happen at once, on the caller's thread, and whose work runs where it
         * is started: for tests and tools that want the lib without threads of its own.
         */
        fun inline(): LibRun = LibRun(Dispatchers.Unconfined, inlineWrites = true).also { it.loaded() }
    }
}
