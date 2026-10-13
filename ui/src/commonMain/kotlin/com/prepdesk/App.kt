package com.prepdesk

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prepdesk.ui.AppScreen
import com.prepdesk.ui.AppViewModel
import com.prepdesk.ui.MoreScreen
import com.prepdesk.ui.account.AccountScreen
import com.prepdesk.ui.ask.AskScreen
import com.prepdesk.ui.cards.CardsScreen
import com.prepdesk.ui.components.CelebrationDialog
import com.prepdesk.ui.components.PrepDeskTheme
import com.prepdesk.ui.components.PrepIcons
import com.prepdesk.ui.focus.FocusScreen
import com.prepdesk.ui.history.HistoryScreen
import com.prepdesk.ui.library.LibraryScreen
import com.prepdesk.ui.mock.MockScreen
import com.prepdesk.ui.plan.PlanScreen
import com.prepdesk.ui.play.PlayScreen
import com.prepdesk.ui.practical.PracticalScreen
import com.prepdesk.ui.practice.PracticeScreen
import com.prepdesk.ui.prepare.PrepareScreen
import com.prepdesk.ui.today.TodayScreen
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun App(vm: AppViewModel = koinViewModel()) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val screen by vm.screen.collectAsStateWithLifecycle()
    val tabState = rememberSaveableStateHolder()
    PrepDeskTheme {
        BackHandler(enabled = screen != AppScreen.Today) { vm.back() }
        ui.celebration?.let { c -> CelebrationDialog(c) { vm.dismissCelebration(c) } }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(if (screen == AppScreen.Today) "PrepDesk" else screen.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (screen == AppScreen.Today && ui.ready) Text("Level ${ui.level} · ${ui.due} due · ${ui.streak}-day streak",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = { if (!screen.isTab) IconButton(vm::back) { Icon(PrepIcons.Back, "Back") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                    AppScreen.tabs.forEach { tab ->
                        val selected = tab == screen || (tab == AppScreen.More && !screen.isTab)
                        NavigationBarItem(selected, { vm.select(tab) }, icon = { Icon(tab.icon, null) }, label = { Text(tab.label) })
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { pad ->
            Column(Modifier.padding(pad).padding(horizontal = 16.dp).padding(top = 4.dp).fillMaxSize()) {
                ui.error?.let { error ->
                    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                            TextButton({ if (ui.ready) vm.dismissError() else vm.retry() }) { Text(if (ui.ready) "Dismiss" else "Retry loading") }
                        }
                    }
                }
                if (!ui.ready) {
                    if (ui.error == null) Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(); Text("Preparing your question library…")
                    }
                } else Box(Modifier.weight(1f).fillMaxWidth()) {
                    tabState.SaveableStateProvider(screen.name) {
                        when (screen) {
                            AppScreen.Today -> TodayScreen()
                            AppScreen.Prepare -> PrepareScreen()
                            AppScreen.Ask -> AskScreen()
                            AppScreen.Practice -> PracticeScreen()
                            AppScreen.More -> MoreScreen(vm::select)
                            AppScreen.Play -> PlayScreen()
                            AppScreen.Mock -> MockScreen()
                            AppScreen.Library -> LibraryScreen()
                            AppScreen.Plan -> PlanScreen()
                            AppScreen.Focus -> FocusScreen()
                            AppScreen.Cards -> CardsScreen()
                            AppScreen.History -> HistoryScreen()
                            AppScreen.Account -> AccountScreen()
                            AppScreen.Code -> PracticalScreen()
                        }
                    }
                }
            }
        }
    }
}
