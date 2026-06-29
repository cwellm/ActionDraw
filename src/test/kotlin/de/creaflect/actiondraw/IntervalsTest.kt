package de.creaflect.actiondraw

import de.creaflect.actiondraw.ui.INTERVAL_OPTIONS
import de.creaflect.actiondraw.ui.intervalIndexOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntervalsTest {
    @Test
    fun coversFullRangeWithExpectedSteps() {
        assertEquals(30, INTERVAL_OPTIONS.first())
        assertEquals(3600, INTERVAL_OPTIONS.last())
        assertEquals(46, INTERVAL_OPTIONS.size)
        assertTrue(INTERVAL_OPTIONS.contains(600))  // 10 min boundary
        assertTrue(INTERVAL_OPTIONS.contains(660))  // 11 min (1-min steps)
        assertTrue(INTERVAL_OPTIONS.contains(1800)) // 30 min boundary
        assertTrue(INTERVAL_OPTIONS.contains(2100)) // 35 min (5-min steps)
        assertTrue(INTERVAL_OPTIONS.zipWithNext().all { (a, b) -> b > a }) // strictly increasing
    }

    @Test
    fun indexLookupSnapsToNearestOption() {
        assertEquals(0, intervalIndexOf(30))
        assertEquals(INTERVAL_OPTIONS.indexOf(120), intervalIndexOf(120))
        assertEquals(INTERVAL_OPTIONS.indexOf(660), intervalIndexOf(650)) // snaps up to next option
    }
}
