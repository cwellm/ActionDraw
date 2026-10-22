package de.creaflect.actiondraw.ui

import de.creaflect.actiondraw.image.ImageScanner
import de.creaflect.actiondraw.platform.FileDialogs
import java.io.File

// The dialogs the rooms ask for, in the app's own terms: which pictures it reads, what a sketch
// is called. The dialogs themselves are the platform's (`FileDialogs`).

/** A folder, starting from [start] when given. Null when cancelled. */
fun chooseFolder(start: File?, title: String = "Select image folder"): File? = FileDialogs.chooseFolder(start, title)

/** Pictures the app can draw (board imports). Empty when cancelled. */
fun chooseImages(start: File?): List<File> =
    FileDialogs.chooseFiles(start, "Import images", "Images", ImageScanner.IMAGE_EXTENSIONS)

/** A sketch document (`*.sketch.json`). Null when cancelled. */
fun chooseSketchFile(start: File?): File? =
    FileDialogs.chooseFile(start, "Open sketch", "Sketches (*.sketch.json)") { it.name.endsWith(".sketch.json") }

/** Where to save an export; the suggested extension is added when the person drops it. */
fun chooseSaveFile(suggested: String, start: File?): File? = FileDialogs.chooseSaveFile(suggested, start)
