package de.creaflect.actiondraw

import androidx.compose.foundation.layout.Box
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import de.creaflect.actiondraw.board.BoardState
import de.creaflect.actiondraw.board.ui.BoardDialogs
import de.creaflect.actiondraw.board.ui.BoardListScreen
import de.creaflect.actiondraw.board.ui.BoardMenuButton
import de.creaflect.actiondraw.board.ui.MenuExtras
import de.creaflect.actiondraw.board.ui.BoardScreen
import de.creaflect.actiondraw.image.ThumbCache
import de.creaflect.actiondraw.ui.AtelierTheme
import de.creaflect.actiondraw.ui.MenuScreen
import de.creaflect.actiondraw.ui.Room
import de.creaflect.actiondraw.ui.PickerScreen
import de.creaflect.actiondraw.ui.SessionScreen
import de.creaflect.actiondraw.ui.SummaryScreen
import de.creaflect.actiondraw.concept.ConceptState
import de.creaflect.actiondraw.concept.ui.ConceptDialogs
import de.creaflect.actiondraw.concept.ui.ConceptListScreen
import de.creaflect.actiondraw.concept.ui.ConceptMenuButton
import de.creaflect.actiondraw.concept.ui.ConceptScreen
import de.creaflect.actiondraw.sketch.SketchState
import de.creaflect.actiondraw.sketch.ui.SketchMenuButton
import de.creaflect.actiondraw.sketch.ui.SketchScreen
import de.creaflect.actiondraw.sketch.ui.SketchDialogs

/** Every screen belongs to a [Room] of the palette, except the start menu, which is the palette. */
enum class Screen(val room: Room?) {
    Menu(null),
    Picker(Room.PRACTICE), Session(Room.PRACTICE), Summary(Room.PRACTICE),
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
    AtelierTheme {
        Surface {
            Box {
                when (state.screen) {
                    Screen.Menu -> MenuScreen(
                        state,
                        boardButton = {
                            BoardMenuButton(boardState)
                            ConceptMenuButton(conceptState)
                            SketchMenuButton(onOpen = { state.showSketch() })
                        },
                        extras = { MenuExtras(state, boardState) },
                    )
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
                    Screen.BoardList -> BoardListScreen(boardState, thumbs)
                    Screen.Board -> BoardScreen(boardState, thumbs, isFullscreen, setFullscreen)
                    Screen.Concepts -> ConceptListScreen(conceptState, thumbs)
                    Screen.Concept -> ConceptScreen(conceptState, thumbs)
                    Screen.Sketch -> SketchScreen(sketchState, thumbs)
                }
                // Board and concept dialogs float above every screen (the pickers open from the menu).
                BoardDialogs(boardState)
                ConceptDialogs(conceptState)
                SketchDialogs(sketchState)
            }
        }
    }
}
