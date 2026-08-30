package de.creaflect.actiondraw.board

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The Idea-Board sidecar (`.actiondraw_board.json`), stored next to the images it references.
 * Membership is explicit: the items listed here ARE the board — the folder is backing store, not
 * source (shaping A1). Image paths are relative to the board root, `/`-separated. Unknown JSON
 * keys are ignored on load so later phases (e.g. freeform `pos`) can extend the schema gently.
 */
@Serializable
data class BoardFile(
    val version: Int = 1,
    val name: String = "",
    val theme: String = "cork",
    val groups: List<BoardGroup> = emptyList(),
    val items: List<BoardItem> = emptyList(),
)

/** A named section of the board; `order` positions it, `color` is an optional `#rrggbb` accent. */
@Serializable
data class BoardGroup(
    val id: String,
    val name: String,
    val color: String? = null,
    val order: Int = 0,
    val collapsed: Boolean = false,
)

/**
 * One card. Items may belong to several groups; an empty [groups] list means the Inbox.
 * Serialized with a `"type"` discriminator (`image` / `note`).
 */
@Serializable
sealed class BoardItem {
    abstract val id: String
    abstract val groups: List<String>

    /** The same item with a different group membership (sealed-class-friendly copy). */
    fun withGroups(groups: List<String>): BoardItem = when (this) {
        is ImageItem -> copy(groups = groups)
        is NoteItem -> copy(groups = groups)
    }
}

@Serializable
@SerialName("image")
data class ImageItem(
    override val id: String,
    val path: String,
    override val groups: List<String> = emptyList(),
    val caption: String? = null,
    val starred: Boolean = false,
    val tags: List<String> = emptyList(),
) : BoardItem()

@Serializable
@SerialName("note")
data class NoteItem(
    override val id: String,
    val text: String,
    override val groups: List<String> = emptyList(),
) : BoardItem()

/** Theme ids stored in the sidecar; their rendering lives in the UI layer. */
object BoardThemes {
    const val CORK = "cork"
    const val PAPYRUS = "papyrus"
    const val PLAIN = "plain"
    val ALL = listOf(CORK, PAPYRUS, PLAIN)
}
