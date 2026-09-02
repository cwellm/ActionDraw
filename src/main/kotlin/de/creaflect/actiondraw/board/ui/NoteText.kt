package de.creaflect.actiondraw.board.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * The little formatting notes are allowed: `**bold**` and `*italic*`. Deliberately not a rich-text
 * editor — the note stays plain text in the sidecar, readable in any editor, and the markers are
 * the ones people already type (decision D4: plain text first, formatting later, but not a
 * document model).
 */
object NoteText {
    private val PATTERN = Regex("""\*\*(.+?)\*\*|\*(.+?)\*""", RegexOption.DOT_MATCHES_ALL)

    fun format(text: String): AnnotatedString = buildAnnotatedString {
        var cursor = 0
        for (match in PATTERN.findAll(text)) {
            append(text.substring(cursor, match.range.first))
            val bold = match.groups[1]
            val italic = match.groups[2]
            val style =
                if (bold != null) SpanStyle(fontWeight = FontWeight.Bold)
                else SpanStyle(fontStyle = FontStyle.Italic)
            pushStyle(style)
            append((bold ?: italic)!!.value)
            pop()
            cursor = match.range.last + 1
        }
        append(text.substring(cursor))
    }

    /** The text without its markers — for search, tooltips and anywhere plain text is wanted. */
    fun plain(text: String): String = text.replace(PATTERN) { match ->
        (match.groups[1] ?: match.groups[2])!!.value
    }
}
