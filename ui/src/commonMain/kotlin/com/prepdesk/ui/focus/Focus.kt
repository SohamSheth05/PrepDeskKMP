package com.prepdesk.ui.focus

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prepdesk.domain.*
import com.prepdesk.ui.*
import com.prepdesk.ui.components.fmt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import org.koin.compose.viewmodel.koinViewModel

data class FocusUiState(val timer: FocusSession = FocusSession(), val finished: Int = 0, val saving: Boolean = false)

/** Pomodoro timer. Completion is counted by AppViewModel so it also happens on other screens. */
class FocusViewModel(progress: ProgressSource, private val focus: FocusRepository, status: AppStatus) : ViewModel() {
    val uiState: StateFlow<FocusUiState> = snapshotOf(progress, status).map { FocusUiState(it.state.focus, it.state.pomos, it.saving) }
        .let { stateOf(it, FocusUiState(progress.state.value.focus, progress.state.value.pomos)) }

    fun toggle() = focus.toggle()
    fun reset() = focus.reset()
}

@Composable
fun FocusScreen(vm: FocusViewModel = koinViewModel()) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val timer = ui.timer
    var tick by remember { mutableStateOf(nowMs()) }
    LaunchedEffect(timer.deadline) { while (timer.deadline != null) { tick = nowMs(); delay(500) } }
    val t = ((timer.remaining(tick) + 999) / 1000).toInt()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${if (timer.focus) "Focus" else "Break"} · ${ui.finished} sessions finished")
        Text(fmt(t), style = MaterialTheme.typography.displayLarge)
        Text("Your timer keeps its deadline when you leave this tab or reopen the app. The next phase starts when you choose Start.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(vm::toggle, enabled = !ui.saving) { Text(if (timer.deadline != null) "Pause" else "Start") }
            OutlinedButton(vm::reset, enabled = !ui.saving) { Text("Reset") }
        }
    }
}
