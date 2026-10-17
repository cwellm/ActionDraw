package de.creaflect.actiondraw.board

/**
 * A card's shape and place on a freeform canvas, a board's or a concept's: its size in board
 * units, the small tilt a hand puts it down with, and a place for every card that has none yet.
 * Pure, so both canvases share one rule and its tests need no board.
 */
object CardLayout {
    /** Base edge length of a freeform card at scale 1, in board units. */
    const val BASE_SIZE = 220f

    /** Width over height of a link or document-note strip on the canvas. */
    const val STRIP_ASPECT = 3.4f

    /** A palette card: a strip of swatches over its lettered caption. */
    const val PALETTE_ASPECT = 2.2f

    /**
     * The small tilt a card is put down with, as by a hand: a degree or two either way, the
     * same for the same card. It is the card's stored rotation, so the drawn card stays the
     * clicked card, and the rotate handle takes it from there.
     */
    fun naturalTilt(id: String): Float = ((id.hashCode() and 0x7fffffff) % 7 - 3) * 0.6f

    /**
     * Gives every card without a position one, cascading in rows of five around
     * ([originX], [originY]) — below everything already placed.
     */
    fun placeMissing(board: BoardFile, originX: Float = 0f, originY: Float = 0f): BoardFile {
        val unplaced = board.items.count { it.pos == null }
        if (unplaced == 0) return board
        // Rows are spaced more generously than columns: a portrait card is far taller than
        // it is wide, and cascading them at one card's height made them overlap.
        val gap = BASE_SIZE * 1.3f
        val rowGap = BASE_SIZE * 1.9f
        val perRow = 5
        val startY = (board.items.mapNotNull { it.pos }.maxOfOrNull { it.y + BASE_SIZE } ?: originY)
        // Centre the row over the cards that will actually be in it: centring a full row of
        // five put a lone new note two cards' widths off the left of the screen.
        val inRow = minOf(unplaced, perRow)
        val startX = originX - (inRow - 1) * gap / 2
        var i = 0
        return board.copy(
            items = board.items.map { item ->
                if (item.pos != null) item
                else item.withPos(ItemPos(startX + (i % perRow) * gap, startY + (i / perRow) * rowGap, rotation = naturalTilt(item.id))).also { i++ }
            },
        )
    }
}
