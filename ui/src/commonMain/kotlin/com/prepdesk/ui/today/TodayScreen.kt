package com.prepdesk.ui.today

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prepdesk.ui.AppScreen
import com.prepdesk.ui.components.*
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TodayScreen(vm: TodayViewModel = koinViewModel()) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val p = ui.plan
    val left = ui.daily.daysLeft
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PlayerCard(ui.player) { vm.go(AppScreen.Play) } }
        item {
            val heading = when {
                left == null || left < 0 -> "Your next preparation steps"
                left == 0 -> "Interview day${p.target.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}"
                else -> "$left ${if (left == 1) "day" else "days"} to ${p.target.ifBlank { "your interview" }}"
            }
            Text(heading, style = MaterialTheme.typography.headlineSmall)
            Text("${ui.daily.phase.label} · ${p.area} · ${LEVELS[p.lv]} · ${ui.actionsToday} study actions today")
            Text(ui.daily.phase.advice, style = MaterialTheme.typography.bodySmall)
            if (left == null || left < 0) TextButton({ vm.go(AppScreen.Plan) }) { Text("Set interview date") }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. Review ${ui.due} due cards", style = MaterialTheme.typography.titleMedium)
                    Text("Recall the answer before checking it.")
                    Button({ vm.go(AppScreen.Practice) }) { Text("Start practice") }
                    if (ui.daily.newCount > 0) {
                        Text("2. Learn ${ui.fresh.size} new questions", style = MaterialTheme.typography.titleMedium)
                        ui.fresh.take(5).forEach { Text("• ${it.q}") }
                        if (ui.fresh.size > 5) Text("+ ${ui.fresh.size - 5} more", style = MaterialTheme.typography.bodySmall)
                        Button({ vm.addAndPractise(ui.fresh) }, enabled = ui.fresh.isNotEmpty()) { Text("Add and practise") }
                    } else Text("2. No new material today. Strengthen your weakest topics below.", style = MaterialTheme.typography.titleMedium)
                    Text("3. ${if (ui.daily.mockToday) "Mock round due today" else "Test your explanation"}", style = MaterialTheme.typography.titleMedium)
                    val mockLabel = if (ui.mockActive) "Resume mock round" else "Open interview practice"
                    if (ui.daily.mockToday) Button({ vm.go(AppScreen.Mock) }) { Text(mockLabel) } else OutlinedButton({ vm.go(AppScreen.Mock) }) { Text(mockLabel) }
                    OutlinedButton({ vm.go(AppScreen.Focus) }) { Text("Start a focus session") }
                }
            }
        }
        item { ChallengesCard(ui.challenges, vm::go) }
        item { ReadinessCard(ui.readiness, vm::drill) }
        if (ui.weakPrompts.isNotEmpty()) item {
            Text("Recent answers to revisit", style = MaterialTheme.typography.titleMedium)
            ui.weakPrompts.forEach { Text("• $it") }
            TextButton({ vm.go(AppScreen.History) }) { Text("See saved answers and feedback") }
        }
        item {
            Text("Your library works offline. Source articles and videos open online.", style = MaterialTheme.typography.bodySmall)
            TextButton({ vm.go(AppScreen.Plan) }) { Text("Adjust my plan and see all topics") }
            TextButton({ vm.go(AppScreen.Prepare) }) { Text("Explore technologies and study answers") }
        }
    }
}
