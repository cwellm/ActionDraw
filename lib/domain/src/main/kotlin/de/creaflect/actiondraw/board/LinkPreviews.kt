package de.creaflect.actiondraw.board

import java.io.File

/**
 * The picture a link shows in a browser's preview, fetched into the board — the lib's only way
 * online, and only ever because the person asked for it on one card. Fetching tells the site the
 * link was opened; that is a preview's privacy cost, and why it is never automatic.
 */
fun interface LinkPreviews {
    /**
     * Fetches [url]'s preview picture into the board in [dir], named after [title] (or the
     * site, when it has none). Blocking: call it off the UI thread.
     */
    fun fetch(dir: File, url: String, title: String): PreviewResult
}

/** What fetching a preview gave. */
sealed interface PreviewResult {
    /** Saved, at [path] in the board. */
    data class Saved(val path: String) : PreviewResult

    /** None, and [why]; [url] is the address that let it down, when there is one to name. */
    data class Failed(val why: PreviewFailure, val url: String? = null) : PreviewResult
}

/** Why there is no preview. */
enum class PreviewFailure {
    /** Only http and https links are previewed. */
    NOT_WEB,

    /** The address could not be reached. */
    UNREACHABLE,

    /** The page names no preview picture. */
    NO_PICTURE,

    /** The page names one, but it could not be fetched. */
    PICTURE_UNREACHABLE,

    /** The address is neither a page nor a picture. */
    NEITHER_PAGE_NOR_PICTURE,

    /** The preview is not a picture ActionDraw reads. */
    NOT_READABLE,

    /** It was fetched, but could not be saved. */
    NOT_SAVED,
}
