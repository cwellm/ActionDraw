package de.creaflect.actiondraw

import java.util.UUID

/** A fresh id for a card, a group, a board or a concept: unique, and meaningless on purpose. */
fun newId(): String = UUID.randomUUID().toString()
