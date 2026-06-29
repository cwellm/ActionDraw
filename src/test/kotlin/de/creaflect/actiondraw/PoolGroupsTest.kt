package de.creaflect.actiondraw

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class PoolGroupsTest {
    private fun files(vararg n: String) = n.map { File(it) }
    private fun names(fs: List<File>) = fs.map { it.name }

    @Test
    fun redoFirstThenUnseenAndSeenDropped() {
        val all = files("a.jpg", "b.jpg", "c.jpg", "d.jpg")
        val (redoFirst, rest) = poolGroups(all, seen = setOf("b.jpg"), redo = setOf("c.jpg"))
        assertEquals(listOf("c.jpg"), names(redoFirst))       // flagged -> first group
        assertEquals(listOf("a.jpg", "d.jpg"), names(rest))   // b seen -> dropped, c only in redo group
    }

    @Test
    fun redoResurfacesEvenWhenAlreadySeen() {
        val all = files("a.jpg", "b.jpg")
        val (redoFirst, rest) = poolGroups(all, seen = setOf("a.jpg"), redo = setOf("a.jpg"))
        assertEquals(listOf("a.jpg"), names(redoFirst))
        assertEquals(listOf("b.jpg"), names(rest))
    }

    @Test
    fun emptyFlagsKeepAllUnseenInScanOrder() {
        val all = files("a.jpg", "b.jpg", "c.jpg")
        val (redoFirst, rest) = poolGroups(all, seen = emptySet(), redo = emptySet())
        assertEquals(emptyList(), names(redoFirst))
        assertEquals(listOf("a.jpg", "b.jpg", "c.jpg"), names(rest))
    }
}
