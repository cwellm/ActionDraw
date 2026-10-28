package de.creaflect.actiondraw.handoff

import de.creaflect.actiondraw.Room
import java.io.File

/** What one room hands another to open with (F10.7). */
sealed interface Handed {
    /** What it is called: a picture's name without its extension, a sketch's title. */
    val name: String

    /** A picture on disk — a card's, a pose's, a concept's. */
    data class Picture(val file: File) : Handed {
        override val name: String get() = file.nameWithoutExtension
    }

    /** A picture that is no file — a sketch's page as it is — drawn as PNG bytes when it is taken. */
    class Drawn(override val name: String, val png: () -> ByteArray) : Handed
}

/** A way into another room for what is handed: the room, and what the button that takes it there says. */
data class Route(val to: Room, val label: String)

/** A room that takes what other rooms hand it: what it would do with a thing, and doing it. */
interface Receiver {
    val room: Room

    /** What taking [what] is called here — "Sketch over", "Check the values" — or null when this room takes nothing of the kind. */
    fun offer(what: Handed): String?

    /** Opens [what] in this room. */
    fun take(what: Handed)
}

/**
 * The hand-offs between the rooms (F10.7): the one place that knows which room takes what. A room
 * that hands things on asks it for the [routes] there are and [send]s along one; the room handed
 * to opens with it, and Back there returns to where it came from. A room that starts taking
 * pictures joins every route at once — each room's buttons are the routes there are.
 */
interface Handoff {
    /** Where [what] can go from [from]: every other room that takes it, in the order the rooms are kept. */
    fun routes(what: Handed, from: Room): List<Route>

    /** Hands [what] to [to]'s room, which opens with it. */
    fun send(what: Handed, to: Room)

    /** No other rooms: no routes, and nowhere to send. A room on its own, as a test has it. */
    object None : Handoff {
        override fun routes(what: Handed, from: Room): List<Route> = emptyList()
        override fun send(what: Handed, to: Room) = Unit
    }
}
