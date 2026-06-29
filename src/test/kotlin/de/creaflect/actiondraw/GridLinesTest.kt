package de.creaflect.actiondraw

import de.creaflect.actiondraw.ui.GridLine
import de.creaflect.actiondraw.ui.PHI_HIGH
import de.creaflect.actiondraw.ui.PHI_LOW
import de.creaflect.actiondraw.ui.gridLines
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GridLinesTest {
    @Test
    fun offHasNoLines() {
        assertTrue(gridLines(GridMode.OFF).isEmpty())
    }

    @Test
    fun thirdsHasFourLinesAtThirds() {
        val lines = gridLines(GridMode.THIRDS)
        assertEquals(4, lines.size)
        assertTrue(lines.contains(GridLine(1f / 3f, 0f, 1f / 3f, 1f)))   // vertical at 1/3
        assertTrue(lines.contains(GridLine(0f, 2f / 3f, 1f, 2f / 3f)))   // horizontal at 2/3
    }

    @Test
    fun phiUsesGoldenSectionPositions() {
        val lines = gridLines(GridMode.PHI)
        assertEquals(4, lines.size)
        assertTrue(lines.contains(GridLine(PHI_LOW, 0f, PHI_LOW, 1f)))
        assertTrue(lines.contains(GridLine(0f, PHI_HIGH, 1f, PHI_HIGH)))
        assertTrue(abs((PHI_LOW + PHI_HIGH) - 1f) < 1e-4f)               // φ symmetry
    }

    @Test
    fun diagonalHasTwoCornerLines() {
        assertEquals(
            listOf(GridLine(0f, 0f, 1f, 1f), GridLine(1f, 0f, 0f, 1f)),
            gridLines(GridMode.DIAGONAL),
        )
    }
}
