package de.creaflect.actiondraw.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// How a board is stored: the sidecar `.actiondraw_board.json`, next to the pictures it holds.
// Field for field, default for default and name for name what the files on disk hold — a field
// added here must be optional, a name changed here breaks every board already saved. Callers never
// see these; they work with the domain's `Board`, and `lib:data` maps between the two.

@Serializable
data class BoardJson(
    val version: Int = 1,
    val name: String = "",
    val theme: String = "cork",
    val layout: String = "grid",
    val camera: CameraJson? = null,
    val session: SessionRecipeJson? = null,
    val groups: List<BoardGroupJson> = emptyList(),
    val items: List<BoardItemJson> = emptyList(),
    val wallpaper: WallpaperJson? = null,
    val concepts: List<String> = emptyList(),
)

@Serializable
data class WallpaperJson(
    val path: String,
    val fit: String = "cover",
    val dim: Float = 0.35f,
    val blur: Float = 0f,
)

@Serializable
data class SessionRecipeJson(
    val plan: String? = null,
    val intervalSeconds: Int = 120,
    val autoAdvance: Boolean = true,
    val viewMode: String = "NONE",
    val grid: String = "OFF",
    val temperature: Float = 0f,
)

@Serializable
data class ItemPosJson(
    val x: Float,
    val y: Float,
    val scale: Float = 1f,
    val rotation: Float = 0f,
)

@Serializable
data class CameraJson(
    val x: Float = 0f,
    val y: Float = 0f,
    val zoom: Float = 1f,
)

@Serializable
data class BoardGroupJson(
    val id: String,
    val name: String,
    val color: String? = null,
    val order: Int = 0,
    val collapsed: Boolean = false,
    val parentId: String? = null,
    val source: String? = null,
)

/** One card, stored with a `"type"` discriminator: `image`, `note` or `link`. */
@Serializable
sealed class BoardItemJson

@Serializable
@SerialName("image")
data class ImageItemJson(
    val id: String,
    val path: String,
    val groups: List<String> = emptyList(),
    val caption: String? = null,
    val starred: Boolean = false,
    val tags: List<String> = emptyList(),
    val pos: ItemPosJson? = null,
    val aspect: Float? = null,
    val contentId: String? = null,
) : BoardItemJson()

@Serializable
@SerialName("note")
data class NoteItemJson(
    val id: String,
    val text: String,
    val groups: List<String> = emptyList(),
    val pos: ItemPosJson? = null,
    val color: String? = null,
    val heading: Boolean = false,
    val kind: String = "document",
) : BoardItemJson()

@Serializable
@SerialName("link")
data class LinkItemJson(
    val id: String,
    val url: String,
    val title: String = "",
    val groups: List<String> = emptyList(),
    val pos: ItemPosJson? = null,
    val preview: String? = null,
) : BoardItemJson()
