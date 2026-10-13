package com.prepdesk.ui.practice

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prepdesk.domain.*
import com.prepdesk.ui.components.*
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PracticeScreen(vm: PracticeViewModel = koinViewModel()) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    var topic by remember(ui.area) { mutableStateOf(ui.area) }
    var shown by remember { mutableStateOf(false) }
    var currentId by remember { mutableStateOf<Long?>(null) }
    val pool = ui.due.filter { topic == null || it.topic == topic }
    val current = pool.firstOrNull { it.id == currentId } ?: pool.firstOrNull()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(topic == null, { topic = null; currentId = null; shown = false }, { Text("All") })
                ui.topics.forEach { t ->
                    FilterChip(topic == t, { topic = t; currentId = null; shown = false }, { Text(t) })
                }
            }
        }
        if (current == null) item { Text("Nothing due. Add questions from Library or return for your next review.") }
        else {
            item {
                QCard(current, if (shown) current.a else null)
                Text("Review stage ${current.box + 1} of 5 · ${pool.size} due", style = MaterialTheme.typography.bodySmall)
                Text("Try answering out loud before revealing the explanation.")
                if (!shown) Button({ currentId = current.id; shown = true }) { Text("Show answer") }
                else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ vm.rate(current, false); currentId = null; shown = false }) { Text("Missed it") }
                    Button({ vm.rate(current, true); currentId = null; shown = false }) { Text("Got it") }
                }
            }
            if (shown) vm.question(current)?.let { q ->
                item { AskActions { vm.askAbout(q, it) } }
                item { QuestionDetails(q, ui.sources, showShortAnswer = false) }
            }
        }
    }
}
