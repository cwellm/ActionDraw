package de.creaflect.actiondraw.collage

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A piece cut as paper is: across from edge to edge, as often as the cut goes through; round a part
 * of it, cut out — and what is left still one outline, the scissors' way in from its edge parting
 * it too when it is cut across later. Torn edges, tape's strips, and what overlaps.
 */
class CutsTest {
    /** A sheet 200 wide and 100 high. */
    private val sheet = listOf(Spot(0f, 0f), Spot(200f, 0f), Spot(200f, 100f), Spot(0f, 100f))

    private fun areas(parts: List<List<Spot>>) = parts.map { areaOf(it).toInt() }.sortedDescending()

    private fun square(l: Float, t: Float, r: Float, b: Float) = listOf(Spot(l, t), Spot(r, t), Spot(r, b), Spot(l, b))

    @Test
    fun aCutFromEdgeToEdgePartsTheSheetInTwo() {
        val parts = divide(sheet, listOf(Spot(150f, -10f), Spot(150f, 110f)), closed = false)
        assertEquals(listOf(15000, 5000), areas(parts))
        val right = parts.first { insideOf(it, Spot(175f, 50f)) }
        assertFalse(insideOf(right, Spot(125f, 50f)), "each part on its own side of the cut")
    }

    @Test
    fun aCutThatStopsInsideOrMissesCutsNothing() {
        assertEquals(listOf(sheet), divide(sheet, listOf(Spot(-10f, 50f), Spot(100f, 50f)), closed = false), "a slit, not a cut")
        assertEquals(listOf(sheet), divide(sheet, listOf(Spot(-10f, 150f), Spot(210f, 150f)), closed = false), "beside it")
        assertEquals(listOf(sheet), divide(sheet, square(-10f, -10f, 210f, 110f), closed = true), "round all of it: nothing cut off")
    }

    @Test
    fun aCutInAndOutAgainPartsTheSheetEachTimeItGoesThrough() {
        // Down through it, along under it, and back up through it.
        val path = listOf(Spot(50f, -10f), Spot(50f, 110f), Spot(150f, 110f), Spot(150f, -10f))
        assertEquals(listOf(10000, 5000, 5000), areas(divide(sheet, path, closed = false)))
    }

    @Test
    fun aCutFreehandFollowsItsCorners() {
        val path = listOf(Spot(60f, -10f), Spot(60f, 40f), Spot(140f, 60f), Spot(140f, 110f))
        val parts = divide(sheet, path, closed = false)
        assertEquals(listOf(10000, 10000), areas(parts), "down, along the slope, and down again: half and half")
        // Under the slope at x = 100 it is y = 50.
        val below = parts.first { insideOf(it, Spot(100f, 55f)) }
        assertFalse(insideOf(below, Spot(100f, 45f)), "parted along the slope, not straight across")
    }

    @Test
    fun aCutRoundAPartInsideCutsItOutAndLeavesAHole() {
        val ring = square(80f, 30f, 120f, 70f)
        val parts = divide(sheet, ring, closed = true)
        assertEquals(listOf(18400, 1600), areas(parts))
        val left = parts.first { areaOf(it) > 2000f }
        assertFalse(insideOf(left, Spot(100f, 50f)), "a hole where the part was")
        assertTrue(insideOf(left, Spot(10f, 10f)) && insideOf(left, Spot(190f, 90f)), "and all round it")
    }

    @Test
    fun aCutRoundOverAnEdgeCutsOffWhatItGoesRound() {
        val parts = divide(sheet, square(80f, -20f, 120f, 30f), closed = true)
        assertEquals(listOf(18800, 1200), areas(parts))
        // Begun inside the sheet, the same.
        val inside = listOf(Spot(100f, 30f), Spot(80f, 30f), Spot(80f, -20f), Spot(120f, -20f), Spot(120f, 30f))
        assertEquals(listOf(18800, 1200), areas(divide(sheet, inside, closed = true)))
    }

    @Test
    fun theWayTheScissorsCameInPartsTooWhenItIsCutAcross() {
        // The hole's corner (80, 30) is nearest the top edge: the scissors came in down x = 80.
        val left = divide(sheet, square(80f, 30f, 120f, 70f), closed = true).maxBy { areaOf(it) }
        // Across above the hole, through the way in: two strips off the top, either side of it.
        assertEquals(listOf(15400, 1800, 1200), areas(divide(left, listOf(Spot(-10f, 15f), Spot(210f, 15f)), closed = false)))
        // Down through the hole: the bit between the way in and the cut, and the two sides.
        assertEquals(listOf(9200, 8600, 600), areas(divide(left, listOf(Spot(100f, -10f), Spot(100f, 110f)), closed = false)))
    }

    @Test
    fun whicheverWayRoundTheOutlineGoesTheCutIsTheSame() {
        val backwards = sheet.reversed()
        assertEquals(listOf(15000, 5000), areas(divide(backwards, listOf(Spot(150f, 110f), Spot(150f, -10f)), closed = false)))
        assertEquals(listOf(18400, 1600), areas(divide(backwards, square(80f, 30f, 120f, 70f).reversed(), closed = true)))
    }

    @Test
    fun aTearWandersOffItsPathButNoFurtherThanItsReach() {
        val path = listOf(Spot(0f, 50f), Spot(300f, 50f))
        val tear = torn(path, seed = 7)
        assertEquals(tear, torn(path, seed = 7), "the same seed, the same tear")
        assertTrue(tear != torn(path, seed = 8))
        assertTrue(tear.size >= 300 / TEAR_STEP.toInt(), "followed every few units: ${tear.size}")
        assertTrue(tear.all { abs(it.y - 50f) <= TEAR_REACH + 0.01f }, "within its reach of the path")
        assertTrue(tear.maxOf { it.y } - tear.minOf { it.y } > TEAR_REACH / 2, "and ragged, not straight")
        // Turned to run down across the sheet at x = 50.
        val parts = divide(sheet, tear.map { Spot(it.y, it.x - 50f) }, closed = false)
        assertEquals(2, parts.size, "torn across, the sheet parts")
        assertEquals(20000f, parts.sumOf { areaOf(it).toDouble() }.toFloat(), 1f)
    }

    @Test
    fun aStripOfTapeIsAsLongAndWideAsItIsLaidTornAtItsEnds() {
        val strip = tapeStrip(120f, 34f, seed = 3)
        assertEquals(0f, strip.minOf { it.x })
        assertEquals(120f, strip.maxOf { it.x })
        assertEquals(34f, strip.maxOf { it.y })
        assertTrue(strip.size > 8, "torn across its ends, not cut square")
        assertTrue(strip.filter { it.x > 0f && it.x < 120f }.all { it.x < 10f || it.x > 110f }, "straight along its sides")
    }

    @Test
    fun outlinesOverlapWhereverTheyMeet() {
        assertTrue(overlaps(sheet, square(190f, 90f, 300f, 300f)), "a corner over")
        assertTrue(overlaps(sheet, square(50f, -10f, 60f, 110f)), "across it, no corner inside either")
        assertTrue(overlaps(sheet, square(10f, 10f, 20f, 20f)), "inside it")
        assertFalse(overlaps(sheet, square(210f, 0f, 300f, 100f)), "beside it")
    }

    @Test
    fun aPlaceInsideIsFoundEvenInAnL() {
        val l = listOf(Spot(0f, 0f), Spot(10f, 0f), Spot(10f, 2f), Spot(2f, 2f), Spot(2f, 10f), Spot(0f, 10f))
        val p = insidePoint(l)
        assertTrue(insideOf(l, p), "$p")
    }
}
