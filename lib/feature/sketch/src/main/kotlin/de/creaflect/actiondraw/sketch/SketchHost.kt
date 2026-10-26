package de.creaflect.actiondraw.sketch

/**
 * What Live Sketch asks of the app itself: the way out, back to where it was entered from. A
 * sketch is saved *into* a board's or a concept's folder and handed over as a file already there,
 * through the domain's `BoardSource` and `ConceptSource`; the page goes into the other rooms along
 * the hand-off seam (`Handoff`).
 */
fun interface SketchHost {
    /** Back to where Live Sketch was entered from. */
    fun leaveSketch()
}
