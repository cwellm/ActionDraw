package de.creaflect.actiondraw

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.image.ImageScanner
import de.creaflect.actiondraw.image.SeenStore
import java.io.File

/** Mutually-exclusive ways of viewing the reference image (value/structure studies). */
enum class ViewMode { NONE, GRAYSCALE, SQUINT, EDGE, SILHOUETTE }

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

    /** The current play set: images not yet seen at session start, shuffled. */
    var pool by mutableStateOf<List<File>>(emptyList())
        private set
    var index by mutableStateOf(0)
        private set

    /** Names of images already shown for this folder (persisted in [SeenStore]). */
    private var seen: MutableSet<String> = mutableSetOf()

    var intervalSeconds by mutableStateOf(120)
    var elapsedSeconds by mutableStateOf(0)
        private set
    var isPaused by mutableStateOf(false)
        private set

    // Live filters.
    var viewMode by mutableStateOf(ViewMode.NONE)
    var blur by mutableStateOf(false)
    var upsideDown by mutableStateOf(false)
    var showGrid by mutableStateOf(false)
    val blurRadius: Dp = 12.dp

    val remainingSeconds: Int
        get() = (intervalSeconds - elapsedSeconds).coerceAtLeast(0)

    val currentImage: File?
        get() = pool.getOrNull(index)

    val totalCount: Int get() = allImages.size
    val unseenCount: Int get() = allImages.count { it.name !in seen }

    // ---- Menu ----

    /** Called when the user picks a folder; loads the image list and the saved seen-set. */
    fun selectFolder(dir: File) {
        folder = dir
        allImages = ImageScanner.scan(dir)
        seen = SeenStore.read(dir).toMutableSet()
    }

    // ---- Session lifecycle ----

    fun start() {
        val dir = folder ?: return
        // Re-read in case the folder contents changed since it was selected.
        allImages = ImageScanner.scan(dir)
        seen = SeenStore.read(dir).toMutableSet()
        rebuildPool()
        index = 0
        elapsedSeconds = 0
        isPaused = false
        screen = Screen.Session
    }

    /** pool = unseen images, shuffled. If nothing is left unseen, reset and use the whole set. */
    private fun rebuildPool() {
        val unseen = allImages.filter { it.name !in seen }
        pool = if (unseen.isEmpty()) {
            resetSeen()
            allImages.shuffled()
        } else {
            unseen.shuffled()
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

    fun stop() {
        markCurrentSeen()
        screen = Screen.Menu
    }

    private fun markCurrentSeen() {
        val dir = folder ?: return
        val current = currentImage ?: return
        if (seen.add(current.name)) {
            SeenStore.write(dir, seen)
        }
    }

    fun next() {
        markCurrentSeen()
        if (index + 1 >= pool.size) {
            // Whole folder has now been shown -> truncate the seen file and start a fresh cycle.
            resetSeen()
            pool = allImages.shuffled()
            index = 0
        } else {
            index += 1
        }
        elapsedSeconds = 0
        isPaused = false
    }

    fun previous() {
        index = (index - 1).coerceAtLeast(0)
        elapsedSeconds = 0
        isPaused = false
    }

    /** Called once per second by the session timer. */
    fun tick() {
        elapsedSeconds += 1
        if (elapsedSeconds >= intervalSeconds) {
            next()
        }
    }
}
