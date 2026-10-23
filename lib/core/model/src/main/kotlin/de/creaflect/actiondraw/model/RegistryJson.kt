package de.creaflect.actiondraw.model

import kotlinx.serialization.Serializable

// How the registries are stored, beside the settings: `boards.json` and `concepts.json`, a list
// of entries each. Unchanged from what the files on disk hold.

@Serializable
data class BoardEntryJson(
    val id: String,
    val name: String,
    val path: String,
    val ownsFolder: Boolean = false,
)

@Serializable
data class ConceptEntryJson(
    val id: String,
    val name: String,
    val kind: String = "",
    val path: String,
)
