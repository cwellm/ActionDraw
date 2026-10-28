package de.creaflect.actiondraw.lens.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The first of [variants] that fits the width it is given, less [reserve] — measured, not guessed:
 * each is laid out at its own width until one fits, and the last is taken, squeezed, when none
 * does. What lets the header fold what it shows into menus as the window narrows, whatever the
 * labels' widths are in the font of the day. The ones only measured are kept out of the semantics,
 * so what is found on screen is what is shown.
 */
@Composable
internal fun FirstThatFits(variants: List<@Composable () -> Unit>, modifier: Modifier = Modifier, reserve: Dp = 0.dp) {
    var shown by remember { mutableIntStateOf(0) }
    SubcomposeLayout(modifier) { constraints ->
        val room = if (constraints.hasBoundedWidth) (constraints.maxWidth - reserve.roundToPx()).coerceAtLeast(0) else Constraints.Infinity
        val loose = constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)
        val (index, chosen) = variants.withIndex().firstNotNullOf { (i, variant) ->
            val last = i == variants.lastIndex
            val measurable = subcompose(i) { Box(if (i == shown) Modifier else Modifier.clearAndSetSemantics {}) { variant() } }.single()
            val placeable = measurable.measure(if (last) constraints.copy(minWidth = 0) else loose)
            if (last || placeable.width <= room) i to placeable else null
        }
        // Read by the variants as they are composed: the next frame's semantics follow this one's choice.
        shown = index
        layout(chosen.width, chosen.height) { chosen.place(0, 0) }
    }
}
