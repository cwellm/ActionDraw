package de.creaflect.actiondraw.board

import de.creaflect.actiondraw.concept.Concept
import de.creaflect.actiondraw.concept.ConceptEntry
import de.creaflect.actiondraw.model.BoardEntryJson
import de.creaflect.actiondraw.model.BoardGroupJson
import de.creaflect.actiondraw.model.BoardItemJson
import de.creaflect.actiondraw.model.BoardJson
import de.creaflect.actiondraw.model.CameraJson
import de.creaflect.actiondraw.model.ConceptEntryJson
import de.creaflect.actiondraw.model.ConceptJson
import de.creaflect.actiondraw.model.ImageItemJson
import de.creaflect.actiondraw.model.ItemPosJson
import de.creaflect.actiondraw.model.LinkItemJson
import de.creaflect.actiondraw.model.NoteItemJson
import de.creaflect.actiondraw.model.SessionRecipeJson
import de.creaflect.actiondraw.model.WallpaperJson

// Between what is stored (`lib:core:model`) and what callers work with (`lib:domain`), field for
// field. The only place that knows both.

internal fun BoardJson.toBoard(): Board = Board(
    name = name,
    theme = theme,
    layout = layout,
    camera = camera?.toCamera(),
    session = session?.let { SessionRecipe(it.plan, it.intervalSeconds, it.autoAdvance, it.viewMode, it.grid, it.temperature) },
    groups = groups.map { BoardGroup(it.id, it.name, it.color, it.order, it.collapsed, it.parentId, it.source) },
    items = items.map { it.toItem() },
    wallpaper = wallpaper?.let { Wallpaper(it.path, it.fit, it.dim, it.blur) },
    concepts = concepts,
)

internal fun Board.toJson(): BoardJson = BoardJson(
    name = name,
    theme = theme,
    layout = layout,
    camera = camera?.toJson(),
    session = session?.let { SessionRecipeJson(it.plan, it.intervalSeconds, it.autoAdvance, it.viewMode, it.grid, it.temperature) },
    groups = groups.map { BoardGroupJson(it.id, it.name, it.color, it.order, it.collapsed, it.parentId, it.source) },
    items = items.map { it.toJson() },
    wallpaper = wallpaper?.let { WallpaperJson(it.path, it.fit, it.dim, it.blur) },
    concepts = concepts,
)

internal fun ConceptJson.toConcept(): Concept = Concept(
    id = id,
    name = name,
    kind = kind,
    notes = notes,
    items = items.map { it.toItem() },
    documents = documents,
    layout = layout,
    camera = camera?.toCamera(),
)

internal fun Concept.toJson(): ConceptJson = ConceptJson(
    id = id,
    name = name,
    kind = kind,
    notes = notes,
    items = items.map { it.toJson() },
    documents = documents,
    layout = layout,
    camera = camera?.toJson(),
)

internal fun BoardEntryJson.toEntry(): BoardEntry = BoardEntry(id, name, path, ownsFolder)

internal fun BoardEntry.toJson(): BoardEntryJson = BoardEntryJson(id, name, path, ownsFolder)

internal fun ConceptEntryJson.toEntry(): ConceptEntry = ConceptEntry(id, name, kind, path)

internal fun ConceptEntry.toJson(): ConceptEntryJson = ConceptEntryJson(id, name, kind, path)

private fun CameraJson.toCamera() = Camera(x, y, zoom)

private fun Camera.toJson() = CameraJson(x, y, zoom)

private fun ItemPosJson.toPos() = ItemPos(x, y, scale, rotation)

private fun ItemPos.toJson() = ItemPosJson(x, y, scale, rotation)

private fun BoardItemJson.toItem(): BoardItem = when (this) {
    is ImageItemJson -> ImageItem(id, path, groups, caption, starred, tags, pos?.toPos(), aspect, contentId)
    is NoteItemJson -> NoteItem(id, text, groups, pos?.toPos(), color, heading, kind)
    is LinkItemJson -> LinkItem(id, url, title, groups, pos?.toPos(), preview)
}

private fun BoardItem.toJson(): BoardItemJson = when (this) {
    is ImageItem -> ImageItemJson(id, path, groups, caption, starred, tags, pos?.toJson(), aspect, contentId)
    is NoteItem -> NoteItemJson(id, text, groups, pos?.toJson(), color, heading, kind)
    is LinkItem -> LinkItemJson(id, url, title, groups, pos?.toJson(), preview)
}
