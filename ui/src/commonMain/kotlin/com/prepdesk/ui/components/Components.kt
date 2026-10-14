package com.prepdesk.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.prepdesk.domain.*
import kotlin.math.roundToInt

val LEVELS = listOf("All levels", "0–2 yrs", "3–6 yrs", "7–10 yrs", "10–15 yrs")
val TIPS = listOf("", "Build fundamentals and explain them in your own words.",
    "Practise debugging, implementation choices, and stories from your projects.",
    "Practise architecture, scaling, performance, and technical ownership.",
    "Practise strategy, architecture trade-offs, mentoring, and business outcomes.")

fun fmt(s: Int) = "${(s / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}"
fun percent(value: Float) = "${(value * 100).roundToInt()}%"

@Composable
fun ChoiceRow(options: List<String>, selected: String, choose: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { value -> FilterChip(selected == value, { choose(value) }, { Text(value) }) }
    }
}

@Composable
fun AreaSelector(options: List<String>, selected: String, choose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("Track: $selected ▾") }
        DropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
            options.forEach { area -> DropdownMenuItem(text = { Text(area) }, onClick = { choose(area); expanded = false }) }
        }
    }
}

@Composable
fun QCard(card: Card, answer: String?) = ElevatedCard(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(card.topic, style = MaterialTheme.typography.labelLarge)
        Text(card.q, style = MaterialTheme.typography.titleLarge)
        if (answer != null) { HorizontalDivider(); Text(answer) }
    }
}

@Composable
fun CodeBlock(code: String) = SelectionContainer {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Text(code, fontFamily = FontFamily.Monospace, modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp))
    }
}

@Composable
fun PracticalTests(p: Practical) {
    if (p.tests.isEmpty()) return
    Text("Test cases", style = MaterialTheme.typography.titleMedium)
    p.tests.forEachIndexed { i, t -> Text("${i + 1}. ${t.input} → ${t.expected}") }
}

/** Read-only view of an exercise inside question details; attempts happen in the Practical tab. */
@Composable
fun PracticalSummary(id: String, p: Practical) {
    var solution by remember(id) { mutableStateOf(false) }
    Text("Practical · ${p.kind} · ${p.language}", style = MaterialTheme.typography.titleMedium)
    Text(p.task)
    if (p.starter.isNotBlank()) CodeBlock(p.starter)
    PracticalTests(p)
    Text("Attempt it in the Practical tab${if (p.runnable) ", where you can run the tests" else ""}.", style = MaterialTheme.typography.bodySmall)
    TextButton({ solution = !solution }) { Text(if (solution) "Hide reference solution" else "Show reference solution") }
    if (solution) CodeBlock(p.solution)
}

@Composable
fun QuestionDetails(q: Q, sources: List<Source>, showShortAnswer: Boolean = true, showRubric: Boolean = true) {
    val uri = LocalUriHandler.current
    var linkError by remember(q.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${q.subtopic} · ${q.round} · ${LEVELS[q.level]}", style = MaterialTheme.typography.labelLarge)
        if (showShortAnswer) { Text("Interview answer", style = MaterialTheme.typography.titleMedium); Text(q.a) }
        if (q.explanation.isNotBlank()) { Text("Why and how", style = MaterialTheme.typography.titleMedium); Text(q.explanation) }
        if (q.code.isNotBlank()) {
            Text("Example sketch", style = MaterialTheme.typography.titleMedium)
            CodeBlock(q.code)
        }
        q.practical?.let { p -> PracticalSummary(q.id, p) }
        if (showRubric && q.rubric.isNotEmpty()) {
            Text("A strong answer covers", style = MaterialTheme.typography.titleMedium)
            q.rubric.forEach { Text("• $it") }
        }
        if (q.mistake.isNotBlank()) { Text("Common mistake", style = MaterialTheme.typography.titleMedium); Text(q.mistake) }
        if (q.followups.isNotEmpty()) {
            Text("Interviewer follow-ups", style = MaterialTheme.typography.titleMedium)
            q.followups.forEachIndexed { i, text -> Text("${i + 1}. $text") }
        }
        HorizontalDivider()
        // Content credits predate the MockMentor name; renaming them in the bank would need new content versions.
        Text(q.origin.replace("PrepDesk original", "MockMentor original"), style = MaterialTheme.typography.bodySmall)
        Text("Content v${q.version}${if (q.reviewedAt.isNotBlank()) " · ${q.reviewedAt}" else ""}", style = MaterialTheme.typography.bodySmall)
        q.sourceIds.forEach { id -> sources.firstOrNull { it.id == id }?.let { source ->
            TextButton({ linkError = runCatching { uri.openUri(source.url) }.isFailure }) {
                Text("${source.kind}: ${source.name}")
            }
            Text("${source.author} · ${source.license}", style = MaterialTheme.typography.bodySmall)
        } }
        if (linkError) Text("Could not open the source link on this device.", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun MasteryBar(label: String, detail: String, mastery: Float, action: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
            Text(percent(mastery), style = MaterialTheme.typography.labelLarge)
            if (action != null) TextButton(action) { Text("Drill") }
        }
        LinearProgressIndicator(progress = { mastery }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun ReadinessCard(r: Readiness, drill: (TopicReadiness) -> Unit, limit: Int = 3) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Readiness ${percent(r.overall)}", style = MaterialTheme.typography.titleLarge)
            Text("${r.practised} of ${r.total} plan questions practised. Combines review stage and mock checklist coverage; unpractised questions count as zero.",
                style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = { r.overall }, modifier = Modifier.fillMaxWidth())
            if (limit > 0 && r.topics.isNotEmpty()) Text("Weakest topics", style = MaterialTheme.typography.titleMedium)
            r.weakest.take(limit).forEach { t -> MasteryBar(t.label, "${t.practised}/${t.questions.size} practised", t.mastery) { drill(t) } }
            Text("Self-assessed preparation coverage, not a hiring prediction.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun FeedbackView(feedback: AIFeedback) {
    Text("AI coaching — check against the reference", style = MaterialTheme.typography.titleMedium)
    Text(feedback.summary)
    feedback.strengths.forEach { Text("Strength: $it") }
    feedback.improvements.forEach { Text("Revisit: $it") }
}
