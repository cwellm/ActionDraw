package de.creaflect.actiondraw.board

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Fetches the picture a link shows in a browser preview — the app's only feature that goes online,
 * and only when the user asks for it card by card. Nothing is fetched in the background, no page
 * is loaded to display, and the downloaded image is written into the board folder so the board
 * keeps working offline afterwards.
 *
 * The request is deliberately plain: a GET with a self-identifying user agent, no cookies, no
 * referrer, a size cap and short timeouts. Note that fetching does tell the site you opened the
 * link — that is the privacy cost of a preview, and why it is never automatic.
 */
object LinkPreview {
    /** Refuses anything larger; a preview is a thumbnail, not a download manager. */
    const val MAX_BYTES = 8L * 1024 * 1024

    private const val USER_AGENT = "ActionDraw/1.0 (+link preview; desktop app)"
    const val PREVIEW_DIR = "_previews"

    /** One HTTP answer, reduced to what a preview needs. */
    data class Fetched(val contentType: String, val bytes: ByteArray, val url: String)

    /** Swappable so tests can exercise the logic without a network. */
    fun interface Fetcher {
        fun get(url: String): Fetched?
    }

    /** The real thing: JDK HttpClient, redirects followed, everything else left at defaults. */
    val http: Fetcher = Fetcher { url ->
        runCatching {
            val client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build()
            val request = HttpRequest.newBuilder(URI(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,image/*;q=0.9,*/*;q=0.5")
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
            if (response.statusCode() !in 200..299) return@runCatching null
            val body = response.body() ?: return@runCatching null
            if (body.size > MAX_BYTES) return@runCatching null
            Fetched(
                contentType = response.headers().firstValue("content-type").orElse("").lowercase(),
                bytes = body,
                url = response.uri().toString(),
            )
        }.getOrNull()
    }

    /** Only http(s) is ever contacted — no file://, no other scheme sneaking in through a card. */
    fun normalize(url: String): String? {
        val candidate = if (url.contains("://")) url else "https://$url"
        val scheme = candidate.substringBefore("://").lowercase()
        if (scheme != "http" && scheme != "https") return null
        return runCatching { URI(candidate).toString() }.getOrNull()
    }

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
    fun fetchInto(root: File, url: String, name: String, fetcher: Fetcher = http): Result {
        val address = normalize(url) ?: return Result.Failed("Only http and https links can be previewed.")
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
            val target = Importer.collisionFree(File(dir, "${BoardState.sanitizeName(name).ifBlank { "link" }}.$extension"))
            target.writeBytes(image.bytes)
            Result.Saved(PREVIEW_DIR + "/" + target.name)
        }.getOrElse { Result.Failed("Couldn't save the preview from $from.") }
    }
}
