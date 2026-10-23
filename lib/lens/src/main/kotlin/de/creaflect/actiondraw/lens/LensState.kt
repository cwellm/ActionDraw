package de.creaflect.actiondraw.lens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.ui.GlassState
import java.io.File
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * How a picture is seen (CONCEPT.md, *Lens*): the lens it is seen through, the knobs over it — the
 * light, blur, mirror, flip, invert, shards — the proportion grid, and the glass. Plain class backed
 * by Compose state, so whatever shows the picture recomposes on change. A session in the Practice
 * room holds one, and so will the Lens room: the tray, the picture and the keys over it are one
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
     * the sizes counted in pixels — pixelate's block, the shards' — scaled along. The glass stays:
     * the copy's own is put away. What a picture is saved through, at its own size.
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
    }

    /**
     * Esc, as far as the lens goes: the glass lifted off [picture], or else put away. False when
     * the lens has nothing to step back from, so whatever shows the picture takes the next step.
     */
    fun stepBack(picture: File?): Boolean = when {
        glass.focusOn(picture) != null -> { glass.wholePicture(); true }
        glass.inHand -> { glass.putAway(); true }
        else -> false
    }
}
