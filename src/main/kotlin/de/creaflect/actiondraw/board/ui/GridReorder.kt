package de.creaflect.actiondraw.board.ui

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import de.creaflect.actiondraw.board.BoardState

/**
 * Drag-to-reorder for the grid. Cards live in a lazy grid, so there is nothing to hit-test
 * against except its layout info: the pointer is turned into viewport coordinates via the dragged
 * card's own offset, and whatever card lies under it becomes the drop target. The move happens on
 * release, so a drag that ends nowhere useful changes nothing.
 *
 * Cell keys are `"<section>/<itemId>"`; only cards of the same section reorder, which keeps the
 * rule-based ("smart") sections read-only.
 */
class GridReorder(private val gridState: LazyGridState, private val board: BoardState) {
    var draggingKey by mutableStateOf<String?>(null)
        private set
    var targetKey by mutableStateOf<String?>(null)
        private set

    fun start(key: String) {
        draggingKey = key
        targetKey = null
    }

    /** [positionInCard] is the pointer within the dragged card, as the drag gesture reports it. */
    fun drag(key: String, positionInCard: Offset) {
        val info = gridState.layoutInfo
        val me = info.visibleItemsInfo.find { it.key == key } ?: return
        val pointer = Offset(me.offset.x + positionInCard.x, me.offset.y + positionInCard.y)
        val hit = info.visibleItemsInfo.find { candidate ->
            val within = pointer.x >= candidate.offset.x &&
                pointer.x <= candidate.offset.x + candidate.size.width &&
                pointer.y >= candidate.offset.y &&
                pointer.y <= candidate.offset.y + candidate.size.height
            within && candidate.key != key
        }
        val candidateKey = hit?.key as? String
        targetKey = candidateKey?.takeIf { section(it) == section(key) && !isSmart(it) }
    }

    fun drop() {
        val from = draggingKey
        val to = targetKey
        if (from != null && to != null) {
            board.dropOn(itemId(from), itemId(to), groupId(section(from)))
        }
        cancel()
    }

    fun cancel() {
        draggingKey = null
        targetKey = null
    }

    companion object {
        fun cellKey(sectionId: String, itemId: String) = "$sectionId/$itemId"

        private fun section(key: String) = key.substringBeforeLast('/')
        private fun itemId(key: String) = key.substringAfterLast('/')
        private fun isSmart(key: String) = section(key).startsWith("smart-")

        /** The Inbox is its own section but has no group id. */
        private fun groupId(section: String): String? = section.takeIf { it != "inbox" }
    }
}
