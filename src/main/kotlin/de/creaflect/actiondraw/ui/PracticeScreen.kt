package de.creaflect.actiondraw.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.creaflect.actiondraw.AppState
import de.creaflect.actiondraw.RampStep
import de.creaflect.actiondraw.SessionPlan
import de.creaflect.actiondraw.SessionPlans
import de.creaflect.actiondraw.ViewMode

/**
 * The Practice room (F9.6): the exercises as index cards taped to the table, and beside them
 * where the pictures come from, the timing — the ramp drawn as graphite bars — and Start.
 * [onHome] is the way back to the palette (none when a test composes the screen on its own).
 */
@Composable
fun PracticeScreen(state: AppState, onHome: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().grain().padding(horizontal = 24.dp, vertical = 6.dp)) {
        // Where the pictures come from sits in the room's header, leaving the panel to the timing.
        if (onHome != null) RoomHeader(Room.PRACTICE, onHome, actions = { DrawingFrom(state) })
        else DrawingFrom(state)
        Row(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.fillMaxSize().padding(top = 6.dp, bottom = 18.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 12.dp, end = 8.dp),
            ) {
                Overline("Choose an exercise")
                ExerciseCards(state)
                Text(
                    "Unseen pictures come first; pictures flagged for redo come before anything new.",
                    fontSize = 13.sp,
                    color = Atelier.Muted,
                )
            }
            TimingPanel(state, Modifier.width(360.dp).fillMaxHeight())
        }
    }
}

@Composable
private fun Overline(text: String) {
    Text(text.uppercase(), fontSize = 12.sp, letterSpacing = 1.8.sp, color = Atelier.Muted)
}

/** Each card lies a little askew on the table, as cards do. */
private val CARD_TILT = floatArrayOf(-1f, 0.8f, -0.6f, 0.5f, -0.9f, 0.7f)

@Composable
private fun ExerciseCards(state: AppState) {
    val chosen = state.exercise
    Column(verticalArrangement = Arrangement.spacedBy(28.dp), modifier = Modifier.padding(top = 10.dp)) {
        Exercise.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { exercise ->
                    ExerciseCard(exercise, exercise == chosen, onChoose = { state.chooseExercise(exercise) }, Modifier.weight(1f))
                }
            }
        }
    }
}

/** An index card: its drawing, its name, one sentence. The chosen one is lifted and taped in cadmium. */
@Composable
private fun ExerciseCard(exercise: Exercise, chosen: Boolean, onChoose: () -> Unit, modifier: Modifier) {
    val lift by animateFloatAsState(if (chosen) 1f else 0f, spring(dampingRatio = Motion.SETTLE_DAMPING, stiffness = Motion.SETTLE_STIFFNESS))
    val glow = Room.PRACTICE.pigment.glow
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier.graphicsLayer {
            rotationZ = CARD_TILT[exercise.ordinal]
            translationY = -6.dp.toPx() * lift
            alpha = if (exercise.available) 1f else 0.5f
        },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (chosen) {
                        val inset = 5.dp.toPx()
                        drawRect(glow, Offset(-inset, -inset), Size(size.width + 2 * inset, size.height + 2 * inset), style = Stroke(2.dp.toPx()))
                    }
                }
                .lampShadow(if (chosen) Lift.LIFTED else Lift.RESTING, shape)
                .clip(shape)
                .background(Atelier.Paper)
                .then(if (exercise.available) Modifier.clickable(role = Role.Button, onClickLabel = exercise.title, onClick = onChoose) else Modifier)
                .semantics {
                    selected = chosen
                    if (!exercise.available) disabled()
                }
                .testTag("exercise-" + exercise.name.lowercase())
                .padding(14.dp),
        ) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(258f / 124f).background(Color(0xFFECE5D8))) { drawExercise(exercise) }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(exercise.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Atelier.Ink)
                if (!exercise.available) Text("coming", style = AtelierType.Hand.copy(fontSize = 20.sp), color = Atelier.InkQuiet)
            }
            Text(exercise.line, fontSize = 13.sp, lineHeight = 18.sp, color = Atelier.InkQuiet, minLines = 3)
        }
        // A strip of tape across the top edge — cadmium on the chosen card.
        Canvas(Modifier.align(Alignment.TopCenter).offset(y = (-10).dp).size(80.dp, 22.dp)) {
            drawTape(
                center, size, if (chosen) -3f else 2f, seed = exercise.ordinal,
                color = if (chosen) Room.PRACTICE.pigment.mass.copy(alpha = 0.88f) else TapeColor,
            )
        }
    }
}

/** Where the pictures come from, how long each pose is, and Start. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimingPanel(state: AppState, modifier: Modifier) {
    val exercise = state.exercise
    val plan = state.rampPlan
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Atelier.Board).padding(22.dp)) {
        // The settings scroll when the window is short; Start is pinned under them.
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        ) {
            Column {
                Text(exercise.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Atelier.Text)
                Text(exercise.verb, style = AtelierType.Hand.copy(fontSize = 22.sp), color = Room.PRACTICE.pigment.glow)
            }
            Overline("Timing")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectChip("Fixed time", plan == null) { state.rampPlan = null }
                SessionPlans.ALL.forEach { p -> SelectChip(p.name, plan == p) { state.rampPlan = p } }
            }
            if (plan == null) {
                IntervalSelector(seconds = state.intervalSeconds, onChange = { state.intervalSeconds = it })
            } else {
                RampDrawing(plan)
                if (plan.hasMemorySteps) {
                    Text(
                        "The reference is hidden after the study time — you draw the rest from memory, " +
                            "and it comes back at the end so you can compare.",
                        fontSize = 12.sp,
                        color = Atelier.Muted,
                    )
                }
            }
            if (exercise == Exercise.LENS) {
                Overline("Starting lens")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LENSES.drop(1).forEach { (mode, name) -> LensChoice(mode, name, state.viewMode == mode) { state.viewMode = mode } }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.autoAdvance, onCheckedChange = { state.autoAdvance = it })
                Text("Auto-advance to the next picture", fontSize = 14.sp, color = Atelier.TextSoft)
            }
            if (state.lastSessionPoses > 0) {
                Text(
                    "Last session: ${state.lastSessionPoses} poses · ${formatDuration(state.lastSessionSeconds)}",
                    fontSize = 12.sp,
                    color = Atelier.Muted,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { state.start() },
            enabled = state.selectedCount > 0,
            modifier = Modifier.fillMaxWidth().height(56.dp).testTag("practice-start"),
        ) {
            Text(if (plan != null) "Start · ${formatDuration(plan.totalSeconds)}" else "Start", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "space pauses · arrows change the picture",
            style = AtelierType.Hand.copy(fontSize = 19.sp),
            color = Atelier.Muted,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp),
        )
    }
}

/** Where the pictures come from, in one line: the folder, how many are unseen, and the ways to change both. */
@Composable
private fun DrawingFrom(state: AppState) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Drawing from", fontSize = 13.sp, color = Atelier.Muted)
        Text(
            state.folder?.name ?: "no folder yet",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Atelier.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 240.dp),
        )
        if (state.folder != null) {
            Text(
                if (state.selection == null) "${state.unseenCount} of ${state.totalCount} unseen"
                else "${state.unseenCount} of ${state.selectedCount} chosen unseen",
                fontSize = 12.sp,
                color = Atelier.Muted,
            )
        }
        TextButton(onClick = { chooseFolder(state.folder)?.let { state.selectFolder(it) } }) {
            Text(if (state.folder == null) "Choose a folder…" else "Change…")
        }
        if (state.folder != null) {
            TextButton(onClick = { state.openPicker() }) {
                Text(
                    if (state.selection == null) "Choose pictures… (all ${state.totalCount})"
                    else "Choose pictures… (${state.selectedCount} of ${state.totalCount})",
                )
            }
        }
    }
}

/** The ramp drawn as graphite bars, one per pose, each as tall as its pose is long; the first in cadmium. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RampDrawing(plan: SessionPlan) {
    val bars = rampBars(plan)
    val starts = plan.steps.runningFold(0) { at, step -> at + step.count }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF1A1715)).padding(14.dp).testTag("ramp"),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            plan.steps.forEachIndexed { leg, step ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(92.dp)) {
                        repeat(step.count) { k ->
                            val i = starts[leg] + k
                            Box(
                                Modifier
                                    .width(6.dp)
                                    .height(18.dp + 72.dp * bars[i])
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (i == 0) Room.PRACTICE.pigment.glow else Color(0xFF8C8377)),
                            )
                        }
                    }
                    Text(legLabel(step), style = AtelierType.Hand.copy(fontSize = 17.sp), color = Atelier.Muted)
                }
            }
        }
        Text("${plan.totalPoses} poses · ${formatDuration(plan.totalSeconds)}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Atelier.Text)
    }
}

private fun legLabel(step: RampStep): String =
    formatTime(step.seconds) + " × " + step.count + (step.studySeconds?.let { " · look ${it}s" } ?: "")

/** A lens to start the session with, as a small slide. */
@Composable
private fun LensChoice(mode: ViewMode, name: String, chosen: Boolean, onChoose: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .clickable(role = Role.Button, onClickLabel = name, onClick = onChoose)
            .semantics { selected = chosen }
            .testTag("start-lens-" + mode.name.lowercase())
            .padding(2.dp),
    ) {
        Canvas(
            Modifier
                .size(40.dp)
                .drawBehind { if (chosen) drawRect(Room.PRACTICE.pigment.glow, Offset(-3f, -3f), Size(size.width + 6f, size.height + 6f), style = Stroke(2.dp.toPx())) }
                .clip(RoundedCornerShape(6.dp)),
        ) { drawLensSign(mode) }
        Text(name, fontSize = 11.sp, color = if (chosen) Atelier.Text else Atelier.Muted)
    }
}
