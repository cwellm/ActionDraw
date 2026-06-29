package de.creaflect.actiondraw.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.Button
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** A compact selectable chip: filled when selected, outlined otherwise. */
@Composable
fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val padding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    if (selected) {
        Button(onClick = onClick, contentPadding = padding) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, contentPadding = padding) { Text(label) }
    }
}
