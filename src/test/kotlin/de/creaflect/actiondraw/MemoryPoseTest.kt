package de.creaflect.actiondraw

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Memory drawing: study the reference, lose it, draw, get it back to compare. The beats follow
 * from the pose's own clock, so these tests drive the clock and watch what the screen would show.
 */
class MemoryPoseTest {
    private val dir: File = Files.createTempDirectory("memory-pics").toFile()
    private val config: File = Files.createTempDirectory("memory-cfg").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
        config.deleteRecursively()
    }

    /** A memory leg (10 s, 4 s of study) followed by an ordinary one, over three pictures. */
    private fun session(plan: SessionPlan): AppState {
        listOf("a.jpg", "b.jpg", "c.jpg").forEach { File(dir, it).createNewFile() }
        val state = AppState(Settings(config))
        state.selectFolder(dir)
        state.rampPlan = plan
        state.start()
        return state
    }

    private val mixed = SessionPlan(
        "Mixed",
        listOf(RampStep(10, 2, studySeconds = 4), RampStep(6, 1)),
    )

    private fun AppState.runFor(seconds: Int) = repeat(seconds) { tick() }

    // ---- The three beats ----

    @Test
    fun theReferenceIsThereToStudyThenGoesAway() {
        val state = session(mixed)
        assertTrue(state.isMemoryPose)
        assertFalse(state.referenceHidden, "the pose opens with the reference up")
        assertEquals(4, state.studyRemainingSeconds)

        state.runFor(3)
        assertFalse(state.referenceHidden, "still one second of study left")
        assertEquals(1, state.studyRemainingSeconds)

        state.runFor(1)
        assertTrue(state.referenceHidden, "study time is up")
        assertEquals(0, state.studyRemainingSeconds)
    }

    @Test
    fun theReferenceComesBackAtTheEndToCompareAgainst() {
        val state = session(mixed)
        state.runFor(9)
        assertTrue(state.referenceHidden, "still drawing")
        assertFalse(state.comparing)

        state.runFor(1) // the pose's 10 seconds are up
        assertFalse(state.referenceHidden, "it is back")
        assertTrue(state.comparing, "and this is the beat for looking at what you made")
    }

    @Test
    fun aMemoryPoseWaitsForYouEvenOnAutoAdvance() {
        val state = session(mixed)
        state.autoAdvance = true

        state.runFor(30) // three times the pose's length

        assertEquals(0, state.rampPose, "it has not moved on by itself")
        assertTrue(state.comparing, "it is still offering the comparison")

        state.next()
        assertEquals(1, state.rampPose)
    }

    @Test
    fun anOrdinaryPoseIsNeverHiddenAndStillAdvancesOnItsOwn() {
        val state = session(SessionPlan("Plain", listOf(RampStep(5, 3))))
        state.autoAdvance = true

        state.runFor(4)
        assertFalse(state.isMemoryPose)
        assertFalse(state.referenceHidden)
        assertFalse(state.comparing)

        state.runFor(1)
        assertEquals(1, state.rampPose, "5 seconds, and on to the next")
    }

    // ---- H ----

    @Test
    fun hPeeksWhileDrawingFromMemory() {
        val state = session(mixed)
        state.runFor(5)
        assertTrue(state.referenceHidden)

        state.toggleReference()
        assertFalse(state.referenceHidden, "a peek")

        state.toggleReference()
        assertTrue(state.referenceHidden, "and back under cover")
    }

    @Test
    fun hCoversAnOrdinaryReferenceSoAnyPictureCanBeDrawnFromMemory() {
        val state = session(SessionPlan("Plain", listOf(RampStep(60, 2))))
        assertFalse(state.referenceHidden)

        state.toggleReference()

        assertTrue(state.referenceHidden, "H works outside a memory ramp too")
    }

    @Test
    fun aPeekDoesNotFollowYouToTheNextPose() {
        val state = session(mixed)
        state.runFor(5)
        state.toggleReference()
        assertFalse(state.referenceHidden)

        state.next()

        assertFalse(state.referenceFlipped, "each pose starts as the pose intends")
        assertFalse(state.referenceHidden, "which here means studying again")
        state.runFor(5)
        assertTrue(state.referenceHidden, "and hiding again on its own schedule")
    }

    @Test
    fun steppingBackAlsoClearsTheFlip() {
        val state = session(mixed)
        state.toggleReference()
        state.previous()
        assertFalse(state.referenceFlipped)
    }

    // ---- Which leg a pose belongs to ----

    @Test
    fun onlyTheLegThatAsksForItHidesAnything() {
        val state = session(mixed)
        state.next()
        state.next() // past both poses of the memory leg, into the plain one
        assertEquals(2, state.rampPose)
        assertFalse(state.isMemoryPose, "the second leg is an ordinary 6-second pose")
        assertEquals(6, state.currentIntervalSeconds)

        state.runFor(6)
        assertFalse(state.referenceHidden)
        assertFalse(state.comparing)
    }

    // ---- The plan itself ----

    @Test
    fun aStepMustLeaveSomethingToDrawIn() {
        assertFailsWith<IllegalArgumentException> { RampStep(30, 1, studySeconds = 30) }
        assertFailsWith<IllegalArgumentException> { RampStep(30, 1, studySeconds = 45) }
    }

    @Test
    fun theBuiltInPlanIsOfferedAndKnowsWhatItIs() {
        assertTrue(SessionPlans.FROM_MEMORY in SessionPlans.ALL, "so a board recipe can name it")
        assertTrue(SessionPlans.FROM_MEMORY.hasMemorySteps)
        assertFalse(SessionPlans.CLASSIC_GESTURE.hasMemorySteps)
        assertTrue(
            SessionPlans.FROM_MEMORY.steps.all { it.studySeconds!! * 2 < it.seconds },
            "more time drawing than looking, on every leg",
        )
    }
}
