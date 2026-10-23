package de.creaflect.actiondraw.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import de.creaflect.actiondraw.board.ui.ViewerZoom
import kotlin.test.Test
import kotlin.test.assertEquals

/** The viewer's zoom arithmetic: fitted size, pan limits, and zooming into the cursor. */
class ViewerZoomTest {
    private val view = Size(1000f, 800f)

    @Test
    fun aPictureIsFittedOnItsLongerSideAndScaledUpIfSmall() {
        assertEquals(Size(1000f, 500f), ViewerZoom.fitted(Size(2000f, 1000f), view), "wide: width rules")
        assertEquals(Size(400f, 800f), ViewerZoom.fitted(Size(100f, 200f), view), "tall and small: scaled up")
        assertEquals(Size.Zero, ViewerZoom.fitted(Size.Zero, view), "no picture, no size")
    }

    @Test
    fun atFittedSizeThereIsNothingToPan() {
        val fitted = Size(1000f, 500f)
        assertEquals(Offset.Zero, ViewerZoom.clampPan(Offset(300f, 300f), fitted, 1f, view))
    }

    @Test
    fun panStopsWhereThePictureWouldLeaveEmptySpace() {
        // 1000x500 at 2x = 2000x1000 over a 1000x800 view: 500 px of slack sideways, 100 up/down.
        val fitted = Size(1000f, 500f)
        assertEquals(Offset(500f, 100f), ViewerZoom.clampPan(Offset(9999f, 9999f), fitted, 2f, view))
        assertEquals(Offset(-500f, -100f), ViewerZoom.clampPan(Offset(-9999f, -9999f), fitted, 2f, view))
        assertEquals(Offset(120f, -30f), ViewerZoom.clampPan(Offset(120f, -30f), fitted, 2f, view), "inside: untouched")
    }

    @Test
    fun anAxisThatStillFitsCannotBePannedEvenWhenTheOtherCan() {
        // 1000x500 at 1.5x = 1500x750: wider than the view, still shorter than it.
        val pan = ViewerZoom.clampPan(Offset(400f, 400f), Size(1000f, 500f), 1.5f, view)
        assertEquals(Offset(250f, 0f), pan)
    }

    @Test
    fun zoomingKeepsTheSpotUnderTheCursorStill() {
        // Point p on the picture shows at cursor c: c = p*zoom + pan. Doubling the zoom about
        // c = (200, 100) with no pan means the picture point (200, 100) must still land on c.
        val cursor = Offset(200f, 100f)
        val pan = ViewerZoom.panKeepingPointStill(Offset.Zero, cursor, from = 1f, to = 2f)
        assertEquals(Offset(-200f, -100f), pan)
        // And it lands there: p*2 + pan = (400, 200) + (-200, -100) = (200, 100).
        assertEquals(cursor, Offset(200f * 2 + pan.x, 100f * 2 + pan.y))
    }

    @Test
    fun zoomingAboutTheCentreLeavesThePanAlone() {
        assertEquals(Offset(40f, -20f), ViewerZoom.panKeepingPointStill(Offset(40f, -20f), Offset.Zero, 2f, 2f))
        assertEquals(Offset.Zero, ViewerZoom.panKeepingPointStill(Offset.Zero, Offset.Zero, 1f, 3f))
    }
}
