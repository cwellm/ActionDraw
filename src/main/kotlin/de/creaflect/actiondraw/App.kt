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
import de.creaflect.actiondraw.board.ui.MenuExtras
import de.creaflect.actiondraw.board.ui.BoardScreen
import de.creaflect.actiondraw.image.ThumbCache
import de.creaflect.actiondraw.ui.AtelierTheme
import de.creaflect.actiondraw.ui.Bloom
import de.creaflect.actiondraw.ui.BloomLayer
import de.creaflect.actiondraw.ui.MenuScreen
import de.creaflect.actiondraw.ui.PaletteScreen
import de.creaflect.actiondraw.ui.PaletteWell
import de.creaflect.actiondraw.ui.Room
import de.creaflect.actiondraw.ui.RoomLine
import de.creaflect.actiondraw.ui.PickerScreen
import de.creaflect.actiondraw.ui.SessionScreen
import de.creaflect.actiondraw.ui.SummaryScreen
import de.creaflect.actiondraw.concept.ConceptState
import de.creaflect.actiondraw.concept.ui.ConceptDialogs
import de.creaflect.actiondraw.concept.ui.ConceptListScreen
import de.creaflect.actiondraw.concept.ui.ConceptScreen
import de.creaflect.actiondraw.sketch.SketchState
import de.creaflect.actiondraw.sketch.ui.SketchScreen
import de.creaflect.actiondraw.sketch.ui.SketchDialogs

/**
 * Every screen belongs to a [Room] of the palette, except the palette itself, which is home.
 * [Menu] is the Practice room's session setup — the start menu before the palette.
 */
enum class Screen(val room: Room?) {
    Palette(null),
    Menu(Room.PRACTICE), Picker(Room.PRACTICE), Session(Room.PRACTICE), Summary(Room.PRACTICE),
    Board(Room.BOARDS), BoardList(Room.BOARDS),
    Concepts(Room.CONCEPTS), Concept(Room.CONCEPTS),
    Sketch(Room.SKETCH),
}

@Composable
fun App(
    state: AppState,
    boardState: BoardState,
    conceptState: ConceptState,
    sketchState: SketchState,
    thumbs: ThumbCache,
    pinTargets: PinTargets,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    setFullscreen: (Boolean) -> Unit,
) {
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
                when (state.screen) {
                    Screen.Palette -> {
                        // Read when the palette shows, not on every frame: it looks at the disk.
                        val wells = remember(state.screen) { paletteWells(boardState.lastBoard(), sketchState.session != null) }
                        PaletteScreen(
                            wells,
                            selected = state.paletteRoom,
                            onSelect = state::selectRoom,
                            // The room opens under its pigment, once that covers the palette.
                            onOpen = { room -> if (bloom == null) bloom = Bloom(room, wellCentres[room] ?: Offset.Unspecified, opening = true) },
                            corner = { MenuExtras(state, boardState) },
                            onWellPlaced = { room, centre -> wellCentres[room] = centre },
                        )
                    }
                    Screen.Menu -> MenuScreen(state, onHome = state::showPalette)
                    Screen.Picker -> PickerScreen(state, thumbs)
                    Screen.Session -> SessionScreen(
                        state,
                        onToggleFullscreen,
                        isFullscreen,
                        pinTargets,
                        onSketch = { picture ->
                            sketchState.openFromSession(picture)
                            state.sketchFromSession()
                        },
                    )
                    Screen.Summary -> SummaryScreen(state, pinTargets)
                    Screen.BoardList -> BoardListScreen(boardState, thumbs, onHome = boardState::leaveList)
                    Screen.Board -> BoardScreen(boardState, thumbs, isFullscreen, setFullscreen, onHome = boardState::closeBoard)
                    Screen.Concepts -> ConceptListScreen(conceptState, thumbs, onHome = conceptState::leaveList)
                    Screen.Concept -> ConceptScreen(conceptState, thumbs, onHome = {
                        conceptState.closeConcept()
                        conceptState.leaveList()
                    })
                    // Leaving the screen lets go of the pen and the mouse, as Back does (see Main).
                    Screen.Sketch -> SketchScreen(sketchState, thumbs, onHome = state::sketchHome)
                }
                // The room's pigment, settled into a line along the top — not over a running pose.
                state.screen.room?.takeIf { state.screen != Screen.Session }?.let {
                    RoomLine(it, Modifier.align(Alignment.TopCenter))
                }
                BloomLayer(
                    bloom,
                    onCovered = { bloom?.takeIf { it.opening }?.let { openRoom(it.room, state, boardState, conceptState) } },
                    onDone = { bloom = null },
                )
                // Board and concept dialogs float above every screen (the pickers open from the menu).
                BoardDialogs(boardState)
                ConceptDialogs(conceptState)
                SketchDialogs(sketchState)
            }
        }
    }
}

/**
 * What each well of the palette says, from what exists: only rooms that are built have a next
 * step. Lens and Collage come with M9; until then their wells are dry.
 */
internal fun paletteWells(lastBoard: Pair<String, java.io.File>?, sketchOpen: Boolean): List<PaletteWell> =
    listOf(
        PaletteWell(Room.PRACTICE, "draw against the clock", "Timed poses and memory drawing.", "Set up a session"),
        PaletteWell(Room.LENS, "see it differently", "Any picture through notan or edge. Not built yet.", null),
        PaletteWell(
            Room.SKETCH, "draw", "Seven tools, three papers, a real pen.",
            if (sketchOpen) "Continue the sketch" else "New sketch",
        ),
        PaletteWell(Room.COLLAGE, "cut and compose", "Cut pictures, test a scene. Not built yet.", null),
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
internal fun escapeToPalette(screen: Screen, state: AppState, boards: BoardState): Boolean = when (screen) {
    Screen.Menu -> { state.showPalette(); true }
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
        Room.SKETCH -> state.showSketch(from = Screen.Palette)
        Room.LENS, Room.COLLAGE -> Unit
    }
}
