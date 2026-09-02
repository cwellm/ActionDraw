package de.creaflect.actiondraw.board

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.creaflect.actiondraw.Settings
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Which board dialog is open (rendered by `BoardDialogs`); the dialogs own their text state. */
sealed class BoardEditor {
    /** The board list: pick one, create one, change the boards home, or explore for a folder. */
    data object PickBoard : BoardEditor()

    data object NewBoard : BoardEditor()
    data object NewGroup : BoardEditor()
    data class RenameGroup(val groupId: String) : BoardEditor()

    /** `itemId == null` creates a new note. */
    data class EditNote(val itemId: String?) : BoardEditor()
    data class EditCaption(val itemId: String) : BoardEditor()
    data class EditTags(val itemIds: Set<String>) : BoardEditor()
}

/**
 * Hoisted state + actions for the Idea Board (mirrors [de.creaflect.actiondraw.AppState]'s style).
 * Every mutation is written straight to the sidecar via [BoardStore] — there is no separate save
 * step. Talks to the rest of the app only through [BoardHost].
 */
class BoardState(
    private val settings: Settings = Settings(),
    private val host: BoardHost,
    private val timestamp: () -> String = {
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
    },
) {
    var root by mutableStateOf<File?>(null)
        private set
    var board by mutableStateOf<BoardFile?>(null)
        private set

    /** The main sidecar was corrupt and the `.bak` was used — shown as a banner. */
    var openedFromBackup by mutableStateOf(false)
        private set

    /** Opening failed entirely (sidecar and backup unreadable) — shown next to the menu buttons. */
    var openFailed by mutableStateOf(false)
        private set

    var recent by mutableStateOf(settings.recentBoards())
        private set

    /** Selected item ids — what Draw/Copy/Move/Delete operate on. */
    var selection by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Keyboard focus & shift-range anchor. */
    var focusId by mutableStateOf<String?>(null)
        private set

    /** Active tag filter (AND semantics). */
    var filterTags by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Pictures the large viewer is showing (ids, in display order); empty = the viewer is closed. */
    var viewerIds by mutableStateOf<List<String>>(emptyList())
        private set

    /** Position within [viewerIds]. */
    var viewerIndex by mutableStateOf(0)
        private set

    var editor by mutableStateOf<BoardEditor?>(null)
        private set

    /** Chrome-less mode; only meaningful while the window is fullscreen. */
    var immersive by mutableStateOf(false)

    // ---- Freeform camera (board point at the view centre + zoom) ----
    var camX by mutableStateOf(0f)
        private set
    var camY by mutableStateOf(0f)
        private set
    var zoom by mutableStateOf(1f)
        private set

    val isOpen: Boolean get() = root != null && board != null
    val theme: String get() = board?.theme ?: BoardThemes.CORK
    val layout: String get() = board?.layout ?: BoardLayouts.GRID

    fun boardsHome(): File = settings.boardsHome()

    /** Bumped when the boards home changes so open board lists recompute. */
    var boardsHomeTick by mutableStateOf(0)
        private set

    fun setBoardsHomeDir(dir: File) {
        settings.setBoardsHome(dir)
        boardsHomeTick++
    }

    /**
     * Boards for the picker: the recently opened ones plus every direct subfolder of the boards
     * home that has a sidecar — deduplicated, with the board's stored name.
     */
    fun availableBoards(): List<Pair<String, File>> {
        val dirs = LinkedHashMap<String, File>()
        settings.recentBoards().forEach { dirs.putIfAbsent(it.absolutePath.lowercase(), it.absoluteFile) }
        settings.boardsHome().listFiles()
            ?.filter { it.isDirectory && BoardStore.exists(it) }
            ?.sortedBy { it.name.lowercase() }
            ?.forEach { dirs.putIfAbsent(it.absolutePath.lowercase(), it.absoluteFile) }
        return dirs.values.map { dir ->
            (BoardStore.peek(dir)?.name?.takeIf { it.isNotBlank() } ?: dir.name) to dir
        }
    }


    // ---- Derived views ----

    val sortedGroups: List<BoardGroup> get() = board?.groups.orEmpty().sortedBy { it.order }

    val allTags: List<String>
        get() = board?.items.orEmpty().filterIsInstance<ImageItem>()
            .flatMap { it.tags }.distinct().sortedBy { it.lowercase() }

    fun tagCount(tag: String): Int =
        board?.items.orEmpty().filterIsInstance<ImageItem>().count { tag in it.tags }

    private fun visible(item: BoardItem): Boolean =
        filterTags.isEmpty() || (item is ImageItem && filterTags.all { it in item.tags })

    /** Items of one group (null = Inbox) that pass the tag filter, in stored order. */
    fun itemsIn(groupId: String?): List<BoardItem> =
        board?.items.orEmpty()
            .filter { if (groupId == null) it.groups.isEmpty() else groupId in it.groups }
            .filter(::visible)

    /** Inbox first, then the groups by order — the board's display structure. */
    val sections: List<Pair<BoardGroup?, List<BoardItem>>>
        get() {
            val result = mutableListOf<Pair<BoardGroup?, List<BoardItem>>>(null to itemsIn(null))
            sortedGroups.forEach { result += it to itemsIn(it.id) }
            return result
        }

    /** Flattened display order (collapsed groups excluded) — basis for range select and focus. */
    val visibleOrder: List<BoardItem>
        get() = sections.flatMap { (group, items) -> if (group?.collapsed == true) emptyList() else items }

    /** The freeform canvas' items (tag filter applied), in z-order (last = frontmost). */
    val freeItems: List<BoardItem>
        get() = board?.items.orEmpty().filter(::visible)

    /** Whatever the current layout puts on screen, in the order it shows it. */
    private val displayItems: List<BoardItem>
        get() = if (layout == BoardLayouts.FREE) freeItems else visibleOrder

    /**
     * What the large viewer would show right now: the selected pictures, or — with nothing
     * selected — every picture currently on screen. Always in display order.
     */
    val viewableIds: List<String>
        get() {
            val shown = displayItems.filterIsInstance<ImageItem>()
            return (shown.filter { it.id in selection }.ifEmpty { shown }).map { it.id }
        }

    fun item(id: String): BoardItem? = board?.items?.find { it.id == id }

    fun fileOf(item: ImageItem): File? = root?.let { File(it, item.path) }

    val selectedItems: List<BoardItem> get() = board?.items.orEmpty().filter { it.id in selection }

    val selectedImageFiles: List<File>
        get() = selectedItems.filterIsInstance<ImageItem>().mapNotNull(::fileOf)

    // ---- Lifecycle ----

    /** Creates `<parent>/<name>` as a fresh board. Returns an error message, or null on success. */
    fun createBoard(parent: File, name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "Give the board a name."
        val dir = File(parent, sanitizeName(trimmed))
        if (BoardStore.exists(dir)) { // already a board -> just open it
            openBoard(dir)
            return null
        }
        if (dir.exists() && !dir.listFiles().isNullOrEmpty()) return "Folder exists and is not empty:\n$dir"
        if (!dir.isDirectory && !dir.mkdirs()) return "Couldn't create:\n$dir"
        settings.setBoardsHome(parent)
        val created = BoardFile(name = trimmed)
        if (!BoardStore.save(dir, created)) return "Couldn't write the board file in:\n$dir"
        root = dir
        board = created
        openedFromBackup = false
        openFailed = false
        afterOpen(dir)
        return null
    }

    fun openBoard(dir: File) {
        when (val result = BoardStore.load(dir)) {
            BoardStore.LoadResult.None -> {
                // A folder without a sidecar becomes an (empty) board named after it.
                val fresh = BoardFile(name = dir.name)
                BoardStore.save(dir, fresh)
                root = dir
                board = fresh
                openedFromBackup = false
                openFailed = false
            }

            is BoardStore.LoadResult.Loaded -> {
                root = dir
                board = result.board.let { if (it.name.isBlank()) it.copy(name = dir.name) else it }
                openedFromBackup = result.fromBackup
                openFailed = false
            }

            BoardStore.LoadResult.Failed -> {
                openFailed = true
                return
            }
        }
        afterOpen(dir)
    }

    private fun afterOpen(dir: File) {
        selection = emptySet()
        focusId = null
        filterTags = emptySet()
        closeViewer()
        editor = null
        immersive = false
        val camera = board?.camera ?: Camera()
        camX = camera.x
        camY = camera.y
        zoom = camera.zoom
        if (layout == BoardLayouts.FREE) update { placeMissing(it) }
        settings.addRecentBoard(dir)
        recent = settings.recentBoards()
        host.showBoard()
    }

    fun closeBoard() {
        commitCamera()
        root = null
        board = null
        selection = emptySet()
        focusId = null
        closeViewer()
        editor = null
        immersive = false
        host.leaveBoard()
    }

    fun dismissOpenFailed() {
        openFailed = false
    }

    // ---- Dialogs ----

    fun openEditor(target: BoardEditor) {
        editor = target
    }

    fun closeEditor() {
        editor = null
    }

    // ---- Mutations (each one is persisted immediately) ----

    private fun update(transform: (BoardFile) -> BoardFile) {
        val dir = root ?: return
        val next = transform(board ?: return)
        board = next
        BoardStore.save(dir, next)
    }

    // Groups

    fun addGroup(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        update { b ->
            val order = (b.groups.maxOfOrNull { it.order } ?: 0) + 1
            b.copy(groups = b.groups + BoardGroup(id = Importer.newId(), name = trimmed, order = order))
        }
    }

    fun renameGroup(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        update { b -> b.copy(groups = b.groups.map { if (it.id == id) it.copy(name = trimmed) else it }) }
    }

    fun cycleGroupColor(id: String) = update { b ->
        b.copy(groups = b.groups.map { g ->
            if (g.id == id) g.copy(color = GROUP_COLORS[(GROUP_COLORS.indexOf(g.color) + 1) % GROUP_COLORS.size])
            else g
        })
    }

    fun toggleCollapsed(id: String) = update { b ->
        b.copy(groups = b.groups.map { if (it.id == id) it.copy(collapsed = !it.collapsed) else it })
    }

    fun moveGroup(id: String, delta: Int) = update { b ->
        val ordered = b.groups.sortedBy { it.order }.toMutableList()
        val idx = ordered.indexOfFirst { it.id == id }
        val target = idx + delta
        if (idx < 0 || target !in ordered.indices) return@update b
        ordered.add(target, ordered.removeAt(idx))
        b.copy(groups = ordered.mapIndexed { i, g -> g.copy(order = i + 1) })
    }

    /** Removes the group; its items simply lose the membership and fall back into the Inbox. */
    fun deleteGroup(id: String) = update { b ->
        b.copy(
            groups = b.groups.filterNot { it.id == id },
            items = b.items.map { if (id in it.groups) it.withGroups(it.groups - id) else it },
        )
    }

    // Items

    fun moveToGroup(ids: Set<String>, groupId: String?) = update { b ->
        b.copy(items = b.items.map { if (it.id in ids) it.withGroups(listOfNotNull(groupId)) else it })
    }

    fun toggleStar(ids: Set<String>) = update { b ->
        val images = b.items.filterIsInstance<ImageItem>().filter { it.id in ids }
        if (images.isEmpty()) return@update b
        val allStarred = images.all { it.starred }
        b.copy(items = b.items.map { if (it is ImageItem && it.id in ids) it.copy(starred = !allStarred) else it })
    }

    fun setCaption(id: String, caption: String) = update { b ->
        b.copy(items = b.items.map {
            if (it is ImageItem && it.id == id) it.copy(caption = caption.trim().ifEmpty { null }) else it
        })
    }

    /** Tags shared by every selected image — what the tag dialog starts from. */
    fun commonTags(ids: Set<String>): Set<String> =
        board?.items.orEmpty().filterIsInstance<ImageItem>().filter { it.id in ids }
            .map { it.tags.toSet() }
            .reduceOrNull { a, b -> a intersect b } ?: emptySet()

    /** Applies a tag-dialog result to [ids]: what left the common set is removed, what's new is added. */
    fun applyTags(ids: Set<String>, before: Set<String>, after: Set<String>) {
        val added = after - before
        val removed = before - after
        if (added.isEmpty() && removed.isEmpty()) return
        update { b ->
            b.copy(items = b.items.map {
                if (it is ImageItem && it.id in ids) it.copy(tags = (it.tags - removed + added).distinct())
                else it
            })
        }
        filterTags = filterTags.filter { it in allTags }.toSet() // a removed tag may be gone entirely
    }

    fun saveNote(itemId: String?, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (itemId == null) {
            val note = NoteItem(id = Importer.newId(), text = trimmed)
            update { it.copy(items = it.items + note) }
            selection = setOf(note.id)
            focusId = note.id
        } else {
            update { b ->
                b.copy(items = b.items.map { if (it is NoteItem && it.id == itemId) it.copy(text = trimmed) else it })
            }
        }
    }

    /** Removes cards from the board — files on disk are never touched. */
    fun removeItems(ids: Set<String>) {
        if (ids.isEmpty()) return
        update { b -> b.copy(items = b.items.filterNot { it.id in ids }) }
        selection = selection - ids
        if (focusId?.let { it in ids } == true) focusId = null
        // Keep the viewer honest when a card it is showing disappears.
        if (viewerOpen) {
            viewerIds = viewerIds - ids
            viewerIndex = viewerIndex.coerceIn(0, (viewerIds.size - 1).coerceAtLeast(0))
        }
    }

    // ---- Selection & focus ----

    fun clickItem(id: String, ctrl: Boolean, shift: Boolean) {
        val order = visibleOrder.map { it.id }
        val anchor = focusId
        selection = when {
            shift && anchor != null && anchor in order && id in order -> {
                val a = order.indexOf(anchor)
                val b = order.indexOf(id)
                order.subList(minOf(a, b), maxOf(a, b) + 1).toSet()
            }

            ctrl -> if (id in selection) selection - id else selection + id
            else -> setOf(id)
        }
        if (!shift) focusId = id
    }

    /** Right-click selects the card underneath unless it is already part of the selection. */
    fun rightClickItem(id: String) {
        if (id !in selection) {
            selection = setOf(id)
            focusId = id
        }
    }

    fun selectAll() {
        selection = visibleOrder.map { it.id }.toSet()
    }

    fun clearSelection() {
        selection = emptySet()
    }

    fun moveFocus(delta: Int) {
        val order = visibleOrder.map { it.id }
        if (order.isEmpty()) return
        val idx = order.indexOf(focusId)
        val next = if (idx < 0) 0 else (idx + delta).coerceIn(order.indices)
        focusId = order[next]
        selection = setOf(order[next])
    }

    fun toggleFilterTag(tag: String) {
        filterTags = if (tag in filterTags) filterTags - tag else filterTags + tag
        selection = emptySet()
        focusId = null
    }

    fun clearFilter() {
        filterTags = emptySet()
    }

    fun setTheme(theme: String) = update { it.copy(theme = theme) }

    // ---- Freeform layout ----

    /** Switch grid ⇄ freeform; entering freeform places every card that has no position yet. */
    fun setLayout(layout: String) {
        update { it.copy(layout = layout) }
        if (layout == BoardLayouts.FREE) update { placeMissing(it, camX, camY) }
    }

    /**
     * In-memory-only board change during a gesture (drag/resize/rotate) — recomposes without
     * hitting the disk on every pointer move; [commitLayout] persists the result.
     */
    private fun updateTransient(transform: (BoardFile) -> BoardFile) {
        board = transform(board ?: return)
    }

    fun commitLayout() {
        val dir = root ?: return
        BoardStore.save(dir, board ?: return)
    }

    /** Moves [id] (or the whole selection, if it is part of it) by a board-space delta. */
    fun dragBy(id: String, dx: Float, dy: Float) {
        val ids = if (id in selection) selection else setOf(id)
        updateTransient { b ->
            b.copy(items = b.items.map {
                val pos = it.pos
                if (it.id in ids && pos != null) it.withPos(pos.copy(x = pos.x + dx, y = pos.y + dy)) else it
            })
        }
    }

    fun resizeBy(id: String, factor: Float) = updateTransient { b ->
        b.copy(items = b.items.map {
            val pos = it.pos
            if (it.id == id && pos != null) {
                it.withPos(pos.copy(scale = (pos.scale * factor).coerceIn(0.15f, 8f)))
            } else it
        })
    }

    fun rotateBy(id: String, degrees: Float) = updateTransient { b ->
        b.copy(items = b.items.map {
            val pos = it.pos
            if (it.id == id && pos != null) it.withPos(pos.copy(rotation = (pos.rotation + degrees) % 360f)) else it
        })
    }

    /** Arrow keys in freeform: move the selection and persist right away. */
    fun nudgeSelection(dx: Float, dy: Float) {
        val id = selection.firstOrNull() ?: focusId ?: return
        dragBy(id, dx, dy)
        commitLayout()
    }

    // ---- Ordering ----
    // One items array is both the grid's display order (within each group) and the freeform
    // z-order (last = frontmost). All reordering happens through these operations.

    /** Raises the card to the top of the z-order / the end of every listing. */
    fun bringToFront(id: String) = update { b ->
        val item = b.items.find { it.id == id } ?: return@update b
        b.copy(items = b.items.filterNot { it.id == id } + item)
    }

    /** Sends the card to the bottom of the z-order / the start of every listing. */
    fun sendToBack(id: String) = update { b ->
        val item = b.items.find { it.id == id } ?: return@update b
        b.copy(items = listOf(item) + b.items.filterNot { it.id == id })
    }

    /** Grid: one position earlier/later among the visible cards of [groupId] (null = Inbox). */
    fun stepInGroup(id: String, groupId: String?, forward: Boolean) =
        step(id, itemsIn(groupId).map { it.id }, forward)

    /** Grid: to the start/end of [groupId]'s visible cards. */
    fun toGroupEdge(id: String, groupId: String?, toEnd: Boolean) {
        val siblings = itemsIn(groupId).map { it.id }.filterNot { it == id }
        val neighbor = (if (toEnd) siblings.lastOrNull() else siblings.firstOrNull()) ?: return
        moveRelative(id, neighbor, after = toEnd)
    }

    /** Freeform: one z-level up/down, skipping cards the tag filter hides. */
    fun stepZ(id: String, forward: Boolean) = step(id, freeItems.map { it.id }, forward)

    private fun step(id: String, displayOrder: List<String>, forward: Boolean) {
        val i = displayOrder.indexOf(id)
        if (i < 0) return
        val neighbor = displayOrder.getOrNull(if (forward) i + 1 else i - 1) ?: return
        moveRelative(id, neighbor, after = forward)
    }

    /** Re-inserts [id] directly after/before [neighborId] in the items array. */
    private fun moveRelative(id: String, neighborId: String, after: Boolean) = update { b ->
        val item = b.items.find { it.id == id } ?: return@update b
        val rest = b.items.filterNot { it.id == id }
        val idx = rest.indexOfFirst { it.id == neighborId }
        if (idx < 0) return@update b
        val insertAt = if (after) idx + 1 else idx
        b.copy(items = rest.take(insertAt) + item + rest.drop(insertAt))
    }

    /** Remembers an image's aspect ratio after its first decode, so freeform layout is stable. */
    fun recordAspect(id: String, aspect: Float) {
        val item = item(id) as? ImageItem ?: return
        if (item.aspect != null) return
        update { b -> b.copy(items = b.items.map { if (it.id == id && it is ImageItem) it.copy(aspect = aspect) else it }) }
    }

    // ---- Camera ----

    fun pan(dx: Float, dy: Float) {
        camX += dx
        camY += dy
    }

    fun setZoom(newZoom: Float, newCamX: Float, newCamY: Float) {
        zoom = newZoom.coerceIn(0.1f, 5f)
        camX = newCamX
        camY = newCamY
    }

    fun commitCamera() {
        if (root == null || board == null) return
        update { it.copy(camera = Camera(camX, camY, zoom)) }
    }

    /** Centres the camera on all placed cards and zooms to fit them into [viewW]×[viewH] px. */
    fun fitAll(viewW: Float, viewH: Float) {
        val positions = board?.items.orEmpty().mapNotNull { it.pos }
        if (positions.isEmpty() || viewW <= 0f || viewH <= 0f) return
        val half = positions.map { BASE_SIZE * it.scale / 2 }
        val minX = positions.mapIndexed { i, p -> p.x - half[i] }.min()
        val maxX = positions.mapIndexed { i, p -> p.x + half[i] }.max()
        val minY = positions.mapIndexed { i, p -> p.y - half[i] }.min()
        val maxY = positions.mapIndexed { i, p -> p.y + half[i] }.max()
        camX = (minX + maxX) / 2
        camY = (minY + maxY) / 2
        zoom = (minOf(viewW / (maxX - minX + BASE_SIZE), viewH / (maxY - minY + BASE_SIZE)))
            .coerceIn(0.1f, 2f)
        commitCamera()
    }

    // ---- Large viewer (carousel) ----

    val viewerOpen: Boolean get() = viewerIds.isNotEmpty()

    fun viewerItemAt(index: Int): ImageItem? = viewerIds.getOrNull(index)?.let { item(it) as? ImageItem }

    val viewerItem: ImageItem? get() = viewerItemAt(viewerIndex)

    /**
     * Opens the large view over [viewableIds], starting at [startId] — or at the focused card
     * when it is part of them, else at the first one. Does nothing when there is no picture.
     */
    fun openViewer(startId: String? = null) {
        val ids = viewableIds
        if (ids.isEmpty()) return
        val start = startId?.takeIf { it in ids } ?: focusId?.takeIf { it in ids } ?: ids.first()
        viewerIds = ids
        viewerIndex = ids.indexOf(start)
    }

    fun toggleViewer() {
        if (viewerOpen) closeViewer() else openViewer()
    }

    fun closeViewer() {
        viewerIds = emptyList()
        viewerIndex = 0
    }

    /** Carousel step; wraps around so flipping never dead-ends. */
    fun viewerStep(delta: Int) {
        val n = viewerIds.size
        if (n == 0) return
        viewerIndex = ((viewerIndex + delta) % n + n) % n
        focusId = viewerIds[viewerIndex]
    }

    fun viewerGoTo(index: Int) {
        if (index !in viewerIds.indices) return
        viewerIndex = index
        focusId = viewerIds[index]
    }

    // ---- Material in / out / draw ----

    fun importExternal(files: List<File>, groupId: String? = null) {
        val dir = root ?: return
        val existing = board?.items.orEmpty().filterIsInstance<ImageItem>().map { it.path }.toSet()
        val items = Importer.importFiles(dir, files, groupId, existing)
        if (items.isEmpty()) return
        update { it.copy(items = it.items + items) }
        if (layout == BoardLayouts.FREE) update { placeMissing(it, camX, camY) }
        selection = items.map { it.id }.toSet()
        focusId = items.first().id
    }

    fun importPasted() {
        val dir = root ?: return
        when (val pasted = BoardClipboard.paste()) {
            is BoardClipboard.Pasted.Files -> importExternal(pasted.files)

            is BoardClipboard.Pasted.Bitmap -> {
                val item = Importer.importBitmap(dir, pasted.image, null, timestamp()) ?: return
                update { it.copy(items = it.items + item) }
                if (layout == BoardLayouts.FREE) update { placeMissing(it, camX, camY) }
                selection = setOf(item.id)
                focusId = item.id
            }

            null -> {}
        }
    }

    /** Image selection → real files on the clipboard (Explorer paste duplicates them). */
    fun copySelection() {
        val files = selectedImageFiles
        if (files.isNotEmpty()) {
            BoardClipboard.copyFiles(files)
            return
        }
        val notes = selectedItems.filterIsInstance<NoteItem>()
        if (notes.isNotEmpty()) BoardClipboard.copyText(notes.joinToString("\n\n") { it.text })
    }

    fun drawSelection() = draw(selectedImageFiles)

    fun drawGroup(groupId: String?) =
        draw(itemsIn(groupId).filterIsInstance<ImageItem>().mapNotNull(::fileOf))

    private fun draw(files: List<File>) {
        val dir = root ?: return
        if (files.isNotEmpty()) host.startSession(dir, files)
    }

    companion object {
        /** Colour accents a group cycles through (null = no accent). */
        val GROUP_COLORS: List<String?> =
            listOf(null, "#80CBC4", "#FFB74D", "#A5D6A7", "#EF9A9A", "#B39DDB")

        /** Base edge length of a freeform card at scale 1, in board units. */
        const val BASE_SIZE = 220f

        /** Windows-safe folder name for a new board. */
        fun sanitizeName(name: String): String =
            name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().trimEnd('.')

        /**
         * Gives every card without a position one, cascading in rows of five around
         * ([originX], [originY]) — below everything already placed. Pure, so it's testable.
         */
        fun placeMissing(board: BoardFile, originX: Float = 0f, originY: Float = 0f): BoardFile {
            val unplaced = board.items.count { it.pos == null }
            if (unplaced == 0) return board
            val gap = BASE_SIZE * 1.2f
            val perRow = 5
            val startY = (board.items.mapNotNull { it.pos }.maxOfOrNull { it.y + BASE_SIZE } ?: originY)
            val startX = originX - (perRow - 1) * gap / 2
            var i = 0
            return board.copy(items = board.items.map { item ->
                if (item.pos != null) item
                else item.withPos(ItemPos(startX + (i % perRow) * gap, startY + (i / perRow) * gap)).also { i++ }
            })
        }
    }
}
