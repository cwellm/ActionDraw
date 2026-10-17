package de.creaflect.actiondraw.board

import java.io.File
import java.security.MessageDigest

/**
 * A picture's identity by content rather than by name: `<size>-<sha1>`. Cards carry it so a file
 * renamed or moved outside the app can be found again instead of losing its caption, tags and
 * place on the board (the Phase-1 tradeoff, shaping A4).
 *
 * Only the first and last [SAMPLE] bytes are hashed together with the file's length, which keeps
 * a folder of large photos cheap to re-index while still being specific enough that two different
 * pictures never collide in practice.
 */
object ContentId {
    private const val SAMPLE = 64 * 1024

    fun of(file: File): String? = runCatching {
        val length = file.length()
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { stream ->
            val head = ByteArray(minOf(SAMPLE.toLong(), length).toInt())
            stream.read(head)
            digest.update(head)
            if (length > 2L * SAMPLE) {
                stream.skip(length - 2L * SAMPLE)
                val tail = ByteArray(SAMPLE)
                val read = stream.read(tail)
                if (read > 0) digest.update(tail, 0, read)
            }
        }
        length.toString() + "-" + digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()
}
