package de.creaflect.actiondraw.lens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.ui.FormState
import de.creaflect.actiondraw.ui.GlassState
import de.creaflect.actiondraw.ui.ZOOM_STEP
import de.creaflect.actiondraw.ui.Zoom
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * How a picture is seen (CONCEPT.md, *Lens*): the lens it is seen through, the knobs over it — the
 * light, blur, mirror, flip, invert, shards — the proportion grid, the glass, and the part of the
 * picture zoomed into, which is then the picture for all the rest. Plain class backed
 * by Compose state, so whatever shows the picture recomposes on change. A session in the Practice
 * room holds one, and so does the Lens room: the tray, the picture and the keys over it are one
 * code path for both.
 */
class LensState {
    var viewMode by mutableStateOf(ViewMode.NONE)
    var blur by mutableStateOf(false)
    var upsideDown by mutableStateOf(false)
    var mirror by mutableStateOf(false)
    var gridMode by mutableStateOf(GridMode.OFF)

    /** The glass held over the picture (F10.9): kept as long as the lens, so its optic carries over. */
    val glass = GlassState()

    /** The form the picture is wrapped onto, if any (F10.10). The picture is on a form or under the glass, not both. */
    val form = FormState()

    /**
     * The lens whose zoom this one shows — set where two views of one picture lie side by side, so
     * they show the same part whichever of them is zoomed; none where it keeps its own.
     */
    var zoomsWith: LensState? = null

    private var ownZoom by mutableStateOf<Zoom?>(null)

    /** The part of a picture zoomed into, taken as the picture itself; none where the whole picture is shown. */
    val zoom: Zoom? get() = zoomsWith?.zoom ?: ownZoom

    init {
        glass.onTakeUp = { form.choose(null) }
        form.onShow = { glass.putAway() }
    }

    /**
     * White balance from -1 (cool) through 0 (neutral) to +1 (warm). Independent of the view
     * mode, so a Notan study can still be lit warm; replaces the old Warm/Cool view modes.
     */
    var temperature by mutableStateOf(0f)

    /** Invert the final colours — independent of (and applied after) every other effect. */
    var invert by mutableStateOf(false)

    /** Cubist "defraction": the image splits into random shards. Re-rolled on every switch-on. */
    var defraction by mutableStateOf(false)
        private set
    var defractionSeed by mutableStateOf(0f)
        private set

    // ---- Adjustable filter parameters ----
    /** Blur radius in dp. */
    var blurRadius by mutableStateOf(12)

    /** Posterize: number of value bands per channel. */
    var posterizeLevels by mutableStateOf(5)

    /** Pixelate: block edge length in pixels. */
    var pixelateBlock by mutableStateOf(8)

    /** Silhouette: luminance threshold (0..1) separating black from white. */
    var silhouetteThreshold by mutableStateOf(0.5f)

    /** Notan: number of values (2 or 3). */
    var notanBands by mutableStateOf(2)

    /** Notan: centre luminance threshold (0..1). */
    var notanThreshold by mutableStateOf(0.5f)

    /** Defraction: approximate shard size in pixels. */
    var defractionBlock by mutableStateOf(96)

    /** Defraction: displacement/rotation strength (0..1). */
    var defractionStrength by mutableStateOf(0.5f)

    /** Cycle the proportion overlay: Off -> Thirds -> Phi -> Diagonal -> Off. */
    fun cycleGrid() {
        gridMode = GridMode.entries[(gridMode.ordinal + 1) % GridMode.entries.size]
    }

    /** Toggle defraction; every switch-on rolls a fresh seed, so the shards differ each time. */
    fun toggleDefraction() {
        defraction = !defraction
        if (defraction) defractionSeed = Random.nextFloat() * 1000f
    }

    /**
     * This lens as it shows the picture [scale] times as large: the same lens, knobs and grid, with
     * the sizes counted in pixels — pixelate's block, the shards' — scaled along. The glass in the
     * hand or set down on a part stays behind; laid over the whole picture it comes along, as it is
     * how the whole picture is seen — and so does the form the picture is on. What a picture is
     * saved through, at its own size.
     */
    fun copy(scale: Float = 1f): LensState = LensState().also { c ->
        c.viewMode = viewMode
        c.blur = blur
        c.upsideDown = upsideDown
        c.mirror = mirror
        c.gridMode = gridMode
        c.temperature = temperature
        c.invert = invert
        c.defraction = defraction
        c.defractionSeed = defractionSeed
        c.blurRadius = blurRadius
        c.posterizeLevels = posterizeLevels
        c.pixelateBlock = (pixelateBlock * scale).roundToInt().coerceAtLeast(1)
        c.silhouetteThreshold = silhouetteThreshold
        c.notanBands = notanBands
        c.notanThreshold = notanThreshold
        c.defractionBlock = (defractionBlock * scale).roundToInt().coerceAtLeast(1)
        c.defractionStrength = defractionStrength
        c.glass.spreadLike(glass)
        c.form.copyFrom(form)
    }

    /**
     * Esc, as far as the lens goes: the glass lifted off [picture], or off the whole picture back
     * into the hand, or else put away; or the picture taken off its form; or the whole picture shown
     * again where it was zoomed into. False when the lens has nothing to step back from, so whatever
     * shows the picture takes the next step.
     */
    fun stepBack(picture: File?): Boolean = when {
        glass.focusOn(picture) != null -> { glass.wholePicture(); true }
        glass.spread != null -> { glass.gather(); true }
        glass.inHand -> { glass.putAway(); true }
        form.form != null -> { form.choose(null); true }
        zoomOn(picture) != null -> { showWhole(); true }
        else -> false
    }

    // ---- Zoomed into ----

    /** The part of [picture] zoomed into — none on another picture: a new one starts whole. */
    fun zoomOn(picture: File?): Zoom? = zoom?.takeIf { it.picture == picture }

    /**
     * Nearer to [picture] by [steps] of [ZOOM_STEP] — further, where they are fewer than none —
     * about ([x], [y]) on what is shown, 0 to 1, which stays where it is: the wheel under the
     * pointer, + and − about the middle. Out as far as the whole picture, and no further.
     */
    fun zoomBy(picture: File?, steps: Float, x: Float = 0.5f, y: Float = 0.5f) {
        val now = zoomOn(picture) ?: Zoom.whole(picture)
        val k = ZOOM_STEP.pow(steps)
        val w = now.width / k
        val h = now.height / k
        val (px, py) = now.onWhole(x, y)
        showPart(Zoom.of(picture, px - x * w, py - y * h, w, h))
    }

    /** Into the part of what is shown from ([x0], [y0]) to ([x1], [y1]), 0 to 1: a part dragged out — further in, where it is zoomed into already. */
    fun zoomInto(picture: File?, x0: Float, y0: Float, x1: Float, y1: Float) {
        val now = zoomOn(picture) ?: Zoom.whole(picture)
        val (ax, ay) = now.onWhole(min(x0, x1).coerceIn(0f, 1f), min(y0, y1).coerceIn(0f, 1f))
        val (bx, by) = now.onWhole(max(x0, x1).coerceIn(0f, 1f), max(y0, y1).coerceIn(0f, 1f))
        showPart(Zoom.of(picture, ax, ay, bx - ax, by - ay))
    }

    /** What is shown moved across [picture] by ([dx], [dy]) of itself, and kept on it. Nothing to move where it is shown whole. */
    fun panBy(picture: File?, dx: Float, dy: Float) {
        val now = zoomOn(picture) ?: return
        showPart(Zoom.of(picture, now.left + dx * now.width, now.top + dy * now.height, now.width, now.height))
    }

    /** The whole picture shown again. */
    fun showWhole() = showPart(null)

    /** [next] taken as the picture; the glass, if it was down on what was shown before, lifted off. */
    private fun showPart(next: Zoom?) {
        if (next == zoom) return
        val keeper = zoomsWith ?: this
        keeper.ownZoom = next
        keeper.glass.wholePicture()
        glass.wholePicture()
    }
}
