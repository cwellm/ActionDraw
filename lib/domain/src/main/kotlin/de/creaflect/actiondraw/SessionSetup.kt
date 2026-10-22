package de.creaflect.actiondraw

/** Mutually-exclusive ways of viewing the reference image (value / colour / structure studies). */
enum class ViewMode { NONE, GRAYSCALE, SQUINT, SEPIA, POSTERIZE, PIXELATE, EDGE, SILHOUETTE, NOTAN }

/** Proportion-overlay variants drawn over the image. */
enum class GridMode { OFF, THIRDS, PHI, DIAGONAL }

/**
 * Session settings handed to `AppState.startBoardSession` by whoever starts it — the practice
 * side's own vocabulary, so it never has to know what an Idea Board is.
 */
data class SessionSetup(
    val plan: SessionPlan?,
    val intervalSeconds: Int,
    val autoAdvance: Boolean,
    val viewMode: ViewMode,
    val gridMode: GridMode,
    /** White balance, -1 (cool) .. +1 (warm). */
    val temperature: Float = 0f,
)
