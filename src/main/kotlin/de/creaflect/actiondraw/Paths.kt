package de.creaflect.actiondraw

import java.io.File

/**
 * Windows treats `C:\Boards\Test` and `c:\boards\test` as one folder, and so does this — a board
 * reached through a differently-cased path is still the same board. Every place that compares
 * two folders goes through here rather than spelling the rule out again.
 */
fun File.samePathAs(other: File): Boolean = absolutePath.equals(other.absolutePath, ignoreCase = true)
