package de.creaflect.actiondraw.io

import java.io.File

/**
 * Where the app keeps its files. Its own live in `~/.actiondraw` — the settings, the board and
 * concept registries, the thumbnail cache, the diagnostics — and the user's boards, concepts and
 * sketches start out in homes beside it until a home of their own is chosen. This is the one
 * place that knows; anything that needs one of these folders asks here.
 *
 * [userHome] is injectable so a test can stand the whole layout up in a temp folder.
 */
class AppDirs(val userHome: File = File(System.getProperty("user.home") ?: ".")) {
    /** `~/.actiondraw`: what belongs to the installation rather than to a board or a picture. */
    val config: File get() = File(userHome, ".actiondraw")

    /** Downscaled previews, kept so a board of large photos opens fast the second time. */
    val thumbnails: File get() = File(config, "thumbs")

    /** Every pointer event the canvas sees, when `ACTIONDRAW_POINTER_LOG` is set. */
    val pointerLog: File get() = File(config, "pointer.log")

    /** The pen's raw samples, while the Pen panel records them. */
    val penSamples: File get() = File(config, "pen-samples.csv")

    /** Where boards go until the user picks a home for them; need not exist yet. */
    val defaultBoardsHome: File get() = File(userHome, "ActionDraw Boards")

    /** Where concepts go until the user picks a home for them; need not exist yet. */
    val defaultConceptsHome: File get() = File(userHome, "ActionDraw Concepts")

    /** Where sketches are saved until the user picks a home for them; need not exist yet. */
    val defaultSketchesHome: File get() = File(userHome, "ActionDraw Sketches")

    companion object {
        /** The folders of the user running the app. */
        val current: AppDirs = AppDirs()
    }
}
