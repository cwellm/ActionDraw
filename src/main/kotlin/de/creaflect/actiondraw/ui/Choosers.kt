package de.creaflect.actiondraw.ui

import de.creaflect.actiondraw.image.ImageScanner
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter

/** Swing folder dialog, starting from [start] when given. Returns null when cancelled. */
fun chooseFolder(start: File?, title: String = "Select image folder"): File? {
    systemLookAndFeel()
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = title
        start?.takeIf { it.isDirectory }?.let { currentDirectory = it }
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

/** Multi-select image dialog (board imports). Empty when cancelled. */
fun chooseImages(start: File?): List<File> {
    systemLookAndFeel()
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.FILES_ONLY
        isMultiSelectionEnabled = true
        dialogTitle = "Import images"
        fileFilter = FileNameExtensionFilter("Images", *ImageScanner.IMAGE_EXTENSIONS.toTypedArray())
        start?.takeIf { it.isDirectory }?.let { currentDirectory = it }
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFiles.toList()
    } else {
        emptyList()
    }
}

private fun systemLookAndFeel() {
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
}

/** Save dialog for an export; appends the suggested extension when the user drops it. */
fun chooseSaveFile(suggested: String, start: File?): File? {
    systemLookAndFeel()
    val chooser = JFileChooser().apply {
        dialogTitle = "Save as"
        selectedFile = File(start ?: File("."), suggested)
    }
    if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return null
    val chosen = chooser.selectedFile
    val ext = suggested.substringAfterLast('.', "")
    return if (ext.isNotEmpty() && !chosen.name.contains('.')) File(chosen.path + "." + ext) else chosen
}
