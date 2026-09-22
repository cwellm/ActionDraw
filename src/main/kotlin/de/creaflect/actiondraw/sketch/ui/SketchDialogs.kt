package de.creaflect.actiondraw.sketch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.sketch.SketchEditor
import de.creaflect.actiondraw.sketch.SketchState
import de.creaflect.actiondraw.ui.SelectChip
import de.creaflect.actiondraw.ui.chooseFolder
import de.creaflect.actiondraw.ui.chooseSketchFile
import de.creaflect.sketch.SketchDocument
import de.creaflect.actiondraw.ui.confirmOnEnter
import de.creaflect.actiondraw.ui.focusOnShow
import de.creaflect.sketch.PageSize
import java.io.File

/** Renders whichever Live Sketch dialog is open — mounted at app level, above every screen. */
@Composable
fun SketchDialogs(state: SketchState) {
    when (val editor = state.editor) {
        null -> Unit
        SketchEditor.NewSketch -> NewSketchDialog(state)
        SketchEditor.SaveAs -> SaveAsDialog(state)
        SketchEditor.ToBoard -> PickDialog(
            state,
            title = "Onto which board?",
            note = "The sketch is saved into the board's folder — the PNG as a card, the .sketch.json beside it to continue later.",
            load = { state.boards().map { (name, dir) -> name to { state.saveToBoard(dir) } } },
            tagPrefix = "to-board-",
        )
        SketchEditor.ToConcept -> PickDialog(
            state,
            title = "Into which concept?",
            note = "The sketch is saved into the concept's folder and becomes one of its pictures, on every board that links it.",
            load = { state.concepts().map { ref -> ref.name to { state.saveToConcept(ref.id) } } },
            tagPrefix = "to-concept-",
        )
        SketchEditor.Open -> OpenDialog(state)
        SketchEditor.Colour -> Scrim(onDismiss = state::closeEditor) {
            Text("Colour", style = MaterialTheme.typography.h6)
            ColorPicker(current = state.brush.color, recent = state.recentColors, onPick = state::setColor)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = state::closeEditor) { Text("Done") }
            }
        }
        is SketchEditor.Confirm -> Scrim(onDismiss = state::closeEditor) {
            Text("Unsaved strokes", style = MaterialTheme.typography.h6)
            Text("“${state.title}” has strokes that are not saved yet.", style = MaterialTheme.typography.body2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = state::closeEditor) { Text("Keep sketching") }
                OutlinedButton(onClick = { state.closeEditor(); state.save() }) { Text("Save first") }
                Button(onClick = { state.closeEditor(); editor.then() }, modifier = Modifier.testTag("sketch-discard")) { Text("Discard them") }
            }
        }
    }
}

/** A page size — paper at a resolution, or width × height in pixels — and its colour. */
@Composable
private fun NewSketchDialog(state: SketchState) {
    var chosen by remember { mutableStateOf<PageSize?>(PageSize.a4(150)) }
    var landscape by remember { mutableStateOf(false) }
    var customW by remember { mutableStateOf("1600") }
    var customH by remember { mutableStateOf("1200") }
    var paper by remember { mutableStateOf(PAPERS.first().second) }
    fun create() {
        val size = chosen?.let { if (landscape) it.landscape else it }
            ?: PageSize.pixels(customW.trim().toIntOrNull()?.coerceIn(64, 12000) ?: 1600, customH.trim().toIntOrNull()?.coerceIn(64, 12000) ?: 1200)
        state.closeEditor()
        state.newSketch(size, paper)
    }
    Scrim(onDismiss = state::closeEditor) {
        Text("New sketch", style = MaterialTheme.typography.h6)
        Text("Paper", style = MaterialTheme.typography.caption)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PageSize.STANDARD.take(3).forEach { size -> SelectChip(size.name, chosen?.name == size.name) { chosen = size } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PageSize.STANDARD.drop(3).forEach { size -> SelectChip(size.name, chosen?.name == size.name) { chosen = size } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SelectChip("Portrait", chosen != null && !landscape) { landscape = false }
            SelectChip("Landscape", chosen != null && landscape) { landscape = true }
        }
        Text("Or pixels", style = MaterialTheme.typography.caption)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = customW, onValueChange = { customW = it; chosen = null }, label = { Text("Width") }, singleLine = true, modifier = Modifier.weight(1f).testTag("sketch-width"))
            Text("×")
            OutlinedTextField(value = customH, onValueChange = { customH = it; chosen = null }, label = { Text("Height") }, singleLine = true, modifier = Modifier.weight(1f).confirmOnEnter(::create))
        }
        Text("Colour of the paper", style = MaterialTheme.typography.caption)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PAPERS.forEach { (name, argb) -> SelectChip(name, paper == argb) { paper = argb } }
        }
        Text(
            chosen?.let { s -> val p = if (landscape) s.landscape else s; "${p.width} × ${p.height} px at ${p.dpi} dpi" } ?: "custom size, 96 dpi",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
        )
        Buttons("Create", onOk = ::create, onCancel = state::closeEditor, tag = "sketch-create")
    }
}

private val PAPERS = listOf(
    "White" to 0xFFFFFFFF.toInt(),
    "Cream" to 0xFFFBF5E4.toInt(),
    "Grey" to 0xFFD9D9D6.toInt(),
    "Toned" to 0xFFC9B99A.toInt(),
)

/** A name and a folder; Enter saves. */
@Composable
private fun SaveAsDialog(state: SketchState) {
    // A dated name to start from, so Ctrl+S then Enter is all a first save takes.
    var name by remember { mutableStateOf(state.suggestedName()) }
    var dir by remember { mutableStateOf(state.file?.parentFile ?: state.sketchesHome()) }
    var error by remember { mutableStateOf<String?>(null) }
    fun save() {
        error = state.saveAs(name, dir)
        if (error == null) state.closeEditor()
    }
    Scrim(onDismiss = state::closeEditor) {
        Text("Save sketch", style = MaterialTheme.typography.h6)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusOnShow().confirmOnEnter(::save).testTag("sketch-name"),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(dir.path, style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { chooseFolder(dir.takeIf { it.isDirectory }, "Where to save")?.let { dir = it } }) { Text("Change…") }
        }
        Text("A PNG of the picture and a .sketch.json of the strokes, to continue later.", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
        error?.let { Text(it, color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption) }
        Buttons("Save", onOk = ::save, onCancel = state::closeEditor, tag = "sketch-save-confirm")
    }
}

/** One choice from a list; picking it is the action. The list is read once, when the dialog opens. */
@Composable
private fun PickDialog(state: SketchState, title: String, note: String, load: () -> List<Pair<String, () -> String>>, tagPrefix: String) {
    val choices = remember { load() }
    var result by remember { mutableStateOf<String?>(null) }
    Scrim(onDismiss = state::closeEditor) {
        Text(title, style = MaterialTheme.typography.h6)
        Text(note, style = MaterialTheme.typography.body2, color = MaterialTheme.colors.onSurface.copy(alpha = 0.75f))
        if (choices.isEmpty()) Text("None yet.", style = MaterialTheme.typography.caption)
        Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            choices.forEach { (name, act) ->
                Text(
                    name,
                    style = MaterialTheme.typography.body1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { result = act() }
                        .testTag(tagPrefix + name)
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                )
            }
        }
        result?.let { Text(it, style = MaterialTheme.typography.caption, color = MaterialTheme.colors.secondary) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = state::closeEditor) { Text(if (result == null) "Cancel" else "Done") }
        }
    }
}

/** Recent sketches — saved or opened anywhere, and all in the sketches home — or browse for one. */
@Composable
private fun OpenDialog(state: SketchState) {
    val recent = remember { state.recentSketches().take(12) }
    fun openThis(file: File) {
        state.closeEditor()
        state.open(file)
    }
    Scrim(onDismiss = state::closeEditor) {
        Text("Open sketch", style = MaterialTheme.typography.h6)
        if (recent.isEmpty()) Text("No sketches saved yet.", style = MaterialTheme.typography.caption)
        Column(Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
            recent.forEach { file ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openThis(file) }
                        .testTag("open-" + file.name.removeSuffix(SketchDocument.FILE_SUFFIX))
                        .padding(vertical = 5.dp, horizontal = 4.dp),
                ) {
                    Text(file.name.removeSuffix(SketchDocument.FILE_SUFFIX), style = MaterialTheme.typography.body1, modifier = Modifier.weight(1f))
                    Text(file.parentFile?.name.orEmpty(), style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.55f))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { chooseSketchFile(state.sketchesHome())?.let(::openThis) }) { Text("Browse…") }
            OutlinedButton(onClick = state::closeEditor) { Text("Cancel") }
        }
    }
}

@Composable
private fun ColumnScope.Buttons(confirm: String, onOk: () -> Unit, onCancel: () -> Unit, tag: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onCancel) { Text("Cancel") }
        Button(onClick = onOk, modifier = Modifier.testTag(tag)) { Text(confirm) }
    }
}

@Composable
private fun Scrim(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    // Pointer-only dismissal, as every dialog in the app: a clickable here would take focus.
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)).pointerInput(onDismiss) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            elevation = 12.dp,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.widthIn(min = 380.dp, max = 620.dp).padding(24.dp).pointerInput(Unit) { detectTapGestures { } },
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

