package de.creaflect.actiondraw.board

import de.creaflect.actiondraw.io.Folders
import de.creaflect.actiondraw.network.Fetcher
import de.creaflect.actiondraw.network.Http
import java.io.File
import java.net.URI

/**
 * Fetches the picture a link shows in a browser preview — the app's only feature that goes online,
 * and only when the user asks for it card by card. Nothing is fetched in the background, no page
 * is loaded to display, and the downloaded image is written into the board folder so the board
 * keeps working offline afterwards.
 *
 * The request itself is `:core:network`'s [Http]: http and https only, a size cap, short timeouts.
 * What is left here is reading a page for its preview picture and keeping the picture. Note that
 * fetching does tell the site you opened the link — that is the privacy cost of a preview, and why
 * it is never automatic.
 */
object LinkPreview {
    const val PREVIEW_DIR = "_previews"

    /**
     * The image a page advertises for sharing: OpenGraph first, then Twitter's variant. Returned
     * absolute, resolved against [pageUrl].
     */
    fun previewImageUrl(html: String, pageUrl: String): String? {
        val head = html.take(200_000) // the tags live in <head>; do not scan a whole article
        val patterns = listOf(
            Regex("""<meta[^>]+property=["']og:image(?::url)?["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:image(?::url)?["']""", RegexOption.IGNORE_CASE),
            Regex("""<meta[^>]+name=["']twitter:image["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
        )
        val found = patterns.firstNotNullOfOrNull { it.find(head)?.groupValues?.get(1) } ?: return null
        val cleaned = found.trim().replace("&amp;", "&")
        return runCatching { URI(pageUrl).resolve(cleaned).toString() }.getOrNull()
    }

    /** File extension for an image content type; null for anything that is not an image. */
    fun extensionFor(contentType: String): String? = when {
        contentType.startsWith("image/png") -> "png"
        contentType.startsWith("image/jpeg") || contentType.startsWith("image/jpg") -> "jpg"
        contentType.startsWith("image/webp") -> "webp"
        contentType.startsWith("image/gif") -> "gif"
        contentType.startsWith("image/bmp") -> "bmp"
        contentType.startsWith("image/avif") -> "avif"
        else -> null
    }

    /** What a fetch produced: a saved file (relative to the board), or why there is none. */
    sealed class Result {
        data class Saved(val path: String) : Result()
        data class Failed(val reason: String) : Result()
    }

    /**
     * Fetches [url]'s preview image and writes it into `<root>/_previews/`. The link itself may be
     * an image, in which case it is used directly. Blocking — call off the UI thread.
     */
    fun fetchInto(root: File, url: String, name: String, fetcher: Fetcher = Http): Result {
        val address = Http.normalize(url) ?: return Result.Failed("Only http and https links can be previewed.")
        val first = fetcher.get(address) ?: return Result.Failed("Couldn't reach that address.")

        val (image, from) = when {
            extensionFor(first.contentType) != null -> first to address
            first.contentType.startsWith("text/html") -> {
                val imageUrl = previewImageUrl(String(first.bytes, Charsets.UTF_8), first.url)
                    ?: return Result.Failed("That page offers no preview picture.")
                val second = fetcher.get(imageUrl) ?: return Result.Failed("Couldn't fetch the preview picture.")
                second to imageUrl
            }

            else -> return Result.Failed("That address is neither a page nor a picture.")
        }

        val extension = extensionFor(image.contentType)
            ?: return Result.Failed("The preview is not an image ActionDraw reads.")
        return runCatching {
            val dir = File(root, PREVIEW_DIR).apply { mkdirs() }
            val target = Folders.freeFile(File(dir, "${Folders.safeName(name).ifBlank { "link" }}.$extension"))
            target.writeBytes(image.bytes)
            Result.Saved(PREVIEW_DIR + "/" + target.name)
        }.getOrElse { Result.Failed("Couldn't save the preview from $from.") }
    }
}
