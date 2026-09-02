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
    /** [BoardLayouts.GRID] (grouped sections) or [BoardLayouts.FREE] (spatial canvas). */
    val layout: String = BoardLayouts.GRID,
    /** Last freeform camera, so the board reopens where you left it. */
    val camera: Camera? = null,
    /** How this board likes to be drawn; null = whatever the menu is set to. */
    val session: SessionRecipe? = null,
    val groups: List<BoardGroup> = emptyList(),
    val items: List<BoardItem> = emptyList(),
)

/**
 * A board's remembered session settings ("Drachenbuch is always 60 s in Notan"). Stored as plain
 * names so the sidecar stays readable and survives renames of the practice enums.
 */
@Serializable
data class SessionRecipe(
    /** Gesture-ramp name, or null for a fixed time per picture. */
    val plan: String? = null,
    val intervalSeconds: Int = 120,
    val autoAdvance: Boolean = true,
    val viewMode: String = "NONE",
    val grid: String = "OFF",
)

/** Freeform placement of one card, in board units; [scale] and [rotation] (degrees) around its centre. */
@Serializable
data class ItemPos(
    val x: Float,
    val y: Float,
    val scale: Float = 1f,
    val rotation: Float = 0f,
)

/** Freeform viewport: board point at the view centre + zoom. */
@Serializable
data class Camera(
    val x: Float = 0f,
    val y: Float = 0f,
    val zoom: Float = 1f,
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

    /** Freeform placement; null = not placed yet (auto-placed when the canvas needs it). */
    abstract val pos: ItemPos?

    /** The same item with a different group membership (sealed-class-friendly copy). */
    fun withGroups(groups: List<String>): BoardItem = when (this) {
        is ImageItem -> copy(groups = groups)
        is NoteItem -> copy(groups = groups)
    }

    /** The same item at a different freeform placement. */
    fun withPos(pos: ItemPos?): BoardItem = when (this) {
        is ImageItem -> copy(pos = pos)
        is NoteItem -> copy(pos = pos)
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
    override val pos: ItemPos? = null,
    /** Width/height, remembered after the first decode so freeform layout is stable. */
    val aspect: Float? = null,
    /** Content identity ([ContentId]) — lets a renamed or moved file be recognised again. */
    val contentId: String? = null,
) : BoardItem()

@Serializable
@SerialName("note")
data class NoteItem(
    override val id: String,
    val text: String,
    override val groups: List<String> = emptyList(),
    override val pos: ItemPos? = null,
) : BoardItem()

/** Layout ids stored in the sidecar. */
object BoardLayouts {
    const val GRID = "grid"
    const val FREE = "free"
}

/** Theme ids stored in the sidecar; their rendering lives in the UI layer. */
object BoardThemes {
    const val CORK = "cork"
    const val PAPYRUS = "papyrus"
    const val PLAIN = "plain"
    val ALL = listOf(CORK, PAPYRUS, PLAIN)
}
