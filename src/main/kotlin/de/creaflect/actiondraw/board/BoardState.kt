package de.creaflect.actiondraw.board

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.creaflect.actiondraw.GridMode
import de.creaflect.actiondraw.SessionPlans
import de.creaflect.actiondraw.SessionSetup
import de.creaflect.actiondraw.Settings
import de.creaflect.actiondraw.ViewMode
import de.creaflect.actiondraw.isInside
import de.creaflect.actiondraw.samePathAs
import de.creaflect.actiondraw.image.RedoStore
import de.creaflect.actiondraw.image.SeenStore
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import de.creaflect.actiondraw.board.ui.Markdown

/** Which board dialog is open (rendered by `BoardDialogs`); the dialogs own their text state. */
sealed class BoardEditor {
    /** [under] is the board a new one should be nested in; null puts it in the boards home. */
    data class NewBoard(val under: File? = null) : BoardEditor()

    /** Confirms deleting a board; [pictures] is what the folder would take with it. */
    /**
     * [ownsFolder] is what the registry recorded when the board was made: true for a folder
     * ActionDraw created, which decides whether deleting the folder is the offered default.
     */
    data class DeleteBoard(
        val dir: File,
        val name: String,
        val pictures: Int,
        val ownsFolder: Boolean,
        /** Boards nested inside this one, which deleting the folder would take as well. */
        val subBoards: Int = 0,
    ) : BoardEditor()

    /** How this board wants to be drawn (interval/ramp, auto-advance, view mode, grid). */
    data object EditSession : BoardEditor()
    /** [parentId] nests the new group inside an existing top-level group. */
    data class NewGroup(val parentId: String? = null) : BoardEditor()

    /** Names a group made out of whatever is selected. */
    /** [parentId] nests the group made from the selection inside an existing top-level group. */
    data class GroupSelection(val parentId: String? = null) : BoardEditor()
    data class RenameGroup(val groupId: String) : BoardEditor()

    /** `itemId == null` creates a new note. */
    data class EditNote(val itemId: String?) : BoardEditor()

    /** `itemId == null` creates a new link card. */
    data class EditLink(val itemId: String?) : BoardEditor()

    /** Picks a new home in the tree for the board at [dir]. */
    data class MoveBoard(val dir: File, val name: String) : BoardEditor()

    /** Asks before the app contacts a site for a link's preview picture. */
    data class FetchPreview(val itemId: String) : BoardEditor()

    /** Colour swatches read off a picture (or a whole group). */
    data class ShowPalette(val itemIds: Set<String>) : BoardEditor()
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

    /** Free-text filter over file names, captions, tags and note text. */
    var query by mutableStateOf("")
        private set

    // ---- Practice state, read from the board folder's seen/redo stores ----
    private var seen: Set<String> = emptySet()
    private var redo: Set<String> = emptySet()

    /** Bumped when seen/redo are re-read, so badges and smart groups recompose. */
    var practiceTick by mutableStateOf(0)
        private set

    /** Pictures the large viewer is showing (ids, in display order); empty = the viewer is closed. */
    var viewerIds by mutableStateOf<List<String>>(emptyList())
        private set

    /** Position within [viewerIds]. */
    var viewerIndex by mutableStateOf(0)
        private set

    var editor by mutableStateOf<BoardEditor?>(null)
        private set

    /** Why the last import left something out (unsupported format, duplicate); null = all fine. */
    var importNotice by mutableStateOf<String?>(null)
        private set

    /** Chrome-less mode; only meaningful while the window is fullscreen. */
    var immersive by mutableStateOf(false)

    /** The side drawer listing every card by group; closed by default. */
    var drawerOpen by mutableStateOf(false)

    /** Groups whose contents are folded away in the drawer. */
    var drawerCollapsed by mutableStateOf<Set<String>>(emptySet())
        private set

    fun toggleDrawerGroup(groupId: String) {
        drawerCollapsed =
            if (groupId in drawerCollapsed) drawerCollapsed - groupId else drawerCollapsed + groupId
    }

    /**
     * Centres the freeform camera on a card — the drawer's way of taking you to a picture that is
     * off-screen. Does nothing in the grid, where the card is simply selected.
     */
    fun revealItem(id: String) {
        selection = setOf(id)
        focusId = id
        if (layout != BoardLayouts.FREE) return
        item(id)?.pos?.let { pos ->
            camX = pos.x
            camY = pos.y
            commitCamera()
        }
    }

    /** Centres the camera on a whole group and selects it. */
    fun revealGroup(groupId: String) {
        selectGroup(groupId)
        if (layout != BoardLayouts.FREE) return
        groupHulls.find { it.group.id == groupId }?.let { hull ->
            camX = (hull.left + hull.right) / 2
            camY = (hull.top + hull.bottom) / 2
            commitCamera()
        }
    }

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

    /** Board -> folder, so a board is not merely "whatever sits under the boards home". */
    private val registry = BoardRegistry(settings.configDir)

    fun boardsHome(): File = settings.boardsHome()

    /** The registry's record for a folder, if it has one. */
    fun entryFor(dir: File): BoardEntry? = registry.entryFor(dir)

    /** Bumped when the boards home changes so open board lists recompute. */
    var boardsHomeTick by mutableStateOf(0)
        private set

    fun setBoardsHomeDir(dir: File) {
        settings.setBoardsHome(dir)
        boardsHomeTick++
    }

    /**
     * Boards for the picker: everything the registry knows, wherever it lives, plus any board
     * folder found under the boards home or in the recent list that is not recorded yet — those
     * are adopted into the registry as they are found, which is also how boards from before the
     * registry existed arrive in it.
     *
     * Because the registry holds absolute paths, pointing the boards home somewhere else adds a
     * place to look; it does not take the existing boards away.
     */
    fun availableBoards(): List<Pair<String, File>> {
        registry.prune()
        val dirs = LinkedHashMap<String, File>()
        registry.entries()
            .filter { BoardStore.exists(it.dir) }
            .forEach { dirs.putIfAbsent(it.path.lowercase(), it.dir) }

        val found = settings.recentBoards() +
            settings.boardsHome().listFiles().orEmpty().sortedBy { it.name.lowercase() }
        found.filter { it.isDirectory && BoardStore.exists(it) }.forEach { dir ->
            if (dirs.putIfAbsent(dir.absolutePath.lowercase(), dir.absoluteFile) == null) adopt(dir)
        }

        return dirs.values.map { dir ->
            (BoardStore.peek(dir)?.name?.takeIf { it.isNotBlank() } ?: dir.name) to dir
        }
    }

    /** One board in the tree of boards, with how deep it sits and whose child it is. */
    data class BoardNode(val name: String, val dir: File, val depth: Int, val parent: String?)

    /**
     * Every board, depth first, so sub-boards follow the board they belong to. A board whose
     * parent is registered but no longer readable is treated as a root rather than vanishing
     * with it.
     */
    fun boardTree(): List<BoardNode> {
        val all = availableBoards()
        val known = all.map { it.second.absolutePath.lowercase() }.toSet()
        fun parentPathOf(dir: File): String? =
            registry.parentOf(dir)?.path?.lowercase()?.takeIf { it in known }

        val out = mutableListOf<BoardNode>()
        fun walk(under: String?, depth: Int, parentName: String?) {
            all.filter { parentPathOf(it.second) == under }
                .sortedBy { it.first.lowercase() }
                .forEach { (name, dir) ->
                    out += BoardNode(name, dir, depth, parentName)
                    walk(dir.absolutePath.lowercase(), depth + 1, name)
                }
        }
        walk(null, 0, null)
        return out
    }

    /** The board the open one sits inside, if it is a sub-board. */
    val parentBoard: BoardEntry?
        get() = root?.let { registry.parentOf(it) }?.takeIf { BoardStore.exists(it.dir) }

    /** Boards directly inside the open one. */
    val subBoards: List<BoardEntry>
        get() {
            val dir = root ?: return emptyList()
            return registry.descendantsOf(dir)
                .filter { registry.parentOf(it.dir)?.isAt(dir) == true && BoardStore.exists(it.dir) }
                .sortedBy { it.name.lowercase() }
        }

    /** How many boards would go with [dir] if its folder were deleted. */
    fun subBoardCount(dir: File): Int = registry.descendantsOf(dir).count { BoardStore.exists(it.dir) }

    /**
     * Records a board folder nobody told us about. A direct child of the boards home is one the
     * app almost certainly created (that is where *New board…* puts them), so it counts as the
     * board's own; anything else was already the user's before ActionDraw saw it.
     */
    private fun adopt(dir: File) {
        val name = BoardStore.peek(dir)?.name?.takeIf { it.isNotBlank() } ?: dir.name
        val underHome = runCatching {
            dir.canonicalFile.parentFile == settings.boardsHome().canonicalFile
        }.getOrDefault(false)
        registry.register(name, dir, ownsFolder = underHome)
    }


    // ---- Derived views ----

    val sortedGroups: List<BoardGroup> get() = board?.groups.orEmpty().sortedBy { it.order }

    val allTags: List<String>
        get() = board?.items.orEmpty().filterIsInstance<ImageItem>()
            .flatMap { it.tags }.distinct().sortedBy { it.lowercase() }

    fun tagCount(tag: String): Int =
        board?.items.orEmpty().filterIsInstance<ImageItem>().count { tag in it.tags }

    private fun visible(item: BoardItem): Boolean {
        if (filterTags.isNotEmpty() && !(item is ImageItem && filterTags.all { it in item.tags })) return false
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return true
        return when (item) {
            is ImageItem ->
                item.path.lowercase().contains(needle) ||
                    item.caption?.lowercase()?.contains(needle) == true ||
                    item.tags.any { it.lowercase().contains(needle) }

            is NoteItem -> Markdown.plain(item.text).lowercase().contains(needle)
            is LinkItem ->
                item.title.lowercase().contains(needle) || item.url.lowercase().contains(needle)
        }
    }

    /** Sets the free-text filter. */
    fun search(text: String) {
        query = text
        selection = emptySet()
        focusId = null
    }

    // ---- Practice badges & smart groups ----

    /** How often a card has been through a session, as far as the board folder's stores know. */
    enum class Practice { UNSEEN, SEEN, REDO }

    /** Re-reads the seen/redo stores — call after a session started from this board ends. */
    fun refreshPractice() {
        val dir = root ?: return
        seen = SeenStore.read(dir)
        redo = RedoStore.read(dir)
        practiceTick++
    }

    fun practiceOf(item: ImageItem): Practice {
        practiceTick // snapshot read: recompose when the stores are re-read
        return when {
            item.path in redo -> Practice.REDO
            item.path in seen -> Practice.SEEN
            else -> Practice.UNSEEN
        }
    }

    /** True once this board has any practice history — smart groups stay hidden until then. */
    val hasPracticeHistory: Boolean
        get() {
            practiceTick
            return seen.isNotEmpty() || redo.isNotEmpty()
        }

    /**
     * Rule-based sections shown above the real groups: what to redo, and what has never been
     * drawn. They are views, not groups — a card keeps its own group membership.
     */
    val smartSections: List<Pair<String, List<BoardItem>>>
        get() {
            if (!hasPracticeHistory) return emptyList()
            val images = displayItems.filterIsInstance<ImageItem>()
            val flagged = images.filter { practiceOf(it) == Practice.REDO }
            val never = images.filter { practiceOf(it) == Practice.UNSEEN }
            return buildList {
                if (flagged.isNotEmpty()) add("⟳ Redo" to flagged)
                if (never.isNotEmpty()) add("Never drawn" to never)
            }
        }

    /** Items of one group (null = Inbox) that pass the tag filter, in stored order. */
    fun itemsIn(groupId: String?): List<BoardItem> =
        board?.items.orEmpty()
            .filter { if (groupId == null) it.groups.isEmpty() else groupId in it.groups }
            .filter(::visible)

    // ---- One level of subgroups ----

    fun groupById(id: String?): BoardGroup? = id?.let { g -> sortedGroups.find { it.id == g } }

    /** The group [id] sits inside, if any. */
    fun parentOf(id: String): BoardGroup? = groupById(groupById(id)?.parentId)

    /** Groups directly inside [parentId] (null = the top level), in order. */
    fun subgroupsOf(parentId: String?): List<BoardGroup> = sortedGroups.filter { it.parentId == parentId }

    /** A group's cards *and* its subgroups' cards: what "draw the group" and its count mean. */
    fun itemsInTree(groupId: String): List<BoardItem> =
        itemsIn(groupId) + subgroupsOf(groupId).flatMap { itemsIn(it.id) }

    /** Top-level groups a group could be moved into — not itself, and none that has subgroups
     *  of its own if the group in question holds any, since the tree stays one level deep. */
    fun possibleParents(groupId: String?): List<BoardGroup> =
        subgroupsOf(null).filter { candidate ->
            candidate.id != groupId && (groupId == null || subgroupsOf(groupId).isEmpty())
        }

    /**
     * Inbox first, then each top-level group followed by its subgroups — the board's display
     * structure, which is also why a subgroup sits right under its parent in grid mode.
     */
    val sections: List<Pair<BoardGroup?, List<BoardItem>>>
        get() {
            val result = mutableListOf<Pair<BoardGroup?, List<BoardItem>>>(null to itemsIn(null))
            subgroupsOf(null).forEach { parent ->
                result += parent to itemsIn(parent.id)
                subgroupsOf(parent.id).forEach { child -> result += child to itemsIn(child.id) }
            }
            return result
        }

    /** Flattened display order (collapsed groups excluded) — basis for range select and focus. */
    val visibleOrder: List<BoardItem>
        get() = sections.flatMap { (group, items) -> if (isFolded(group)) emptyList() else items }

    /** A section is folded when its group, or the group above it, is collapsed. */
    fun isFolded(group: BoardGroup?): Boolean =
        group != null && (group.collapsed || groupById(group.parentId)?.collapsed == true)

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

    /** How far a deletion goes. The board file is ActionDraw's; the pictures are the user's. */
    enum class Deletion { FORGET, DELETE_FOLDER }

    /**
     * Deletes the board at [dir].
     *
     * [Deletion.FORGET] removes only the sidecar (and its backup), so the folder and every
     * picture in it survive — the folder simply stops being a board. [Deletion.DELETE_FOLDER]
     * removes the folder itself, which is the only genuinely destructive thing this app does and
     * is therefore never the default. Refuses obviously wrong targets (a drive root, the user's
     * home, the boards home itself). Returns a line to show the user.
     */
    fun deleteBoard(dir: File, mode: Deletion): String {
        if (!dir.isDirectory) return "That folder is gone already."
        if (mode == Deletion.DELETE_FOLDER && !safeToDeleteFolder(dir)) {
            return "Refusing to delete ${dir.name} — that is not a board folder."
        }

        // If it is the board on screen, leave it before the file underneath disappears.
        if (root?.samePathAs(dir) == true) {
            root = null
            board = null
            selection = emptySet()
            focusId = null
            closeStrip()
        }

        val message = when (mode) {
            Deletion.FORGET -> {
                val removed = File(dir, BoardStore.FILE_NAME).delete()
                File(dir, BoardStore.FILE_NAME + ".bak").delete()
                if (removed) "Board removed. The pictures are still in ${dir.name}."
                else "Couldn't remove the board file in ${dir.name}."
            }


            Deletion.DELETE_FOLDER ->
                if (dir.deleteRecursively()) "Deleted ${dir.name} and everything in it."
                else "Couldn't delete ${dir.name} — something in it is in use."
        }

        // A deleted folder takes any board nested inside it, so their records go too.
        if (mode == Deletion.DELETE_FOLDER) registry.descendantsOf(dir).forEach { registry.forget(it.dir) }
        registry.forget(dir)
        settings.removeRecentBoard(dir)
        recent = settings.recentBoards()
        boardsHomeTick++ // the board list is keyed on this
        importNotice = message
        return message
    }

    /** Guards the destructive path against paths nobody means to erase. */
    private fun safeToDeleteFolder(dir: File): Boolean {
        val target = runCatching { dir.canonicalFile }.getOrNull() ?: return false
        if (target.parentFile == null) return false // a drive root
        val home = runCatching { File(System.getProperty("user.home")).canonicalFile }.getOrNull()
        if (target == home) return false
        val boardsHome = runCatching { settings.boardsHome().canonicalFile }.getOrNull()
        if (target == boardsHome) return false
        // Only a folder ActionDraw actually knows as a board may be removed wholesale.
        return BoardStore.exists(target)
    }

    /**
     * Creates `<parent>/<name>` as a fresh board, with [template]'s starter groups. Returns an
     * error message, or null on success.
     */
    fun createBoard(parent: File, name: String, template: BoardTemplate = BoardTemplate.ALL.first()): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "Give the board a name."
        // "Do I already have a board called this?" is a question for the registry: the board's
        // name and its folder's name are no longer the same thing.
        val known = registry.entries().firstOrNull {
            it.name.equals(trimmed, ignoreCase = true) &&
                it.dir.parentFile?.samePathAs(parent) == true &&
                BoardStore.exists(it.dir)
        }
        if (known != null) { // already a board -> just open it
            openBoard(known.dir)
            return null
        }
        val wanted = File(parent, sanitizeName(trimmed))
        if (BoardStore.exists(wanted)) { // a board here the registry has not met yet
            openBoard(wanted)
            return null
        }
        // A board's folder is where its files go, not what the board *is*. So a leftover folder
        // keeps the name it has and the new board gets one beside it, still called [trimmed] --
        // deleting a board must never make its name unusable.
        val dir = if (wanted.exists() && !wanted.listFiles().isNullOrEmpty()) freeFolder(wanted) else wanted
        if (!dir.isDirectory && !dir.mkdirs()) return "Couldn't create:\n$dir"
        // Creating a board inside another board is making a sub-board of it; that is nesting,
        // not a decision about where new top-level boards should go.
        if (registry.parentOf(dir) == null) settings.setBoardsHome(parent)
        val created = BoardFile(
            name = trimmed,
            groups = template.groups.mapIndexed { i, groupName ->
                BoardGroup(id = Importer.newId(), name = groupName, order = i + 1)
            },
        )
        if (!BoardStore.save(dir, created)) return "Couldn't write the board file in:\n$dir"
        registry.register(trimmed, dir, ownsFolder = true)
        root = dir
        board = created
        openedFromBackup = false
        openFailed = false
        afterOpen(dir)
        return null
    }

    /** `Test` -> `Test (2)`. Unlike file naming this never splits a dot off as an extension. */
    private fun freeFolder(wanted: File): File {
        if (!wanted.exists()) return wanted
        var n = 2
        while (true) {
            val candidate = File(wanted.parentFile, wanted.name + " (" + n + ")")
            if (!candidate.exists()) return candidate
            n++
        }
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
        query = ""
        closeViewer()
        editor = null
        immersive = false
        val camera = board?.camera ?: Camera()
        camX = camera.x
        camY = camera.y
        zoom = camera.zoom
        if (layout == BoardLayouts.FREE) update { placeMissing(it) }
        refreshPractice()
        settings.addRecentBoard(dir)
        recent = settings.recentBoards()
        if (registry.entryFor(dir) == null) adopt(dir)
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

    /** Rewrites the one item of type [T] with this [id]; everything else on the board is untouched. */
    private inline fun <reified T : BoardItem> updateItem(id: String, crossinline change: (T) -> T) =
        update { b -> b.copy(items = b.items.map { if (it is T && it.id == id) change(it) else it }) }

    // Groups

    fun addGroup(name: String, parentId: String? = null) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val parent = parentId?.takeIf { groupById(it)?.parentId == null } // one level, no deeper
        update { b ->
            val order = (b.groups.maxOfOrNull { it.order } ?: 0) + 1
            b.copy(groups = b.groups + BoardGroup(id = Importer.newId(), name = trimmed, order = order, parentId = parent))
        }
    }

    /**
     * Puts a group inside [parentId], or at the top level with null. Refused when it would make
     * the tree deeper than one level: a group that has subgroups cannot become one, and a
     * subgroup cannot be the parent.
     */
    fun setGroupParent(id: String, parentId: String?): Boolean {
        if (parentId == id) return false
        if (parentId != null) {
            if (groupById(parentId)?.parentId != null) return false
            if (subgroupsOf(id).isNotEmpty()) return false
        }
        update { b -> b.copy(groups = b.groups.map { if (it.id == id) it.copy(parentId = parentId) else it }) }
        return true
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

    /**
     * Removes the group; its own cards fall back into the Inbox, and any subgroups it held move
     * up to the top level with their cards, since nothing about *them* was deleted.
     */
    fun deleteGroup(id: String) = update { b ->
        b.copy(
            groups = b.groups.filterNot { it.id == id }.map { if (it.parentId == id) it.copy(parentId = null) else it },
            items = b.items.map { if (id in it.groups) it.withGroups(it.groups - id) else it },
        )
    }

    // Items

    /**
     * Makes a group out of the current selection — the natural way to group on the canvas, where
     * there are no sections to drop things into. Returns the new group's id, or null if nothing
     * was selected.
     */
    fun groupSelection(name: String, parentId: String? = null): String? {
        val ids = selection
        if (ids.isEmpty()) return null
        val id = Importer.newId()
        val trimmed = name.trim().ifEmpty { "Group" }
        val parent = parentId?.takeIf { groupById(it)?.parentId == null }
        update { b ->
            val order = (b.groups.maxOfOrNull { it.order } ?: 0) + 1
            b.copy(
                groups = b.groups + BoardGroup(id = id, name = trimmed, order = order, parentId = parent),
                items = b.items.map { if (it.id in ids) it.withGroups(listOf(id)) else it },
            )
        }
        pruneEmptyGroups()
        return id
    }

    /**
     * The group the selected cards would naturally nest under: the one top-level group they all
     * belong to, if there is one. Offered as the default when grouping a selection.
     */
    fun sharedParentOfSelection(): BoardGroup? {
        // Each card's group, resolved up to the top level: a card in Membran is under Flügel too.
        val tops = selection.mapNotNull { id ->
            val group = groupById(item(id)?.groups?.firstOrNull()) ?: return@mapNotNull null
            if (group.parentId == null) group else groupById(group.parentId)
        }.toSet()
        return tops.singleOrNull()
    }

    /**
     * What "group" means depends on the situation: with cards selected it groups them, otherwise
     * it starts an empty group. Having two separate commands (one of which quietly made an empty
     * group while cards were selected) was a trap.
     */
    fun startGrouping() {
        openEditor(
            if (selection.isEmpty()) BoardEditor.NewGroup()
            else BoardEditor.GroupSelection(sharedParentOfSelection()?.id),
        )
    }

    /** Takes the given cards out of every group they are in; the cards themselves stay put. */
    fun ungroupItems(ids: Set<String>) {
        if (ids.isEmpty()) return
        update { b -> b.copy(items = b.items.map { if (it.id in ids) it.withGroups(emptyList()) else it }) }
        pruneEmptyGroups()
    }

    /**
     * Dissolves a group: the group disappears, its cards stay exactly where they are — inside
     * the parent, when the group was a subgroup, since that is the group they were in as well.
     */
    fun ungroup(groupId: String) {
        val parent = groupById(groupId)?.parentId
        if (parent == null) {
            deleteGroup(groupId)
        } else {
            update { b ->
                b.copy(
                    groups = b.groups.filterNot { it.id == groupId },
                    items = b.items.map {
                        if (groupId in it.groups) it.withGroups((it.groups - groupId + parent).distinct()) else it
                    },
                )
            }
        }
    }

    /**
     * Drops groups that no longer hold anything. Without this, ungrouping would leave empty
     * groups behind that are invisible on the canvas but still clutter the drawer.
     */
    private fun pruneEmptyGroups() = update { b ->
        val used = b.items.flatMap { it.groups }.toSet()
        val parentsInUse = b.groups.filter { it.id in used }.mapNotNull { it.parentId }.toSet()
        b.copy(groups = b.groups.filter { it.id in used || it.id in parentsInUse })
    }

    fun moveToGroup(ids: Set<String>, groupId: String?) = update { b ->
        b.copy(items = b.items.map { if (it.id in ids) it.withGroups(listOfNotNull(groupId)) else it })
    }

    fun toggleStar(ids: Set<String>) = update { b ->
        val images = b.items.filterIsInstance<ImageItem>().filter { it.id in ids }
        if (images.isEmpty()) return@update b
        val allStarred = images.all { it.starred }
        b.copy(items = b.items.map { if (it is ImageItem && it.id in ids) it.copy(starred = !allStarred) else it })
    }

    fun setCaption(id: String, caption: String) =
        updateItem<ImageItem>(id) { it.copy(caption = caption.trim().ifEmpty { null }) }

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
            updateItem<NoteItem>(itemId) { it.copy(text = trimmed) }
        }
    }

    fun setNoteColor(id: String, color: String?) = updateItem<NoteItem>(id) { it.copy(color = color) }

    fun toggleNoteHeading(id: String) = updateItem<NoteItem>(id) { it.copy(heading = !it.heading) }

    /** Creates or updates a link card. A blank url is ignored. */
    fun saveLink(itemId: String?, url: String, title: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        val name = title.trim()
        if (itemId == null) {
            val link = LinkItem(id = Importer.newId(), url = trimmed, title = name)
            update { it.copy(items = it.items + link) }
            if (layout == BoardLayouts.FREE) update { placeMissing(it, camX, camY) }
            selection = setOf(link.id)
            focusId = link.id
        } else {
            updateItem<LinkItem>(itemId) { it.copy(url = trimmed, title = name) }
        }
    }

    /**
     * Fetches the page's preview picture into the board folder and hangs it on the card. This is
     * the one place the app makes a network request, and only ever because the user asked for it
     * on a particular card. Blocking — call from a background dispatcher.
     */
    fun fetchLinkPreview(itemId: String, fetcher: LinkPreview.Fetcher = LinkPreview.http): String {
        val dir = root ?: return "No board open."
        val link = item(itemId) as? LinkItem ?: return "That card is not a link."
        val label = link.title.ifBlank { LinkPreview.normalize(link.url)?.let { URI(it).host } ?: "link" }
        return when (val result = LinkPreview.fetchInto(dir, link.url, label, fetcher)) {
            is LinkPreview.Result.Saved -> {
                updateItem<LinkItem>(itemId) { it.copy(preview = result.path) }
                "Preview fetched for ${link.title.ifBlank { link.url }}.".also { importNotice = it }
            }

            is LinkPreview.Result.Failed -> result.reason.also { importNotice = it }
        }
    }

    /** Drops a fetched preview (the file stays in `_previews/` until the folder is tidied). */
    fun clearLinkPreview(itemId: String) = updateItem<LinkItem>(itemId) { it.copy(preview = null) }

    /** The saved preview file of a link card, if it still exists. */
    fun previewFileOf(item: LinkItem): File? =
        root?.let { dir -> item.preview?.let { File(dir, it) } }?.takeIf { it.isFile }

    /** Opens a link card in the system browser — ActionDraw never loads a page itself. */
    fun openLink(item: LinkItem) = openUrl(item.url)

    /**
     * How a url leaves the app: the system browser. Replaceable so a test can see that a link
     * inside a note was followed without a browser window opening on the build machine.
     */
    var linkOpener: (String) -> Unit = { url ->
        runCatching {
            Desktop.getDesktop().browse(URI(if (url.contains("://")) url else "https://$url"))
        }
    }

    /** Opens any address — a link card's, or a `[text](url)` inside a note. */
    fun openUrl(url: String) = linkOpener(url)

    /** Dominant colours of the given cards, in display order (empty for notes and links). */
    fun palettesOf(ids: Set<String>): List<Pair<ImageItem, List<Int>>> =
        displayItems.filterIsInstance<ImageItem>()
            .filter { it.id in ids }
            .mapNotNull { item -> fileOf(item)?.let { item to Palette.of(it) } }
            .filter { it.second.isNotEmpty() }

    /**
     * Writes a printable contact sheet of [items] to [target]; returns a line for the board's
     * notice area either way.
     */
    fun exportContactSheet(items: List<BoardItem>, target: File): String {
        val dir = root ?: return "No board open."
        val name = board?.name ?: dir.name
        val ok = ContactSheet.write(dir, items, name, target)
        importNotice = if (ok) "Contact sheet written to ${target.name}." else "Nothing to put on a sheet."
        return importNotice!!
    }

    /** What a contact sheet would contain: the selection, else everything on screen. */
    val sheetItems: List<BoardItem>
        get() = displayItems.filter { it is ImageItem }.let { shown ->
            shown.filter { it.id in selection }.ifEmpty { shown }
        }

    // ---- Groups on the freeform canvas ----

    /**
     * The area a group occupies on the canvas: the bounding box of its cards, in board units,
     * plus the colour it is drawn in. Gives a group a visible identity on a surface that
     * otherwise only has loose cards.
     */
    data class GroupHull(
        val group: BoardGroup,
        val color: String,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val count: Int,
        /** The padded boxes the frame is the union of — the cards', plus any subgroup's bounds. */
        val boxes: List<List<Float>> = emptyList(),
        /** Thin connectors between pieces of the union that do not touch. */
        val bridges: List<List<Float>> = emptyList(),
    )

    /** Padding between a group's cards and the edge of its hull, in board units. */
    private val hullPadding = BASE_SIZE * 0.18f

    /**
     * Half the width and half the height of a card in board units. Cards are as wide as
     * [BASE_SIZE] times their scale and as tall as that divided by the picture's aspect — a
     * portrait photograph is much taller than it is wide, which anything measuring cards has to
     * take into account.
     */
    private fun halfSizeOf(item: BoardItem): Pair<Float, Float> {
        val scale = item.pos?.scale ?: 1f
        val aspect = ((item as? ImageItem)?.aspect ?: 1f).coerceIn(0.2f, 5f)
        val width = BASE_SIZE * scale
        return (width / 2f) to (width / aspect / 2f)
    }

    /** One hull per group that has placed, currently visible cards. */
    val groupHulls: List<GroupHull>
        get() {
            val shown = freeItems
            // Subgroups first, so a parent can take its children's finished hulls into its own.
            val childHulls = mutableMapOf<String, List<Float>>()
            fun boxesOf(group: BoardGroup): List<List<Float>> =
                shown.filter { group.id in it.groups && it.pos != null }.map { item ->
                    val pos = item.pos!!
                    val (halfW, halfH) = halfSizeOf(item)
                    listOf(pos.x - halfW, pos.y - halfH, pos.x + halfW, pos.y + halfH)
                }
            val ordered = sortedGroups.sortedBy { if (it.parentId == null) 1 else 0 }
            val hulls = ordered.mapNotNull { group ->
                val index = sortedGroups.indexOf(group)
                val raw = boxesOf(group) + sortedGroups.filter { it.parentId == group.id }.mapNotNull { childHulls[it.id] }
                if (raw.isEmpty()) return@mapNotNull null
                // Grown by the padding here, so the union the canvas draws and the bounds below
                // agree on where the frame's edge is.
                val boxes = raw.map { listOf(it[0] - hullPadding, it[1] - hullPadding, it[2] + hullPadding, it[3] + hullPadding) }
                val members = shown.count { it.pos != null && it.groups.any { g -> g == group.id || groupById(g)?.parentId == group.id } }
                GroupHull(
                    group = group,
                    // A group without its own accent still needs to be told apart from the next.
                    color = group.color ?: fallbackGroupColor(index),
                    left = boxes.minOf { it[0] },
                    top = boxes.minOf { it[1] },
                    right = boxes.maxOf { it[2] },
                    bottom = boxes.maxOf { it[3] },
                    count = members,
                    boxes = boxes,
                    bridges = FrameShape.bridges(boxes),
                ).also { hull -> childHulls[group.id] = listOf(hull.left, hull.top, hull.right, hull.bottom) }
            }
            // Parents drawn first (underneath), then their subgroups on top of the tint.
            return hulls.sortedBy { if (it.group.parentId == null) 0 else 1 }
        }

    /** The colour a group is drawn in: its own accent, or a distinct one derived from its place. */
    fun accentOfGroup(group: BoardGroup): String {
        val index = sortedGroups.indexOfFirst { it.id == group.id }
        return group.color ?: fallbackGroupColor(index.coerceAtLeast(0))
    }

    /** The accent a card should show, so a grouped card is recognisable on its own too. */
    fun accentOf(item: BoardItem): String? {
        val groups = sortedGroups
        val index = groups.indexOfFirst { it.id in item.groups }
        if (index < 0) return null
        return groups[index].color ?: fallbackGroupColor(index)
    }

    /** Moves every card of a group together — the group behaves as one object. */
    fun dragGroupBy(groupId: String, dx: Float, dy: Float) = updateTransient { b ->
        val inTree = setOf(groupId) + b.groups.filter { it.parentId == groupId }.map { it.id }
        b.copy(items = b.items.map { item ->
            val pos = item.pos
            if (item.groups.any { it in inTree } && pos != null) {
                item.withPos(pos.copy(x = pos.x + dx, y = pos.y + dy))
            } else {
                item
            }
        })
    }

    /** Selects a whole group — clicking its label picks the group up as a unit. */
    fun selectGroup(groupId: String) {
        val inTree = setOf(groupId) + subgroupsOf(groupId).map { it.id }
        val ids = freeItems.filter { it.groups.any { g -> g in inTree } }.map { it.id }
        selection = ids.toSet()
        focusId = ids.firstOrNull()
    }

    // ---- Always-on-top reference strip ----

    /** Pictures the floating strip is showing; empty = the strip window is closed. */
    var stripIds by mutableStateOf<List<String>>(emptyList())
        private set

    var stripIndex by mutableStateOf(0)
        private set

    val stripOpen: Boolean get() = stripIds.isNotEmpty()

    val stripItem: ImageItem? get() = stripIds.getOrNull(stripIndex)?.let { item(it) as? ImageItem }

    /** Opens (or refills) the floating strip with what the viewer would show. */
    fun openStrip() {
        val ids = viewableIds
        if (ids.isEmpty()) return
        stripIds = ids
        stripIndex = ids.indexOf(focusId).coerceAtLeast(0)
    }

    fun closeStrip() {
        stripIds = emptyList()
        stripIndex = 0
    }

    fun stripStep(delta: Int) {
        val n = stripIds.size
        if (n == 0) return
        stripIndex = ((stripIndex + delta) % n + n) % n
    }

    fun stripGoTo(id: String) {
        stripIds.indexOf(id).takeIf { it >= 0 }?.let { stripIndex = it }
    }

    // ---- Freeform: snapping and marquee selection ----

    /** Align dragged cards to their neighbours' centres. */
    var snapping by mutableStateOf(true)

    /** Guides to draw while dragging (board-space x / y of the lines that matched). */
    var snapGuideX by mutableStateOf<Float?>(null)
        private set
    var snapGuideY by mutableStateOf<Float?>(null)
        private set

    /** Marquee rectangle in board space while rubber-band selecting; null when not dragging. */
    var marquee by mutableStateOf<List<Float>?>(null)
        private set

    fun startMarquee(x: Float, y: Float) {
        marquee = listOf(x, y, x, y)
    }

    fun updateMarquee(x: Float, y: Float) {
        marquee?.let { marquee = listOf(it[0], it[1], x, y) }
    }

    /** Selects every card the rubber band touches. */
    fun commitMarquee() {
        val rect = marquee ?: return
        marquee = null
        val left = minOf(rect[0], rect[2])
        val right = maxOf(rect[0], rect[2])
        val top = minOf(rect[1], rect[3])
        val bottom = maxOf(rect[1], rect[3])
        val hits = freeItems.filter { item ->
            val pos = item.pos ?: return@filter false
            val (halfW, halfH) = halfSizeOf(item)
            pos.x + halfW >= left && pos.x - halfW <= right &&
                pos.y + halfH >= top && pos.y - halfH <= bottom
        }
        selection = hits.map { it.id }.toSet()
        focusId = hits.lastOrNull()?.id
    }

    fun cancelMarquee() {
        marquee = null
    }

    /**
     * Snaps a dragged card to the centre line of a nearby neighbour, within [threshold] board
     * units, and records the guides to draw. Returns the corrected position.
     */
    fun snapPosition(id: String, x: Float, y: Float, threshold: Float): Pair<Float, Float> {
        if (!snapping) {
            snapGuideX = null
            snapGuideY = null
            return x to y
        }
        val others = freeItems.filter { it.id != id && it.id !in selection }.mapNotNull { it.pos }
        val nearX = others.minByOrNull { kotlin.math.abs(it.x - x) }?.takeIf { kotlin.math.abs(it.x - x) <= threshold }
        val nearY = others.minByOrNull { kotlin.math.abs(it.y - y) }?.takeIf { kotlin.math.abs(it.y - y) <= threshold }
        snapGuideX = nearX?.x
        snapGuideY = nearY?.y
        return (nearX?.x ?: x) to (nearY?.y ?: y)
    }

    fun clearSnapGuides() {
        snapGuideX = null
        snapGuideY = null
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
        focusId = null
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
        query = ""
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

    /**
     * Drops the dragged card [id] onto [targetId] within group [groupId]: it takes the target's
     * place, pushing it aside in the direction the card came from.
     */
    fun dropOn(id: String, targetId: String, groupId: String?) {
        if (id == targetId) return
        val order = itemsIn(groupId).map { it.id }
        val from = order.indexOf(id)
        val to = order.indexOf(targetId)
        if (from < 0 || to < 0) return
        moveRelative(id, targetId, after = to > from)
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
        val placed = board?.items.orEmpty().filter { it.pos != null }
        if (placed.isEmpty() || viewW <= 0f || viewH <= 0f) return
        val boxes = placed.map { item ->
            val pos = item.pos!!
            val (halfW, halfH) = halfSizeOf(item)
            listOf(pos.x - halfW, pos.y - halfH, pos.x + halfW, pos.y + halfH)
        }
        val minX = boxes.minOf { it[0] }
        val maxX = boxes.maxOf { it[2] }
        val minY = boxes.minOf { it[1] }
        val maxY = boxes.maxOf { it[3] }
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
        viewerZoom = 1f
    }

    fun toggleViewer() {
        if (viewerOpen) closeViewer() else openViewer()
    }

    fun closeViewer() {
        viewerIds = emptyList()
        viewerIndex = 0
        viewerZoom = 1f
    }

    /** Carousel step; wraps around so flipping never dead-ends. */
    fun viewerStep(delta: Int) {
        val n = viewerIds.size
        if (n == 0) return
        viewerIndex = ((viewerIndex + delta) % n + n) % n
        focusId = viewerIds[viewerIndex]
        viewerZoom = 1f
    }

    fun viewerGoTo(index: Int) {
        if (index !in viewerIds.indices) return
        viewerIndex = index
        focusId = viewerIds[index]
        viewerZoom = 1f
    }

    /**
     * Magnification of the picture on screen: 1 is fitted to the view, [VIEWER_MAX_ZOOM] the
     * most it will enlarge. Lives here rather than in the composable so the keys can reach it;
     * every change of picture starts fitted again.
     */
    var viewerZoom by mutableStateOf(1f)
        private set

    /** Zooms by [factor] — above 1 in, below 1 out — and never smaller than fitted. */
    fun viewerZoomBy(factor: Float) {
        viewerZoom = (viewerZoom * factor).coerceIn(1f, VIEWER_MAX_ZOOM)
    }

    fun viewerResetZoom() {
        viewerZoom = 1f
    }

    // ---- Material in / out / draw ----

    fun importExternal(files: List<File>, groupId: String? = null) {
        val dir = root ?: return
        val existing = board?.items.orEmpty().filterIsInstance<ImageItem>().map { it.path }.toSet()
        val outcome = Importer.importFiles(dir, files, groupId, existing)
        importNotice = describeImport(outcome)
        val items = outcome.items
        if (items.isEmpty()) return
        update { it.copy(items = it.items + items) }
        if (layout == BoardLayouts.FREE) update { placeMissing(it, camX, camY) }
        selection = items.map { it.id }.toSet()
        focusId = items.first().id
    }

    /** A one-line report of what an import did — shown on the board, `null` when all went in. */
    private fun describeImport(outcome: Importer.Outcome): String? {
        val parts = mutableListOf<String>()
        if (outcome.unsupported.isNotEmpty()) {
            val kinds = outcome.unsupported.map { it.extension.lowercase().ifEmpty { "?" } }
                .distinct().sorted().joinToString(", ") { ".$it" }
            parts += "${outcome.unsupported.size} file(s) this build cannot read ($kinds)"
        }
        if (outcome.duplicates > 0) parts += "${outcome.duplicates} already on the board"
        if (parts.isEmpty()) return null
        val added = if (outcome.items.isEmpty()) "Nothing added" else "Added ${outcome.items.size}"
        return "$added — skipped ${parts.joinToString(" · ")}."
    }

    fun dismissImportNotice() {
        importNotice = null
    }

    fun importPasted() {
        val dir = root ?: return
        when (val pasted = BoardClipboard.paste()) {
            is BoardClipboard.Pasted.Files -> importExternal(pasted.files)

            is BoardClipboard.Pasted.Bitmap -> {
                val item = Importer.importBitmap(dir, pasted.image, null, timestamp()) ?: return
                importNotice = null
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
        draw((if (groupId == null) itemsIn(null) else itemsInTree(groupId)).filterIsInstance<ImageItem>().mapNotNull(::fileOf))

    /** Draw exactly these cards — used by the smart sections' Draw buttons. */
    fun drawItems(items: List<BoardItem>) =
        draw(items.filterIsInstance<ImageItem>().mapNotNull(::fileOf))

    private fun draw(files: List<File>) {
        val dir = root ?: return
        if (files.isNotEmpty()) host.startSession(dir, files, sessionSetup())
    }

    // ---- Session recipe ----

    val recipe: SessionRecipe? get() = board?.session

    /** The stored recipe translated into the practice side's vocabulary; null = keep the menu's. */
    private fun sessionSetup(): SessionSetup? {
        val stored = recipe ?: return null
        // Boards written before the temperature slider may name the retired WARM/COOL view modes.
        // Those were a fixed white balance, so carry the intent over to the slider rather than
        // quietly opening the board under neutral light.
        val retired = when (stored.viewMode.uppercase()) {
            "WARM" -> 0.6f
            "COOL" -> -0.6f
            else -> null
        }
        return SessionSetup(
            plan = SessionPlans.ALL.find { it.name == stored.plan },
            intervalSeconds = stored.intervalSeconds,
            autoAdvance = stored.autoAdvance,
            viewMode = runCatching { ViewMode.valueOf(stored.viewMode) }.getOrDefault(ViewMode.NONE),
            gridMode = runCatching { GridMode.valueOf(stored.grid) }.getOrDefault(GridMode.OFF),
            temperature = retired ?: stored.temperature,
        )
    }

    fun saveRecipe(recipe: SessionRecipe?) = update { it.copy(session = recipe) }

    /** Stores whatever the practice side is set to right now as this board's recipe. */
    fun rememberCurrentSetup() {
        val setup = host.currentSetup()
        saveRecipe(
            SessionRecipe(
                plan = setup.plan?.name,
                intervalSeconds = setup.intervalSeconds,
                autoAdvance = setup.autoAdvance,
                viewMode = setup.viewMode.name,
                grid = setup.gridMode.name,
                temperature = setup.temperature,
            ),
        )
    }

    // ---- Pinning from a session ----

    /**
     * Files a picture away on another board without opening it: the file is copied into that
     * board's folder and a card is appended. Returns a line to show the user.
     */
    fun pinTo(dir: File, files: List<File>): String {
        val target = when (val loaded = BoardStore.load(dir)) {
            is BoardStore.LoadResult.Loaded -> loaded.board
            BoardStore.LoadResult.None -> BoardFile(name = dir.name)
            BoardStore.LoadResult.Failed -> return "Couldn't read the board in ${dir.name}."
        }
        val existing = target.items.filterIsInstance<ImageItem>().map { it.path }.toSet()
        val outcome = Importer.importFiles(dir, files, null, existing)
        if (outcome.items.isEmpty()) {
            return if (outcome.duplicates > 0) "Already on ${target.name}." else "Nothing to pin."
        }
        var next = target.copy(items = target.items + outcome.items)
        if (next.layout == BoardLayouts.FREE) next = placeMissing(next)
        if (!BoardStore.save(dir, next)) return "Couldn't write the board in ${dir.name}."
        // Pinning into the board that is currently open must show up straight away.
        if (root?.samePathAs(dir) == true) board = next
        return "Pinned ${outcome.items.size} to ${next.name}."
    }

    /**
     * Moves a board under [into] — or out to the boards home when that is null — taking its
     * folder, its pictures and any boards nested inside it along. Nesting *is* where the folder
     * sits, so rearranging the hierarchy means moving it; there is no second place to keep the
     * shape of the tree, and so no way for the two to disagree.
     *
     * Returns what happened, for the board to report.
     */
    fun moveBoard(dir: File, into: File?): String {
        if (!BoardStore.exists(dir)) return "${dir.name} is not a board."
        val name = entryFor(dir)?.name ?: dir.name
        val target = (into ?: settings.boardsHome()).absoluteFile
        // Compared canonically: a junction or symlink pointing back into the board would slip
        // past a plain string test, and moving a folder into itself copies it into itself until
        // the filesystem gives out. This guard is the only thing between that and the board.
        val realDir = runCatching { dir.canonicalFile }.getOrDefault(dir.absoluteFile)
        val realTarget = runCatching { target.canonicalFile }.getOrDefault(target)
        if (realTarget.samePathAs(realDir)) return "A board cannot be moved into itself."
        if (realTarget.isInside(realDir)) return "A board cannot be moved into one of its own sub-boards."
        if (!target.isDirectory && !target.mkdirs()) return "Couldn't open ${target.name}."
        if (dir.parentFile?.samePathAs(target) == true) return "$name is already there."

        val destination = freeFolder(File(target, dir.name))
        // The open board may be the one moving, or may live inside it; either way it has to come
        // out at the other end rather than pointing at a folder that is no longer there.
        val openInside = root?.let { it.samePathAs(dir) || it.isInside(dir) } == true
        val openSuffix = if (openInside) root!!.absolutePath.substring(dir.absolutePath.length) else ""

        val failure = moveFolder(dir, destination)
        if (failure != null) return failure.also { importNotice = it }

        registry.repath(dir, destination)
        settings.removeRecentBoard(dir)
        settings.addRecentBoard(destination)
        recent = settings.recentBoards()
        if (openInside) root = File(destination.absolutePath + openSuffix)
        boardsHomeTick++
        val where = if (into == null) "the boards home" else (entryFor(target)?.name ?: target.name)
        return "Moved $name to $where.".also { importNotice = it }
    }

    /**
     * Rename where the filesystem allows it, copy-then-delete where it does not (another drive).
     * A copy that fails part-way is cleaned up, so the board is never left in two halves: either
     * it moved, or nothing happened to it.
     */
    private fun moveFolder(from: File, to: File): String? {
        // Never copy a folder into its own subtree, whatever the caller believed.
        if (to.samePathAs(from) || to.isInside(from)) return "Couldn't move ${from.name} into itself."
        if (from.renameTo(to)) return null
        val copied = runCatching { from.copyRecursively(to, overwrite = false) }.isSuccess
        if (!copied) {
            to.deleteRecursively()
            return "Couldn't move ${from.name}."
        }
        if (!from.deleteRecursively()) {
            // It moved; the original just could not be cleared away. Say so, rather than call a
            // move that worked a failure.
            importNotice = "Moved ${from.name}, but its old folder could not be removed."
        }
        return null
    }

    /**
     * Where [dir] could go: the boards home, then every board that is neither it nor inside it.
     * Its current parent is in the list too, marked by the dialog, so the tree reads the same
     * there as everywhere else.
     */
    fun moveTargets(dir: File): List<BoardNode> =
        boardTree().filterNot { it.dir.samePathAs(dir) || it.dir.isInside(dir) }

    fun openBoardList() {
        openFailed = false
        host.showBoardList()
    }

    /** Leaves the board list without opening anything. */
    fun leaveList() {
        openFailed = false
        host.leaveBoard()
    }

    companion object {
        /** One wheel tick or key press: enough to feel, few enough to steer. */
        const val VIEWER_ZOOM_STEP = 1.2f
        const val VIEWER_MAX_ZOOM = 8f

        /** Colour accents a group cycles through (null = no accent). */
        val GROUP_COLORS: List<String?> =
            listOf(null, "#80CBC4", "#FFB74D", "#A5D6A7", "#EF9A9A", "#B39DDB")

        /** Distinct colour for a group that has none of its own, by its position on the board. */
        fun fallbackGroupColor(index: Int): String {
            val named = GROUP_COLORS.filterNotNull()
            return named[index % named.size]
        }

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
            // Rows are spaced more generously than columns: a portrait card is far taller than
            // it is wide, and cascading them at one card's height made them overlap.
            val gap = BASE_SIZE * 1.3f
            val rowGap = BASE_SIZE * 1.9f
            val perRow = 5
            val startY = (board.items.mapNotNull { it.pos }.maxOfOrNull { it.y + BASE_SIZE } ?: originY)
            // Centre the row over the cards that will actually be in it: centring a full row of
            // five put a lone new note two cards' widths off the left of the screen.
            val inRow = minOf(unplaced, perRow)
            val startX = originX - (inRow - 1) * gap / 2
            var i = 0
            return board.copy(items = board.items.map { item ->
                if (item.pos != null) item
                else item.withPos(ItemPos(startX + (i % perRow) * gap, startY + (i / perRow) * rowGap)).also { i++ }
            })
        }
    }
}
