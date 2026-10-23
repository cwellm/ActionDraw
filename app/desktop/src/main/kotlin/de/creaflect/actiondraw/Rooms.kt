package de.creaflect.actiondraw

import de.creaflect.actiondraw.board.BoardHost
import de.creaflect.actiondraw.board.BoardState
import de.creaflect.actiondraw.concept.ConceptHost
import de.creaflect.actiondraw.concept.ConceptState
import de.creaflect.actiondraw.lens.LensHost
import de.creaflect.actiondraw.lens.LensRoomState
import de.creaflect.actiondraw.platform.Browser
import de.creaflect.actiondraw.platform.FileDialogs
import de.creaflect.actiondraw.platform.HostPreferences
import de.creaflect.actiondraw.platform.SystemClipboard
import de.creaflect.actiondraw.practice.PracticeState
import de.creaflect.actiondraw.sketch.SketchHost
import de.creaflect.actiondraw.sketch.SketchState
import java.io.File

/**
 * The rooms, put together: each room's state, built over the lib with the host services it asks
 * for, and wired to the others through the contracts each room owns. The one place that knows
 * which room answers which question; the rooms themselves know none of the others. Main builds it
 * over the person's files and the desktop, a test over a temp folder and no desktop at all.
 */
class Rooms(val lib: Lib, host: HostServices = HostServices.None) {
    /** Where the app is: the palette, or a room. */
    val app = AppState(lib, host.preferences)

    val practice = PracticeState(lib, dialogs = host.dialogs)

    val concepts: ConceptState = ConceptState(
        lib,
        object : ConceptHost {
            override fun showConcepts() = app.showConcepts()
            override fun showConcept() = app.showConcept()
            override fun leaveConcepts() = app.leaveConcepts()
            override fun boardsFor(conceptId: String) = boards.boardsFor(conceptId)
            override fun setLinked(conceptId: String, board: File, linked: Boolean) {
                boards.setLinked(conceptId, board, linked)
            }
            override fun unlinkEverywhere(conceptId: String) = boards.unlinkEverywhere(conceptId)
            override fun openSketch(file: File) {
                sketch.open(file)
                app.showSketch(from = Screen.Concept)
            }
        },
        browser = host.browser,
        dialogs = host.dialogs,
    )

    val boards: BoardState = BoardState(
        lib,
        object : BoardHost {
            override fun startSession(root: File, images: List<File>, setup: SessionSetup?) = practice.startBoardSession(root, images, setup)
            override fun showBoard() = app.showBoard()
            override fun showBoardList() = app.showBoardList()
            override fun leaveBoard() = app.leaveBoard()
            override fun currentSetup(): SessionSetup = practice.currentSetup()
            override fun showConcept(id: String) {
                concepts.openById(id)
            }
            override fun showConcepts() = concepts.openList()
            override fun openSketch(file: File) {
                sketch.open(file)
                app.showSketch(from = Screen.Board)
            }
            override fun sketchOver(picture: File) {
                sketch.openFromSession(picture)
                app.showSketch(from = Screen.Board)
            }
            override fun checkValues(picture: File) {
                lens.open(picture)
                app.showLens(from = Screen.Board)
            }
        },
        concepts = concepts,
        clipboard = host.clipboard,
        browser = host.browser,
        dialogs = host.dialogs,
    )

    /** Lets a running session file pictures away on a board, without the session knowing what a board is. */
    val pinTargets = PinTargets(boards = { boards.availableBoards() }, pin = boards::pinTo)

    /** Live Sketch saves into boards and concepts through its host, and never reaches past it. */
    val sketch: SketchState = SketchState(
        lib,
        object : SketchHost {
            override fun boards() = boards.availableBoards()
            override fun addToBoard(dir: File, pictures: List<File>) = boards.pinTo(dir, pictures)
            override fun concepts() = concepts.available()
            override fun conceptDir(id: String) = concepts.entryById(id)?.dir
            override fun addToConcept(id: String, pictures: List<File>) = concepts.addToConcept(id, pictures, emptyList()) != null
            override fun leaveSketch() = app.leaveSketch()
            override fun checkValues(png: ByteArray, name: String) {
                lens.openPng(png, name)
                app.showLens(from = Screen.Sketch)
            }
        },
        dialogs = host.dialogs,
    )

    /** The Lens room pins its views to the boards, and goes back to where it was entered from. */
    val lens: LensRoomState = LensRoomState(
        lib,
        object : LensHost {
            override fun boards() = boards.availableBoards()
            override fun pin(dir: File, pictures: List<File>) = boards.pinTo(dir, pictures)
            override fun leaveLens() = app.leaveLens()
        },
        dialogs = host.dialogs,
        clipboard = host.clipboard,
    )

    init {
        // The Practice room can draw from a board: the boards say which there are and what is on them.
        practice.practiceBoards = object : PracticeState.PracticeBoards {
            override fun boards() = boards.availableBoards()
            override fun pictures(dir: File) = boards.picturesOf(dir)
        }
    }

    /** A picture on screen in a session goes into Live Sketch; the session waits. */
    fun sketchFromSession(picture: File) {
        sketch.openFromSession(picture)
        practice.pauseForSketch()
        app.sketchFromSession()
    }
}

/**
 * What the host offers the rooms: its clipboard, its browser, its file dialogs, and what the person
 * chose in its settings. The desktop's are Main's to choose; [None] is a host without any — what a
 * test gets.
 */
class HostServices(
    val clipboard: SystemClipboard,
    val browser: Browser,
    val dialogs: FileDialogs,
    val preferences: HostPreferences,
) {
    companion object {
        val None = HostServices(SystemClipboard.None, Browser.None, FileDialogs.None, HostPreferences { false })
    }
}
