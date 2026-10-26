package de.creaflect.actiondraw.practice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.creaflect.actiondraw.Lib
import de.creaflect.actiondraw.RampStep
import de.creaflect.actiondraw.Room
import de.creaflect.actiondraw.SessionPlan
import de.creaflect.actiondraw.SessionSetup
import de.creaflect.actiondraw.board.BoardRef
import de.creaflect.actiondraw.board.BoardSource
import de.creaflect.actiondraw.handoff.Handed
import de.creaflect.actiondraw.handoff.Handoff
import de.creaflect.actiondraw.handoff.Route
import de.creaflect.actiondraw.image.Pictures
import de.creaflect.actiondraw.image.relKey
import de.creaflect.actiondraw.lens.LensState
import de.creaflect.actiondraw.platform.FileDialogs
import de.creaflect.actiondraw.ui.keptIn
import java.io.File

/**
 * The Practice room: the folder or board it draws from, the pictures chosen, a session's pool and
 * clock, the [lens] its pictures are seen through, and what has been drawn. Plain class backed by
 * Compose state so the UI recomposes on change; all mutations happen through the action methods
 * below. Where the room is — its menu, the picker, a session, the summary — is its own [page]; the
 * app shows the room, and a session started on a board in a window of its own ([windowPage]).
 *
 * [lib] is where the pictures, what has been drawn of them, and the preferences (the last opened
 * folder) come from; a test hands in one over a temp folder, so it never touches the real user's.
 * [boards] are what the room draws from and pins to besides folders, [handoff] where a pose's
 * picture is taken into the other rooms.
 */
class PracticeState(
    private val lib: Lib,
    /** The host's file dialogs, for the room's screens to ask with. */
    val dialogs: FileDialogs = FileDialogs.None,
    private val boards: BoardSource = BoardSource.None,
    private val handoff: Handoff = Handoff.None,
) {
    /** Where the room's pictures come from, decoded for the session. */
    val pictures: Pictures get() = lib.pictures

    /** Where the room is: its menu (the session setup), the picker, a session, the summary. */
    var page by mutableStateOf(PracticePage.Menu)
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

    /** Names of images already shown for this folder (kept in the folder: `lib.practice`). */
    private var seen: MutableSet<String> = mutableSetOf()

    /** Names the user flagged to "redo" — resurfaced first next session (kept in the folder: `lib.practice`). */
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

    /**
     * How the pose's picture is seen: its lens, the knobs, the grid and the glass. It lives as long
     * as the room, so a lens chosen carries over from pose to pose; the glass is kept from one run
     * to the next.
     */
    val lens = LensState().also { it.glass.keptIn(lib.preferences) }

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

    /** The ramp leg the current pose belongs to; null in fixed-interval mode. */
    private val currentStep: RampStep?
        get() = rampPlan?.steps?.getOrNull(rampStepIndex)

    /** Duration for the current image: the ramp step's time, or the fixed interval. */
    val currentIntervalSeconds: Int
        get() = currentStep?.seconds ?: intervalSeconds

    val remainingSeconds: Int
        get() = (currentIntervalSeconds - elapsedSeconds).coerceAtLeast(0)

    /** Seconds past the interval — only ever non-zero in manual (non-auto-advance) mode. */
    val overtimeSeconds: Int
        get() = (elapsedSeconds - currentIntervalSeconds).coerceAtLeast(0)

    // ---- Memory drawing ----
    //
    // A memory pose runs in three beats: you study the reference, it goes away while you draw,
    // and at the end it comes back so you can see what you missed. Nothing here is stored — the
    // beat follows from the pose's own clock.

    /** How long the reference stays up on this pose, or null on an ordinary one. */
    val currentStudySeconds: Int? get() = currentStep?.studySeconds

    val isMemoryPose: Boolean get() = currentStudySeconds != null

    /** Counts down to the moment the reference vanishes, so it is never a jump scare. */
    val studyRemainingSeconds: Int
        get() = ((currentStudySeconds ?: 0) - elapsedSeconds).coerceAtLeast(0)

    /** The drawing time is up and the reference is back: the beat for comparing what you made. */
    val comparing: Boolean get() = isMemoryPose && elapsedSeconds >= currentIntervalSeconds

    /** What the pose itself would do — hide the reference for the drawing beat. */
    private val hiddenByPose: Boolean
        get() {
            val study = currentStudySeconds ?: return false
            return elapsedSeconds >= study && elapsedSeconds < currentIntervalSeconds
        }

    /**
     * `H` flips whatever the pose would show: a peek while drawing from memory, and cover on an
     * ordinary pose so any reference can be worked from memory. Cleared on every new pose.
     */
    var referenceFlipped by mutableStateOf(false)
        private set

    /** Whether the reference is out of sight right now, for whichever of the two reasons. */
    val referenceHidden: Boolean get() = hiddenByPose != referenceFlipped

    fun toggleReference() {
        referenceFlipped = !referenceFlipped
    }

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

    /** Whether the session was started on a board rather than from the menu (labels and navigation). */
    var sessionFromBoard by mutableStateOf(false)
        private set

    /**
     * Board sessions run in their own window so the board stays visible and closing the window
     * aborts back to it. null = no session window; otherwise the window shows this page (Session
     * or Summary).
     */
    var windowPage by mutableStateOf<PracticePage?>(null)
        private set

    /** Root + pool of the active board session, so "Go again" replays the same set. */
    private var boardSession: Pair<File, List<File>>? = null

    /** Practice state to restore once a board session is over. */
    private var practiceSnapshot: Triple<File?, List<File>, Set<String>?>? = null

    // ---- A board as the source (F9.10) ----

    /**
     * The board the Practice room draws from, or null while a folder is the source. Its pictures
     * are the board's own and its concepts'; seen and redo live in the board's folder, as for a
     * session started on the board.
     */
    var practiceBoard by mutableStateOf<BoardRef?>(null)
        private set

    // Reopen the folder from the previous run — or the board, where one was the source. Declared
    // after the state above so those properties are initialised before this runs.
    init {
        lib.preferences.lastFolder()?.let { loadFolder(it) }
        lib.preferences.practiceBoard()?.let { remembered ->
            boards.available().firstOrNull { it.dir.absolutePath == remembered.absolutePath }?.let(::loadBoard)
        }
    }

    /** The boards the room can draw from, and pin to. */
    fun boards(): List<BoardRef> = boards.available()

    /** Draws from [board] from now on, and next time. */
    fun selectBoard(board: BoardRef) {
        loadBoard(board)
        lib.preferences.setPracticeBoard(board.dir)
    }

    private fun loadBoard(board: BoardRef) {
        val dir = board.dir
        val pictures = boards.pictures(dir)
        practiceBoard = board
        folder = dir
        allImages = pictures
        selection = null
        seen = lib.practice.seen(dir).toMutableSet()
        redo = lib.practice.redo(dir).toMutableSet()
    }

    // ---- Menu ----

    /** Called when the user picks a folder; also remembered for the next start. */
    fun selectFolder(dir: File) {
        loadFolder(dir)
        lib.preferences.setLastFolder(dir)
        if (practiceBoard != null) {
            practiceBoard = null
            lib.preferences.setPracticeBoard(null)
        }
    }

    /** Loads the image list and the saved seen/redo sets for [dir]. */
    private fun loadFolder(dir: File) {
        folder = dir
        allImages = lib.pictures.scan(dir)
        selection = null
        seen = lib.practice.seen(dir).toMutableSet()
        redo = lib.practice.redo(dir).toMutableSet()
    }

    // ---- Picker ----

    fun openPicker() {
        if (folder != null) page = PracticePage.Picker
    }

    fun closePicker() {
        page = PracticePage.Menu
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
            if (sessionFromBoard) {
                startBoardSession(root, images)
                return
            }
        }
        val dir = folder ?: return
        sessionFromBoard = false
        // Re-read in case the folder - or the board - changed since it was chosen.
        allImages = practiceBoard?.let { boards.pictures(it.dir) } ?: lib.pictures.scan(dir)
        seen = lib.practice.seen(dir).toMutableSet()
        redo = lib.practice.redo(dir).toMutableSet()
        beginSession()
        page = PracticePage.Session
    }

    /**
     * Session with an explicit pool (an Idea-Board selection): [images] are the candidates and
     * seen/redo state lives in [root]. It opens in its own window (the board stays visible in
     * the main one); practice state is snapshotted and restored when that window closes.
     */
    fun startBoardSession(root: File, images: List<File>, setup: SessionSetup? = null) {
        if (images.isEmpty()) return
        if (!sessionFromBoard) {
            practiceSnapshot = Triple(folder, allImages, selection)
        }
        setup?.let { apply(it) }
        sessionFromBoard = true
        boardSession = root to images
        folder = root // deliberately not persisted: the remembered practice folder stays untouched
        allImages = images
        selection = null
        seen = lib.practice.seen(root).toMutableSet()
        redo = lib.practice.redo(root).toMutableSet()
        beginSession()
        windowPage = PracticePage.Session
    }

    /** Takes over a board's remembered session settings for the run that is about to start. */
    private fun apply(setup: SessionSetup) {
        rampPlan = setup.plan
        intervalSeconds = setup.intervalSeconds
        autoAdvance = setup.autoAdvance
        lens.viewMode = setup.viewMode
        lens.gridMode = setup.gridMode
        lens.temperature = setup.temperature
    }

    /** The current settings, so a board can remember exactly what is on screen. */
    fun currentSetup(): SessionSetup =
        SessionSetup(rampPlan, intervalSeconds, autoAdvance, lens.viewMode, lens.gridMode, lens.temperature)

    /** Files of this session's pool that are flagged for redo — what the summary offers to pin. */
    val sessionFlaggedFiles: List<File>
        get() {
            redoTick
            return pool.filter { key(it) in redo }
        }

    /** Result of the last pin from a session, shown briefly in the session/summary chrome. */
    var pinNotice by mutableStateOf<String?>(null)

    /**
     * The redo flags the session began with. Drawing one of those pictures is the redo, and clears
     * its flag; a flag set during this session is for next time and stays.
     */
    private var flaggedAtStart: Set<String> = emptySet()

    private fun beginSession() {
        flaggedAtStart = redo.toSet()
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
            folder?.let { lib.practice.setSeen(it, seen) }
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
        if (sessionFromBoard) {
            windowPage = PracticePage.Summary // summary shows inside the session window
        } else {
            page = PracticePage.Summary
        }
    }

    /** Leaves the summary — to the menu, or (board flow) by closing the session window. */
    fun backToMenu() {
        if (sessionFromBoard) {
            restorePractice()
            windowPage = null // the board is still on the main window
        } else {
            page = PracticePage.Menu
        }
    }

    /** The session window was closed (X): abort the drawing and return to the board. */
    fun abortBoardSession() {
        if (windowPage == PracticePage.Session) markCurrentSeen()
        restorePractice()
        windowPage = null
    }

    /** Pins [pictures] onto the board in [dir]; what came of it shows in the session's chrome. */
    fun pin(dir: File, pictures: List<File>) {
        pinNotice = boards.pin(dir, pictures)
    }

    /** Where the picture on screen can be taken: into Sketch, into the Lens… */
    fun routes(): List<Route> = currentImage?.let { handoff.routes(Handed.Picture(it), Room.PRACTICE) }.orEmpty()

    /**
     * Takes the picture on screen into [to]'s room; the session waits, paused, and Back from there
     * returns to it.
     */
    fun handOff(to: Room) {
        val picture = currentImage ?: return
        isPaused = true
        handoff.send(Handed.Picture(picture), to)
    }

    /** Undo what [startBoardSession] borrowed, so the menu shows the practice folder again. */
    private fun restorePractice() {
        practiceSnapshot?.let { (dir, images, sel) ->
            folder = dir
            allImages = images
            selection = sel
            seen = dir?.let { lib.practice.seen(it).toMutableSet() } ?: mutableSetOf()
            redo = dir?.let { lib.practice.redo(it).toMutableSet() } ?: mutableSetOf()
        }
        practiceSnapshot = null
        boardSession = null
        sessionFromBoard = false
    }

    /** Flag/unflag the current image for redo; persisted immediately. */
    fun toggleRedoCurrent() {
        val dir = folder ?: return
        val k = currentImage?.let(::key) ?: return
        if (!redo.add(k)) redo.remove(k)
        lib.practice.setRedo(dir, redo)
        redoTick++
    }

    private fun markCurrentSeen() {
        val dir = folder ?: return
        val k = currentImage?.let(::key) ?: return
        if (seen.add(k)) lib.practice.setSeen(dir, seen)
        // Drawing an image that came into the session flagged counts as having redone it -> clear
        // the flag. One flagged during this session is for the next: clearing it as the pose ended
        // meant every R was lost the moment you moved on.
        if (k in flaggedAtStart && redo.remove(k)) {
            lib.practice.setRedo(dir, redo)
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
        referenceFlipped = false
    }

    fun previous() {
        if (isRamp && rampPose > 0) rampPose -= 1
        index = (index - 1).coerceAtLeast(0)
        elapsedSeconds = 0
        isPaused = false
        referenceFlipped = false
    }

    /** Called once per second by the session timer (only while running). */
    fun tick() {
        elapsedSeconds += 1
        sessionSeconds += 1
        // A memory pose never advances on its own: at the end the reference comes back and waits
        // for you, because a comparison you get no time to make is no comparison at all.
        if (autoAdvance && !isMemoryPose && elapsedSeconds >= currentIntervalSeconds) {
            next()
        }
    }
}

/** Where the Practice room is. */
enum class PracticePage {
    /** The session setup: exercise, timing, source. */
    Menu,

    /** Choosing the pictures of the folder a session draws from. */
    Picker,

    /** A session running. */
    Session,

    /** What a session came to. */
    Summary,
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
