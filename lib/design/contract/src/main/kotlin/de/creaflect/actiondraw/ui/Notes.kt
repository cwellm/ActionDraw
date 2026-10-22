package de.creaflect.actiondraw.ui

import androidx.compose.ui.graphics.Color

/**
 * A note's paper, the same in every room that shows one: a post-it's yellow slip, the colour a
 * note was given, and the ink every note is written in.
 */
object Notes {
    /** A post-it's yellow slip. */
    val backing = Color(0xFFFFF3B8)

    /** The ink every note is written in. */
    val ink = Color(0xFF3A3315)

    /** `#rrggbb` as a colour; null for anything else. */
    fun parseColor(hex: String?): Color? =
        hex?.removePrefix("#")?.takeIf { it.length == 6 }?.toLongOrNull(16)
            ?.let { Color(0xFF000000 or it) }
}
