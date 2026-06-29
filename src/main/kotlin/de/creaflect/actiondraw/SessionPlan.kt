package de.creaflect.actiondraw

/** One leg of a gesture ramp: show [count] images, [seconds] each. */
data class RampStep(val seconds: Int, val count: Int)

/** A finite gesture-drawing sequence that ramps from short poses to longer ones. */
data class SessionPlan(val name: String, val steps: List<RampStep>) {
    val totalPoses: Int get() = steps.sumOf { it.count }
    val totalSeconds: Int get() = steps.sumOf { it.seconds * it.count }
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
    val ALL: List<SessionPlan> = listOf(QUICK_WARMUP, CLASSIC_GESTURE, LONG_STUDIES)
}
