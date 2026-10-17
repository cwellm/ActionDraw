package de.creaflect.actiondraw.network

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** No test here opens a socket: what would be sent is caught by the [Http.fetch] seam. */
class HttpTest {
    @Test
    fun onlyHttpAddressesAreEverContacted() {
        assertEquals("https://example.com/x", Http.normalize("https://example.com/x"))
        assertEquals("http://example.com", Http.normalize("http://example.com"))
        assertEquals("https://example.com", Http.normalize("example.com"), "a bare host means https")
        assertNull(Http.normalize("file:///C:/secrets.txt"), "no local files")
        assertNull(Http.normalize("ftp://example.com/x"))
    }

    @Test
    fun anotherSchemeIsRefusedBeforeAnythingIsSent() {
        // A page's preview picture is an address the page chose, not the user: the client itself
        // must refuse what is not http(s), not rely on its caller to have checked.
        var sent = false
        val send = { _: URI -> sent = true; null }
        assertNull(Http.fetch("file:///C:/secrets.txt", send))
        assertNull(Http.fetch("FTP://example.com/x", send))
        assertFalse(sent, "nothing may go out for a file or ftp address")
    }

    @Test
    fun anHttpAddressGoesOutAsGiven() {
        val sentTo = mutableListOf<String>()
        val answer = Http.fetch("example.com/a.png") { uri ->
            sentTo += uri.toString()
            Fetched("image/png", byteArrayOf(1), uri.toString())
        }
        assertEquals(listOf("https://example.com/a.png"), sentTo)
        assertEquals("image/png", answer?.contentType)
    }

    @Test
    fun aFailingSendIsNoAnswerNotACrash() {
        assertNull(Http.fetch("https://example.com") { error("connection reset") })
    }
}
