package de.creaflect.actiondraw.board

import java.io.File

/** A concept as the board sees it: an id to link by, a name to show, a kind to sort by. */
data class ConceptRef(val id: String, val name: String, val kind: String = "")

/** Everything a board needs of a linked concept at one moment: where it is and what it holds. */
data class ConceptSnapshot(val id: String, val name: String, val root: File, val items: List<BoardItem>)

/** One board as the Concepts side sees it: a name, a folder, and whether it links the concept. */
data class BoardLink(val name: String, val dir: File, val linked: Boolean)
