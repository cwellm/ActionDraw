package de.creaflect.actiondraw

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.creaflect.actiondraw.image.ImageScanner
import de.creaflect.actiondraw.image.RedoStore
import de.creaflect.actiondraw.image.SeenStore
import de.creaflect.actiondraw.image.relKey
import java.io.File
import kotlin.random.Random

/** Mutually-exclusive ways of viewing the reference image (value / colour / structure studies). */
enum class ViewMode { NONE, GRAYSCALE, SQUINT, SEPIA, POSTERIZE, PIXELATE, WARM, COOL, EDGE, SILHOUETTE, NOTAN }

/** Proportion-overlay variants drawn over the image. */
enum class GridMode { OFF, THIRDS, PHI, DIAGONAL }

/**
 * Single hoisted state holder for the whole app. Plain class backed by Compose state so the UI
 * recomposes on change; all mutations happen through the action methods below.
 *
 * [settings] persists app-level preferences (the last opened folder); inject a different one in
 * tests so they never touch the real user config.
 */
class AppState(private val settings: Settings = Settings()) {
    var screen by mutableStateOf(Screen.Menu)
        private set

    var folder by mutableStateOf<File?>(null)
        private set

    /** Every image in the folder (sorted) — basis for the picker and the seen/unseen counts. */
    private var allImages: List<File> = emptyList()

    /** All images of the folder, for the picker grid. */
    val images: List<File> get() = allImages

    /**
     * Names of the images chosen in the picker; `null` means "all images". Session pools are
     * always built from this subset.
     */
    var selection by mutableStateOf<Set<String>?>(null)
        private set

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

    /**
     * When true (default) the timer advances to the next image at 0. When false the countdown is
     * informational only: it runs into overtime and switching stays manual.
     */
    var autoAdvance by mutableStateOf(true)

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

    /** Seconds past the interval — only ever non-zero in manual (non-auto-advance) mode. */
    val overtimeSeconds: Int
        get() = (elapsedSeconds - currentIntervalSeconds).coerceAtLeast(0)

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

    /** The images the next session will draw from: the picker selection, or the whole folder. */
    val candidates: List<File>
        get() = selection?.let { sel -> allImages.filter { key(it) in sel } } ?: allImages

    val totalCount: Int get() = allImages.size
    val selectedCount: Int get() = candidates.size
    val unseenCount: Int get() = candidates.count { key(it) !in seen }

    /** Whether the current image is flagged for redo (reads [redoTick] so the control recomposes). */
    val isCurrentRedo: Boolean
        get() {
            redoTick // snapshot read: recompose when flags change
            val k = currentImage?.let(::key) ?: return false
            return k in redo
        }

    /**
     * Store key for [f]: its path relative to the current folder (`/`-separated). Identical to the
     * plain file name in the flat practice folders — existing seen/redo files stay valid — while
     * board sessions bring subfolder files, where names alone could collide.
     */
    private fun key(f: File): String = folder?.let { relKey(it, f) } ?: f.name

    // ---- Board-session bookkeeping ----

    /** Where the session was started from: the menu, or a board (drives labels and navigation). */
    var sessionOrigin by mutableStateOf(Screen.Menu)
        private set

    /**
     * Board sessions run in their own window so the board stays visible and closing the window
     * aborts back to it. null = no session window; otherwise the window shows this screen
     * (Session or Summary).
     */
    var boardWindowScreen by mutableStateOf<Screen?>(null)
        private set

    /** Root + pool of the active board session, so "Go again" replays the same set. */
    private var boardSession: Pair<File, List<File>>? = null

    /** Practice state to restore once a board session is over. */
    private var practiceSnapshot: Triple<File?, List<File>, Set<String>?>? = null

    // Reopen the folder from the previous run. Declared after the state above so those properties
    // are initialised before this runs.
    init {
        settings.lastFolder()?.let { loadFolder(it) }
    }

    // ---- Menu ----

    /** Called when the user picks a folder; also remembered for the next start. */
    fun selectFolder(dir: File) {
        loadFolder(dir)
        settings.setLastFolder(dir)
    }

    /** Loads the image list and the saved seen/redo sets for [dir]. */
    private fun loadFolder(dir: File) {
        folder = dir
        allImages = ImageScanner.scan(dir)
        selection = null
        seen = SeenStore.read(dir).toMutableSet()
        redo = RedoStore.read(dir).toMutableSet()
    }

    // ---- Picker ----

    fun openPicker() {
        if (folder != null) screen = Screen.Picker
    }

    fun closePicker() {
        screen = Screen.Menu
    }

    fun isSelected(name: String): Boolean = selection?.let { name in it } ?: true

    fun toggleSelected(name: String) {
        val all = allImages.mapTo(mutableSetOf(), ::key)
        val current = selection ?: all
        val next = if (name in current) current - name else current + name
        selection = if (next == all) null else next
    }

    fun selectAllImages() {
        selection = null
    }

    fun selectNoImages() {
        selection = emptySet()
    }

    // ---- Session lifecycle ----

    fun start() {
        // "Go again" on a board session's summary replays the same board pool.
        boardSession?.let { (root, images) ->
            if (sessionOrigin == Screen.Board) {
                startBoardSession(root, images)
                return
            }
        }
        val dir = folder ?: return
        sessionOrigin = Screen.Menu
        // Re-read in case the folder contents changed since it was selected.
        allImages = ImageScanner.scan(dir)
        seen = SeenStore.read(dir).toMutableSet()
        redo = RedoStore.read(dir).toMutableSet()
        beginSession()
        screen = Screen.Session
    }

    /**
     * Session with an explicit pool (an Idea-Board selection): [images] are the candidates and
     * seen/redo state lives in [root]. It opens in its own window (the board stays visible in
     * the main one); practice state is snapshotted and restored when that window closes.
     */
    fun startBoardSession(root: File, images: List<File>, setup: SessionSetup? = null) {
        if (images.isEmpty()) return
        if (sessionOrigin != Screen.Board) {
            practiceSnapshot = Triple(folder, allImages, selection)
        }
        setup?.let { apply(it) }
        sessionOrigin = Screen.Board
        boardSession = root to images
        folder = root // deliberately not persisted: the remembered practice folder stays untouched
        allImages = images
        selection = null
        seen = SeenStore.read(root).toMutableSet()
        redo = RedoStore.read(root).toMutableSet()
        beginSession()
        boardWindowScreen = Screen.Session
    }

    /** Takes over a board's remembered session settings for the run that is about to start. */
    private fun apply(setup: SessionSetup) {
        rampPlan = setup.plan
        intervalSeconds = setup.intervalSeconds
        autoAdvance = setup.autoAdvance
        viewMode = setup.viewMode
        gridMode = setup.gridMode
    }

    /** The current settings, so a board can remember exactly what is on screen. */
    fun currentSetup(): SessionSetup =
        SessionSetup(rampPlan, intervalSeconds, autoAdvance, viewMode, gridMode)

    /** Files of this session's pool that are flagged for redo — what the summary offers to pin. */
    val sessionFlaggedFiles: List<File>
        get() {
            redoTick
            return pool.filter { key(it) in redo }
        }

    /** Result of the last pin from a session, shown briefly in the session/summary chrome. */
    var pinNotice by mutableStateOf<String?>(null)

    private fun beginSession() {
        rebuildPool()
        index = 0
        rampPose = 0
        elapsedSeconds = 0
        isPaused = false
        sessionPoses = if (pool.isEmpty()) 0 else 1
        sessionSeconds = 0
        redoTick++
    }

    /** pool = redo-flagged first, then unseen — each group shuffled. If nothing is left, reshuffle. */
    private fun rebuildPool() {
        val cand = candidates
        val (redoFirst, rest) = poolGroups(cand, seen, redo, ::key)
        pool = if (redoFirst.isEmpty() && rest.isEmpty()) {
            startFreshCycle(cand)
        } else {
            redoFirst.shuffled() + rest.shuffled()
        }
    }

    /**
     * Every candidate has been seen: forget ONLY the candidates' seen state (other images of the
     * folder keep theirs — matters when a picker subset is active) and reshuffle them all.
     */
    private fun startFreshCycle(cand: List<File>): List<File> {
        if (cand.isNotEmpty()) {
            seen.removeAll(cand.mapTo(mutableSetOf(), ::key))
            folder?.let { SeenStore.write(it, seen) }
        }
        return cand.shuffled()
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
        if (sessionOrigin == Screen.Board) {
            boardWindowScreen = Screen.Summary // summary shows inside the session window
        } else {
            screen = Screen.Summary
        }
    }

    /** Leaves the summary — to the menu, or (board flow) by closing the session window. */
    fun backToMenu() {
        if (sessionOrigin == Screen.Board) {
            restorePractice()
            boardWindowScreen = null // the board is still on the main window
        } else {
            screen = Screen.Menu
        }
    }

    /** The session window was closed (X): abort the drawing and return to the board. */
    fun abortBoardSession() {
        if (boardWindowScreen == Screen.Session) markCurrentSeen()
        restorePractice()
        boardWindowScreen = null
    }

    // ---- Board navigation (wired by the app shell; the practice side stays board-agnostic) ----

    fun showBoardList() {
        screen = Screen.BoardList
    }

    fun showBoard() {
        screen = Screen.Board
    }

    fun leaveBoard() {
        screen = Screen.Menu
    }

    /** Undo what [startBoardSession] borrowed, so the menu shows the practice folder again. */
    private fun restorePractice() {
        practiceSnapshot?.let { (dir, images, sel) ->
            folder = dir
            allImages = images
            selection = sel
            seen = dir?.let { SeenStore.read(it).toMutableSet() } ?: mutableSetOf()
            redo = dir?.let { RedoStore.read(it).toMutableSet() } ?: mutableSetOf()
        }
        practiceSnapshot = null
        boardSession = null
        sessionOrigin = Screen.Menu
    }

    /** Flag/unflag the current image for redo; persisted immediately. */
    fun toggleRedoCurrent() {
        val dir = folder ?: return
        val k = currentImage?.let(::key) ?: return
        if (!redo.add(k)) redo.remove(k)
        RedoStore.write(dir, redo)
        redoTick++
    }

    /** Cycle the proportion overlay: Off -> Thirds -> Phi -> Diagonal -> Off. */
    fun cycleGrid() {
        gridMode = GridMode.entries[(gridMode.ordinal + 1) % GridMode.entries.size]
    }

    /** Toggle defraction; every switch-on rolls a fresh seed, so the shards differ each time. */
    fun toggleDefraction() {
        defraction = !defraction
        if (defraction) defractionSeed = Random.nextFloat() * 1000f
    }

    private fun markCurrentSeen() {
        val dir = folder ?: return
        val k = currentImage?.let(::key) ?: return
        if (seen.add(k)) SeenStore.write(dir, seen)
        // Drawing a flagged image counts as having redone it -> clear the flag.
        if (redo.remove(k)) {
            RedoStore.write(dir, redo)
            redoTick++
        }
    }

    private fun advanceImage() {
        if (index + 1 >= pool.size) {
            // All candidates have now been shown -> forget their seen state and start a new cycle.
            pool = startFreshCycle(candidates)
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
        if (autoAdvance && elapsedSeconds >= currentIntervalSeconds) {
            next()
        }
    }
}

/**
 * Splits the play set into the next session's groups: images flagged for **redo** come first
 * (regardless of seen state), then the **unseen, un-flagged** images. Seen, un-flagged images are
 * dropped. [keyOf] maps a file to its store key (plain name for flat folders, relative path for
 * board sessions). Pure and deterministic (no shuffle) so the ordering rule is unit-testable.
 */
fun poolGroups(
    all: List<File>,
    seen: Set<String>,
    redo: Set<String>,
    keyOf: (File) -> String = { it.name },
): Pair<List<File>, List<File>> {
    val redoFirst = all.filter { keyOf(it) in redo }
    val rest = all.filter { keyOf(it) !in seen && keyOf(it) !in redo }
    return redoFirst to rest
}
