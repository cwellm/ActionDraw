package de.creaflect.actiondraw

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.image.ImageScanner
import de.creaflect.actiondraw.image.RedoStore
import de.creaflect.actiondraw.image.SeenStore
import java.io.File

/** Mutually-exclusive ways of viewing the reference image (value / colour / structure studies). */
enum class ViewMode { NONE, GRAYSCALE, SQUINT, SEPIA, POSTERIZE, PIXELATE, WARM, COOL, EDGE, SILHOUETTE }

/** Proportion-overlay variants drawn over the image. */
enum class GridMode { OFF, THIRDS, PHI, DIAGONAL }

/**
 * Single hoisted state holder for the whole app. Plain class backed by Compose state so the UI
 * recomposes on change; all mutations happen through the action methods below.
 */
class AppState {
    var screen by mutableStateOf(Screen.Menu)
        private set

    var folder by mutableStateOf<File?>(null)
        private set

    /** Every image in the folder (sorted) — basis for the seen/unseen counts. */
    private var allImages: List<File> = emptyList()

    /** The current play set: redo-flagged images first, then unseen, shuffled within each group. */
    var pool by mutableStateOf<List<File>>(emptyList())
        private set
    var index by mutableStateOf(0)
        private set

    /** Names of images already shown for this folder (persisted in [SeenStore]). */
    private var seen: MutableSet<String> = mutableSetOf()

    /** Names the user flagged to "redo" — resurfaced first next session (persisted in [RedoStore]). */
    private var redo: MutableSet<String> = mutableSetOf()

    /** Bumped whenever the redo set changes so [isCurrentRedo] recomposes. */
    var redoTick by mutableStateOf(0)
        private set

    // ---- Timing ----
    /** Per-image duration in fixed mode. */
    var intervalSeconds by mutableStateOf(120)

    /** When non-null, the session runs this finite gesture ramp instead of fixed timing. */
    var rampPlan by mutableStateOf<SessionPlan?>(null)

    /** 0-based pose index within the ramp. */
    var rampPose by mutableStateOf(0)
        private set

    var elapsedSeconds by mutableStateOf(0)
        private set
    var isPaused by mutableStateOf(false)
        private set

    // ---- Live filters ----
    var viewMode by mutableStateOf(ViewMode.NONE)
    var blur by mutableStateOf(false)
    var upsideDown by mutableStateOf(false)
    var mirror by mutableStateOf(false)
    var gridMode by mutableStateOf(GridMode.OFF)
    val blurRadius: Dp = 12.dp

    // ---- Session stats ----
    var sessionPoses by mutableStateOf(0)
        private set
    var sessionSeconds by mutableStateOf(0)
        private set
    var lastSessionPoses by mutableStateOf(0)
        private set
    var lastSessionSeconds by mutableStateOf(0)
        private set
    var lastSessionCompleted by mutableStateOf(false)
        private set

    val isRamp: Boolean get() = rampPlan != null

    /** Duration for the current image: the ramp step's time, or the fixed interval. */
    val currentIntervalSeconds: Int
        get() {
            val plan = rampPlan ?: return intervalSeconds
            var n = rampPose
            for (step in plan.steps) {
                if (n < step.count) return step.seconds
                n -= step.count
            }
            return plan.steps.last().seconds
        }

    val remainingSeconds: Int
        get() = (currentIntervalSeconds - elapsedSeconds).coerceAtLeast(0)

    /** Index of the ramp leg the current pose belongs to. */
    val rampStepIndex: Int
        get() {
            val plan = rampPlan ?: return 0
            var n = rampPose
            plan.steps.forEachIndexed { i, step ->
                if (n < step.count) return i
                n -= step.count
            }
            return plan.steps.lastIndex
        }

    val rampTotalPoses: Int get() = rampPlan?.totalPoses ?: 0

    val currentImage: File?
        get() = pool.getOrNull(index)

    val totalCount: Int get() = allImages.size
    val unseenCount: Int get() = allImages.count { it.name !in seen }

    /** Whether the current image is flagged for redo (reads [redoTick] so the control recomposes). */
    val isCurrentRedo: Boolean
        get() {
            redoTick // snapshot read: recompose when flags change
            val name = currentImage?.name ?: return false
            return name in redo
        }

    // ---- Menu ----

    /** Called when the user picks a folder; loads the image list and the saved seen/redo sets. */
    fun selectFolder(dir: File) {
        folder = dir
        allImages = ImageScanner.scan(dir)
        seen = SeenStore.read(dir).toMutableSet()
        redo = RedoStore.read(dir).toMutableSet()
    }

    // ---- Session lifecycle ----

    fun start() {
        val dir = folder ?: return
        // Re-read in case the folder contents changed since it was selected.
        allImages = ImageScanner.scan(dir)
        seen = SeenStore.read(dir).toMutableSet()
        redo = RedoStore.read(dir).toMutableSet()
        rebuildPool()
        index = 0
        rampPose = 0
        elapsedSeconds = 0
        isPaused = false
        sessionPoses = if (pool.isEmpty()) 0 else 1
        sessionSeconds = 0
        redoTick++
        screen = Screen.Session
    }

    /** pool = redo-flagged first, then unseen — each group shuffled. If nothing is left, reshuffle all. */
    private fun rebuildPool() {
        val (redoFirst, rest) = poolGroups(allImages, seen, redo)
        pool = if (redoFirst.isEmpty() && rest.isEmpty()) {
            resetSeen()
            allImages.shuffled()
        } else {
            redoFirst.shuffled() + rest.shuffled()
        }
    }

    private fun resetSeen() {
        folder?.let { SeenStore.clear(it) }
        seen.clear()
    }

    /** Restart the timer fresh for the current picture. */
    fun play() {
        elapsedSeconds = 0
        isPaused = false
    }

    fun togglePause() {
        isPaused = !isPaused
    }

    /** Manual stop -> session summary (incomplete). */
    fun stop() = endSession(completed = false)

    private fun endSession(completed: Boolean) {
        markCurrentSeen()
        lastSessionPoses = sessionPoses
        lastSessionSeconds = sessionSeconds
        lastSessionCompleted = completed
        screen = Screen.Summary
    }

    fun backToMenu() {
        screen = Screen.Menu
    }

    /** Flag/unflag the current image for redo; persisted immediately. */
    fun toggleRedoCurrent() {
        val dir = folder ?: return
        val name = currentImage?.name ?: return
        if (!redo.add(name)) redo.remove(name)
        RedoStore.write(dir, redo)
        redoTick++
    }

    /** Cycle the proportion overlay: Off -> Thirds -> Phi -> Diagonal -> Off. */
    fun cycleGrid() {
        gridMode = GridMode.entries[(gridMode.ordinal + 1) % GridMode.entries.size]
    }

    private fun markCurrentSeen() {
        val dir = folder ?: return
        val current = currentImage ?: return
        if (seen.add(current.name)) SeenStore.write(dir, seen)
        // Drawing a flagged image counts as having redone it -> clear the flag.
        if (redo.remove(current.name)) {
            RedoStore.write(dir, redo)
            redoTick++
        }
    }

    private fun advanceImage() {
        if (index + 1 >= pool.size) {
            // Whole folder has now been shown -> truncate the seen file and start a fresh cycle.
            resetSeen()
            pool = allImages.shuffled()
            index = 0
        } else {
            index += 1
        }
    }

    fun next() {
        markCurrentSeen()
        if (isRamp) {
            rampPose += 1
            if (rampPose >= rampTotalPoses) {
                endSession(completed = true) // ramp finished
                return
            }
        }
        advanceImage()
        sessionPoses += 1
        elapsedSeconds = 0
        isPaused = false
    }

    fun previous() {
        if (isRamp && rampPose > 0) rampPose -= 1
        index = (index - 1).coerceAtLeast(0)
        elapsedSeconds = 0
        isPaused = false
    }

    /** Called once per second by the session timer (only while running). */
    fun tick() {
        elapsedSeconds += 1
        sessionSeconds += 1
        if (elapsedSeconds >= currentIntervalSeconds) {
            next()
        }
    }
}

/**
 * Splits the folder into the next session's play groups: images flagged for **redo** come first
 * (regardless of seen state), then the **unseen, un-flagged** images. Seen, un-flagged images are
 * dropped. Pure and deterministic (no shuffle) so the ordering rule is unit-testable.
 */
fun poolGroups(
    all: List<File>,
    seen: Set<String>,
    redo: Set<String>,
): Pair<List<File>, List<File>> {
    val redoFirst = all.filter { it.name in redo }
    val rest = all.filter { it.name !in seen && it.name !in redo }
    return redoFirst to rest
}
