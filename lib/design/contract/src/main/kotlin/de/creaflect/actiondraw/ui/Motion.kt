package de.creaflect.actiondraw.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * How the UI kit moves (CONCEPT.md, *Motion: wet, weighty, drawn*): four movements, each with one
 * job, timed alike whichever design draws them — how long a thing takes is part of how the app
 * works. With reduced motion on, every one of them becomes a short cross-fade.
 */
object Motion {
    /**
     * Bloom: a room opens as its pigment spreads from the well. The design's ease-out
     * (0.2, 0.8, 0.2, 1) covered the screen in 80 ms — a flash, not a spreading; this one lets the
     * pigment be seen leaving the well before it rushes to the corners.
     */
    const val BLOOM_MS = 420
    val BLOOM_EASING = CubicBezierEasing(0.35f, 0f, 0.25f, 1f)

    /** The share of the bloom by which the pigment covers the screen and the room is opened under it. */
    const val BLOOM_COVERS = 0.55f

    /** Everything, with reduced motion on. */
    const val REDUCED_MS = 120

    /** Recede: how long the pen is down before the chrome fades, and how long the fade takes. */
    const val RECEDE_AFTER_MS = 1500L
    const val RECEDE_MS = 600
    const val RETURN_MS = 200

    /** How close to its edge the pointer must come for receded chrome to show again, and how strongly. */
    val APPROACH = 56.dp
    const val APPROACH_ALPHA = 0.7f

    /**
     * Settle: a card lifts in [LIFT_MS] to [LIFT_SCALE] larger, leans into its sideways speed
     * ([LEAN_PER_PX] degrees per pixel of a move, at most [MAX_LEAN]), and lands on a spring that
     * overshoots a little.
     */
    const val LIFT_MS = 120
    const val LIFT_SCALE = 0.03f
    const val LEAN_PER_PX = 0.5f
    const val MAX_LEAN = 4f
    const val SETTLE_DAMPING = 0.7f
    const val SETTLE_STIFFNESS = 380f

    /** Draw-on: a new group's frame is drawn round its cards in this long, then its tag appears. */
    const val DRAW_ON_MS = 360

    /** The glass set down on a part of the picture, or lifted off it: a zoom that starts at once and lands softly. */
    const val GLASS_MS = 380
    val GLASS_EASING = CubicBezierEasing(0.3f, 0f, 0.2f, 1f)
}

/** Whether a card is lifted off the table, for tests to read. */
val Lifted = SemanticsPropertyKey<Boolean>("Lifted")
var SemanticsPropertyReceiver.lifted by Lifted

/** How far a card leans while dragged sideways by [dx] pixels in one move. */
fun leanFor(dx: Float): Float = (dx * Motion.LEAN_PER_PX).coerceIn(-Motion.MAX_LEAN, Motion.MAX_LEAN)

/** Whether to keep movement to cross-fades: ActionDraw's own setting, or the system's when unset. */
val LocalReducedMotion = compositionLocalOf { false }

// ---------------- Recede ----------------

/**
 * How visible a bar of chrome is: all there unless it has [receded]; once receded, back at
 * [Motion.APPROACH_ALPHA] while the pointer is [near] its edge, and wholly while [over] it.
 */
fun chromeAlpha(receded: Boolean, near: Boolean, over: Boolean): Float = when {
    !receded || over -> 1f
    near -> Motion.APPROACH_ALPHA
    else -> 0f
}

/** The alpha a bar of chrome is aiming for, for tests to read: chrome is drawn, not announced. */
val ChromeAlpha = SemanticsPropertyKey<Float>("ChromeAlpha")
var SemanticsPropertyReceiver.chromeAlpha by ChromeAlpha

/** Where the pointer is over a room (in window coordinates), and each move as it happens. */
class PointerWatch {
    var position by mutableStateOf(Offset.Unspecified)
        internal set

    /**
     * One item per move, sent by the pointer handler itself. A flow and not a counter read with
     * snapshotFlow: a snapshotFlow's observer runs on whatever thread applies the snapshot, and in
     * a desktop test that is Swing's while the composition is on the test thread — the two once took
     * each other's locks (Compose 1.7.3) and a test hung for good. This is sent on the thread the
     * pointer events arrive on, which is the composition's.
     */
    val moves: SharedFlow<Unit> get() = moved
    private val moved = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    internal fun moved() {
        moved.tryEmit(Unit)
    }

    internal var origin = Offset.Zero
}

/** Watches the pointer over this element and everything in it, taking nothing from anyone. */
fun Modifier.watchPointer(watch: PointerWatch): Modifier = this
    .onGloballyPositioned { watch.origin = it.positionInRoot() }
    .pointerInput(watch) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: continue
                watch.position = change.position + watch.origin
                if (event.type == PointerEventType.Move) watch.moved()
            }
        }
    }

/** The edge of the room a bar of chrome lies along. */
enum class ChromeEdge { TOP, BOTTOM, LEFT, RIGHT }

/**
 * A bar of chrome that steps back while the work goes on. [receded] says when — each room knows
 * what working means there. Receded, it comes back at [Motion.APPROACH_ALPHA] as the pointer
 * nears its [edge] and wholly when the pointer is over it, which also calls [onOver]. Only this
 * recomposes as the pointer moves: what lies around it, a sketch page among them, is untouched.
 */
@Composable
fun RecedingChrome(
    receded: Boolean,
    watch: PointerWatch,
    edge: ChromeEdge,
    modifier: Modifier = Modifier,
    onOver: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val isReceded by rememberUpdatedState(receded)
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val approach = with(LocalDensity.current) { Motion.APPROACH.toPx() }
    val over by remember { derivedStateOf { watch.position.isSpecified && bounds.contains(watch.position) } }
    val target by remember {
        derivedStateOf {
            val p = watch.position
            val near = p.isSpecified && when (edge) {
                ChromeEdge.TOP -> p.y <= bounds.bottom + approach
                ChromeEdge.BOTTOM -> p.y >= bounds.top - approach
                ChromeEdge.LEFT -> p.x <= bounds.right + approach
                ChromeEdge.RIGHT -> p.x >= bounds.left - approach
            }
            chromeAlpha(isReceded, near, over)
        }
    }
    LaunchedEffect(over) { if (over) onOver() }
    val alpha by animateFloatAsState(
        target,
        tween(if (reduced) Motion.REDUCED_MS else if (target < 1f) Motion.RECEDE_MS else Motion.RETURN_MS),
    )
    Box(
        modifier
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .graphicsLayer { this.alpha = alpha }
            .semantics { chromeAlpha = target },
    ) { content() }
}
