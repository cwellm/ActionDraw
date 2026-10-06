package de.creaflect.actiondraw.ui

import de.creaflect.actiondraw.AppState
import de.creaflect.actiondraw.SessionPlan
import de.creaflect.actiondraw.SessionPlans
import de.creaflect.actiondraw.ViewMode
import kotlin.math.ln

/**
 * The Practice room's exercises (CONCEPT.md, *The rooms*). Three exist: the ones M9 adds are on
 * the table already, lettered *coming*, like the palette's dry wells.
 */
enum class Exercise(val title: String, val line: String, val verb: String, val available: Boolean) {
    ACTION("Action drawing", "Fast gestures against the clock. The line of action first; the rest follows it.", "line of action first", true),
    LENS("Lens studies", "Draw through a lens: notan for the masses, edge for the contour, upside down for shapes.", "see it differently", true),
    MEMORY("From memory", "Study the picture, then it hides. It comes back at the end, so you can compare.", "look, then draw", true),
    FLICKER("Flicker", "Two pictures trade places every second. Draw what they share.", "draw what stays", false),
    STAGED("Staged", "One pose in three stages: notan for the masses, edge for the contour, then the full picture.", "masses, contour, picture", false),
    COMPARE("Compare", "Bring your drawing back in and lay it over the reference.", "lay it over", false),
}

/**
 * Which exercise the settings amount to — the exercise is not stored, it is what the session
 * will be: a plan that hides the picture is memory work, a lens to start with is a lens study,
 * anything else is action drawing.
 */
fun exerciseOf(plan: SessionPlan?, viewMode: ViewMode): Exercise = when {
    plan?.hasMemorySteps == true -> Exercise.MEMORY
    viewMode != ViewMode.NONE -> Exercise.LENS
    else -> Exercise.ACTION
}

val AppState.exercise: Exercise get() = exerciseOf(rampPlan, viewMode)

/** Choosing an exercise sets what makes the session that exercise, and leaves the rest alone. */
fun AppState.chooseExercise(exercise: Exercise) {
    if (!exercise.available) return
    when (exercise) {
        Exercise.ACTION -> {
            viewMode = ViewMode.NONE
            if (rampPlan?.hasMemorySteps == true) rampPlan = SessionPlans.CLASSIC_GESTURE
        }
        Exercise.LENS -> {
            if (viewMode == ViewMode.NONE) viewMode = ViewMode.NOTAN
            if (rampPlan?.hasMemorySteps == true) rampPlan = null
        }
        Exercise.MEMORY -> rampPlan = SessionPlans.FROM_MEMORY
        else -> Unit
    }
}

/** One bar per pose of [plan], each as tall as its pose is long on a log scale: 0 (the shortest) to 1. */
fun rampBars(plan: SessionPlan): List<Float> {
    val poses = plan.steps.flatMap { step -> List(step.count) { step.seconds } }
    if (poses.isEmpty()) return emptyList()
    val lo = poses.min().toDouble()
    val hi = poses.max().toDouble()
    if (hi <= lo) return poses.map { 1f }
    return poses.map { (ln(it / lo) / ln(hi / lo)).toFloat() }
}

/** How far round the ensō is drawn: the share of the pose gone by, full once the time is up. */
fun ensoSweep(elapsedSeconds: Int, intervalSeconds: Int): Float =
    if (intervalSeconds <= 0) 0f else (elapsedSeconds.toFloat() / intervalSeconds).coerceIn(0f, 1f)

/** The lenses in the order of their keys, 1 to 9, with the names on their slides. */
val LENSES: List<Pair<ViewMode, String>> = listOf(
    ViewMode.NONE to "None",
    ViewMode.GRAYSCALE to "B&W",
    ViewMode.SQUINT to "Squint",
    ViewMode.SEPIA to "Sepia",
    ViewMode.POSTERIZE to "Posterize",
    ViewMode.PIXELATE to "Pixelate",
    ViewMode.EDGE to "Edge",
    ViewMode.SILHOUETTE to "Silhouette",
    ViewMode.NOTAN to "Notan",
)
