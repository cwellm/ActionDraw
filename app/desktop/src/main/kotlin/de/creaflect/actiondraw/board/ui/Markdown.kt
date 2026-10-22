package de.creaflect.actiondraw.board.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * The Markdown a note is allowed: `# heading`, `## sub-heading`, `**bold**`, `*italic*`,
 * `` `code` ``, `[text](url)` links that open in the browser, `-` and `1.` lists, `---` rules,
 * and blank lines between paragraphs. Nothing more — tables, images and HTML show as typed.
 *
 * Deliberately a small hand-written renderer rather than a library: the target is a card, not a
 * page, and the note stays plain text in the sidecar (decision D4), readable in any editor. The
 * same renderer draws Concepts' documents, which is why it knows nothing about notes.
 */
object Markdown {
    /** One block of a document. */
    sealed class Block {
        data class Heading(val level: Int, val text: List<Span>) : Block()
        data class Paragraph(val text: List<Span>) : Block()
        data class Bullets(val items: List<List<Span>>) : Block()
        data class Numbered(val items: List<List<Span>>) : Block()
        data object Rule : Block()
    }

    /** One run of inline text. */
    sealed class Span {
        abstract val text: String

        data class Plain(override val text: String) : Span()
        data class Bold(override val text: String) : Span()
        data class Italic(override val text: String) : Span()
        data class Code(override val text: String) : Span()
        data class Link(override val text: String, val url: String) : Span()
    }

    private val INLINE = Regex(
        """\*\*(.+?)\*\*|\*(.+?)\*|`([^`]+)`|\[([^\]]+)]\(([^)\s]+)\)""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val HEADING = Regex("""^(#{1,2})\s+(.*)$""")
    private val BULLET = Regex("""^[-*]\s+(.*)$""")
    private val NUMBERED = Regex("""^\d+[.)]\s+(.*)$""")
    private val RULE = Regex("""^-{3,}$""")

    // ---- Parsing ----

    fun parse(text: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val paragraph = mutableListOf<String>()
        val bullets = mutableListOf<List<Span>>()
        val numbered = mutableListOf<List<Span>>()

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                // Notes are written line by line; a line break inside a paragraph is kept.
                blocks += Block.Paragraph(inline(paragraph.joinToString("\n")))
                paragraph.clear()
            }
        }
        fun flushLists() {
            if (bullets.isNotEmpty()) { blocks += Block.Bullets(bullets.toList()); bullets.clear() }
            if (numbered.isNotEmpty()) { blocks += Block.Numbered(numbered.toList()); numbered.clear() }
        }
        fun flushAll() { flushParagraph(); flushLists() }

        for (raw in text.lines()) {
            val line = raw.trimEnd()
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> flushAll()
                RULE.matches(trimmed) -> { flushAll(); blocks += Block.Rule }
                HEADING.matches(trimmed) -> {
                    flushAll()
                    val m = HEADING.find(trimmed)!!
                    blocks += Block.Heading(m.groupValues[1].length, inline(m.groupValues[2]))
                }
                BULLET.matches(trimmed) -> {
                    flushParagraph()
                    if (numbered.isNotEmpty()) flushLists()
                    bullets += inline(BULLET.find(trimmed)!!.groupValues[1])
                }
                NUMBERED.matches(trimmed) -> {
                    flushParagraph()
                    if (bullets.isNotEmpty()) flushLists()
                    numbered += inline(NUMBERED.find(trimmed)!!.groupValues[1])
                }
                else -> { flushLists(); paragraph += line }
            }
        }
        flushAll()
        return blocks
    }

    /** Splits one line into its runs: plain text, and the markers people already type. */
    fun inline(text: String): List<Span> {
        val spans = mutableListOf<Span>()
        var cursor = 0
        for (m in INLINE.findAll(text)) {
            if (m.range.first > cursor) spans += Span.Plain(text.substring(cursor, m.range.first))
            val g = m.groups
            spans += when {
                g[1] != null -> Span.Bold(g[1]!!.value)
                g[2] != null -> Span.Italic(g[2]!!.value)
                g[3] != null -> Span.Code(g[3]!!.value)
                else -> Span.Link(g[4]!!.value, g[5]!!.value)
            }
            cursor = m.range.last + 1
        }
        if (cursor < text.length) spans += Span.Plain(text.substring(cursor))
        return spans
    }

    /** The document without its markers — for search, tooltips, and anywhere plain text is wanted. */
    fun plain(text: String): String = parse(text).joinToString("\n") { block ->
        when (block) {
            is Block.Heading -> block.text.joinToString("") { it.text }
            is Block.Paragraph -> block.text.joinToString("") { it.text }
            is Block.Bullets -> block.items.joinToString("\n") { item -> item.joinToString("") { it.text } }
            is Block.Numbered -> block.items.withIndex().joinToString("\n") { (i, item) ->
                "${i + 1}. " + item.joinToString("") { it.text }
            }
            Block.Rule -> ""
        }
    }.trim()

    /** True when the text uses any of the markup — a note that is just words needs no renderer. */
    fun hasMarkup(text: String): Boolean =
        INLINE.containsMatchIn(text) ||
            text.lines().any { l -> l.trim().let { HEADING.matches(it) || BULLET.matches(it) || NUMBERED.matches(it) || RULE.matches(it) } }

    // ---- Rendering ----

    /** Inline runs as one styled string; links carry a [LinkAnnotation] that calls [onLink]. */
    fun annotate(spans: List<Span>, linkColor: Color, onLink: (String) -> Unit): AnnotatedString =
        buildAnnotatedString {
            for (span in spans) {
                when (span) {
                    is Span.Plain -> append(span.text)
                    is Span.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) }
                    is Span.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(span.text) }
                    is Span.Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(span.text) }
                    is Span.Link -> withLink(
                        LinkAnnotation.Url(
                            span.url,
                            TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                        ) { onLink(span.url) },
                    ) { append(span.text) }
                }
            }
        }

    /**
     * Draws [text] as blocks. [style] is the paragraph style; headings scale from it so a card
     * and a page can both use the renderer with their own base size.
     */
    @Composable
    fun Rendered(
        text: String,
        style: TextStyle,
        color: Color,
        onLink: (String) -> Unit,
        modifier: Modifier = Modifier,
        linkColor: Color = MaterialTheme.colors.primary,
    ) {
        val blocks = parse(text)
        Column(modifier) {
            blocks.forEachIndexed { index, block ->
                if (index > 0) Spacer(Modifier.height(if (block is Block.Rule) 2.dp else 4.dp))
                when (block) {
                    is Block.Heading -> Text(
                        annotate(block.text, linkColor, onLink),
                        style = style.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = style.fontSize * if (block.level == 1) 1.45f else 1.2f,
                        ),
                        color = color,
                    )
                    is Block.Paragraph -> Text(annotate(block.text, linkColor, onLink), style = style, color = color)
                    is Block.Bullets -> block.items.forEach { item ->
                        ListLine("•", annotate(item, linkColor, onLink), style, color)
                    }
                    is Block.Numbered -> block.items.forEachIndexed { i, item ->
                        ListLine("${i + 1}.", annotate(item, linkColor, onLink), style, color)
                    }
                    Block.Rule -> Divider(color = color.copy(alpha = 0.3f), thickness = 1.dp)
                }
            }
        }
    }

    @Composable
    private fun ListLine(marker: String, text: AnnotatedString, style: TextStyle, color: Color) {
        Row(Modifier.fillMaxWidth()) {
            Text(marker, style = style, color = color.copy(alpha = 0.7f), modifier = Modifier.width(18.dp))
            Text(text, style = style, color = color)
        }
    }
}
