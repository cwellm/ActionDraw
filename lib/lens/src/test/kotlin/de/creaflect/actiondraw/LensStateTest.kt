package de.creaflect.actiondraw

import de.creaflect.actiondraw.lens.LensState
import de.creaflect.actiondraw.ui.GlassState
import de.creaflect.actiondraw.ui.Optic
import de.creaflect.actiondraw.ui.keptIn
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LensStateTest {
    @Test
    fun defractionRollsAFreshSeedOnEverySwitchOn() {
        val lens = LensState()
        val seeds = mutableSetOf<Float>()
        repeat(4) {
            lens.toggleDefraction() // on -> rolls a seed
            assertTrue(lens.defraction)
            seeds += lens.defractionSeed
            lens.toggleDefraction() // off
        }
        assertTrue(seeds.size > 1, "seed must differ between activations (got $seeds)")
    }

    @Test
    fun theGridGoesRoundAndBackToOff() {
        val lens = LensState()
        val seen = (1..GridMode.entries.size).map { lens.cycleGrid(); lens.gridMode }
        assertEquals(listOf(GridMode.THIRDS, GridMode.PHI, GridMode.DIAGONAL, GridMode.OFF), seen)
    }

    @Test
    fun steppingBackLiftsTheGlassOffThePictureThenPutsItAway() {
        val lens = LensState()
        val picture = File("pose.png")
        lens.glass.setDown(picture, 0.4f, 0.6f)
        assertTrue(lens.stepBack(picture))
        assertNull(lens.glass.focus, "first the whole picture again")
        assertTrue(lens.glass.inHand)
        assertTrue(lens.stepBack(picture))
        assertFalse(lens.glass.inHand, "then the glass put away")
        assertFalse(lens.stepBack(picture), "and then there is nothing of the lens to step back from")
    }

    @Test
    fun theGlassIsKeptAsItWasLastSettled() {
        val home = Files.createTempDirectory("lens-glass").toFile()
        try {
            val glass = GlassState().keptIn(FileLib(home).preferences)
            glass.choose(Optic.FISHEYE)
            glass.magnify(6f)
            glass.resizeBy(2f)
            glass.putAway()
            val next = GlassState().keptIn(FileLib(home).preferences)
            assertEquals(Optic.FISHEYE, next.optic, "the next run takes it up as it was")
            assertEquals(6f, next.magnification)
            assertEquals(glass.radius, next.radius)
            assertFalse(next.inHand, "but not in hand")
        } finally {
            home.deleteRecursively()
        }
    }

    @Test
    fun aCopySeesThePictureAsTheLensDoesWithItsPixelsScaled() {
        val lens = LensState().apply {
            viewMode = ViewMode.PIXELATE
            pixelateBlock = 8
            mirror = true
            toggleDefraction()
            glass.takeUp()
        }
        val copy = lens.copy(scale = 2.5f)
        assertEquals(ViewMode.PIXELATE, copy.viewMode)
        assertTrue(copy.mirror && copy.defraction)
        assertEquals(lens.defractionSeed, copy.defractionSeed, "the same shards")
        assertEquals(20, copy.pixelateBlock, "a block as large on the larger picture")
        assertEquals(240, copy.defractionBlock)
        assertFalse(copy.glass.inHand, "and the glass stays behind")
    }

    @Test
    fun theGlassDownOnAnotherPictureIsOnlyInHandOnThisOne() {
        val lens = LensState()
        lens.glass.setDown(File("last.png"), 0.4f, 0.6f)
        assertTrue(lens.stepBack(File("this.png")))
        assertFalse(lens.glass.inHand, "a new picture starts whole, so the first step puts the glass away")
    }
}
