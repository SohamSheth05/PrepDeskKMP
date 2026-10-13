package com.prepdesk.ui.library

import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import org.koin.compose.viewmodel.koinViewModel

data class LibraryUiState(
    val filter: QuestionFilter = QuestionFilter(), val results: List<Q> = emptyList(), val total: Int = 0,
    val areas: List<String> = emptyList(), val subtopics: List<String> = emptyList(), val rounds: List<String> = emptyList(),
    val inDeck: Set<String> = emptySet(), val bookmarks: Set<String> = emptySet(), val sources: List<Source> = emptyList(),
) {
    val fresh: List<Q> get() = results.filter { it.id !in inDeck }
}

class LibraryViewModel(
    progress: ProgressSource, private val deck: DeckRepository, private val tutor: TutorRepository, status: AppStatus, private val navigator: Navigator,
) : ViewModel() {
    private val filter = MutableStateFlow(QuestionFilter())

    // Filtering 1,000+ questions runs off the main thread.
    val uiState: StateFlow<LibraryUiState> = combine(snapshotOf(progress, status), filter) { snap, f ->
        val s = snap.state; val all = snap.library.questions
        val inDeck = s.deckQuestionIds(all)
        LibraryUiState(f, filterQuestions(all, f, s.bookmarks).sortedBy { it.explanation.isBlank() }, all.size,
            questionAreas(all), all.filter { f.area == "All areas" || it.area == f.area }.map { it.subtopic }.distinct(),
            all.map { it.round }.distinct(), inDeck, s.bookmarks, snap.library.sources)
    }.flowOn(Dispatchers.Default).let { stateOf(it, LibraryUiState()) }

    fun setFilter(f: QuestionFilter) { filter.value = f }
    fun bookmark(id: String) = deck.toggleBookmark(id)
    fun addToDeck(questions: List<Q>) = deck.addQuestions(questions)
    fun askAbout(q: Q, action: TutorAction) { tutor.askAbout(q, action); navigator.go(AppScreen.Ask) }
}

@Composable
fun LibraryScreen(vm: LibraryViewModel = koinViewModel()) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val filter = ui.filter
    fun set(f: QuestionFilter) = vm.setFilter(f)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Interview library", style = MaterialTheme.typography.headlineSmall)
            Text("${ui.areas.size} tracks · ${ui.total} questions")
            Text("Original interview exercises with learning references, expected points, and follow-ups. Android includes Amit Shekhar topic references.")
            OutlinedTextField(filter.search, { set(filter.copy(search = it)) }, label = { Text("Search questions, topics, or concepts") }, modifier = Modifier.fillMaxWidth())
            AreaSelector(listOf("All areas") + ui.areas, filter.area) { set(filter.copy(area = it, subtopic = "All topics")) }
            ChoiceRow(listOf("All topics") + ui.subtopics, filter.subtopic) { set(filter.copy(subtopic = it)) }
            ChoiceRow(LEVELS, LEVELS[filter.level]) { set(filter.copy(level = LEVELS.indexOf(it))) }
            ChoiceRow(listOf("All rounds") + ui.rounds, filter.round) { set(filter.copy(round = it)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(filter.bookmarkedOnly, { set(filter.copy(bookmarkedOnly = it)) }); Text("Bookmarked only")
            }
            Text("${ui.results.size} matching questions")
            val fresh = ui.fresh
            Button({ vm.addToDeck(fresh) }, enabled = fresh.isNotEmpty()) { Text("Add ${fresh.size} to my deck") }
            if (ui.results.isEmpty()) Text("No matches. Change the search or filters.")
        }
        items(ui.results, key = { it.id }) { q ->
            var open by remember(q.id) { mutableStateOf(false) }
            val inDeck = q.id in ui.inDeck
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { open = !open }) {
                            Text(q.q, style = MaterialTheme.typography.titleMedium)
                            Text("${q.subtopic} · ${q.round}", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton({ vm.bookmark(q.id) }) { Text(if (q.id in ui.bookmarks) "★" else "☆") }
                    }
                    TextButton({ open = !open }) { Text(if (open) "Hide explanation" else "Read answer and follow-ups") }
                    if (open) { AskActions { vm.askAbout(q, it) }; QuestionDetails(q, ui.sources) }
                    TextButton({ vm.addToDeck(listOf(q)) }, enabled = !inDeck) { Text(if (inDeck) "In your deck" else "Add to practice") }
                }
            }
        }
    }
}
