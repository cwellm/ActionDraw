package de.creaflect.actiondraw.lifecycle

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class LibRunTest {
    private val pool = Executors.newFixedThreadPool(2)
    private val run = LibRun(pool.asCoroutineDispatcher())

    @AfterTest
    fun cleanup() {
        run.close()
        pool.shutdownNow()
    }

    @Test
    fun aWriteHappensOffTheCallersThreadAndFlushWaitsForIt() {
        val caller = Thread.currentThread()
        var writer: Thread? = null
        run.writes.write("board") {
            Thread.sleep(50)
            writer = Thread.currentThread()
        }
        assertTrue(run.writes.flush(5.seconds))
        assertNotEquals(caller, writer, "written on the run's thread, not the caller's")
        assertFalse(run.writes.isPending("board"))
    }

    @Test
    fun theNewestWriteOfAKeyWins() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val written = mutableListOf<String>()
        run.writes.write("a") {
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
        }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        // While "a" is being written, three edits of "b" come in; only the last is worth writing.
        run.writes.write("b") { synchronized(written) { written += "b1" } }
        run.writes.write("b") { synchronized(written) { written += "b2" } }
        run.writes.write("b") { synchronized(written) { written += "b3" } }
        assertTrue(run.writes.isPending("b"))
        release.countDown()
        assertTrue(run.writes.flush(5.seconds))
        assertEquals(listOf("b3"), written)
    }

    @Test
    fun writesOfDifferentKeysAllHappenInTheOrderTheyCameIn() {
        val written = mutableListOf<String>()
        listOf("one", "two", "three").forEach { key -> run.writes.write(key) { synchronized(written) { written += key } } }
        assertTrue(run.writes.flush(5.seconds))
        assertEquals(listOf("one", "two", "three"), written)
    }

    @Test
    fun awaitKeyWaitsForThatKeysWriteBeforeItIsReadBack() {
        var onDisk = "old"
        run.writes.write("sidecar") {
            Thread.sleep(80)
            onDisk = "new"
        }
        assertTrue(run.writes.awaitKey("sidecar", 5.seconds))
        assertEquals("new", onDisk)
    }

    @Test
    fun aWriteThatThrowsIsReportedAndTheOthersGoOn() {
        val errors = AtomicInteger()
        var second = false
        run.writes.onError = { errors.incrementAndGet() }
        run.writes.write("bad") { error("disk full") }
        run.writes.write("good") { second = true }
        assertTrue(run.writes.flush(5.seconds))
        assertEquals(1, errors.get())
        assertTrue(second)
    }

    @Test
    fun closingWritesWhatIsPendingAndALaterWriteStillHappens() {
        var first = false
        run.writes.write("a") {
            Thread.sleep(80)
            first = true
        }
        assertTrue(run.close(5.seconds))
        assertTrue(first, "the pending write was waited for")
        assertEquals(LibRun.Phase.CLOSED, run.phase.value)
        var late = false
        run.writes.write("b") { late = true }
        assertTrue(late, "after closing, a write happens at once rather than being lost")
    }

    @Test
    fun theSuspendingFlushWaitsToo() {
        var done = false
        run.writes.write("a") {
            Thread.sleep(50)
            done = true
        }
        runBlocking { run.writes.flush() }
        assertTrue(done)
    }

    @Test
    fun anInlineRunWritesAtOnceOnTheCallersThread() {
        val inline = LibRun.inline()
        var writer: Thread? = null
        inline.writes.write("a") { writer = Thread.currentThread() }
        assertEquals(Thread.currentThread(), writer)
        assertEquals(LibRun.Phase.RUNNING, inline.phase.value)
    }

    @Test
    fun theRunGoesFromLoadingToRunningToClosed() {
        assertEquals(LibRun.Phase.LOADING, run.phase.value)
        run.loaded()
        assertEquals(LibRun.Phase.RUNNING, run.phase.value)
        run.close()
        assertEquals(LibRun.Phase.CLOSED, run.phase.value)
    }

    @Test
    fun aCacheForgetsTheLeastRecentlyUsedAndIsGivenBack() {
        val cache = run.cache<String, Int>(capacity = 2)
        cache.put("a", 1)
        cache.put("b", 2)
        assertEquals(1, cache["a"], "reading a makes b the least recently used")
        cache.put("c", 3)
        assertNull(cache["b"])
        assertEquals(setOf(1, 3), setOfNotNull(cache["a"], cache["c"]))

        assertNull(cache.getOrPut("missing") { null }, "nothing to keep")
        assertEquals(2, cache.size)
        run.trim()
        assertEquals(0, cache.size, "trim gives the memory back")
        cache.put("d", 4)
        run.close()
        assertEquals(0, cache.size, "and so does closing")
    }
}
