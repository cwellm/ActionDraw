package de.creaflect.actiondraw.sketch

import de.creaflect.sketch.Lead
import de.creaflect.sketch.PencilModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A lead as the Tune panel had it, under a name of your own — "my HB". Kept in the settings. */
@Serializable
data class LeadPreset(val name: String, val lead: String, val model: PencilModel) {
    val base: Lead get() = Lead.entries.firstOrNull { it.name == lead } ?: Lead.MEDIUM
}

/** The presets as one JSON string: the shape the settings file keeps them in. */
object LeadPresets {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String?): List<LeadPreset> =
        text?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString<List<LeadPreset>>(it) }.getOrNull() }.orEmpty()

    fun encode(presets: List<LeadPreset>): String = json.encodeToString(presets)
}
