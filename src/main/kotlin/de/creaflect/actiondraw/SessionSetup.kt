package de.creaflect.actiondraw

import java.io.File

/**
 * Session settings handed to [AppState.startBoardSession] by whoever starts it — the practice
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

/**
 * Lets a running session hand pictures to an Idea Board without knowing what a board is: the app
 * shell supplies the list of targets and the action that files them away.
 */
class PinTargets(
    val boards: () -> List<Pair<String, File>>,
    /** Copies [files] into the board at that folder; returns a line to show the user. */
    val pin: (File, List<File>) -> String,
)
