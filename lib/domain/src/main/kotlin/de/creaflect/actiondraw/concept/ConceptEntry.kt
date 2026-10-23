package de.creaflect.actiondraw.concept

import de.creaflect.actiondraw.samePathAs
import java.io.File

/** One concept as the app knows it: its id, name and kind, and the folder it lives in. */
data class ConceptEntry(
    val id: String,
    val name: String,
    val kind: String = "",
    val path: String,
) {
    val dir: File get() = File(path)

    fun isAt(folder: File): Boolean = dir.samePathAs(folder)
}
