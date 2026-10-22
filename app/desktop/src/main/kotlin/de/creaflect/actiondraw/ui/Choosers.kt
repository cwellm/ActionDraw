package de.creaflect.actiondraw.ui

import de.creaflect.actiondraw.platform.SwingFileDialogs
import java.io.File

// The dialogs the rooms ask for, in the app's own terms: which pictures it reads, what a sketch
// is called. The dialogs themselves are the platform's (`SwingFileDialogs`).

/** A folder, starting from [start] when given. Null when cancelled. */
fun chooseFolder(start: File?, title: String = "Select image folder"): File? = SwingFileDialogs.chooseFolder(start, title)

/** Pictures the app can draw — those with one of [extensions] (board imports). Empty when cancelled. */
fun chooseImages(start: File?, extensions: Set<String>): List<File> =
    SwingFileDialogs.chooseFiles(start, "Import images", "Images", extensions)

/** A sketch document (`*.sketch.json`). Null when cancelled. */
fun chooseSketchFile(start: File?): File? =
    SwingFileDialogs.chooseFile(start, "Open sketch", "Sketches (*.sketch.json)") { it.name.endsWith(".sketch.json") }

/** Where to save an export; the suggested extension is added when the person drops it. */
fun chooseSaveFile(suggested: String, start: File?): File? = SwingFileDialogs.chooseSaveFile(suggested, start)
