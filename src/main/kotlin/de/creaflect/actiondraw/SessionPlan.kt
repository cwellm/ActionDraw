package de.creaflect.actiondraw

/**
 * One leg of a gesture ramp: show [count] images, [seconds] each.
 *
 * [studySeconds] turns the leg into **memory drawing**: the reference is visible for that long,
 * then goes away for the rest of the pose and you draw from what you retained. Null — the
 * default — is an ordinary pose that stays visible throughout.
 */
data class RampStep(val seconds: Int, val count: Int, val studySeconds: Int? = null) {
    init {
        require(studySeconds == null || studySeconds < seconds) {
            "study time must leave something to draw in: $studySeconds of $seconds"
        }
    }

    /** True when this leg hides the reference part-way through. */
    val fromMemory: Boolean get() = studySeconds != null
}

/** A finite gesture-drawing sequence that ramps from short poses to longer ones. */
data class SessionPlan(val name: String, val steps: List<RampStep>) {
    val totalPoses: Int get() = steps.sumOf { it.count }
    val totalSeconds: Int get() = steps.sumOf { it.seconds * it.count }

    /** True when any leg draws from memory — the menu says so before you start. */
    val hasMemorySteps: Boolean get() = steps.any { it.fromMemory }
}

/** Built-in ramp presets, from a quick warm-up to long studies. */
object SessionPlans {
    val QUICK_WARMUP = SessionPlan(
        "Quick warm-up",
        listOf(RampStep(30, 8), RampStep(60, 4), RampStep(120, 2)),
    )
    val CLASSIC_GESTURE = SessionPlan(
        "Classic gesture",
        listOf(RampStep(60, 10), RampStep(120, 5), RampStep(300, 2), RampStep(600, 1)),
    )
    val LONG_STUDIES = SessionPlan(
        "Long studies",
        listOf(RampStep(300, 3), RampStep(600, 2), RampStep(1200, 1)),
    )
    /**
     * Study, then draw without it. A third of each pose to look, two thirds to work from memory —
     * the exercise that matters when the thing you are drawing does not exist to be photographed.
     */
    val FROM_MEMORY = SessionPlan(
        "From memory",
        listOf(
            RampStep(60, 4, studySeconds = 20),
            RampStep(120, 3, studySeconds = 40),
            RampStep(240, 2, studySeconds = 80),
        ),
    )
    val ALL: List<SessionPlan> = listOf(QUICK_WARMUP, CLASSIC_GESTURE, LONG_STUDIES, FROM_MEMORY)
}
