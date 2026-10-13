package com.prepdesk.ui.plan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prepdesk.domain.*
import com.prepdesk.ui.*
import com.prepdesk.ui.components.*
import com.prepdesk.ui.today.launchDrill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

data class PlanUiState(
    val plan: StudyPlan = StudyPlan(), val daysLeft: Int? = null, val days: Int = 14, val poolSize: Int = 0, val fresh: List<Q> = emptyList(),
    val daily: DailyPlan = DailyPlan(PrepPhase.Open, null, 0, false), val readiness: Readiness = Readiness(0f, 0, 0, emptyList(), emptyMap()),
    val areas: List<String> = emptyList(),
)

class PlanViewModel(
    progress: ProgressSource, private val deck: DeckRepository, private val study: StudyRepository, status: AppStatus, private val navigator: Navigator,
) : ViewModel() {
    val uiState: StateFlow<PlanUiState> = snapshotOf(progress, status).map { snap ->
        val s = snap.state; val questions = snap.library.questions
        val pool = planPool(questions, s.plan).sortedByDescending { it.level }
        PlanUiState(s.plan, s.plan.daysLeft(), s.plan.effectiveDays(), pool.size, pool.filterNot { s.inDeck(it) },
            dailyPlan(s, questions), readiness(s, questions), questionAreas(questions))
    }.flowOn(Dispatchers.Default).let { stateOf(it, PlanUiState()) }

    private val _target = MutableStateFlow(progress.state.value.plan.target)
    /** Company or role text as typed; seeded from saved progress so the field never starts empty. */
    val target: StateFlow<String> = _target.asStateFlow()

    fun set(f: (StudyPlan) -> StudyPlan) = study.updatePlan(f)
    fun setTarget(value: String) {
        if (value.length > 200) return
        _target.value = value; set { it.copy(target = value.trim()) }
    }
    fun addToday() { val ui = uiState.value; deck.addQuestions(ui.fresh.take(ui.daily.newCount)) }
    fun drill(topic: TopicReadiness) = launchDrill(study, navigator, topic, uiState.value.readiness.scores)
}

private fun LocalDate.utcMillis() = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
private fun utcDate(millis: Long) = kotlin.time.Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(vm: PlanViewModel = koinViewModel()) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val p = ui.plan
    val left = ui.daysLeft
    val daily = ui.daily
    var picking by remember { mutableStateOf(false) }
    val target by vm.target.collectAsStateWithLifecycle()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Your preparation plan", style = MaterialTheme.typography.headlineSmall)
            Text("Interview")
            OutlinedTextField(target, vm::setTarget,
                label = { Text("Company or role (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ picking = true }) { Text(p.interviewDay()?.let { "Interview on $it" } ?: "Set interview date") }
                if (p.interviewDate != null) TextButton({ vm.set { it.copy(interviewDate = null) } }) { Text("Clear") }
            }
            Text("Experience")
            ChoiceRow(LEVELS.drop(1), LEVELS[p.lv]) { value -> vm.set { it.copy(lv = LEVELS.indexOf(value)) } }
            Text("Target area")
            AreaSelector(listOf("All areas") + ui.areas, if (p.area == "All") "All areas" else p.area) { value -> vm.set { it.copy(area = value) } }
            if (left == null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton({ vm.set { s -> s.copy(days = (s.days - 1).coerceAtLeast(1)) } }) { Text("−") }
                    Text("${p.days} days to prepare")
                    OutlinedButton({ vm.set { s -> s.copy(days = (s.days + 1).coerceAtMost(120)) } }) { Text("+") }
                }
                Text("Without a date this is a preparation budget. Set an interview date for an automatic countdown.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(daily.phase.label + (left?.takeIf { it > 0 }?.let { " · $it days left" } ?: ""), style = MaterialTheme.typography.titleLarge)
                    Text(daily.phase.advice)
                    Text("${ui.poolSize} plan questions · ${ui.fresh.size} not yet in your deck · ${ui.days}-day budget", style = MaterialTheme.typography.bodySmall)
                    Text(TIPS[p.lv])
                    Button(vm::addToday, enabled = ui.fresh.isNotEmpty() && daily.newCount > 0) {
                        Text(when { ui.fresh.isEmpty() -> "Plan questions are in your deck"; daily.newCount == 0 -> "No new questions in this phase"; else -> "Add today's ${daily.newCount} new questions" })
                    }
                }
            }
        }
        item { ReadinessCard(ui.readiness, vm::drill, limit = 0) }
        item { Text("Readiness by topic", style = MaterialTheme.typography.titleMedium) }
        items(ui.readiness.weakest, key = { it.label }) { t ->
            MasteryBar(t.label, "${t.practised}/${t.questions.size} practised", t.mastery) { vm.drill(t) }
        }
    }
    if (picking) {
        val start = today()
        val state = rememberDatePickerState(initialSelectedDateMillis = (p.interviewDay() ?: start.plus(14, DateTimeUnit.DAY)).utcMillis(),
            selectableDates = object : SelectableDates { override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= start.utcMillis() })
        DatePickerDialog(onDismissRequest = { picking = false },
            confirmButton = { TextButton({ state.selectedDateMillis?.let { ms -> vm.set { it.copy(interviewDate = utcDate(ms).toString()) } }; picking = false }, enabled = state.selectedDateMillis != null) { Text("Save") } },
            dismissButton = { TextButton({ picking = false }) { Text("Cancel") } }) { DatePicker(state) }
    }
}
