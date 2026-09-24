package de.creaflect.actiondraw.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.Button
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag

/**
 * Focuses the field as soon as it is on screen, so typing can start without a click — and so the
 * first keystroke goes into the field rather than to whatever happened to hold focus (a dialog's
 * scrim once took a Space as a click and closed the note being written).
 */
@Composable
fun Modifier.focusOnShow(): Modifier {
    val requester = remember { FocusRequester() }
    LaunchedEffect(requester) { requester.requestFocus() }
    return focusRequester(requester)
}

/** Enter in a single-line field means "yes, that" — the same as the confirm button. */
fun Modifier.confirmOnEnter(onOk: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
        onOk()
        true
    } else {
        false
    }
}

/** A compact selectable chip: filled when selected, outlined otherwise. */
@Composable
fun SelectChip(label: String, selected: Boolean, tag: String? = null, onClick: () -> Unit) {
    val padding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    val modifier = if (tag != null) Modifier.testTag(tag) else Modifier
    if (selected) {
        Button(onClick = onClick, contentPadding = padding, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, contentPadding = padding, modifier = modifier) { Text(label) }
    }
}
