package de.creaflect.actiondraw.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CardLayoutTest {
    @Test
    fun placeMissingCascadesInRowsBelowExistingCards() {
        val placed = NoteItem(id = "p", text = "x", pos = ItemPos(0f, 0f))
        val unplaced = (1..6).map { NoteItem(id = "n$it", text = "x") }
        val board = CardLayout.placeMissing(BoardFile(items = listOf(placed) + unplaced))

        assertTrue(board.items.all { it.pos != null }, "every card must get a position")
        val newYs = board.items.filter { it.id != "p" }.map { it.pos!!.y }.distinct().sorted()
        assertEquals(2, newYs.size, "6 cards in rows of 5 -> two rows")
        assertTrue(newYs.all { it > 0f }, "new rows start below the already placed card")
    }

    @Test
    fun autoPlacementLeavesRoomBetweenRows() {
        val cards = (1..7).map { NoteItem(id = "n$it", text = "x") }
        val board = CardLayout.placeMissing(BoardFile(items = cards))
        val rows = board.items.mapNotNull { it.pos?.y }.distinct().sorted()
        assertEquals(2, rows.size, "7 cards in rows of 5 -> two rows")
        assertTrue(
            rows[1] - rows[0] > CardLayout.BASE_SIZE * 1.5f,
            "rows need more room than a card's width, or tall cards overlap",
        )
    }

    @Test
    fun aLoneNewCardIsPlacedAtTheOriginNotOffToTheLeft() {
        val one = BoardFile(name = "one", items = listOf(NoteItem(id = "n", text = "hi")))
        val placed = CardLayout.placeMissing(one)
        assertEquals(0f, placed.items.single().pos!!.x, "one card: centred where the camera looks")

        val three = BoardFile(name = "three", items = (1..3).map { NoteItem(id = "n$it", text = "hi") })
        val xs = CardLayout.placeMissing(three).items.map { it.pos!!.x }
        assertEquals(0f, xs[1], "three cards: the middle one on the origin")
        assertEquals(-xs[0], xs[2], "and the outer two symmetric about it")
    }
}
