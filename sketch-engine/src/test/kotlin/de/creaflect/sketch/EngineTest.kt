package de.creaflect.sketch

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The engine, headless: samples in, points and pixels out. No window, no Compose, no pen — the
 * numbers from LEARNINGS L2 pinned as behaviour, and a stroke that leaves graphite where it went
 * and nowhere else.
 */
class EngineTest {
    private fun sample(x: Float, y: Float, pressure: Float, ms: Long) =
        InputSample(x, y, pressure, timeNanos = ms * 1_000_000L)

    // ---- The filter ----

    @Test
    fun theFilterStillsAJitteringHandAndFollowsAMovingOne() {
        val filter = OneEuroFilter()
        // A hand resting at 100 with ±3 px of jitter at 200 Hz: the output settles well inside it.
        var t = 0L
        val settled = mutableListOf<Float>()
        repeat(200) { i ->
            val jitter = if (i % 2 == 0) 3f else -3f
            settled += filter.filter(100f + jitter, t)
            t += 5_000_000L
        }
        val late = settled.takeLast(50)
        assertTrue(late.all { abs(it - 100f) < 1.5f }, "resting hand: ${late.minOrNull()}..${late.maxOrNull()}")

        // Then the hand moves fast: 20 px per sample. The filter opens up and keeps up.
        var x = 100f
        var out = 0f
        repeat(40) {
            x += 20f
            out = filter.filter(x, t)
            t += 5_000_000L
        }
        assertTrue(abs(out - x) < 25f, "moving hand: filtered $out vs actual $x")
    }

    // ---- The leads ----

    @Test
    fun widthAndDarknessFollowPressureAndTheLeadsDifferAsTheyShould() {
        for (lead in Lead.entries) {
            val model = Pencils.of(lead)
            var lastWidth = -1f
            var lastAlpha = -1f
            for (p in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
                val width = model.width(p, 10f)
                val alpha = model.alpha(p, 0f)
                assertTrue(width > lastWidth, "$lead width grows with pressure")
                assertTrue(alpha > lastAlpha, "$lead darkness grows with pressure")
                assertTrue(alpha in 0f..1f)
                lastWidth = width
                lastAlpha = alpha
            }
        }
        // A hard lead barely widens and never gets black; a soft one opens up and does.
        assertTrue(Pencils.HARD.width(1f, 10f) < Pencils.SOFT.width(1f, 10f))
        assertTrue(Pencils.HARD.alpha(1f, 0f) < 0.6f && Pencils.SOFT.alpha(1f, 0f) > 0.9f)
        // Half pressure: the hard lead is still near its thinnest, the soft one already well open.
        val hardHalf = (Pencils.HARD.width(0.5f, 10f) - Pencils.HARD.width(0f, 10f)) / (Pencils.HARD.width(1f, 10f) - Pencils.HARD.width(0f, 10f))
        val softHalf = (Pencils.SOFT.width(0.5f, 10f) - Pencils.SOFT.width(0f, 10f)) / (Pencils.SOFT.width(1f, 10f) - Pencils.SOFT.width(0f, 10f))
        assertTrue(hardHalf < 0.4f && softHalf > 0.55f, "hard $hardHalf, soft $softHalf of the way at half pressure")
    }

    @Test
    fun speedLightensSoftLeadsMuchAndHardLeadsLittle() {
        val slow = 0f
        val fast = 3000f
        val hardDrop = Pencils.HARD.alpha(1f, slow) - Pencils.HARD.alpha(1f, fast)
        val softDrop = Pencils.SOFT.alpha(1f, slow) - Pencils.SOFT.alpha(1f, fast)
        assertTrue(hardDrop > 0f && softDrop > hardDrop * 2f, "hard loses $hardDrop, soft $softDrop")
    }

    // ---- The stroke ----

    @Test
    fun aStrokeKnowsItsSpeedAndItsPressure() {
        val builder = StrokeBuilder(Brush(Lead.MEDIUM, size = 10f), smoothing = false)
        builder.add(sample(0f, 0f, 0.2f, 0))
        builder.add(sample(10f, 0f, 0.5f, 10)) // 10 px in 10 ms = 1000 px/s
        val third = builder.add(sample(20f, 0f, 1f, 20))
        val stroke = builder.build()
        assertEquals(3, stroke.points.size)
        assertTrue(third.speed in 500f..1000f, "speed smoothed towards 1000: ${third.speed}")
        assertEquals(1f, third.pressure)
        assertTrue(stroke.points[0].width < stroke.points[2].width, "pressed harder, drawn wider")
    }

    // ---- The pixels ----

    @Test
    fun aStrokeLeavesGraphiteAlongItsPathAndNowhereElse() {
        SketchSurface(200, 100).use { page ->
            val builder = StrokeBuilder(Brush(Lead.SOFT, size = 8f), smoothing = false)
            var t = 0L
            for (x in 20..180 step 4) {
                builder.add(sample(x.toFloat(), 50f, 1f, t))
                t += 5
            }
            page.draw(builder.build())

            assertTrue(page.darkness(100, 50) > 0.8f, "dark on the line: ${page.darkness(100, 50)}")
            assertTrue(page.darkness(100, 20) < 0.02f, "white well off it: ${page.darkness(100, 20)}")
            assertTrue(page.darkness(10, 50) < 0.02f, "white before it starts")
        }
    }

    @Test
    fun lightPressureLeavesALighterAndThinnerMark() {
        SketchSurface(200, 100).use { page ->
            fun line(y: Float, pressure: Float) {
                val builder = StrokeBuilder(Brush(Lead.MEDIUM, size = 12f), smoothing = false)
                var t = 0L
                for (x in 20..180 step 4) {
                    builder.add(sample(x.toFloat(), y, pressure, t))
                    t += 5
                }
                page.draw(builder.build())
            }
            line(30f, 0.2f)
            line(70f, 1f)

            assertTrue(page.darkness(100, 30) < page.darkness(100, 70), "light pressure is lighter")
            // Five pixels off the centre line: within the heavy stroke's width, outside the light one's.
            assertTrue(page.darkness(100, 75) > 0.3f, "the heavy line is wide: ${page.darkness(100, 75)}")
            assertTrue(page.darkness(100, 35) < 0.05f, "the light line is thin: ${page.darkness(100, 35)}")
        }
    }

    @Test
    fun drawingLiveSegmentBySegmentMatchesDrawingTheWholeStroke() {
        val brush = Brush(Lead.MEDIUM, size = 8f)
        fun points(): List<StrokePoint> {
            val builder = StrokeBuilder(brush, smoothing = false)
            var t = 0L
            for (x in 20..180 step 5) {
                builder.add(sample(x.toFloat(), 50f + (x % 3), 0.8f, t))
                t += 5
            }
            return builder.build().points
        }
        SketchSurface(200, 100).use { whole ->
            SketchSurface(200, 100).use { live ->
                val pts = points()
                whole.draw(Stroke(brush, pts))
                pts.zipWithNext { a, b -> live.drawSegment(brush, a, b) }
                for (x in listOf(40, 100, 160)) {
                    assertEquals(whole.darkness(x, 50), live.darkness(x, 50), 0.02f)
                }
            }
        }
    }

    @Test
    fun alphaGoesIntoTheColoursTopByte() {
        assertEquals(0xFF123456.toInt(), Rasterizer.withAlpha(0x00123456, 1f))
        assertEquals(0x00123456, Rasterizer.withAlpha(0xFF123456.toInt(), 0f))
        assertEquals(0x80, (Rasterizer.withAlpha(0x123456, 0.5f) ushr 24))
    }
}
