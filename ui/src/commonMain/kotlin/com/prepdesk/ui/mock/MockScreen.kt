package com.prepdesk.ui.mock

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prepdesk.domain.*
import com.prepdesk.ui.components.*
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MockScreen(vm: MockViewModel = koinViewModel()) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val saving = ui.saving
    var area by remember { mutableStateOf(ui.defaultArea) }
    var level by remember { mutableStateOf(0) }
    val run = ui.run
    var answerDraft by remember(run?.id, run?.index) { mutableStateOf(run?.draft.orEmpty()) }
    var followupDraft by remember(run?.id, run?.index) { mutableStateOf(run?.followupNotes.orEmpty()) }
    var tick by remember { mutableStateOf(nowMs()) }
    var endRound by remember { mutableStateOf(false) }
    LaunchedEffect(run?.id, run?.index, run?.stage) {
        while (run != null && run.stage < 2) { tick = nowMs(); delay(500) }
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (run == null) {
            item {
                Text("Interview practice", style = MaterialTheme.typography.headlineSmall)
                Text("Up to five questions balanced across available round types. Answer first, then practise follow-ups and check the expected points. Your answers and results are saved on this device.")
                Text("Assessment is your own checklist; it is not an automated interview verdict.", style = MaterialTheme.typography.bodySmall)
                AreaSelector(listOf("All areas", "My deck") + ui.areas, area) { area = it }
                ChoiceRow(LEVELS, LEVELS[level]) { level = LEVELS.indexOf(it) }
                val count = ui.available(area, level)
                Button({ vm.start(area, level) }, enabled = count > 0) { Text("Start ${minOf(5, count)}-question round") }
                if (count == 0) Text("No questions match. Choose another level or add cards.")
            }
            ui.lastResult?.let { result -> item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Last round: ${result.score}/${result.total} self-assessed points")
                        Text("${result.attempts.count { it.weak }} answers need revision. See History for your answers and feedback.")
                    }
                }
            } }
        } else {
            val card = run.cards[run.index]
            val q = run.questions[run.index]
            item {
                Text("Question ${run.index + 1}/${run.cards.size} · ${q?.round ?: "My deck"}", style = MaterialTheme.typography.labelLarge)
                if (run.stage < 2) {
                    val left = ((run.deadline - tick).coerceAtLeast(0) / 1000).toInt()
                    Text(fmt(left), style = MaterialTheme.typography.headlineLarge)
                    if (left == 0) Text("Time is up. Finish your thought and continue.")
                }
                QCard(card, null)
                TextButton({ endRound = true }, enabled = !saving) { Text("End this mock round") }
            }
            when (run.stage) {
                0 -> item {
                    Text("Main answer · 2 minutes")
                    OutlinedTextField(answerDraft, { value -> answerDraft = value; vm.setDraft(value) },
                        label = { Text("Your answer or notes after speaking aloud") }, minLines = 4, modifier = Modifier.fillMaxWidth())
                    VoiceAnswer(ui.signedIn && ui.capabilities.transcription, saving, vm::transcribe, vm::reportError) { text -> if (vm.uiState.value.run?.let { it.id == run.id && it.index == run.index && it.stage == 0 } == true) { answerDraft = text; vm.applyTranscript(run, text) } }
                    Button({ vm.advance(run) }, enabled = !saving) { Text("Continue to follow-ups") }
                    Text("Leaving this tab keeps the round. The timer continues against its saved deadline.", style = MaterialTheme.typography.bodySmall)
                }
                1 -> item {
                    Text("Follow-up discussion · 1 minute", style = MaterialTheme.typography.titleMedium)
                    if (q?.followups.isNullOrEmpty()) Text("Explain an example, a trade-off, and how you would verify your answer.")
                    else q!!.followups.forEachIndexed { i, text -> Text("${i + 1}. $text") }
                    OutlinedTextField(followupDraft, { value -> followupDraft = value; vm.setFollowups(value) },
                        label = { Text("Follow-up answer or notes") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    VoiceAnswer(ui.signedIn && ui.capabilities.transcription, saving, vm::transcribe, vm::reportError) { text -> if (vm.uiState.value.run?.let { it.id == run.id && it.index == run.index && it.stage == 1 } == true) { followupDraft = text; vm.applyTranscript(run, text) } }
                    Button({ vm.advance(run) }, enabled = !saving) { Text("Review expected answer") }
                }
                else -> {
                    item {
                        Text("Check the points you actually covered", style = MaterialTheme.typography.titleMedium)
                        Text("Weak answers are added to your deck and become due for revision.")
                        Text("Your answer: ${run.draft.ifBlank { "Answered aloud; no notes recorded" }}")
                        if (run.followupNotes.isNotBlank()) Text("Follow-ups: ${run.followupNotes}")
                    }
                    if (q != null) item { QuestionDetails(q, ui.sources, showRubric = false) }
                    else item { Text(card.a) }
                    if (q != null && q.rubric.isNotEmpty() && ui.signedIn && ui.capabilities.assessment) item {
                        Text("Get optional AI coaching. This sends this answer and follow-up notes to the feedback service; your checklist remains your own assessment.")
                        OutlinedButton(vm::requestFeedback, enabled = !saving && run.draft.isNotBlank()) { Text("Send answer for AI feedback") }
                        run.aiFeedback?.let { FeedbackView(it) }
                    }
                    if (q?.rubric.isNullOrEmpty()) item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ vm.assess(run.index, false) }, enabled = !saving) { Text("Weak") }
                            Button({ vm.assess(run.index, true) }, enabled = !saving) { Text("Solid") }
                        }
                    } else {
                        items(q!!.rubric.indices.toList()) { index ->
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Checkbox(index in run.checked, { value -> vm.check(index, value) })
                                Text(q.rubric[index], Modifier.weight(1f))
                            }
                        }
                        item { Button({ vm.assess(run.index) }, enabled = !saving) { Text(if (run.index == run.cards.lastIndex) "Save round and feedback" else "Save and next question") } }
                    }
                }
            }
        }
    }
    if (endRound) AlertDialog(onDismissRequest = { endRound = false }, title = { Text("End mock round?") }, text = { Text("This discards the unfinished round. Previously completed rounds remain in History.") },
        confirmButton = { TextButton({ vm.cancel(); endRound = false }) { Text("End round") } }, dismissButton = { TextButton({ endRound = false }) { Text("Continue round") } })
}
