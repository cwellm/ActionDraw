package de.creaflect.actiondraw.board

/**
 * How a linked concept appears on a board: as one group whose id is `concept:<id>` — the same
 * string its `source` carries — holding *borrowed* cards. A borrowed card is the board's copy of
 * a concept item: the same id, the concept's picture, text or address, and the board's own
 * opinions about it (its place, its star, its tags). A borrowed picture points at the concept's
 * file by a `concept:<id>/<path>` path, which `BoardState.fileOf` resolves through the concept.
 *
 * Pure functions over `BoardFile`; the state calls them when a board opens or a link changes.
 */
object ConceptLink {
    const val PREFIX = "concept:"

    fun groupId(conceptId: String): String = PREFIX + conceptId

    fun borrowedPath(conceptId: String, path: String): String = "$PREFIX$conceptId/$path"

    fun isBorrowedPath(path: String): Boolean = path.startsWith(PREFIX)

    /** A card in a concept group is the concept's, not the board's. */
    fun isBorrowed(item: BoardItem): Boolean = item.groups.any { it.startsWith(PREFIX) }

    /** (concept id, path inside the concept) for a borrowed picture path, else null. */
    fun splitBorrowed(path: String): Pair<String, String>? {
        if (!isBorrowedPath(path)) return null
        val rest = path.removePrefix(PREFIX)
        val slash = rest.indexOf('/')
        if (slash <= 0 || slash == rest.length - 1) return null
        return rest.substring(0, slash) to rest.substring(slash + 1)
    }

    /**
     * Reconciles a board with its linked concepts: one group per linked id, named after the
     * concept, and the borrowed cards brought up to date — new items appear, vanished ones go,
     * pictures, captions and text follow the concept while place, stars and tags stay the
     * board's. A concept [lookup] cannot find keeps what the board last saw of it; a group whose
     * concept is no longer linked goes, with its cards.
     */
    fun reconcile(board: BoardFile, lookup: (String) -> ConceptSnapshot?): BoardFile {
        val linked = board.concepts.toSet()
        // Only a group in the form this code writes (id `concept:<id>`) is judged stale; its cards
        // are borrowed by definition. Anything else with a source is left exactly as found.
        val stale = board.groups
            .filter { g -> g.id.startsWith(PREFIX) && g.conceptId?.let { it !in linked } == true }
            .map { it.id }
            .toSet()
        var groups = board.groups.filterNot { it.id in stale }
        var items = board.items.filterNot { it.groups.any { g -> g in stale } }

        for (id in board.concepts) {
            val snapshot = lookup(id) ?: continue
            val gid = groupId(id)
            groups = if (groups.none { it.id == gid }) {
                val order = (groups.maxOfOrNull { it.order } ?: 0) + 1
                groups + BoardGroup(id = gid, name = snapshot.name, order = order, source = gid)
            } else {
                groups.map { if (it.id == gid) it.copy(name = snapshot.name, parentId = null, source = gid) else it }
            }
            val before = items.filter { gid in it.groups }.associateBy { it.id }
            val fresh = snapshot.items.map { borrow(it, id, gid, before[it.id]) }
            val freshById = fresh.associateBy { it.id }
            // The board's order is kept: a card already here stays in its place, new ones go last.
            val kept = items.mapNotNull { item -> if (gid !in item.groups) item else freshById[item.id] }
            items = kept + fresh.filter { it.id !in before }
        }
        return board.copy(groups = groups, items = items)
    }

    private fun borrow(item: BoardItem, conceptId: String, gid: String, previous: BoardItem?): BoardItem = when (item) {
        is ImageItem -> {
            val old = previous as? ImageItem
            ImageItem(
                id = item.id,
                path = borrowedPath(conceptId, item.path),
                groups = listOf(gid),
                caption = item.caption,
                starred = old?.starred ?: false,
                tags = old?.tags ?: emptyList(),
                pos = old?.pos,
                aspect = item.aspect ?: old?.aspect,
                contentId = item.contentId,
            )
        }

        is NoteItem -> {
            val old = previous as? NoteItem
            item.copy(groups = listOf(gid), pos = old?.pos, color = old?.color ?: item.color, heading = old?.heading ?: item.heading)
        }

        is LinkItem -> {
            val old = previous as? LinkItem
            item.copy(groups = listOf(gid), pos = old?.pos, preview = old?.preview)
        }
    }
}
