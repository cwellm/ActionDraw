package de.creaflect.actiondraw

import de.creaflect.actiondraw.board.BoardHost
import de.creaflect.actiondraw.board.BoardState
import de.creaflect.actiondraw.concept.ConceptHost
import de.creaflect.actiondraw.concept.ConceptState
import de.creaflect.actiondraw.handoff.Handed
import de.creaflect.actiondraw.handoff.Handoff
import de.creaflect.actiondraw.handoff.Receiver
import de.creaflect.actiondraw.handoff.Route
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

    /** What the host does once a hand-off has opened a room in the main window: brings it forward. */
    var onEntered: () -> Unit = {}

    /**
     * The hand-offs between the rooms (F10.7): the rooms that take what others hand them, and the
     * way into each. A room handed something opens with it, and Back there returns to where it came
     * from — a board, a sketch, a session waiting, paused.
     */
    val handoff: Handoff = object : Handoff {
        override fun routes(what: Handed, from: Room): List<Route> =
            receivers().filter { it.room != from }.mapNotNull { receiver -> receiver.offer(what)?.let { Route(receiver.room, it) } }

        override fun send(what: Handed, to: Room) {
            val receiver = receivers().firstOrNull { it.room == to } ?: return
            receiver.take(what)
            app.enter(Screen.entries.first { it.room == to })
            onEntered()
        }
    }

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
        },
        browser = host.browser,
        dialogs = host.dialogs,
        handoff = handoff,
    )

    /** The boards — and, as the domain's BoardSource, what every other room draws from and pins to. */
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
        },
        concepts = concepts,
        clipboard = host.clipboard,
        browser = host.browser,
        dialogs = host.dialogs,
        handoff = handoff,
    )

    val practice = PracticeState(lib, dialogs = host.dialogs, boards = boards, handoff = handoff)

    /** Live Sketch saves into the boards and the concepts, and takes pictures handed to it. */
    val sketch: SketchState = SketchState(
        lib,
        SketchHost { app.back(Screen.Sketch) },
        dialogs = host.dialogs,
        boards = boards,
        concepts = concepts,
        handoff = handoff,
    )

    /** The Lens room pins its views to the boards, and takes pictures handed to it. */
    val lens: LensRoomState = LensRoomState(
        lib,
        LensHost { app.back(Screen.Lens) },
        dialogs = host.dialogs,
        clipboard = host.clipboard,
        boards = boards,
        handoff = handoff,
    )

    /** The rooms that take what others hand them, drawing first, then looking. */
    private fun receivers(): List<Receiver> = listOf(sketch, lens)
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
