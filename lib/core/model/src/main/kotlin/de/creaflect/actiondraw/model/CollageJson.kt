package de.creaflect.actiondraw.model

import kotlinx.serialization.Serializable

// How a collage is stored: `<name>.collage.json`, beside `<name>.png` once the collage is saved as a
// picture. Its pieces from the bottom of the stack to the top. The same rule as for boards: what the
// file on disk holds, unchanged.

@Serializable
data class CollageJson(
    val version: Int = 1,
    val pieces: List<PieceJson> = emptyList(),
)

/**
 * One piece: the picture it is cut from — relative to the collage's folder where it lies inside it,
 * absolute otherwise — the cut's corners in that picture's pixels as x, y pairs, and where it lies on
 * the mat: its middle, its turn in degrees, mat units to a picture pixel, and whether it is flipped.
 */
@Serializable
data class PieceJson(
    val id: String = "",
    val name: String = "",
    val source: String = "",
    val outline: List<Float> = emptyList(),
    val x: Float = 0f,
    val y: Float = 0f,
    val turn: Float = 0f,
    val scale: Float = 1f,
    val flipped: Boolean = false,
)
