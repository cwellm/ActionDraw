package de.creaflect.actiondraw.concept.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.creaflect.actiondraw.board.ImageItem
import de.creaflect.actiondraw.board.LinkItem
import de.creaflect.actiondraw.board.NoteItem
import de.creaflect.actiondraw.board.NoteKind
import de.creaflect.actiondraw.board.ui.Markdown
import de.creaflect.actiondraw.concept.ConceptEditor
import de.creaflect.actiondraw.concept.ConceptEntry
import de.creaflect.actiondraw.concept.ConceptKinds
import de.creaflect.actiondraw.concept.ConceptState
import de.creaflect.actiondraw.concept.ConceptStore
import de.creaflect.actiondraw.image.ThumbCache
import de.creaflect.actiondraw.ui.SelectChip
import de.creaflect.actiondraw.ui.chooseFolder
import de.creaflect.actiondraw.ui.chooseImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.net.URI
import androidx.compose.runtime.LaunchedEffect
import de.creaflect.actiondraw.board.BoardLink
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import de.creaflect.actiondraw.board.BoardLayouts
import de.creaflect.actiondraw.ui.confirmOnEnter
import de.creaflect.actiondraw.ui.focusOnShow

/** The Concepts entry on the menu, equal in weight to Draw and Boards. */
@Composable
fun RowScope.ConceptMenuButton(state: ConceptState) {
    Button(
        onClick = { state.openList() },
        modifier = Modifier.weight(1f).height(56.dp).testTag("menu-concepts"),
    ) {
        Text("Concepts", style = MaterialTheme.typography.h6)
    }
}

// ---------------- The list ----------------

private data class ConceptSummary(
    val entry: ConceptEntry,
    val pictures: Int,
    val documents: Int,
    val cover: File?,
    /** How many boards link this concept. */
    val boards: Int = 0,
)

/** Every concept, grouped by kind, each with a cover and its counts. */
@Composable
fun ConceptListScreen(state: ConceptState, thumbs: ThumbCache) {
    val concepts by produceState(initialValue = emptyList<ConceptSummary>(), state.listTick) {
        value = withContext(Dispatchers.IO) {
            state.availableConcepts().map { entry ->
                val file = ConceptStore.peek(entry.dir)
                val images = file?.items.orEmpty().filterIsInstance<ImageItem>()
                ConceptSummary(
                    entry = entry,
                    pictures = images.size,
                    documents = file?.documents.orEmpty().size,
                    cover = images.firstOrNull()?.let { File(entry.dir, it.path) }?.takeIf { it.isFile },
                    boards = state.boardsFor(entry.id).count { it.linked },
                )
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Concepts", style = MaterialTheme.typography.h4, color = MaterialTheme.colors.primary)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { state.leaveList() }) { Text("Back") }
        }
        Text(
            "A thing that lives once — a character, a creature, a landscape — and is linked onto any board.",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
        ) {
            Text(
                "Home: ${state.conceptsHome().path}",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = {
                chooseFolder(state.conceptsHome().takeIf { it.isDirectory }, "Concepts home")?.let { state.setConceptsHomeDir(it) }
            }) { Text("Change home…") }
            Button(onClick = { state.openEditor(ConceptEditor.NewConcept) }, modifier = Modifier.testTag("new-concept")) {
                Text("New concept…")
            }
        }
        if (state.openFailed) {
            Text("Couldn't read that concept file (and no usable backup).", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.error)
        }
        state.notice?.let { Text(it, style = MaterialTheme.typography.caption, color = MaterialTheme.colors.error) }

        if (concepts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No concepts yet — make one for the thing you keep drawing.",
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 200.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                concepts.groupBy { it.entry.kind.ifBlank { "unsorted" } }.forEach { (kind, ofKind) ->
                    item(key = "kind-$kind", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            kind.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.subtitle1,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colors.secondary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(ofKind, key = { it.entry.id }) { summary ->
                        ConceptTile(summary, thumbs, onOpen = { state.openConcept(summary.entry.dir) }, onDelete = {
                            state.openEditor(ConceptEditor.DeleteConcept(summary.entry.dir, summary.entry.name))
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun ConceptTile(summary: ConceptSummary, thumbs: ThumbCache, onOpen: () -> Unit, onDelete: () -> Unit) {
    val cover: ImageBitmap? by produceState<ImageBitmap?>(null, summary.cover) {
        value = summary.cover?.let { withContext(Dispatchers.IO) { thumbs.load(it, maxSize = 400) } }
    }
    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colors.surface)
            .clickable { onOpen() }
            .testTag("concept-" + summary.entry.name),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.4f).background(Color(0x22000000)), contentAlignment = Alignment.Center) {
            val bmp = cover
            if (bmp != null) {
                Image(bitmap = bmp, contentDescription = summary.entry.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text("no picture yet", color = Color.White.copy(alpha = 0.35f), style = MaterialTheme.typography.caption)
            }
        }
        Text(
            summary.entry.name,
            style = MaterialTheme.typography.subtitle1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
            Text(
                "${summary.pictures} picture${if (summary.pictures == 1) "" else "s"} · ${summary.documents} doc${if (summary.documents == 1) "" else "s"}" +
                    if (summary.boards > 0) " · on ${summary.boards} board${if (summary.boards == 1) "" else "s"}" else "",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.secondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Delete…",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.error,
                modifier = Modifier.clip(RoundedCornerShape(3.dp)).clickable { onDelete() }.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

// ---------------- One concept ----------------

/** A concept opened: its pictures, notes and links in a grid, its documents rendered beside. */
@Composable
fun ConceptScreen(state: ConceptState, thumbs: ThumbCache) {
    val concept = state.concept ?: return
    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colors.surface.copy(alpha = 0.94f), elevation = 3.dp, modifier = Modifier.fillMaxWidth().testTag("concept-header")) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    concept.name,
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 300.dp).clickable { state.openEditor(ConceptEditor.Rename) },
                )
                Text(
                    concept.kind.ifBlank { "kind…" },
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.secondary,
                    modifier = Modifier.clickable { state.openEditor(ConceptEditor.Rename) },
                )
                Spacer(Modifier.width(8.dp))
                LayoutToggle(state)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { state.closeConcept() }, modifier = Modifier.testTag("concept-up")) { Text("↑ All concepts") }
                AddMenu(state)
                TextButton(onClick = { state.openEditor(ConceptEditor.LinkToBoards) }, modifier = Modifier.testTag("concept-link")) { Text("Boards…") }
                TextButton(onClick = { state.openEditor(ConceptEditor.DeleteConcept(state.root!!, concept.name)) }) { Text("Delete…") }
            }
        }
        state.notice?.let {
            Text("$it  (click to dismiss)", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.error,
                modifier = Modifier.padding(horizontal = 12.dp).clickable { state.notice = null })
        }
        Row(Modifier.fillMaxSize()) {
            // Cards: pictures, notes, links — in a grid, or placed by hand.
            if (state.layout == BoardLayouts.FREE) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (concept.notes.isNotBlank()) {
                        Text(
                            concept.notes,
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                    ConceptCanvas(state, thumbs, Modifier.fillMaxWidth().weight(1f))
                }
            } else LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                if (concept.notes.isNotBlank()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(concept.notes, style = MaterialTheme.typography.body2, color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f))
                    }
                }
                if (concept.items.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Nothing here yet. + ▾ adds pictures, a note, a link or a document.",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
                items(concept.items, key = { it.id }) { item ->
                    when (item) {
                        is ImageItem -> PictureCard(state, thumbs, item)
                        is NoteItem -> NoteCard(state, item)
                        is LinkItem -> LinkCard(state, item)
                    }
                }
            }
            // Documents.
            if (concept.documents.isNotEmpty()) {
                Divider(modifier = Modifier.width(1.dp).fillMaxHeight())
                LazyColumn(Modifier.width(360.dp).fillMaxHeight(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(concept.documents.size, key = { concept.documents[it] }) { i ->
                        DocumentPanel(state, concept.documents[i])
                    }
                }
            }
        }
    }
}

/** Grid | Free, as a board has: the concept's own arrangement, kept in its file. */
@Composable
private fun LayoutToggle(state: ConceptState) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(BoardLayouts.GRID to "Grid", BoardLayouts.FREE to "Free").forEach { (value, label) ->
            val on = state.layout == value
            Text(
                label,
                style = MaterialTheme.typography.body2,
                color = if (on) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { state.setLayout(value) }
                    .testTag("concept-layout-$label")
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun AddMenu(state: ConceptState) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, modifier = Modifier.testTag("concept-add")) { Text("+ ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(onClick = {
                open = false
                chooseImages(state.root).takeIf { it.isNotEmpty() }?.let { state.addPictures(it) }
            }) { Text("Add pictures…") }
            DropdownMenuItem(onClick = { open = false; state.openEditor(ConceptEditor.EditNote(null)) }) { Text("New note") }
            DropdownMenuItem(onClick = { open = false; state.openEditor(ConceptEditor.EditLink(null)) }) { Text("New link") }
            DropdownMenuItem(onClick = { open = false; state.openEditor(ConceptEditor.EditDocument(null)) }, modifier = Modifier.testTag("concept-new-document")) {
                Text("New document")
            }
        }
    }
}

@Composable
internal fun PictureCard(state: ConceptState, thumbs: ThumbCache, item: ImageItem, modifier: Modifier = Modifier.aspectRatio(1f)) {
    val file = state.fileOf(item)
    val thumb: ImageBitmap? by produceState<ImageBitmap?>(null, file) {
        value = file?.let { withContext(Dispatchers.IO) { thumbs.load(it) } }
    }
    // Once the picture is decoded its shape is known, and the free layout sizes the card by it.
    LaunchedEffect(thumb) { thumb?.let { state.rememberAspect(item.id, it.width.toFloat() / it.height) } }
    val selected = item.id in state.selection
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0x14000000))
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colors.primary else Color(0x33000000), RoundedCornerShape(4.dp))
            .clickable { state.toggleSelected(item.id) }
            .testTag("concept-picture-" + item.id),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = thumb
        if (bmp != null) Image(bitmap = bmp, contentDescription = item.path, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text("…", color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f))
        if (selected) {
            Text("Remove", style = MaterialTheme.typography.caption, color = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).background(Color(0xAA000000)).clickable { state.removeItems(setOf(item.id)) }.padding(4.dp))
        }
    }
}

@Composable
internal fun NoteCard(state: ConceptState, item: NoteItem, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFFFF3B8))
            .clickable { state.openEditor(ConceptEditor.EditNote(item.id)) }
            .padding(10.dp),
    ) {
        if (item.kind == NoteKind.POSTIT) {
            Text(item.text, style = MaterialTheme.typography.body2, color = Color(0xFF3A3315))
        } else {
            Markdown.Rendered(item.text, style = MaterialTheme.typography.body2, color = Color(0xFF3A3315), onLink = ::browse)
        }
    }
}

@Composable
internal fun LinkCard(state: ConceptState, item: LinkItem, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colors.surface)
            .clickable { browse(item.url) }
            .padding(10.dp),
    ) {
        Text("🔗 ", style = MaterialTheme.typography.body2)
        Text(item.title.ifBlank { item.url }, style = MaterialTheme.typography.body2, color = MaterialTheme.colors.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** One document, rendered, with its title and a way into editing it. */
@Composable
private fun DocumentPanel(state: ConceptState, path: String) {
    val text = state.readDocument(path) ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colors.surface)
            .padding(12.dp)
            .testTag("document-" + File(path).name),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.documentTitle(path), style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { state.openEditor(ConceptEditor.EditDocument(path)) }) { Text("Edit…") }
        }
        Markdown.Rendered(text, style = MaterialTheme.typography.body2, color = MaterialTheme.colors.onSurface, onLink = ::browse)
    }
}

private fun browse(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(if (url.contains("://")) url else "https://$url")) }
}

// ---------------- Dialogs ----------------

@Composable
fun ConceptDialogs(state: ConceptState) {
    when (val editor = state.editor) {
        null -> Unit

        ConceptEditor.NewConcept -> {
            var name by remember { mutableStateOf("") }
            var kind by remember { mutableStateOf("") }
            var error by remember { mutableStateOf<String?>(null) }
            Scrim(onDismiss = state::closeEditor) {
                Text("New concept", style = MaterialTheme.typography.h6)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusOnShow().confirmOnEnter {
                        error = state.createConcept(name, kind)
                        if (error == null) state.closeEditor()
                    }.testTag("concept-name"),
                )
                Text("Kind", style = MaterialTheme.typography.caption)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ConceptKinds.SUGGESTED.forEach { k -> SelectChip(k, kind == k) { kind = if (kind == k) "" else k } }
                }
                OutlinedTextField(value = kind, onValueChange = { kind = it }, label = { Text("or anything") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption) }
                Buttons("Create", onOk = { error = state.createConcept(name, kind); if (error == null) state.closeEditor() }, onCancel = state::closeEditor)
            }
        }

        ConceptEditor.Rename -> {
            var name by remember { mutableStateOf(state.concept?.name ?: "") }
            var kind by remember { mutableStateOf(state.concept?.kind ?: "") }
            var notes by remember { mutableStateOf(state.concept?.notes ?: "") }
            Scrim(onDismiss = state::closeEditor) {
                Text("About this concept", style = MaterialTheme.typography.h6)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth().focusOnShow())
                OutlinedTextField(value = kind, onValueChange = { kind = it }, label = { Text("Kind") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("A few words") }, modifier = Modifier.fillMaxWidth().height(100.dp))
                Buttons("Save", onOk = { state.rename(name); state.setKind(kind); state.setNotes(notes); state.closeEditor() }, onCancel = state::closeEditor)
            }
        }

        is ConceptEditor.EditNote -> {
            val existing = editor.itemId?.let(state::item) as? NoteItem
            var text by remember(editor) { mutableStateOf(existing?.text ?: "") }
            Scrim(onDismiss = state::closeEditor) {
                Text(if (existing == null) "New note" else "Edit note", style = MaterialTheme.typography.h6)
                OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth().height(150.dp).focusOnShow().testTag("concept-note-text"))
                if (existing != null) TextButton(onClick = { state.removeItems(setOf(existing.id)); state.closeEditor() }) { Text("Remove note") }
                Buttons("Save", onOk = { state.saveNote(editor.itemId, text, existing?.kind ?: NoteKind.DOCUMENT); state.closeEditor() }, onCancel = state::closeEditor)
            }
        }

        is ConceptEditor.EditLink -> {
            val existing = editor.itemId?.let(state::item) as? LinkItem
            var url by remember(editor) { mutableStateOf(existing?.url ?: "") }
            var title by remember(editor) { mutableStateOf(existing?.title ?: "") }
            Scrim(onDismiss = state::closeEditor) {
                Text(if (existing == null) "New link" else "Edit link", style = MaterialTheme.typography.h6)
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Address") }, singleLine = true, modifier = Modifier.fillMaxWidth().focusOnShow())
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Buttons("Save", onOk = { state.saveLink(editor.itemId, url, title); state.closeEditor() }, onCancel = state::closeEditor)
            }
        }

        is ConceptEditor.EditDocument -> {
            var text by remember(editor) { mutableStateOf(editor.path?.let(state::readDocument) ?: "") }
            Scrim(onDismiss = state::closeEditor) {
                Text(if (editor.path == null) "New document" else "Edit document", style = MaterialTheme.typography.h6)
                Row(Modifier.fillMaxWidth().heightIn(max = 420.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f).fillMaxHeight().focusOnShow().testTag("document-text"))
                    // The live preview, since a document is written to be read.
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        Markdown.Rendered(text, style = MaterialTheme.typography.body2, color = MaterialTheme.colors.onSurface, onLink = {})
                    }
                }
                Text("Markdown: # heading · **bold** · *italic* · - list · [text](url) · --- ; saved as a .md file in the concept's _docs folder.",
                    style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                    if (editor.path != null) OutlinedButton(onClick = { state.deleteDocument(editor.path); state.closeEditor() }) { Text("Delete document") }
                    OutlinedButton(onClick = state::closeEditor) { Text("Cancel") }
                    Button(onClick = { if (text.isNotBlank()) state.saveDocument(editor.path, text); state.closeEditor() }, modifier = Modifier.testTag("document-save")) { Text("Save") }
                }
            }
        }

        ConceptEditor.LinkToBoards -> {
            val id = state.concept?.id
            var boards by remember(editor, id) { mutableStateOf<List<BoardLink>?>(null) }
            LaunchedEffect(editor, id) {
                boards = withContext(Dispatchers.IO) { id?.let { state.boardsFor(it) }.orEmpty() }
            }
            Scrim(onDismiss = state::closeEditor) {
                Text("On which boards?", style = MaterialTheme.typography.h6)
                Text(
                    "A linked concept shows on the board as a group of its own — the concept itself, not a copy.",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                )
                val list = boards
                when {
                    list == null -> Text("Looking…", style = MaterialTheme.typography.caption)
                    list.isEmpty() -> Text("No boards yet.", style = MaterialTheme.typography.caption)
                    else -> Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        list.forEach { link ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().testTag("board-link-" + link.name).clickable {
                                    id?.let { state.setLinked(it, link.dir, !link.linked) }
                                    boards = list.map { if (it.dir == link.dir) it.copy(linked = !link.linked) else it }
                                },
                            ) {
                                Checkbox(checked = link.linked, onCheckedChange = null)
                                Text(link.name, style = MaterialTheme.typography.body2)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = state::closeEditor) { Text("Done") }
                }
            }
        }

        is ConceptEditor.DeleteConcept -> {
            var alsoFolder by remember(editor) { mutableStateOf(true) }
            val linked by produceState(emptyList<String>(), editor) {
                value = withContext(Dispatchers.IO) {
                    ConceptStore.peek(editor.dir)?.id?.let { id -> state.boardsFor(id).filter { it.linked }.map { it.name } }.orEmpty()
                }
            }
            Scrim(onDismiss = state::closeEditor) {
                Text("Delete \"${editor.name}\"?", style = MaterialTheme.typography.h6)
                Text(editor.dir.path, style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                Text(
                    "A concept lives once. Every board it is linked onto loses it at the same time.",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.error,
                )
                if (linked.isNotEmpty()) {
                    Text(
                        "Linked on: " + linked.joinToString(),
                        style = MaterialTheme.typography.body2,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("delete-linked-boards"),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = alsoFolder, onCheckedChange = { alsoFolder = it })
                    Text("Delete the folder and everything in it", style = MaterialTheme.typography.body2)
                }
                Buttons(if (alsoFolder) "Delete everything" else "Remove concept", onOk = { state.deleteConcept(editor.dir, alsoFolder); state.closeEditor() }, onCancel = state::closeEditor)
            }
        }
    }
}

@Composable
private fun ColumnScope.Buttons(confirm: String, onOk: () -> Unit, onCancel: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onCancel) { Text("Cancel") }
        Button(onClick = onOk, modifier = Modifier.testTag("concept-confirm")) { Text(confirm) }
    }
}

@Composable
private fun Scrim(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    // Pointer-only dismissal: a `clickable` here takes focus, and a Space or Enter meant for a
    // note could then close the dialog from under it.
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)).pointerInput(onDismiss) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            elevation = 12.dp,
            shape = RoundedCornerShape(8.dp),
            // Swallow taps so the dialog body doesn't dismiss itself.
            modifier = Modifier.widthIn(max = 760.dp).padding(24.dp).pointerInput(Unit) { detectTapGestures { } },
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}
