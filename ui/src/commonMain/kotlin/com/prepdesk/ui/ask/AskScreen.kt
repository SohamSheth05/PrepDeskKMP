package com.prepdesk.ui.ask

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prepdesk.domain.*
import com.prepdesk.ui.components.*
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AskScreen(vm: AskViewModel = koinViewModel()) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    var open by remember { mutableStateOf<Q?>(null) }
    val list = rememberLazyListState()
    fun send(text: String) { if (text.isNotBlank() && !ui.busy) { vm.send(text); draft = "" } }
    LaunchedEffect(ui.messages.size, ui.busy) { if (ui.messages.isNotEmpty()) list.animateScrollToItem(list.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1) }

    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            if (ui.messages.isEmpty()) {
                item { AskIntro(ui.aiAvailable) }
                item { Text("Try asking", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(ui.suggestions) { s ->
                    OutlinedCard(onClick = { send(s) }, modifier = Modifier.fillMaxWidth()) { Text(s, Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) }
                }
            } else item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ModeBadge(ui.aiAvailable); Spacer(Modifier.weight(1f))
                    TextButton(vm::clear, enabled = !ui.busy) { Text("New chat") }
                }
            }
            items(ui.messages, key = { it.id }) { m ->
                if (m.source == TutorSource.User) UserBubble(m.text)
                else ReplyBubble(m, m.related.mapNotNull { ui.related[it] }) { open = it }
            }
            if (ui.busy) item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(if (ui.aiAvailable) "Thinking…" else "Searching your library…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(draft, { if (it.length <= 1_000) draft = it }, Modifier.weight(1f), placeholder = { Text("Ask about any interview topic…") },
                shape = RoundedCornerShape(24.dp), maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send(draft) }),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest))
            FilledIconButton({ send(draft) }, enabled = draft.isNotBlank() && !ui.busy, modifier = Modifier.size(52.dp)) { Icon(PrepIcons.Send, "Send") }
        }
    }
    open?.let { q ->
        RelatedQuestionDialog(q, q.id in ui.inDeck, onAdd = { vm.addToDeck(q) }, onAsk = { action -> vm.askAbout(q, action); open = null }, onClose = { open = null })
    }
}

@Composable
private fun AskIntro(ai: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(PrepIcons.Ask, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text("Ask MockMentor", style = MaterialTheme.typography.headlineSmall)
        Text(if (ai) "Ask anything about interviews and engineering. Answers use AI, grounded in your question library, and link to questions you can save for review."
            else "Ask anything and get the closest answers from your question library, even offline. Connect to the internet to get AI answers.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        ModeBadge(ai)
    }
}

@Composable
private fun ModeBadge(ai: Boolean) {
    val (label, color) = if (ai) "AI answers on" to Good else "Library mode" to MaterialTheme.colorScheme.outline
    Row(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun UserBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp), modifier = Modifier.widthIn(max = 320.dp)) {
            Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun ReplyBubble(m: TutorMessage, related: List<Q>, open: (Q) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (m.source == TutorSource.AI) "MockMentor AI" else "From your library", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp))) {
            SelectionContainer { RichText(m.text, Modifier.padding(14.dp)) }
        }
        if (related.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            related.forEach { q ->
                AssistChip(onClick = { open(q) }, label = { Text(q.q, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp)) },
                    leadingIcon = { Icon(PrepIcons.Book, null, Modifier.size(16.dp)) })
            }
        }
        if (m.source == TutorSource.AI) Text("AI can be wrong. Check important details against the reference answer.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RelatedQuestionDialog(q: Q, inDeck: Boolean, onAdd: () -> Unit, onAsk: (TutorAction) -> Unit, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose,
        title = { Text(q.q, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${q.area} · ${q.subtopic}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(q.a)
                if (q.explanation.isNotBlank()) Text(q.explanation, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AskActions { onAsk(it) }
            }
        },
        confirmButton = { TextButton(onAdd, enabled = !inDeck) { Text(if (inDeck) "In your deck" else "Add to deck") } },
        dismissButton = { TextButton(onClose) { Text("Close") } })
}
