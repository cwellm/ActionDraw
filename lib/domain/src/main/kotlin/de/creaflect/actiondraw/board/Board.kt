package de.creaflect.actiondraw.board

/**
 * An Idea Board: its groups and cards, how it is laid out and drawn. Membership is explicit — the
 * items listed here ARE the board, and its folder is backing store, not source (shaping A1).
 * Picture paths are relative to the board's folder, `/`-separated. How a board is stored is
 * not this type's business: that is the sidecar's (`lib:core:model`), mapped by `lib:data`.
 */
data class Board(
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
    /** A picture behind the cards, or null for the theme's texture alone. */
    val wallpaper: Wallpaper? = null,
    /** Ids of the concepts linked onto this board; each shows as a group (see [ConceptLink]). */
    val concepts: List<String> = emptyList(),
)

/**
 * The board's background picture. [path] is relative to the board (the file is copied into
 * `_wallpaper/`), so it moves with the board. [fit] is one of [WallpaperFit]; [dim] darkens it so
 * the cards stay readable; [blur] softens it, in 0..1.
 */
data class Wallpaper(
    val path: String,
    val fit: String = WallpaperFit.COVER,
    val dim: Float = 0.35f,
    val blur: Float = 0f,
)

object WallpaperFit {
    const val COVER = "cover"
    const val TILE = "tile"
    const val CENTER = "center"
    val ALL = listOf(COVER, TILE, CENTER)
}

/**
 * A board's remembered session settings ("Drachenbuch is always 60 s in Notan"). Stored as plain
 * names so the sidecar stays readable and survives renames of the practice enums.
 */
data class SessionRecipe(
    /** Gesture-ramp name, or null for a fixed time per picture. */
    val plan: String? = null,
    val intervalSeconds: Int = 120,
    val autoAdvance: Boolean = true,
    val viewMode: String = "NONE",
    val grid: String = "OFF",
    /** White balance, -1 (cool) .. +1 (warm). */
    val temperature: Float = 0f,
)

/** Freeform placement of one card, in board units; [scale] and [rotation] (degrees) around its centre. */
data class ItemPos(
    val x: Float,
    val y: Float,
    val scale: Float = 1f,
    val rotation: Float = 0f,
)

/** Freeform viewport: board point at the view centre + zoom. */
data class Camera(
    val x: Float = 0f,
    val y: Float = 0f,
    val zoom: Float = 1f,
)

/** A named section of the board; `order` positions it, `color` is an optional `#rrggbb` accent. */
data class BoardGroup(
    val id: String,
    val name: String,
    val color: String? = null,
    val order: Int = 0,
    val collapsed: Boolean = false,
    /**
     * The group this one sits inside, or null at the top level. Exactly one level is allowed —
     * a subgroup cannot hold subgroups — and `BoardStore.validate` flattens anything deeper.
     */
    val parentId: String? = null,
    /**
     * Where the group's cards come from when they are not the board's own: reserved for a linked
     * concept (`concept:<id>`), whose group cannot be dissolved from the board. Null for every
     * group the board made itself.
     */
    val source: String? = null,
) {
    /** The concept this group shows, when it is a linked one: the id after `concept:`. */
    val conceptId: String? get() = source?.takeIf { it.startsWith(ConceptLink.PREFIX) }?.removePrefix(ConceptLink.PREFIX)

    /** A linked concept's group: not the board's to rename, recolour, nest or dissolve. */
    val isConcept: Boolean get() = conceptId != null
}

/**
 * One card. Items may belong to several groups; an empty [groups] list means the Inbox.
 * Stored with a `"type"` discriminator (`image`, `note`, `link`).
 */
sealed class BoardItem {
    abstract val id: String
    abstract val groups: List<String>

    /** Freeform placement; null = not placed yet (auto-placed when the canvas needs it). */
    abstract val pos: ItemPos?

    /** The same item with a different group membership (sealed-class-friendly copy). */
    fun withGroups(groups: List<String>): BoardItem = when (this) {
        is ImageItem -> copy(groups = groups)
        is NoteItem -> copy(groups = groups)
        is LinkItem -> copy(groups = groups)
    }

    /** The same item at a different freeform placement. */
    fun withPos(pos: ItemPos?): BoardItem = when (this) {
        is ImageItem -> copy(pos = pos)
        is NoteItem -> copy(pos = pos)
        is LinkItem -> copy(pos = pos)
    }
}

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

data class NoteItem(
    override val id: String,
    /** Plain text; a document note reads it as Markdown, a post-it shows it as typed. */
    val text: String,
    override val groups: List<String> = emptyList(),
    override val pos: ItemPos? = null,
    /** Paper colour (`#rrggbb`); null = the theme's default note paper. */
    val color: String? = null,
    /** Draw the note in a larger, heavier type — for the sign-post notes on a big board. */
    val heading: Boolean = false,
    /** [NoteKind.DOCUMENT] (a title on the board, the text in a popup) or [NoteKind.POSTIT]. */
    val kind: String = NoteKind.DOCUMENT,
) : BoardItem() {
    /**
     * What a document note shows on the board: its first `#` heading, else its first line —
     * markers stripped. A note with no text at all is "Note".
     */
    val title: String
        get() {
            val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val heading = lines.firstOrNull { it.startsWith("#") }?.trimStart('#')?.trim()
            val first = heading ?: lines.firstOrNull() ?: return "Note"
            return first.replace(Regex("""\*\*(.+?)\*\*|\*(.+?)\*|`([^`]+)`|\[([^\]]+)]\([^)]*\)""")) { m ->
                m.groups.drop(1).firstNotNullOfOrNull { it?.value } ?: ""
            }.trim().ifBlank { "Note" }
        }

    /**
     * A post-it is as tall as its text. Estimated from the text alone so the state (frames,
     * fit-all) and the canvas agree without a text measurement: width over height, where height
     * grows by one line per ~[charsPerLine] characters and per line break.
     */
    fun estimatedAspect(charsPerLine: Int = 26): Float {
        val lines = text.lines().sumOf { line -> maxOf(1, (line.length + charsPerLine - 1) / charsPerLine) }
        val heightUnits = 1.6f + lines * 1.05f // padding plus lines, in units of one line height
        val widthUnits = charsPerLine * 0.62f
        return (widthUnits / heightUnits).coerceIn(0.35f, 3.5f)
    }
}

object NoteKind {
    const val DOCUMENT = "document"
    const val POSTIT = "postit"

    /**
     * A palette pinned to the board: its first line says whose colours they are, its second holds
     * them as `#rrggbb` words. An app that does not know the kind shows it as a document note
     * titled by that first line.
     */
    const val PALETTE = "palette"
}

/** The colours of a palette note, as `0xRRGGBB`, in their order. */
fun NoteItem.paletteColours(): List<Int> =
    text.lines().drop(1).joinToString(" ").split(' ', ',').mapNotNull { word ->
        word.trim().removePrefix("#").takeIf { it.length == 6 }?.toIntOrNull(16)
    }

/** What a palette note says it is: its first line. */
fun NoteItem.paletteCaption(): String = text.lines().firstOrNull().orEmpty()

/**
 * A card that points somewhere on the web. ActionDraw never fetches it: the URL is stored as
 * typed and opened in the system browser, so boards stay an offline, plain-file affair.
 */
data class LinkItem(
    override val id: String,
    val url: String,
    val title: String = "",
    override val groups: List<String> = emptyList(),
    override val pos: ItemPos? = null,
    /**
     * Path (relative to the board) of a preview picture fetched from the page, or null while the
     * card is just an address. Fetching is always an explicit act — see [LinkPreview].
     */
    val preview: String? = null,
) : BoardItem()

/** Paper colours a note cycles through; null is the theme default. */
object NoteColors {
    val ALL: List<String?> = listOf(null, "#FFE082", "#C5E1A5", "#B3E5FC", "#F8BBD0", "#D7CCC8")
}

/**
 * Starter groups for a new board, so a fresh board is not an empty rectangle. Purely a
 * convenience: the groups are ordinary groups once created.
 */
data class BoardTemplate(val name: String, val groups: List<String>) {
    companion object {
        val ALL = listOf(
            BoardTemplate("Empty", emptyList()),
            BoardTemplate("Creature design", listOf("Heads", "Bodies", "Wings", "Details", "Colour studies")),
            BoardTemplate("Character sheet", listOf("Faces", "Poses", "Clothing", "Props", "Expressions")),
            BoardTemplate("Environment", listOf("Landscapes", "Architecture", "Light & mood", "Textures")),
            BoardTemplate("Anatomy practice", listOf("Gesture", "Hands & feet", "Heads", "Full figure")),
        )
    }
}

/** How a board lays out its cards: in groups, or placed freely. */
object BoardLayouts {
    const val GRID = "grid"
    const val FREE = "free"
}

/** The surfaces a board can lie on; how each looks is the design system's business. */
object BoardThemes {
    const val CORK = "cork"
    const val PAPYRUS = "papyrus"
    const val PLAIN = "plain"
    val ALL = listOf(CORK, PAPYRUS, PLAIN)
}
