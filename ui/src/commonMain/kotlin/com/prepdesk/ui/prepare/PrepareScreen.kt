package com.prepdesk.ui.prepare

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prepdesk.domain.*
import com.prepdesk.ui.components.*
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PrepareScreen(vm: PrepareViewModel = koinViewModel()) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val saving = ui.saving
    val mock = ui.mock
    val questions0 = ui.questions
    var area by rememberSaveable { mutableStateOf(ui.session?.area.orEmpty()) }
    var level by rememberSaveable { mutableStateOf(ui.session?.level ?: 0) }
    var topic by rememberSaveable { mutableStateOf("All topics") }
    var search by rememberSaveable { mutableStateOf("") }
    var questionSearch by rememberSaveable { mutableStateOf("") }
    var bankFilter by rememberSaveable { mutableStateOf("All questions") }
    var section by rememberSaveable { mutableStateOf("Learn") }
    val session = ui.session
    val confident = ui.confident
    fun start(ids: List<String>? = null) { if (!saving) vm.startQuest(area, level, topic, ids) }
    if (ui.studying && session != null) {
        StudyScreen(vm, ui, session) {
            area = session.area; level = session.level; topic = session.subtopic; vm.closeQuest()
        }
        return
    }
    val areas = ui.areas
    val trackQuestions = remember(questions0, area) { questions0.filter { it.area == area } }
    val questions = remember(questions0, area, level, topic) { studyQuestions(questions0, area, level, topic) }
    val links = remember(questions0, ui.sources, area, level, ui.resources) { readingLinks(questions0, ui.sources, area, level, ui.resources) }
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints {
        val columns = if (maxWidth >= 340.dp && fontScale <= 1.25f) 2 else 1
        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                PrepHero(if (area.isBlank()) "Your next chapter\nstarts here." else "Let's level up\nyour answers.",
                    if (area.isBlank()) "Pick a track. Build confidence, one question at a time." else "$area · recall, reveal, reflect.",
                    areas.size, questions0.size, ui.streak)
            }
            if (session != null && !session.complete) item {
                Card(colors = CardDefaults.cardColors(containerColor = Mint), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Your quest is waiting", color = Ink, style = MaterialTheme.typography.labelLarge)
                        Text(session.area, color = Ink, style = MaterialTheme.typography.titleMedium)
                        Text("Question ${session.index + 1} of ${session.questionIds.size} · ${session.reviewed.size} assessed", color = Ink)
                        QuestProgress(session.index, session.questionIds.size)
                        Button(vm::continueQuest, enabled = !saving) { Text("Continue quest →") }
                    }
                }
            }
            if (area.isBlank()) {
                item {
                    OutlinedTextField(search, { search = it }, label = { Text("Find your technology or topic") }, singleLine = true,
                        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Text("Choose your adventure", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Every track has 40+ questions · beginner to leadership", style = MaterialTheme.typography.bodySmall)
                }
                val matches = areas.filter { track -> track.contains(search.trim(), true) || questions0.any { it.area == track && it.subtopic.contains(search.trim(), true) } }
                if (matches.isEmpty()) item { Text("No tracks found. Try a technology or topic such as Kotlin, SQL, or networking.") }
                items(matches.chunked(columns), key = { it.first() }) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        row.forEach { track ->
                            val bank = questions0.filter { it.area == track }
                            TrackTile(track, bank.size, bank.count { it.id in confident }, TrackColors[areas.indexOf(track) % TrackColors.size], Modifier.weight(1f).fillMaxHeight()) {
                                area = track; topic = "All topics"; questionSearch = ""; bankFilter = "All questions"
                            }
                        }
                        if (row.size < columns) Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                item {
                    TextButton({ area = "" }) { Text("← All technologies") }
                    AreaSelector(areas, area) { area = it; topic = "All topics"; questionSearch = "" }
                    ChoiceRow(LEVELS, LEVELS[level]) { level = LEVELS.indexOf(it); topic = "All topics" }
                    if (level > 0) Text(TIPS[level], style = MaterialTheme.typography.bodySmall)
                    ChoiceRow(listOf("Learn", "Question bank", "Resources"), section) { section = it }
                }
                if (section == "Resources") {
                    item {
                        Text("Go a little deeper", style = MaterialTheme.typography.titleLarge)
                        Text("${links.size} reading resources for ${LEVELS[level]}. Links need internet; some articles may require membership.")
                    }
                    items(links, key = { it.source.id }) { ReadingCard(it) }
                    if (links.isEmpty()) item { Text("Try All levels to see more reading links.") }
                } else {
                    item {
                        ChoiceRow(listOf("All topics") + trackQuestions.filter { level == 0 || it.level == level }.map { it.subtopic }.distinct().sorted(), topic) { topic = it }
                    }
                    if (section == "Learn") {
                        item {
                            val ready = questions.count { it.id in confident }
                            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Lilac), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("YOUR LEARNING PATH", color = Ink, style = MaterialTheme.typography.labelLarge)
                                    Text("Small steps. Strong answers.", color = Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text("$ready / ${questions.size} reviewed confidently", color = Ink)
                                    QuestProgress(ready, questions.size)
                                    Text("Recall your answer, reveal the explanation, then choose what needs another look.", color = Ink)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button({ if (!saving) vm.startQuest(area, level, topic, questions, 5) }, enabled = questions.isNotEmpty() && !saving, modifier = Modifier.weight(1f)) { Text("5-question quest") }
                                        OutlinedButton({ if (!saving) vm.startQuest(area, level, topic, questions, 10) }, enabled = questions.isNotEmpty() && !saving, modifier = Modifier.weight(1f)) { Text("10-question quest") }
                                    }
                                    Text("Quests mix questions, prioritizing those not yet reviewed confidently.", color = Ink, style = MaterialTheme.typography.bodySmall)
                                    TextButton({ start() }, enabled = questions.isNotEmpty() && !saving) { Text("Study all ${questions.size} questions →") }
                                    if (session != null && !session.complete) Text("Starting again replaces your saved quest position. Revision cards stay saved.", color = Ink, style = MaterialTheme.typography.bodySmall)
                                    if (questions.isEmpty()) Text("No questions match. Try another topic or experience level.", color = Ink)
                                }
                            }
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                MetricTile("${questions.count { it.id in ui.inDeck }}", "saved for revision", Mint, Modifier.weight(1f))
                                MetricTile("${questions.count { it.id in ui.bookmarks }}", "bookmarked", Peach, Modifier.weight(1f))
                            }
                        }
                        item {
                            Text("Explore the question bank", style = MaterialTheme.typography.titleMedium)
                            Text("Pick a question, find a concept, or return to a bookmarked favorite.")
                            OutlinedButton({ section = "Question bank" }, modifier = Modifier.fillMaxWidth()) { Text("Browse ${questions.size} questions →") }
                            val due = ui.dueByArea[area] ?: 0
                            OutlinedButton({ vm.practice(area) }, modifier = Modifier.fillMaxWidth()) { Text("Review $due due cards") }
                            Button({ vm.mock(area, level) }, enabled = !saving && (mock != null || questions.isNotEmpty()), modifier = Modifier.fillMaxWidth()) {
                                Text(if (mock != null) "Resume ${mock.area} mock" else "Try an interview mock")
                            }
                            val isPlan = ui.plan.area == area && (level == 0 || ui.plan.lv == level)
                            TextButton({ vm.usePlanTrack(area, level) }, enabled = !saving && !isPlan) { Text(if (isPlan) "Current preparation plan" else "Use $area for my plan") }
                        }
                        item { Text("Topic trail", style = MaterialTheme.typography.titleLarge) }
                        items(questions.groupBy { it.subtopic }.entries.toList(), key = { it.key }) { (name, bank) ->
                            val ready = bank.count { it.id in confident }
                            Card(onClick = { topic = name; section = "Question bank" }, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    TrackBadge(if (ready == bank.size) "✓" else "?", Mint)
                                    Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.SemiBold); Text("$ready / ${bank.size} confident · tap to explore", style = MaterialTheme.typography.bodySmall) }
                                    Text("→", color = Purple)
                                }
                            }
                        }
                    } else {
                        item {
                            OutlinedTextField(questionSearch, { questionSearch = it }, label = { Text("Search questions and concepts") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                            ChoiceRow(listOf("All questions", "New", "Needs revision", "Confident", "Bookmarked"), bankFilter) { bankFilter = it }
                        }
                        val visible = filterQuestions(questions, QuestionFilter(search = questionSearch), ui.bookmarks).filter { q ->
                            when (bankFilter) {
                                "New" -> q.id !in ui.inDeck
                                "Needs revision" -> q.id in ui.inDeck && q.id !in confident
                                "Confident" -> q.id in confident
                                "Bookmarked" -> q.id in ui.bookmarks
                                else -> true
                            }
                        }
                        item {
                            Text("${visible.size} questions · tap one to practise", style = MaterialTheme.typography.labelLarge)
                            if (session != null && !session.complete) Text("Picking a question starts a new quest. Your revision cards stay saved.", style = MaterialTheme.typography.bodySmall)
                        }
                        if (visible.isEmpty()) item {
                            Text("No questions match these filters.")
                            TextButton({ questionSearch = ""; bankFilter = "All questions"; topic = "All topics"; level = 0 }) { Text("Reset filters") }
                        }
                        items(visible, key = { it.id }) { q ->
                            Card(onClick = { start(listOf(q.id)) }, enabled = !saving, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("${q.subtopic} · ${LEVELS[q.level]}", color = Purple, style = MaterialTheme.typography.labelMedium)
                                    Text(q.q, style = MaterialTheme.typography.titleMedium)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (q.id in confident) "✓ Reviewed confidently" else if (q.id in ui.inDeck) "↻ Needs revision" else "○ New question", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                        TextButton({ vm.bookmark(q.id) }, enabled = !saving) { Text(if (q.id in ui.bookmarks) "★ Saved" else "☆ Save") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrepHero(title: String, description: String, tracks: Int, questions: Int, streak: Int) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(HeroStart, HeroEnd)))) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Color.White.copy(alpha = 0.06f), size.width * 0.35f, Offset(size.width * 0.95f, size.height * 0.08f))
            drawCircle(Color.White.copy(alpha = 0.07f), 34.dp.toPx(), Offset(size.width * 0.88f, size.height * 0.55f))
            listOf(Offset(size.width * 0.78f, 24.dp.toPx()), Offset(size.width * 0.93f, size.height * 0.68f)).forEach { point ->
                drawLine(Color(0xFFFFD8A8), point - Offset(5.dp.toPx(), 0f), point + Offset(5.dp.toPx(), 0f), 2.dp.toPx())
                drawLine(Color(0xFFFFD8A8), point - Offset(0f, 5.dp.toPx()), point + Offset(0f, 5.dp.toPx()), 2.dp.toPx())
            }
        }
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("THE INTERVIEW PLAYGROUND", color = Color(0xFFE5DAFF), style = MaterialTheme.typography.labelMedium)
            Text(title, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(description, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Text("$tracks tracks  ·  $questions questions  ·  $streak-day streak", color = Color(0xFFE5DAFF), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun TrackBadge(text: String, color: Color) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(text, color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun TrackTile(track: String, total: Int, ready: Int, color: Color, modifier: Modifier, open: () -> Unit) {
    Card(onClick = open, modifier = modifier, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = color)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TrackBadge(trackSymbol(track), MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.75f))
            Text(track, color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text("$total questions", color = Ink, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            QuestProgress(ready, total)
            Text("$ready confident  →", color = Ink, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun trackSymbol(track: String): String = when (track) {
    "Android" -> "A"; "iOS and Swift" -> "Sw"; "JavaScript and TypeScript" -> "JS"; "C# and .NET" -> "C#"; "C and C++" -> "C++"
    "Node.js" -> "N"; "React" -> "Re"; "Python" -> "Py"; "DevOps and Cloud" -> "☁"; "DSA and Coding" -> "{}"
    "AI and Data" -> "AI"; "System Design" -> "SD"; "HR and Behavioral" -> "Hi"; else -> track.take(2)
}

@Composable
private fun MetricTile(value: String, label: String, color: Color, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = color) {
        Column(Modifier.padding(16.dp)) { Text(value, color = Ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(label, color = Ink, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun QuestProgress(done: Int, total: Int) {
    val fraction = if (total == 0) 0f else done.toFloat() / total
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f))
    LinearProgressIndicator(progress = { animated }, color = Purple, trackColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.65f),
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).semantics { stateDescription = "$done of $total" })
}

@Composable
private fun StudyScreen(vm: PrepareViewModel, ui: PrepareUiState, session: StudySession, back: () -> Unit) {
    val saving = ui.saving
    val q = ui.currentQuestion
    key(session.currentId) {
        var checked by rememberSaveable { mutableStateOf(listOf<Int>()) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                TextButton(back) { Text("← Back to track") }
                Text("${session.area} · ${LEVELS[session.level]}", style = MaterialTheme.typography.labelLarge)
            }
            if (session.complete) item {
                Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Lilac), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        TrackBadge("★", Peach)
                        Text(if (session.reviewed.isEmpty()) "Quest finished" else "Nice work. Keep growing!", color = Ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("${session.reviewed.size} of ${session.questionIds.size} questions assessed", color = Ink)
                        QuestProgress(session.index, session.questionIds.size)
                        Text("Skipped questions are still waiting for a review. Your assessed answers are saved for spaced revision.", color = Ink)
                        Button({ vm.practice(session.area) }, modifier = Modifier.fillMaxWidth()) { Text("Review due cards") }
                        OutlinedButton(back, modifier = Modifier.fillMaxWidth()) { Text("Choose your next quest →") }
                    }
                }
            } else if (q == null) item {
                Text("This question is unavailable. Return to the track to choose another quest.")
                OutlinedButton(back) { Text("Choose questions") }
            } else {
                item {
                    Text("QUEST ${session.index + 1} / ${session.questionIds.size}  ·  ${session.reviewed.size} assessed", color = Purple, style = MaterialTheme.typography.labelLarge)
                    QuestProgress(session.index, session.questionIds.size)
                    Spacer(Modifier.height(14.dp))
                    Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Lilac), modifier = Modifier.fillMaxWidth().animateContentSize()) {
                        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            TrackBadge("?", MaterialTheme.colorScheme.surfaceContainerLowest)
                            Text(q.subtopic, color = Purple, style = MaterialTheme.typography.labelLarge)
                            Text(q.q, color = Ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            Text("Think it through. Try an example and a trade-off before you peek.", color = Ink)
                            TextButton({ vm.bookmark(q.id) }, enabled = !saving) { Text(if (q.id in ui.bookmarks) "★ Bookmarked" else "☆ Bookmark") }
                            if (!session.revealed) Button({ vm.reveal(q) }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("Ready? Reveal answer ✦") }
                            AnimatedVisibility(session.revealed) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    HorizontalDivider(color = Purple.copy(alpha = 0.2f))
                                    Text("THE KEY IDEA", color = Purple, style = MaterialTheme.typography.labelLarge)
                                    Text(q.a, color = Ink)
                                }
                            }
                        }
                    }
                }
                if (session.revealed) {
                    if (q.rubric.isNotEmpty()) item {
                        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Mint), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("How many points did you cover?", color = Ink, style = MaterialTheme.typography.titleMedium)
                                Text("${checked.size} / ${q.rubric.size} checked · your self-assessment", color = Ink, style = MaterialTheme.typography.bodySmall)
                                q.rubric.forEachIndexed { i, point ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(i in checked, { value -> checked = if (value) checked + i else checked - i })
                                        Text(point, color = Ink, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                    item { AskActions { vm.askAbout(q, it) } }
                    item { QuestionDetails(q, ui.sources, showShortAnswer = false, showRubric = false) }
                    item {
                        Text("Choose your next step", style = MaterialTheme.typography.titleMedium)
                        Text("Both choices save a revision card. The checklist is a reflection aid; choose your confidence honestly.", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton({ vm.review(q, false) }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("↻ Needs another look") }
                        Button({ vm.review(q, true) }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("✓ Feeling confident · next") }
                    }
                }
                item {
                    TextButton({ vm.skip(q) }, enabled = !saving) { Text("Skip for now") }
                    Text("Your position and revision progress save automatically.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ReadingCard(link: ReadingLink) {
    val uri = LocalUriHandler.current
    var failed by remember(link.source.id) { mutableStateOf(false) }
    ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(link.source.kind, style = MaterialTheme.typography.labelLarge, color = Purple)
            Text(link.source.name, style = MaterialTheme.typography.titleMedium)
            Text(link.source.author, style = MaterialTheme.typography.bodySmall)
            Text(link.note)
            TextButton({ failed = runCatching { uri.openUri(link.source.url) }.isFailure }) { Text("Open resource ↗") }
            if (failed) Text("Could not open the link on this device.", color = MaterialTheme.colorScheme.error)
        }
    }
}
