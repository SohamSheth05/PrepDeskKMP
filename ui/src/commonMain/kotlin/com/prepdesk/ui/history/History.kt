package com.prepdesk.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prepdesk.domain.ProgressSource
import com.prepdesk.domain.MockResult
import com.prepdesk.ui.components.FeedbackView
import com.prepdesk.ui.stateOf
import kotlinx.coroutines.flow.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

class HistoryViewModel(progress: ProgressSource) : ViewModel() {
    val history: StateFlow<List<MockResult>> = progress.state.map { it.history }.let { stateOf(it, progress.state.value.history) }
}

@Composable
fun HistoryScreen(vm: HistoryViewModel = koinViewModel()) {
    val history by vm.history.collectAsStateWithLifecycle()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Mock history", style = MaterialTheme.typography.headlineSmall)
            Text("Self-assessed answer points. Scores measure your checklist coverage, not hiring probability.")
            if (history.isEmpty()) Text("Finish a mock round to save your answers and feedback here.")
        }
        items(history, key = { it.id }) { result ->
            var open by remember(result.id) { mutableStateOf(false) }
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val date = kotlin.time.Instant.fromEpochMilliseconds(result.startedAt).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
                    Text("${result.area} · $date", style = MaterialTheme.typography.titleMedium)
                    Text("${result.score}/${result.total} points · ${result.attempts.count { it.weak }} answers to revise")
                    TextButton({ open = !open }) { Text(if (open) "Hide answers" else "Review answers and feedback") }
                    if (open) result.attempts.forEach { attempt ->
                        HorizontalDivider()
                        Text(attempt.prompt, style = MaterialTheme.typography.titleMedium)
                        Text("${attempt.score}/${attempt.total} points · ${attempt.elapsedSeconds}s main answer · content v${attempt.version}")
                        Text("Your answer: ${attempt.userAnswer.ifBlank { "Answered aloud; no notes recorded" }}")
                        if (attempt.followupNotes.isNotBlank()) Text("Your follow-ups: ${attempt.followupNotes}")
                        Text("Reference answer: ${attempt.answer}")
                        attempt.aiFeedback?.let { FeedbackView(it) }
                        val missing = attempt.expected.filterIndexed { i, _ -> i !in attempt.checked }
                        if (missing.isNotEmpty()) { Text("Points to revisit:"); missing.forEach { Text("• $it") } }
                        else Text(if (attempt.weak) "Revisit this answer in Practice." else "You marked the expected points as covered.")
                    }
                }
            }
        }
    }
}
