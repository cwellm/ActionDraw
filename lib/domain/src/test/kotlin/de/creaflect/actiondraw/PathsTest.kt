package de.creaflect.actiondraw

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The domain's rules on names and paths: what a folder may be called, and which lies inside which. */
class PathsTest {
    @Test
    fun safeNamesHaveNothingWindowsRefuses() {
        assertEquals("a_b_c_d", safeFileName("a/b\\c:d"))
        assertEquals("Wings_ study", safeFileName("Wings: study"))
        assertEquals("Drachen", safeFileName("  Drachen.. "), "nor the spaces and dots Windows would trim")
        assertEquals("_______", safeFileName("*?\"<>|/"))
    }

    @Test
    fun aFolderIsTheSameWhateverItsCase() {
        assertTrue(File("C:\\Boards\\Test").samePathAs(File("c:\\boards\\test")))
        assertFalse(File("C:\\Boards\\Test").samePathAs(File("C:\\Boards\\Test2")))
    }

    @Test
    fun aNeighbourWithALongerNameIsNotInside() {
        val board = File("C:\\Boards\\Drachen")
        assertTrue(File(board, "Wings").isInside(board))
        assertFalse(File("C:\\Boards\\Drachen2").isInside(board), "beside it, however alike the names")
        assertFalse(board.isInside(board), "a folder is not inside itself")
    }
}
