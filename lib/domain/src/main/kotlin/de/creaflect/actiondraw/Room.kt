package de.creaflect.actiondraw

/**
 * The six rooms of ActionDraw (CONCEPT.md, *The six rooms*), one per stage of the way from an idea
 * to a finished piece: what the palette shows, where a hand-off leads, what the person was in last.
 * How a room looks — its pigment — is the design system's to say.
 */
enum class Room(val label: String) {
    PRACTICE("Practice"),
    LENS("Lens"),
    BOARDS("Boards"),
    CONCEPTS("Concepts"),
    SKETCH("Sketch"),
    COLLAGE("Collage"),
}
