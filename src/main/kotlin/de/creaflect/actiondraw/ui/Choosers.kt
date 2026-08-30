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
