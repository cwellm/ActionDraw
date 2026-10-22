package de.creaflect.actiondraw.board

import de.creaflect.actiondraw.board.ui.Markdown
import de.creaflect.actiondraw.board.ui.Markdown.Block
import de.creaflect.actiondraw.board.ui.Markdown.Span
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The Markdown a note is allowed, parsed the way a person would read it. */
class MarkdownTest {
    // ---- Inline ----

    @Test
    fun theMarkersPeopleAlreadyTypeBecomeRuns() {
        val spans = Markdown.inline("draw **wings** and *scales*, see `foo` or [ref](https://a.b/c)")
        assertEquals(
            listOf(
                Span.Plain("draw "), Span.Bold("wings"), Span.Plain(" and "), Span.Italic("scales"),
                Span.Plain(", see "), Span.Code("foo"), Span.Plain(" or "), Span.Link("ref", "https://a.b/c"),
            ),
            spans,
        )
    }

    @Test
    fun aLineWithoutMarkupIsOnePlainRun() {
        assertEquals(listOf(Span.Plain("just words")), Markdown.inline("just words"))
        assertEquals(emptyList(), Markdown.inline(""))
    }

    @Test
    fun aLoneAsteriskIsNotAMarker() {
        assertEquals(listOf(Span.Plain("2 * 3 = 6")), Markdown.inline("2 * 3 = 6"))
    }

    // ---- Blocks ----

    @Test
    fun headingsListsRulesAndParagraphsAreToldApart() {
        val blocks = Markdown.parse(
            """
            # Flügel
            membrane folds, ¾ view
            second line of the same thought

            - bat
            - pterosaur
            1. study
            2. draw
            ---
            ## later
            """.trimIndent(),
        )
        assertEquals(6, blocks.size, blocks.toString())
        assertEquals(Block.Heading(1, listOf(Span.Plain("Flügel"))), blocks[0])
        assertIs<Block.Paragraph>(blocks[1])
        assertEquals(
            "membrane folds, ¾ view\nsecond line of the same thought",
            (blocks[1] as Block.Paragraph).text.single().text,
            "lines inside a paragraph keep their breaks — notes are written line by line",
        )
        assertEquals(Block.Bullets(listOf(listOf(Span.Plain("bat")), listOf(Span.Plain("pterosaur")))), blocks[2])
        assertEquals(Block.Numbered(listOf(listOf(Span.Plain("study")), listOf(Span.Plain("draw")))), blocks[3])
        assertEquals(Block.Rule, blocks[4])
        assertEquals(Block.Heading(2, listOf(Span.Plain("later"))), blocks[5])
    }

    @Test
    fun aBlankLineSeparatesParagraphs() {
        val blocks = Markdown.parse("one\n\ntwo")
        assertEquals(2, blocks.size)
        assertTrue(blocks.all { it is Block.Paragraph })
    }

    @Test
    fun aHeadingMayCarryInlineMarkup() {
        val heading = Markdown.parse("## the *big* one").single() as Block.Heading
        assertEquals(listOf(Span.Plain("the "), Span.Italic("big"), Span.Plain(" one")), heading.text)
    }

    @Test
    fun whatIsNotSupportedShowsAsTyped() {
        val blocks = Markdown.parse("| a | b |\n<b>x</b>\n![pic](p.png)")
        val paragraph = blocks.single() as Block.Paragraph
        assertEquals("| a | b |\n<b>x</b>\n!", paragraph.text.first().text, "tables and HTML are just text")
        assertTrue(paragraph.text.any { it is Span.Link && it.text == "pic" }, "an image reads as its link")
    }

    // ---- Plain text ----

    @Test
    fun plainTextDropsEveryMarkerButKeepsTheWords() {
        val plain = Markdown.plain("# Flügel\n- **bat** wings\n- see [ref](https://x.y)\n1. `code`\n---\nend")
        assertEquals("Flügel\nbat wings\nsee ref\n1. code\n\nend", plain)
    }

    @Test
    fun hasMarkupTellsAPlainNoteFromAFormattedOne() {
        assertFalse(Markdown.hasMarkup("just a thought"))
        assertTrue(Markdown.hasMarkup("just a **thought**"))
        assertTrue(Markdown.hasMarkup("- a list"))
        assertTrue(Markdown.hasMarkup("# title"))
    }
}
