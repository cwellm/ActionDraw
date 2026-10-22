package de.creaflect.actiondraw.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** Chrome that steps back while the work goes on (F9.4), and comes back as the pointer nears it. */
class RecedeTest {
    @Test
    fun chromeIsThereUntilItRecedesAndComesBackAsThePointerNears() {
        assertEquals(1f, chromeAlpha(receded = false, near = false, over = false))
        assertEquals(0f, chromeAlpha(receded = true, near = false, over = false))
        assertEquals(Motion.APPROACH_ALPHA, chromeAlpha(receded = true, near = true, over = false))
        assertEquals(1f, chromeAlpha(receded = true, near = true, over = true))
    }
}
