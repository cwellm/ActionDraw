package de.creaflect.actiondraw

import java.io.File

/**
 * Windows treats `C:\Boards\Test` and `c:\boards\test` as one folder, and so does this — a board
 * reached through a differently-cased path is still the same board. Every place that compares
 * two folders goes through here rather than spelling the rule out again.
 */
fun File.samePathAs(other: File): Boolean = absolutePath.equals(other.absolutePath, ignoreCase = true)

/**
 * True when this lies somewhere inside [folder]. The separator is part of the test on purpose:
 * `Drachen2` sits next to `Drachen`, not inside it, however much their names look alike.
 */
fun File.isInside(folder: File): Boolean {
    if (samePathAs(folder)) return false
    val prefix = folder.absolutePath.trimEnd(File.separatorChar) + File.separatorChar
    return absolutePath.startsWith(prefix, ignoreCase = true)
}
