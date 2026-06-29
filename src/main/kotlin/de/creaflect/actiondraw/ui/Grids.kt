package de.creaflect.actiondraw.ui

import de.creaflect.actiondraw.GridMode

/** A line in normalized [0,1] image space (origin top-left), for the proportion overlay. */
data class GridLine(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

private fun vertical(x: Float) = GridLine(x, 0f, x, 1f)
private fun horizontal(y: Float) = GridLine(0f, y, 1f, y)

/** Golden-section positions: 1 − 1/φ and 1/φ. */
const val PHI_LOW = 0.381966f
const val PHI_HIGH = 0.618034f

/**
 * Structural lines for a proportion overlay, in normalized image space. The shared centre cross and
 * outer border are drawn separately for every non-[GridMode.OFF] mode. Pure, so it is unit-testable.
 */
fun gridLines(mode: GridMode): List<GridLine> = when (mode) {
    GridMode.OFF -> emptyList()
    GridMode.THIRDS -> listOf(vertical(1f / 3f), vertical(2f / 3f), horizontal(1f / 3f), horizontal(2f / 3f))
    GridMode.PHI -> listOf(vertical(PHI_LOW), vertical(PHI_HIGH), horizontal(PHI_LOW), horizontal(PHI_HIGH))
    GridMode.DIAGONAL -> listOf(GridLine(0f, 0f, 1f, 1f), GridLine(1f, 0f, 0f, 1f))
}
