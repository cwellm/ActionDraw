package de.creaflect.actiondraw

import androidx.compose.foundation.layout.Box
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import de.creaflect.actiondraw.board.BoardState
import de.creaflect.actiondraw.board.ui.BoardDialogs
import de.creaflect.actiondraw.board.ui.BoardListScreen
import de.creaflect.actiondraw.board.ui.BoardScreen
import de.creaflect.actiondraw.collage.Collages
import de.creaflect.actiondraw.collage.ui.CollageScreen
import de.creaflect.actiondraw.concept.ConceptState
import de.creaflect.actiondraw.concept.ui.ConceptDialogs
import de.creaflect.actiondraw.concept.ui.ConceptListScreen
import de.creaflect.actiondraw.concept.ui.ConceptScreen
import de.creaflect.actiondraw.lens.ui.LensRoomScreen
import de.creaflect.actiondraw.practice.PracticePage
import de.creaflect.actiondraw.practice.PracticeState
import de.creaflect.actiondraw.sketch.ui.SketchDialogs
import de.creaflect.actiondraw.sketch.ui.SketchScreen
import de.creaflect.actiondraw.ui.AtelierTheme
import de.creaflect.actiondraw.ui.Bloom
import de.creaflect.actiondraw.ui.BloomLayer
import de.creaflect.actiondraw.ui.PaletteScreen
import de.creaflect.actiondraw.ui.PaletteWell
import de.creaflect.actiondraw.ui.PracticeRoom
import de.creaflect.actiondraw.ui.RoomLine
import de.creaflect.actiondraw.ui.RoomTheme

/**
 * Every screen belongs to a [Room] of the palette, except the palette itself, which is home. The
 * Practice room is one screen here; where in the room it is — the setup, the picker, a session —
 * is the room's own business ([PracticeState.page]).
 */
enum class Screen(val room: Room?) {
    Palette(null),
    Practice(Room.PRACTICE),
    Board(Room.BOARDS), BoardList(Room.BOARDS),
    Concepts(Room.CONCEPTS), Concept(Room.CONCEPTS),
    Sketch(Room.SKETCH),
    Lens(Room.LENS),
    Collage(Room.COLLAGE),
}

/** The main window's content: the palette, or the room on screen, with the bloom between them. */
@Composable
fun App(
    rooms: Rooms,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    setFullscreen: (Boolean) -> Unit,
) {
    val state = rooms.app
    val practice = rooms.practice
    val boardState = rooms.boards
    val conceptState = rooms.concepts
    val sketchState = rooms.sketch
    val thumbs = rooms.lib.pictures
    // Where each well sits on screen, for the pigment to spread from and drain back into.
    val wellCentres = remember { mutableMapOf<Room, Offset>() }
    var bloom by remember { mutableStateOf<Bloom?>(null) }
    // Home from any room drains its pigment back into its well, however home was reached.
    var previous by remember { mutableStateOf(state.screen) }
    LaunchedEffect(state.screen) {
        val from = previous.room
        previous = state.screen
        if (state.screen == Screen.Palette && from != null && bloom == null) {
            bloom = Bloom(from, wellCentres[from] ?: Offset.Unspecified, opening = false)
        }
    }

    AtelierTheme(reducedMotion = state.reducedMotion) {
        Surface {
            Box {
                // The room on screen, its controls in its own pigment (RoomTheme).
                RoomTheme(state.screen.room) {
                    when (state.screen) {
                        Screen.Palette -> {
                            // Read when the palette shows, not on every frame: it looks at the disk.
                            val wells = remember(state.screen) {
                                val collage = (rooms.collage.file ?: rooms.collage.recent().firstOrNull())?.let(Collages::nameOf)
                                paletteWells(boardState.lastBoard(), sketchState.session != null, rooms.lens.picture?.name, collage)
                            }
                            PaletteScreen(
                                wells,
                                selected = state.paletteRoom,
                                onSelect = state::selectRoom,
                                // The room opens under its pigment, once that covers the palette.
                                onOpen = { room -> if (bloom == null) bloom = Bloom(room, wellCentres[room] ?: Offset.Unspecified, opening = true) },
                                corner = { MenuExtras(state, practice, boardState) },
                                onWellPlaced = { room, centre -> wellCentres[room] = centre },
                            )
                        }
                        Screen.Practice -> PracticeRoom(practice, thumbs, isFullscreen, onToggleFullscreen, onHome = state::showPalette)
                        Screen.BoardList -> BoardListScreen(boardState, thumbs, onHome = boardState::leaveList)
                        Screen.Board -> BoardScreen(boardState, thumbs, isFullscreen, setFullscreen, onHome = boardState::closeBoard)
                        Screen.Concepts -> ConceptListScreen(conceptState, thumbs, onHome = conceptState::leaveList)
                        Screen.Concept -> ConceptScreen(conceptState, thumbs, onHome = {
                            conceptState.closeConcept()
                            conceptState.leaveList()
                        })
                        // Leaving the screen lets go of the pen and the mouse, as Back does (see Main).
                        Screen.Sketch -> SketchScreen(sketchState, thumbs, onHome = { state.home(Screen.Sketch) })
                        Screen.Lens -> LensRoomScreen(rooms.lens, onHome = { state.home(Screen.Lens) })
                        Screen.Collage -> CollageScreen(rooms.collage, onHome = { state.home(Screen.Collage) })
                    }
                }
                // The room's pigment, settled into a line along the top — not over a running pose.
                state.screen.room?.takeIf { !(state.screen == Screen.Practice && practice.page == PracticePage.Session) }?.let {
                    RoomLine(it, Modifier.align(Alignment.TopCenter))
                }
                BloomLayer(
                    bloom,
                    onCovered = { bloom?.takeIf { it.opening }?.let { openRoom(it.room, state, boardState, conceptState) } },
                    onDone = { bloom = null },
                )
                // Board and concept dialogs float above every screen (the pickers open from the menu).
                RoomTheme(Room.BOARDS) { BoardDialogs(boardState) }
                RoomTheme(Room.CONCEPTS) { ConceptDialogs(conceptState) }
                RoomTheme(Room.SKETCH) { SketchDialogs(sketchState) }
            }
        }
    }
}

/**
 * What each well of the palette says, from what exists: each room's next step. [lensPicture] is
 * what the Lens room is looking at, if anything; [collage] the collage open or kept last.
 */
internal fun paletteWells(lastBoard: Pair<String, java.io.File>?, sketchOpen: Boolean, lensPicture: String? = null, collage: String? = null): List<PaletteWell> =
    listOf(
        PaletteWell(Room.PRACTICE, "draw against the clock", "Timed poses and memory drawing.", "Set up a session"),
        PaletteWell(Room.LENS, "see it differently", "Any picture through notan, edge or the glass.", lensPicture?.let { "Look at $it" } ?: "Open a picture"),
        PaletteWell(
            Room.SKETCH, "draw", "Seven tools, three papers, a real pen.",
            if (sketchOpen) "Continue the sketch" else "New sketch",
        ),
        PaletteWell(Room.COLLAGE, "cut and compose", "Cut pictures apart, test whether a scene holds.", collage?.let { "Continue $it" } ?: "New collage"),
        PaletteWell(Room.CONCEPTS, "keep", "Kept once, linked onto every board.", "All concepts"),
        PaletteWell(
            Room.BOARDS, "collect", "Pin, group and arrange your material.",
            lastBoard?.let { "Open ${it.first}" } ?: "All boards",
        ),
    )

/**
 * Esc at the top of a room that has no Esc of its own goes home: the Practice setup and the board
 * list. The others already climb there — a concept to the concept list, the list to the palette,
 * a board and a sketch to where they were opened from. Pure, so a test can press it.
 */
internal fun escapeToPalette(state: AppState, practice: PracticeState, boards: BoardState): Boolean = when (state.screen) {
    Screen.Practice -> (practice.page == PracticePage.Menu).also { if (it) state.showPalette() }
    Screen.BoardList -> { boards.leaveList(); true }
    else -> false
}

/** Opens [room] from the palette, at its next step; the well stays selected for the way back. */
internal fun openRoom(room: Room, state: AppState, boards: BoardState, concepts: ConceptState) {
    state.selectRoom(room)
    when (room) {
        Room.PRACTICE -> state.showPractice()
        Room.BOARDS -> {
            val last = boards.lastBoard()
            if (last == null) {
                boards.openBoardList()
            } else {
                boards.openBoard(last.second)
                // A board that cannot be read keeps its flag up for the list, which says so.
                if (boards.openFailed) state.showBoardList()
            }
        }
        Room.CONCEPTS -> concepts.openList()
        Room.SKETCH -> state.enter(Screen.Sketch, from = Screen.Palette)
        Room.LENS -> state.enter(Screen.Lens, from = Screen.Palette)
        Room.COLLAGE -> state.enter(Screen.Collage, from = Screen.Palette)
    }
}
