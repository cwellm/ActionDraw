package de.creaflect.actiondraw.board

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Link previews are the app's only network feature, so the logic is exercised through a stubbed
 * fetcher: these tests never open a socket, and neither does anything else in the suite.
 */
class LinkPreviewTest {
    private val root: File = Files.createTempDirectory("preview").toFile()

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
    }

    private fun html(body: String) =
        LinkPreview.Fetched("text/html; charset=utf-8", body.toByteArray(), "https://example.com/page")

    private fun png(url: String = "https://example.com/p.png") =
        LinkPreview.Fetched("image/png", byteArrayOf(1, 2, 3, 4), url)

    // ---- What may be contacted at all ----

    @Test
    fun onlyHttpAddressesAreEverContacted() {
        assertEquals("https://example.com/x", LinkPreview.normalize("https://example.com/x"))
        assertEquals("http://example.com", LinkPreview.normalize("http://example.com"))
        assertEquals("https://example.com", LinkPreview.normalize("example.com"), "a bare host means https")
        assertNull(LinkPreview.normalize("file:///C:/secrets.txt"), "no local files")
        assertNull(LinkPreview.normalize("ftp://example.com/x"))
    }

    // ---- Finding the picture a page advertises ----

    @Test
    fun findsTheOpenGraphImageInEitherAttributeOrder() {
        val a = """<head><meta property="og:image" content="https://cdn.example.com/a.jpg"></head>"""
        val b = """<head><meta content="https://cdn.example.com/b.jpg" property="og:image"></head>"""
        assertEquals("https://cdn.example.com/a.jpg", LinkPreview.previewImageUrl(a, "https://example.com/p"))
        assertEquals("https://cdn.example.com/b.jpg", LinkPreview.previewImageUrl(b, "https://example.com/p"))
    }

    @Test
    fun fallsBackToTwitterAndResolvesRelativeAddresses() {
        val page = """<meta name="twitter:image" content="/img/card.png">"""
        assertEquals(
            "https://example.com/img/card.png",
            LinkPreview.previewImageUrl(page, "https://example.com/deep/page.html"),
        )
    }

    @Test
    fun aPageWithoutAPreviewPictureSaysSo() {
        assertNull(LinkPreview.previewImageUrl("<html><body>no meta here</body></html>", "https://example.com"))
    }

    // ---- Saving into the board ----

    @Test
    fun anImageLinkIsSavedStraightIntoTheBoard() {
        val result = LinkPreview.fetchInto(root, "https://example.com/p.png", "Wing photo") { png() }

        assertIs<LinkPreview.Result.Saved>(result)
        assertEquals("${LinkPreview.PREVIEW_DIR}/Wing photo.png", result.path)
        assertTrue(File(root, result.path).isFile, "the picture lives in the board folder")
    }

    @Test
    fun aPageIsFetchedThenItsPicture() {
        val asked = mutableListOf<String>()
        val fetcher = LinkPreview.Fetcher { url ->
            asked += url
            when {
                url.endsWith("/page") -> html("""<meta property="og:image" content="https://cdn.example.com/c.jpg">""")
                else -> LinkPreview.Fetched("image/jpeg", byteArrayOf(9), url)
            }
        }

        val result = LinkPreview.fetchInto(root, "https://example.com/page", "Article", fetcher)

        assertIs<LinkPreview.Result.Saved>(result)
        assertTrue(result.path.endsWith(".jpg"))
        assertEquals(listOf("https://example.com/page", "https://cdn.example.com/c.jpg"), asked)
    }

    @Test
    fun anUnreachableAddressIsReportedNotThrown() {
        val result = LinkPreview.fetchInto(root, "https://example.com/x", "x") { null }
        assertIs<LinkPreview.Result.Failed>(result)
        assertTrue(result.reason.contains("reach"), "got: ${result.reason}")
    }

    @Test
    fun somethingThatIsNeitherPageNorPictureIsRefused() {
        val pdf = LinkPreview.Fetched("application/pdf", byteArrayOf(1), "https://example.com/x.pdf")
        val result = LinkPreview.fetchInto(root, "https://example.com/x.pdf", "doc") { pdf }
        assertIs<LinkPreview.Result.Failed>(result)
    }

    @Test
    fun aLocalFileLinkIsNeverFetched() {
        var called = false
        val result = LinkPreview.fetchInto(root, "file:///C:/secrets.txt", "nope") { called = true; null }
        assertIs<LinkPreview.Result.Failed>(result)
        assertTrue(!called, "the fetcher must not even be asked")
    }

    @Test
    fun twoPreviewsWithTheSameNameDoNotOverwriteEachOther() {
        LinkPreview.fetchInto(root, "https://example.com/p.png", "same") { png() }
        val second = LinkPreview.fetchInto(root, "https://example.com/p.png", "same") { png() }
        assertIs<LinkPreview.Result.Saved>(second)
        assertEquals("${LinkPreview.PREVIEW_DIR}/same (2).png", second.path)
    }
}
