package de.creaflect.actiondraw.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import de.creaflect.actiondraw.Room
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** A room's Material controls wear its pigment; the palette's, the design's own colours. */
class RoomThemeTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun everyRoomsControlsWearItsPigment() {
        val seen = mutableMapOf<Room?, Pair<Color, Color>>()
        rule.setContent {
            AtelierTheme {
                (Room.entries + null).forEach { room ->
                    RoomTheme(room) { seen[room] = MaterialTheme.colors.primary to MaterialTheme.colors.primaryVariant }
                }
            }
        }
        rule.waitForIdle()
        Room.entries.forEach { room ->
            val pigment = Atelier.pigmentOf(room)
            assertEquals(pigment.glow to pigment.mass, seen[room], "$room")
        }
        assertEquals(Atelier.materialColors.primary, seen[null]?.first, "no room: the design's own")
    }
}
