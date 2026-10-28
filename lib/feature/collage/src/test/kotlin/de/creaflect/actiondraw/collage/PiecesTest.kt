package de.creaflect.actiondraw.collage

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Where a piece is: its picture laid on the mat, turned, scaled and flipped about its middle — and what a click there hits. */
class PiecesTest {
    private fun piece(turn: Float = 0f, scale: Float = 1f, flipped: Boolean = false) = Piece(
        id = "p",
        name = "p",
        source = File("p.png"),
        outline = listOf(Spot(0f, 0f), Spot(200f, 0f), Spot(200f, 100f), Spot(0f, 100f)),
        x = 500f,
        y = 400f,
        turn = turn,
        scale = scale,
        flipped = flipped,
    )

    @Test
    fun aPieceLiesWithItsMiddleAtItsPlace() {
        val p = piece()
        assertEquals(Spot(100f, 50f), p.middle())
        assertEquals(Spot(500f, 400f), p.toMat(p.middle()))
        assertEquals(Spot(400f, 350f), p.toMat(Spot(0f, 0f)), "its top left corner half its size up and left of its place")
    }

    @Test
    fun turnedClockwiseScaledAndFlippedAboutItsMiddle() {
        val turned = piece(turn = 90f)
        // A quarter turn clockwise on the mat, y down: the picture's right end points down.
        val end = turned.toMat(Spot(200f, 50f))
        assertEquals(500f, end.x, 0.01f)
        assertEquals(500f, end.y, 0.01f)
        val big = piece(scale = 2f)
        assertEquals(Spot(300f, 300f), big.toMat(Spot(0f, 0f)))
        val flipped = piece(flipped = true)
        val left = flipped.toMat(Spot(0f, 50f))
        assertEquals(600f, left.x, 0.01f, "flipped, the picture's left end is on the right")
    }

    @Test
    fun aPlaceOnTheMatGoesBackToTheSamePlaceOnThePicture() {
        for (p in listOf(piece(turn = 33f, scale = 1.7f), piece(turn = 250f, scale = 0.4f, flipped = true))) {
            for (spot in listOf(Spot(0f, 0f), Spot(123f, 77f), Spot(200f, 100f))) {
                val back = p.toPicture(p.toMat(spot))
                assertEquals(spot.x, back.x, 0.01f)
                assertEquals(spot.y, back.y, 0.01f)
            }
        }
    }

    @Test
    fun aClickHitsThePieceInsideItsOutlineAsItLiesNotItsBox() {
        // A triangle cut from the picture: half of its box is not the piece.
        val triangle = piece(turn = 30f).copy(outline = listOf(Spot(0f, 0f), Spot(200f, 0f), Spot(0f, 100f)))
        val inside = triangle.toMat(Spot(30f, 20f))
        val outsideTheCut = triangle.toMat(Spot(180f, 90f))
        assertTrue(inside in triangle)
        assertFalse(outsideTheCut in triangle, "in its box but beyond its cut edge")
        assertFalse(Spot(500f, 400f + 200f) in triangle, "nowhere near it")
    }

    @Test
    fun aPolygonCountsWhatIsInsideIt() {
        val l = listOf(Spot(0f, 0f), Spot(10f, 0f), Spot(10f, 4f), Spot(4f, 4f), Spot(4f, 10f), Spot(0f, 10f))
        assertTrue(insideOf(l, Spot(2f, 8f)))
        assertTrue(insideOf(l, Spot(8f, 2f)))
        assertFalse(insideOf(l, Spot(8f, 8f)), "the notch of the L")
        assertFalse(insideOf(l, Spot(-2f, 8f)), "left of it: two of its edges crossed on the way out, so outside")
    }
}
