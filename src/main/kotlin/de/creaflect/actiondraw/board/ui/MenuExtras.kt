package de.creaflect.actiondraw.board.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.AppState
import de.creaflect.actiondraw.board.BoardState
import de.creaflect.actiondraw.ui.chooseFolder

/**
 * Two quiet links under the menu's big buttons: the app's settings, and every hotkey on one
 * sheet. Both are dialogs over the menu, so the menu stays the menu.
 */
@Composable
fun MenuExtras(app: AppState, boards: BoardState) {
    var settings by remember { mutableStateOf(false) }
    var hotkeys by remember { mutableStateOf(false) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    ) {
        OutlinedButton(onClick = { settings = true }, modifier = Modifier.weight(1f).testTag("menu-settings")) { Text("Settings") }
        OutlinedButton(onClick = { hotkeys = true }, modifier = Modifier.weight(1f).testTag("menu-hotkeys")) { Text("Hotkeys") }
    }
    if (settings) MenuScrim(onDismiss = { settings = false }) { SettingsSheet(app, boards) { settings = false } }
    if (hotkeys) MenuScrim(onDismiss = { hotkeys = false }) { HotkeysSheet { hotkeys = false } }
}

/** What the app remembers between runs, in one place. [app] is null when opened from a board. */
@Composable
internal fun ColumnScope.SettingsSheet(app: AppState?, boards: BoardState, onClose: () -> Unit) {
    // From a board ([app] null) only what pertains to boards is shown, under its own title.
    Text(if (app == null) "Board settings" else "Settings", style = MaterialTheme.typography.h6)
    if (app == null) {
        Text(
            "What concerns boards. The rest is under Settings on the start menu.",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
        )
    }

    Text("Boards home", style = MaterialTheme.typography.caption)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            boards.boardsHome().path,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = {
            chooseFolder(boards.boardsHome().takeIf { it.isDirectory }, "Boards home")?.let { boards.setBoardsHomeDir(it) }
        }) { Text("Change…") }
    }
    Text(
        "Where new boards are made. Boards you already have stay where they are.",
        style = MaterialTheme.typography.caption,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
    )

    if (app != null) {
        Text("Reference folder", style = MaterialTheme.typography.caption)
        Text(
            app.folder?.path ?: "none chosen yet",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
        )
    }

    Text("Board canvas", style = MaterialTheme.typography.caption)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = boards.snapping,
            onCheckedChange = { boards.setSnappingPreference(it) },
            modifier = Modifier.testTag("settings-snap"),
        )
        Text("Snap dragged cards to their neighbours' centre lines", style = MaterialTheme.typography.body2)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onClose) { Text("Done") }
    }
}

/** Every hotkey, session and board, on one sheet. */
@Composable
internal fun ColumnScope.HotkeysSheet(
    sections: List<Pair<String, List<Pair<String, String>>>> = Hotkeys.SECTIONS,
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

/** A dimmed backdrop with a card in the middle; a click outside closes it. */
@Composable
private fun MenuScrim(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            elevation = 12.dp,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.widthIn(max = 640.dp).padding(24.dp).clickable(enabled = false) {},
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

/** The one list both hotkey sheets draw from, so they cannot drift apart. */
object Hotkeys {
    val SESSION: List<Pair<String, String>> = listOf(
        "Space" to "play / pause",
        "← / →" to "previous / next picture",
        "1 – 9" to "view mode (None … Notan)",
        "N" to "Notan view",
        ", / . / 0" to "cooler light · warmer light · neutral",
        "H" to "hide the reference · peek while drawing from memory",
        "B / I / D / M / U" to "blur / invert / defraction / mirror / upside down",
        "G" to "cycle the proportion grid",
        "R" to "toggle the redo flag",
        "A" to "toggle auto-advance",
        "F" to "toggle fullscreen",
        "Esc" to "leave fullscreen · stop the session",
    )
    val BOARD: List<Pair<String, String>> = listOf(
        "Click · Ctrl+click · Shift+click" to "select · toggle · range",
        "Ctrl+A · Ctrl+C · Ctrl+V" to "select all · copy · paste",
        "← → ↑ ↓" to "move focus (grid) · nudge the selection (free)",
        "Ctrl+↑ / ↓ (+Shift)" to "reorder one step (all the way)",
        "Space" to "view the selection large",
        "wheel · + / − · 0" to "zoom the large view · fit",
        "← → · Home / End" to "flip · first / last, in the large view",
        "Enter" to "draw the selection",
        "N · L · G" to "new note · new link · group the selection",
        "Ctrl+Shift+G · Ctrl+D" to "ungroup · contents drawer",
        "S · T · P · F2" to "star · tags · palette · caption",
        "Del" to "remove the card (the file stays)",
        "F · Esc" to "immersive · leave immersive / close",
        "Shift+drag" to "rubber-band select (free)",
    )
    const val SESSION_TITLE = "Drawing session"
    const val BOARD_TITLE = "Idea Board"
    val SECTIONS: List<Pair<String, List<Pair<String, String>>>> = listOf(SESSION_TITLE to SESSION, BOARD_TITLE to BOARD)
}
