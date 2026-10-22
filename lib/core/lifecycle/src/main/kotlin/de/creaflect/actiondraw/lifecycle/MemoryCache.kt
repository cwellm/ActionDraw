package de.creaflect.actiondraw.lifecycle

/**
 * Memory the lib holds while it runs: at most [capacity] entries, the least recently used going
 * first when a new one comes in. Safe to use from several threads. Its run gives it back — empties
 * it — on `trim` and when it closes; anything held here can always be computed again.
 */
class MemoryCache<K : Any, V : Any> internal constructor(val capacity: Int) {
    init {
        require(capacity > 0) { "a cache holds at least one entry" }
    }

    private val entries = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > capacity
    }

    val size: Int get() = synchronized(entries) { entries.size }

    operator fun get(key: K): V? = synchronized(entries) { entries[key] }

    fun put(key: K, value: V) {
        synchronized(entries) { entries[key] = value }
    }

    /**
     * What is held for [key], or else what [compute] makes of it, kept for next time. A null from
     * [compute] is not kept, so a picture that could not be read is tried again later. [compute]
     * runs outside the lock: two threads may both compute a missing entry, and the later one stays.
     */
    fun getOrPut(key: K, compute: () -> V?): V? {
        get(key)?.let { return it }
        val value = compute() ?: return null
        put(key, value)
        return value
    }

    fun remove(key: K) {
        synchronized(entries) { entries.remove(key) }
    }

    fun clear() {
        synchronized(entries) { entries.clear() }
    }
}
