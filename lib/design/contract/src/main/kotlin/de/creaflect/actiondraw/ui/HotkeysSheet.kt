package de.creaflect.actiondraw.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Hotkeys on one sheet, a section for each room the sheet is about. */
@Composable
fun ColumnScope.HotkeysSheet(
    sections: List<Pair<String, List<Pair<String, String>>>>,
    onClose: () -> Unit,
) {
    Text(if (sections.size == 1) "${sections.single().first} hotkeys" else "Hotkeys", style = MaterialTheme.typography.h6)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        sections.forEach { (title, rows) ->
            Text(
                title,
                style = MaterialTheme.typography.subtitle2,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
            )
            rows.forEach { (keys, what) ->
                Row(Modifier.fillMaxWidth()) {
                    Text(keys, style = MaterialTheme.typography.body2, modifier = Modifier.width(230.dp))
                    Text(what, style = MaterialTheme.typography.body2, color = MaterialTheme.colors.onSurface.copy(alpha = 0.75f))
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onClose) { Text("Close") }
    }
}
