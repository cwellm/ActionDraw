package de.creaflect.actiondraw.image

import java.io.File

/**
 * Path of [file] relative to [root], `/`-separated on every OS — the stable key used by the
 * seen/redo stores and by board sidecars. For a file directly inside [root] this equals the file
 * name, which keeps existing flat-folder store files valid. Falls back to the plain name when
 * [file] cannot be relativized against [root].
 */
fun relKey(root: File, file: File): String = runCatching {
    root.absoluteFile.toPath().normalize()
        .relativize(file.absoluteFile.toPath().normalize())
        .joinToString("/")
}.getOrDefault(file.name)
