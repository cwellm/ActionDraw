package de.creaflect.actiondraw.platform

import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager
import javax.swing.filechooser.FileFilter
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * The system's file and folder dialogs — Swing's, in the system's look. Each blocks until the
 * person has chosen, and says "nothing" (null, or an empty list) when they cancel. What a dialog
 * shows (which pictures, which documents) is the caller's to say.
 */
object FileDialogs {
    /** A folder, starting from [start] when it is one. */
    fun chooseFolder(start: File?, title: String): File? {
        val chooser = chooser(title, start).apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    /** Several files with one of [extensions], shown as [description]. */
    fun chooseFiles(start: File?, title: String, description: String, extensions: Collection<String>): List<File> {
        val chooser = chooser(title, start).apply {
            fileSelectionMode = JFileChooser.FILES_ONLY
            isMultiSelectionEnabled = true
            fileFilter = FileNameExtensionFilter(description, *extensions.toTypedArray())
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFiles.toList() else emptyList()
    }

    /** One file that [accepts] lets through, shown as [description]. */
    fun chooseFile(start: File?, title: String, description: String, accepts: (File) -> Boolean): File? {
        val chooser = chooser(title, start).apply {
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = object : FileFilter() {
                override fun accept(f: File) = f.isDirectory || accepts(f)
                override fun getDescription() = description
            }
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    /** Where to save [suggested]; the suggested extension is added when the person drops it. */
    fun chooseSaveFile(suggested: String, start: File?): File? {
        lookOfTheSystem()
        val chooser = JFileChooser().apply {
            dialogTitle = "Save as"
            selectedFile = File(start ?: File("."), suggested)
        }
        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return null
        val chosen = chooser.selectedFile
        val ext = suggested.substringAfterLast('.', "")
        return if (ext.isNotEmpty() && !chosen.name.contains('.')) File(chosen.path + "." + ext) else chosen
    }

    private fun chooser(title: String, start: File?): JFileChooser {
        lookOfTheSystem()
        return JFileChooser().apply {
            dialogTitle = title
            start?.takeIf { it.isDirectory }?.let { currentDirectory = it }
        }
    }

    private fun lookOfTheSystem() {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    }
}
