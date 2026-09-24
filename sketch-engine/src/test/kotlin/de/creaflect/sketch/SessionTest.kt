package de.creaflect.sketch

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The stamped pencil, the eraser, undo and redo, and the document: a sketch that renders the
 * same whether drawn, undone and redone, or loaded again from its JSON.
 */
class SessionTest {
    private fun sample(x: Float, y: Float, pressure: Float, ms: Long) =
        InputSample(x, y, pressure, timeNanos = ms * 1_000_000L)

    private fun SketchSession.line(y: Float, pressure: Float, brush: Brush = Brush(Lead.MEDIUM, size = 12f), eraser: Boolean = false, from: Int = 20, to: Int = 180) {
        begin(brush, eraser)
        var t = 0L
        for (x in from..to step 4) {
            add(sample(x.toFloat(), y, pressure, t))
            t += 5
        }
        end()
    }

    // ---- Stamps ----

    @Test
    fun aStampedStrokeLeavesGraphiteWhereItWentWithPaperToothInIt() {
        SketchSession(200, 100).use { s ->
            s.line(50f, 1f, Brush(Lead.SOFT, size = 12f))
            val onLine = (40..160 step 8).map { s.surface.darkness(it, 50) }
            assertTrue(onLine.all { it > 0.5f }, "dark along the line: ${onLine.map { "%.2f".format(it) }}")
            assertTrue(onLine.max() - onLine.min() > 0.02f, "and not perfectly even — the paper's tooth shows: $onLine")
            assertTrue(s.surface.darkness(100, 20) < 0.02f, "bare paper off the line")
            assertEquals(1, s.strokeCount)
        }
    }

    @Test
    fun lightPressureIsLighterAndThinnerAndAHardLeadNeverGoesBlack() {
        SketchSession(200, 120).use { s ->
            s.line(30f, 0.25f)
            s.line(70f, 1f)
            assertTrue(s.surface.darkness(100, 30) < s.surface.darkness(100, 70) - 0.2f, "light: ${s.surface.darkness(100, 30)} heavy: ${s.surface.darkness(100, 70)}")
            assertTrue(s.surface.darkness(100, 74) > 0.25f, "the heavy line is wide: ${s.surface.darkness(100, 74)}")
            assertTrue(s.surface.darkness(100, 35) < 0.08f, "the light line is thin: ${s.surface.darkness(100, 35)}")
            s.line(100f, 1f, Brush(Lead.HARD, size = 12f))
            assertTrue(s.surface.darkness(100, 100) < 0.75f, "a hard lead pressed hard stays grey: ${s.surface.darkness(100, 100)}")
        }
    }

    @Test
    fun theEraserTakesGraphiteAwayAgain() {
        SketchSession(200, 100).use { s ->
            s.line(50f, 1f, Brush(Lead.SOFT, size = 12f))
            val before = s.surface.darkness(100, 50)
            s.line(50f, 1f, Brush(Lead.MEDIUM, size = 24f), eraser = true, from = 60, to = 140)
            assertTrue(s.surface.darkness(100, 50) < before * 0.25f, "erased: $before -> ${s.surface.darkness(100, 50)}")
            assertTrue(s.surface.darkness(30, 50) > 0.5f, "the rest of the line stays: ${s.surface.darkness(30, 50)}")
        }
    }

    @Test
    fun aSoftEraserLiftsPartOfTheGraphiteAHardOneAllOfIt() {
        fun erased(strength: Float): Pair<Float, Float> = SketchSession(200, 100).use { s ->
            s.line(50f, 1f, Brush(Lead.SOFT, size = 12f))
            val before = s.surface.darkness(100, 50)
            s.begin(Brush(Lead.MEDIUM, size = 24f), eraser = true, eraserStrength = strength)
            var t = 0L
            for (x in 60..140 step 4) {
                s.add(sample(x.toFloat(), 50f, 1f, t))
                t += 5
            }
            s.end()
            before to s.surface.darkness(100, 50)
        }
        val (b1, soft) = erased(0.45f)
        val (b2, hard) = erased(1f)
        assertTrue(soft > b1 * 0.3f && soft < b1 * 0.8f, "a soft pass leaves part of it: $b1 -> $soft")
        assertTrue(hard < b2 * 0.15f, "a hard pass takes it all: $b2 -> $hard")
    }

    @Test
    fun theEraserStrengthIsInTheDocumentAndReplaysWithIt() {
        SketchSession(200, 100).use { s ->
            s.line(50f, 1f, Brush(Lead.SOFT, size = 12f))
            s.begin(Brush(Lead.MEDIUM, size = 24f), eraser = true, eraserStrength = 0.45f)
            var t = 0L
            for (x in 60..140 step 4) {
                s.add(sample(x.toFloat(), 50f, 1f, t))
                t += 5
            }
            s.end()
            val json = s.document().toJson()
            assertTrue(json.contains("\"eraserStrength\":0.45"), json.take(300))
            SketchSession.fromDocument(SketchDocument.fromJson(json)).use { again ->
                assertEquals(fingerprint(s), fingerprint(again), "the soft pass replays as a soft pass")
            }
        }
    }

    @Test
    fun thePaperToothIsFineNotBlotchy() {
        val grain = PaperGrain.default.image
        val bitmap = org.jetbrains.skia.Bitmap()
        bitmap.allocPixels(org.jetbrains.skia.ImageInfo.makeN32Premul(grain.width, grain.height))
        assertTrue(grain.readPixels(bitmap))
        // The grain is white at alpha g and getColor unpremultiplies, so the tooth is in the alpha.
        fun v(x: Int, y: Int) = ((bitmap.getColor(x, y) ushr 24) and 0xFF) / 255f
        var neighbours = 0f
        var n = 0
        val values = ArrayList<Float>()
        for (y in 0 until grain.height step 3) for (x in 0 until grain.width - 1 step 3) {
            neighbours += abs(v(x + 1, y) - v(x, y))
            values += v(x, y)
            n++
        }
        bitmap.close()
        val meanStep = neighbours / n
        val mean = values.average().toFloat()
        val spread = kotlin.math.sqrt(values.map { (it - mean) * (it - mean) }.average()).toFloat()
        assertTrue(meanStep > 0.04f, "pixel-to-pixel tooth, not smooth blobs: $meanStep")
        assertTrue(spread > 0.08f, "and real contrast across the paper: $spread")
    }

    @Test
    fun onScreenThePaperIsAFaintUnevenShadeOverWhiteAndNothingOutsideThePage() {
        val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(300, 200)
        surface.canvas.clear(0xFFFFFFFF.toInt())
        PaperGrain.default.shade(surface.canvas, 10f, 10f, 290f, 190f, scale = 1f)
        val bitmap = org.jetbrains.skia.Bitmap()
        bitmap.allocPixels(org.jetbrains.skia.ImageInfo.makeN32Premul(300, 200))
        assertTrue(surface.readPixels(bitmap, 0, 0))
        fun dark(x: Int, y: Int) = 1f - (bitmap.getColor(x, y) and 0xFF) / 255f
        val inside = (20 until 280 step 5).flatMap { x -> (20 until 180 step 5).map { y -> dark(x, y) } }
        val mean = inside.average().toFloat()
        assertTrue(mean > 0.02f && mean < 0.12f, "a faint shade, not grey paper: $mean")
        assertTrue(inside.max() - inside.min() > 0.03f, "and uneven, the tooth: ${inside.min()}..${inside.max()}")
        assertEquals(0f, dark(5, 5), "white outside the page")
        bitmap.close()
        surface.close()
    }

    // ---- Undo / redo ----

    @Test
    fun undoPutsThePageBackExactlyAndRedoDrawsTheStrokeAgain() {
        SketchSession(200, 100).use { s ->
            s.line(30f, 1f)
            val afterFirst = fingerprint(s)
            s.line(70f, 1f)
            assertTrue(s.surface.darkness(100, 70) > 0.5f)

            assertTrue(s.undo())
            assertEquals(afterFirst, fingerprint(s), "the page is as it was before the second stroke")
            assertTrue(s.surface.darkness(100, 70) < 0.02f)
            assertTrue(s.canRedo)

            assertTrue(s.redo())
            assertTrue(s.surface.darkness(100, 70) > 0.5f, "back again")
            assertFalse(s.canRedo)

            assertTrue(s.undo() && s.undo())
            assertFalse(s.undo(), "nothing left to undo")
            assertTrue(s.surface.darkness(100, 30) < 0.02f, "an empty page")
        }
    }

    @Test
    fun undoAcrossSnapshotsStillRestoresExactly() {
        SketchSession(200, 400).use { s ->
            // More strokes than a snapshot interval, then undo back past the last snapshot.
            val prints = ArrayList<List<Float>>()
            for (i in 0 until SketchSession.SNAPSHOT_EVERY + 3) {
                s.line(10f + i * 12f, 0.8f, Brush(Lead.MEDIUM, size = 8f))
                prints += fingerprint(s)
            }
            for (i in prints.indices.reversed().drop(1)) {
                assertTrue(s.undo())
                assertEquals(prints[i], fingerprint(s), "after undoing to ${i + 1} strokes")
            }
        }
    }

    // ---- The document ----

    @Test
    fun aDocumentReplaysToTheSamePixelsThroughJson() {
        SketchSession(200, 100).use { s ->
            s.line(30f, 0.6f, Brush(Lead.HARD, color = 0xFF803020.toInt(), size = 10f))
            s.line(60f, 1f, Brush(Lead.SOFT, size = 14f))
            s.line(60f, 1f, Brush(Lead.MEDIUM, size = 20f), eraser = true, from = 90, to = 110)
            val json = s.document().toJson()
            assertTrue(json.contains("\"lead\":\"HARD\"") && json.contains("\"eraser\":true"), json.take(200))

            val loaded = SketchDocument.fromJson(json)
            assertEquals(3, loaded.strokes.size)
            SketchSession.fromDocument(loaded).use { again ->
                assertEquals(fingerprint(s), fingerprint(again), "loaded and drawn are the same picture")
                assertTrue(again.canUndo)
                assertFalse(again.dirty)
            }
        }
    }

    @Test
    fun theExportedPngIsARealPictureOfPaperAndStrokes() {
        SketchSession(120, 80, paper = 0xFFFFF8E0.toInt()).use { s ->
            s.line(40f, 1f, from = 10, to = 110)
            val png = s.exportPng()
            assertTrue(png.size > 100 && png[1] == 'P'.code.toByte() && png[2] == 'N'.code.toByte() && png[3] == 'G'.code.toByte(), "a PNG")
            org.jetbrains.skia.Image.makeFromEncoded(png).use { image ->
                assertEquals(120, image.width)
                assertEquals(80, image.height)
            }
        }
    }

    @Test
    fun endingAnEmptyStrokeKeepsNothingAndDirtyFollowsSaving() {
        SketchSession(50, 50).use { s ->
            s.begin(Brush())
            assertFalse(s.end())
            assertEquals(0, s.strokeCount)
            assertFalse(s.dirty)
            s.line(25f, 1f, from = 5, to = 45)
            assertTrue(s.dirty)
            s.markSaved()
            assertFalse(s.dirty)
            s.undo()
            assertTrue(s.dirty, "undo is a change too")
        }
    }

    // ---- Tiles ----

    @Test
    fun aStrokeAcrossTileBordersIsSeamless() {
        val tile = SketchSurface.TILE
        SketchSession(tile * 2 + 90, tile + 90).use { s ->
            // Straight across the first column border, then down across the first row border.
            s.line(tile / 2f, 1f, Brush(Lead.SOFT, size = 12f), from = 40, to = tile * 2 + 40)
            s.begin(Brush(Lead.SOFT, size = 12f))
            var t = 0L
            for (y in 40..(tile + 60) step 4) {
                s.add(sample(tile + 100f, y.toFloat(), 1f, t))
                t += 5
            }
            s.end()
            val across = (tile - 3..tile + 3).map { s.surface.darkness(it, tile / 2) }
            val down = (tile - 3..tile + 3).map { s.surface.darkness(tile + 100, it) }
            assertTrue(across.all { it > 0.5f }, "no gap at the column border: $across")
            assertTrue(down.all { it > 0.5f }, "no gap at the row border: $down")
            assertTrue(across.max() - across.min() < 0.25f, "and no seam either: $across")
            assertTrue(s.surface.tileCount == 6, "three columns, two rows: ${s.surface.tileCount}")
        }
    }

    @Test
    fun undoRestoresTheRaggedEdgeTilesToo() {
        val tile = SketchSurface.TILE
        // A page whose last column and row are narrow tiles.
        SketchSession(tile + 44, tile + 30).use { s ->
            for (i in 0 until SketchSession.SNAPSHOT_EVERY) s.line(10f + i * 20f, 0.8f, from = 10, to = tile + 40)
            val atSnapshot = fingerprint(s)
            s.line(tile + 20f, 1f, Brush(Lead.SOFT, size = 16f), from = tile - 40, to = tile + 40)
            assertTrue(s.surface.darkness(tile + 30, tile + 20) > 0.3f, "drawn into the corner tile")
            assertTrue(s.undo())
            assertEquals(atSnapshot, fingerprint(s), "the corner tile is back as it was")
        }
    }

    @Test
    fun aTilesPictureStaysTheSameObjectUntilTheTileIsDrawnOn() {
        SketchSession(SketchSurface.TILE * 2, SketchSurface.TILE).use { s ->
            val left = s.surface.tileImage(0)
            val right = s.surface.tileImage(1)
            s.line(40f, 1f, from = 20, to = 120) // only in the left tile
            assertTrue(s.surface.tileImage(1) === right, "an untouched tile keeps its picture: nothing to upload again")
            assertTrue(s.surface.tileImage(0) !== left, "a touched one gets a new one")
        }
    }

    @Test
    fun cancellingAStrokeTakesItsMarksOffThePage() {
        SketchSession(200, 100).use { s ->
            s.line(30f, 1f)
            val before = fingerprint(s)
            s.begin(Brush(Lead.SOFT, size = 14f))
            var t = 0L
            for (x in 20..180 step 4) {
                s.add(sample(x.toFloat(), 70f, 1f, t))
                t += 5
            }
            assertTrue(s.surface.darkness(100, 70) > 0.5f, "on the page while it is drawn")
            s.cancel()
            assertEquals(before, fingerprint(s), "and gone again")
            assertEquals(1, s.strokeCount, "kept nowhere")
            assertFalse(s.isDrawing)
        }
    }

    // ---- Resampling ----

    @Test
    fun dabsAreEvenlySpacedWhateverTheSampleSpacing() {
        fun dabsFor(step: Int): List<StrokePoint> {
            val brush = Brush(Lead.MEDIUM, size = 9f)
            val builder = StrokeBuilder(brush, smoothing = false)
            val resampler = Resampler { (it.width / 3f).coerceAtLeast(0.5f) }
            val out = ArrayList<StrokePoint>()
            var t = 0L
            for (x in 0..300 step step) {
                resampler.add(builder.add(sample(x.toFloat(), 50f, 1f, t))) { out += it }
                t += 5
            }
            return out
        }
        val fine = dabsFor(2)
        val coarse = dabsFor(30)
        val gaps = fine.zipWithNext { a, b -> hypot(b.x - a.x, b.y - a.y) }
        assertTrue(gaps.all { abs(it - 3f) < 0.8f }, "even gaps of a third of the width: ${gaps.take(8)}")
        assertTrue(abs(fine.size - coarse.size) <= 2, "the same number of dabs from 151 samples as from 11: ${fine.size} vs ${coarse.size}")
    }

    /** Darkness at a grid of points — enough to tell two renderings apart, cheap to compare. */
    private fun fingerprint(s: SketchSession): List<Float> {
        val out = ArrayList<Float>()
        for (y in 5 until s.height step 10) for (x in 5 until s.width step 10) out += s.surface.darkness(x, y)
        return out
    }
}
