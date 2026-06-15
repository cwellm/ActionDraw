package de.creaflect.actiondraw.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.AppState
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager
import kotlin.math.roundToInt

@Composable
fun MenuScreen(state: AppState) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Text("ActionDraw", style = MaterialTheme.typography.h3)
        Text("Timed reference practice for drawing", style = MaterialTheme.typography.subtitle1)

        Button(onClick = { chooseFolder()?.let { state.selectFolder(it) } }) {
            Text("Select folder…")
        }

        state.folder?.let { dir ->
            Text(dir.absolutePath, style = MaterialTheme.typography.body2)
            Text("${state.unseenCount} unseen of ${state.totalCount} images")
        }

        IntervalSelector(
            seconds = state.intervalSeconds,
            onChange = { state.intervalSeconds = it },
        )

        Button(onClick = { state.start() }, enabled = state.totalCount > 0) {
            Text("Start")
        }
    }
}

/** Slider over 30s..10min in 30s steps, with a mm:ss label. */
@Composable
fun IntervalSelector(seconds: Int, onChange: (Int) -> Unit, enabled: Boolean = true) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Time per image: ${formatTime(seconds)}")
        Slider(
            value = (seconds / 30).toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(1, 20) * 30) },
            valueRange = 1f..20f,
            steps = 18, // 20 discrete stops (30s .. 600s)
            enabled = enabled,
            modifier = Modifier.width(360.dp),
        )
    }
}

fun formatTime(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}

private fun chooseFolder(): File? {
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "Select image folder"
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}
