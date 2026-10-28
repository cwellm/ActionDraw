package de.creaflect.actiondraw

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.board.BoardState
import de.creaflect.actiondraw.board.ui.BoardCanvasSetting
import de.creaflect.actiondraw.board.ui.BoardHotkeys
import de.creaflect.actiondraw.board.ui.BoardsHomeSetting
import de.creaflect.actiondraw.lens.ui.LensRoomHotkeys
import de.creaflect.actiondraw.practice.PracticeState
import de.creaflect.actiondraw.sketch.SketchHotkeys
import de.creaflect.actiondraw.ui.Design
import de.creaflect.actiondraw.ui.HotkeysSheet
import de.creaflect.actiondraw.ui.Lift
import de.creaflect.actiondraw.ui.SessionHotkeys
import de.creaflect.actiondraw.ui.lampShadow

/**
 * Two quiet links in the palette's top right corner: the app's settings, and every hotkey on one
 * sheet. Both are dialogs over the palette, so the palette stays the palette. This fills the
 * screen (and draws nothing but the links) so a sheet's backdrop can cover all of it.
 */
@Composable
fun MenuExtras(app: AppState, practice: PracticeState, boards: BoardState) {
    var settings by remember { mutableStateOf(false) }
    var hotkeys by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 24.dp),
        ) {
            TextButton(onClick = { settings = true }, modifier = Modifier.testTag("menu-settings")) { Text("Settings", color = Design.colors.muted) }
            TextButton(onClick = { hotkeys = true }, modifier = Modifier.testTag("menu-hotkeys")) { Text("Hotkeys", color = Design.colors.muted) }
        }
        if (settings) MenuScrim(onDismiss = { settings = false }) { SettingsSheet(app, practice, boards) { settings = false } }
        if (hotkeys) MenuScrim(onDismiss = { hotkeys = false }) { HotkeysSheet(Hotkeys.SECTIONS) { hotkeys = false } }
    }
}

/**
 * What the app remembers between runs, in one place: each room's settings — the board's as it
 * offers them — and the app's own, the motion.
 */
@Composable
internal fun ColumnScope.SettingsSheet(app: AppState, practice: PracticeState, boards: BoardState, onClose: () -> Unit) {
    Text("Settings", style = MaterialTheme.typography.h6)

    BoardsHomeSetting(boards)

    Text("Reference folder", style = MaterialTheme.typography.caption)
    Text(
        practice.folder?.path ?: "none chosen yet",
        style = MaterialTheme.typography.body2,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
    )

    BoardCanvasSetting(boards)

    Text("Motion", style = MaterialTheme.typography.caption)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = app.reducedMotion,
            onCheckedChange = { app.setReducedMotionPreference(it) },
            modifier = Modifier.testTag("settings-reduced-motion"),
        )
        Text("Reduce motion: rooms open with a short fade, nothing spreads, ripples or slides", style = MaterialTheme.typography.body2)
    }
    Text(
        "Until it is set here, it follows Windows' “Show animations”.",
        style = MaterialTheme.typography.caption,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onClose) { Text("Done") }
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
            elevation = 0.dp,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.widthIn(max = 640.dp).padding(24.dp).lampShadow(Lift.HELD, RoundedCornerShape(8.dp)).clickable(enabled = false) {},
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

/**
 * Every hotkey on one sheet: the palette's own, and each room's, which the room keeps beside the
 * keys it handles so that the two cannot drift apart.
 */
object Hotkeys {
    val PALETTE: List<Pair<String, String>> = listOf(
        "← → ↑ ↓ · Tab" to "turn the palette to the next room",
        "Enter · click the chosen well" to "open the room",
        "Esc (in any room)" to "back up, and from a room's top back to the palette",
    )
    const val PALETTE_TITLE = "The palette"
    val SECTIONS: List<Pair<String, List<Pair<String, String>>>> = listOf(
        PALETTE_TITLE to PALETTE,
        SessionHotkeys.TITLE to SessionHotkeys.ROWS,
        BoardHotkeys.TITLE to BoardHotkeys.ROWS,
        SketchHotkeys.TITLE to SketchHotkeys.ROWS,
        LensRoomHotkeys.TITLE to LensRoomHotkeys.ROWS,
    )
}
