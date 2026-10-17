package de.creaflect.actiondraw.network

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** One HTTP answer, reduced to what the app needs of it. */
data class Fetched(val contentType: String, val bytes: ByteArray, val url: String)

/** Fetches one address. Swappable, so everything above it can be tested without a network. */
fun interface Fetcher {
    fun get(url: String): Fetched?
}

/**
 * The app's one way online: a plain GET with a self-identifying user agent, no cookies, no
 * referrer, a size cap and short timeouts, redirects followed. Only http and https are ever
 * contacted — an address naming anything else, a local file included, is refused before anything
 * is sent, whoever handed it over. Blocking: call it off the UI thread.
 */
object Http : Fetcher {
    /** Refuses anything larger: the app fetches previews, not downloads. */
    const val MAX_BYTES = 8L * 1024 * 1024

    private const val USER_AGENT = "ActionDraw/1.0 (+link preview; desktop app)"

    /** [url] as an http or https address — a bare host means https — or null for any other scheme. */
    fun normalize(url: String): String? {
        val candidate = if (url.contains("://")) url else "https://$url"
        val scheme = candidate.substringBefore("://").lowercase()
        if (scheme != "http" && scheme != "https") return null
        return runCatching { URI(candidate).toString() }.getOrNull()
    }

    override fun get(url: String): Fetched? = fetch(url, ::send)

    /** [get], with the sending handed in: what a test needs to see that nothing went out. */
    internal fun fetch(url: String, send: (URI) -> Fetched?): Fetched? {
        val address = normalize(url) ?: return null
        return runCatching { send(URI(address)) }.getOrNull()
    }

    private fun send(uri: URI): Fetched? {
        val client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build()
        val request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,image/*;q=0.9,*/*;q=0.5")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() !in 200..299) return null
        val body = response.body() ?: return null
        if (body.size > MAX_BYTES) return null
        return Fetched(
            contentType = response.headers().firstValue("content-type").orElse("").lowercase(),
            bytes = body,
            url = response.uri().toString(),
        )
    }
}
