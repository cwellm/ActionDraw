package de.creaflect.actiondraw

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionPlanTest {
    @Test
    fun totalsAreSummedAcrossSteps() {
        val plan = SessionPlan("t", listOf(RampStep(30, 2), RampStep(60, 3)))
        assertEquals(5, plan.totalPoses)
        assertEquals(30 * 2 + 60 * 3, plan.totalSeconds)
    }

    @Test
    fun presetsAreWellFormed() {
        assertTrue(SessionPlans.ALL.isNotEmpty())
        assertTrue(SessionPlans.ALL.all { it.steps.isNotEmpty() && it.totalPoses > 0 })
    }
}
