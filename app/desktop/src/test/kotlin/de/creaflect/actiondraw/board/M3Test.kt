package de.creaflect.actiondraw.board

import androidx.compose.ui.graphics.Color
import de.creaflect.actiondraw.board.ui.Markdown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase-3 pieces that are pure enough to test on their own: note markup, a palette's colours in
 * words, a sheet's name. Reading the colours and drawing the sheet are the lib's (`PicturesTest`).
 */
class M3Test {
    // ---- Note markup ----

    @Test
    fun noteMarkupTurnsIntoBoldAndItalicSpans() {
        val formatted = Markdown.annotate(Markdown.inline("draw **wings** and *scales*"), Color.Unspecified) {}
        assertEquals("draw wings and scales", formatted.text, "markers are not shown")
        assertEquals(2, formatted.spanStyles.size, "one span per marked run")
    }

    @Test
    fun noteMarkupLeavesPlainTextAlone() {
        val text = "just a plain note"
        val rendered = Markdown.annotate(Markdown.inline(text), Color.Unspecified) {}
        assertEquals(text, rendered.text)
        assertTrue(rendered.spanStyles.isEmpty())
    }

    @Test
    fun plainStripsTheMarkers() {
        assertEquals("wings and scales", Markdown.plain("**wings** and *scales*"))
    }

    // ---- Palette ----

    @Test
    fun hexIsPrintable() {
        assertEquals("#cc2020", paletteHex(0xCC2020))
    }

    // ---- Contact sheet ----

    @Test
    fun suggestedNameIsFileSystemSafe() {
        assertEquals("Drachen_ Buch-contact-sheet.png", BoardState.contactSheetName("Drachen: Buch"))
    }
}
