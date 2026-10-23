package de.creaflect.actiondraw

import java.io.File

/**
 * Lets a running session hand pictures to an Idea Board without knowing what a board is: the app
 * shell supplies the list of targets and the action that files them away.
 */
class PinTargets(
    val boards: () -> List<Pair<String, File>>,
    /** Copies [files] into the board at that folder; returns a line to show the user. */
    val pin: (File, List<File>) -> String,
)
