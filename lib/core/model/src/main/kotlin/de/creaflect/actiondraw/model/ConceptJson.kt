package de.creaflect.actiondraw.model

import kotlinx.serialization.Serializable

// How a concept is stored: the sidecar `.actiondraw_concept.json` in its folder. Its cards are
// stored as a board's are. The same rule as for boards: what the files on disk hold, unchanged.

@Serializable
data class ConceptJson(
    val version: Int = 1,
    val id: String = "",
    val name: String = "",
    val kind: String = "",
    val notes: String = "",
    val items: List<BoardItemJson> = emptyList(),
    val documents: List<String> = emptyList(),
    val layout: String = "grid",
    val camera: CameraJson? = null,
)
