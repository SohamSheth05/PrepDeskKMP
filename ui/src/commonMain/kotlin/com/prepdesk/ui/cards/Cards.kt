package com.prepdesk.ui.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prepdesk.domain.*
import com.prepdesk.ui.stateOf
import kotlinx.coroutines.flow.*
import org.koin.compose.viewmodel.koinViewModel

/** Personal revision cards written by the learner. */
class CardsViewModel(progress: ProgressSource, private val deck: DeckRepository) : ViewModel() {
    val cards: StateFlow<List<Card>> = progress.state.map { it.cards }.let { stateOf(it, progress.state.value.cards) }

    fun add(topic: String, prompt: String, answer: String) = deck.addCard(topic, prompt, answer)
    fun delete(card: Card) = deck.deleteCard(card)
}

@Composable
fun CardsScreen(vm: CardsViewModel = koinViewModel()) {
    val cards by vm.cards.collectAsStateWithLifecycle()
    var topic by remember { mutableStateOf("") }
    var q by remember { mutableStateOf("") }
    var a by remember { mutableStateOf("") }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            OutlinedTextField(topic, { topic = it }, label = { Text("Topic") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(q, { q = it }, label = { Text("Question or prompt") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(a, { a = it }, label = { Text("Answer") }, modifier = Modifier.fillMaxWidth())
            Button({ vm.add(topic, q, a); q = ""; a = "" }, enabled = q.isNotBlank() && a.isNotBlank(), modifier = Modifier.padding(top = 8.dp)) { Text("Add card") }
            Text("${cards.size} cards", Modifier.padding(top = 8.dp))
        }
        items(cards, key = { it.id }) { c ->
            ListItem(headlineContent = { Text(c.q) }, supportingContent = { Text(c.topic) },
                trailingContent = { TextButton({ vm.delete(c) }) { Text("Delete") } })
        }
    }
}
