package de.creaflect.actiondraw

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.window.WindowState
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The window's keys as the desktop sends them, handed to the room on screen. The events are made with
 * Compose's own maker for them, which it keeps for its modules — a test of the routing has no other
 * way to make one, and the version is pinned.
 */
@OptIn(InternalComposeUiApi::class)
class KeysTest {
    private val root: File = Files.createTempDirectory("keys").toFile()

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
    }

    /** A character typed — with AltGr, as a German keyboard types `[` and `]`: Ctrl and Alt held. */
    private fun typed(char: Char, altGr: Boolean) = KeyEvent(Key.Unknown, KeyEventType.Unknown, codePoint = char.code, isCtrlPressed = altGr, isAltPressed = altGr)

    private fun pressed(key: Key, altGr: Boolean) = KeyEvent(key, KeyEventType.KeyDown, isCtrlPressed = altGr, isAltPressed = altGr)

    private fun picture(name: String) = File(root, "$name.png").apply { ImageIO.write(BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB), "png", this) }

    @Test
    fun theCollagesBracketsAreTheCharactersTypedHoweverTheKeyboardTypesThem() {
        val rooms = Rooms(FileLib(File(root, "config")))
        val collage = rooms.collage
        rooms.app.enter(Screen.Collage)
        val a = collage.addPicture(picture("a"))!!
        val b = collage.addPicture(picture("b"))!!
        collage.select(a.id)
        val window = WindowState()

        // AltGr+9 on a German keyboard: the key reads as Ctrl+9, which is nothing of the room's…
        assertFalse(handleKey(pressed(Key.Nine, altGr = true), rooms, window))
        // …and the character it types is ], which brings the piece forward.
        assertTrue(handleKey(typed(']', altGr = true), rooms, window))
        assertEquals(listOf(b.id, a.id), collage.collage.pieces.map { it.id })
        assertTrue(handleKey(typed('[', altGr = true), rooms, window))
        assertEquals(listOf(a.id, b.id), collage.collage.pieces.map { it.id }, "and [ sends it back")

        // On a US keyboard [ is a key of its own: pressed and typed, it moves the piece once.
        handleKey(pressed(Key.RightBracket, altGr = false), rooms, window)
        handleKey(typed(']', altGr = false), rooms, window)
        assertEquals(listOf(b.id, a.id), collage.collage.pieces.map { it.id })
    }
}
