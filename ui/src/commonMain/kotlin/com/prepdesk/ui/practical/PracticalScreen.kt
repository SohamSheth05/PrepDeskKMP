package com.prepdesk.ui.practical

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prepdesk.domain.*
import com.prepdesk.ui.components.*
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PracticalScreen(vm: PracticalViewModel = koinViewModel()) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val code by vm.code.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableStateOf("Exercises") }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val open = vm.exercise(openId)
    if (open != null) {
        PracticalWorkspace(vm, ui, code, open) { openId = null }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceRow(listOf("Exercises", "Free workbench"), section) { section = it }
        if (section == "Free workbench") Workbench(vm, code, ui.saving) else PracticalList(ui) { openId = it }
    }
}

@Composable
private fun PracticalList(ui: PracticalUiState, open: (String) -> Unit) {
    val all = ui.exercises
    val areas = ui.areas
    var area by rememberSaveable(ui.defaultArea) { mutableStateOf(ui.defaultArea) }
    val shown = all.filter { area == ALL_TRACKS || it.area == area }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Practical exercises", style = MaterialTheme.typography.headlineSmall)
            Text("Write, debug, and predict code as in a technical round. Every exercise works offline with test cases, hints, and a reference solution. Exercises with tests also run on the code service when it is available.")
            if (all.isEmpty()) Text("No practical exercises are available yet.")
            else ChoiceRow(listOf(ALL_TRACKS) + areas, area) { area = it }
        }
        items(shown, key = { it.id }) { q ->
            val p = q.practical!!
            val stage = ui.stages[q.id]
            val status = when { stage == null -> "Not attempted"; stage > 0 -> "Solved"; else -> "Needs more work" }
            ElevatedCard(onClick = { open(q.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${q.area} · ${p.kind} · ${LEVELS[q.level]}", style = MaterialTheme.typography.labelLarge)
                    Text(q.q, style = MaterialTheme.typography.titleMedium)
                    Text("$status · ${if (p.runnable) "${p.tests.size} runnable tests" else "self-checked"}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun PracticalWorkspace(vm: PracticalViewModel, ui: PracticalUiState, run: CodeRunState, q: Q, back: () -> Unit) {
    val p = q.practical!!
    val predict = p.kind == "Predict output"
    val initial = if (predict) "" else p.starter
    var code by rememberSaveable(q.id) { mutableStateOf(initial) }
    var hints by rememberSaveable(q.id) { mutableStateOf(0) }
    var solution by rememberSaveable(q.id) { mutableStateOf(false) }
    var checked by rememberSaveable(q.id) { mutableStateOf(listOf<Int>()) }
    var consent by rememberSaveable { mutableStateOf(false) }
    var saved by rememberSaveable(q.id) { mutableStateOf<Boolean?>(null) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            TextButton(back) { Text("← All exercises") }
            Text("${q.area} · ${q.subtopic} · ${p.kind} · ${p.language} · ${LEVELS[q.level]}", style = MaterialTheme.typography.labelLarge)
            Text(p.task, style = MaterialTheme.typography.titleLarge)
        }
        if (predict && p.starter.isNotBlank()) item { CodeBlock(p.starter) }
        item {
            OutlinedTextField(code, { if (it.length <= 24_000) code = it },
                label = { Text(if (predict) "Your predicted output" else "Your code") }, minLines = 8,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.fillMaxWidth())
            if (!predict) TextButton({ code = initial }) { Text("Reset to starter code") }
        }
        item { PracticalTests(p) }
        if (p.hints.isNotEmpty()) item {
            p.hints.take(hints).forEachIndexed { i, hint -> Text("Hint ${i + 1}: $hint") }
            if (hints < p.hints.size) TextButton({ hints++ }) { Text("Show a hint (${hints + 1} of ${p.hints.size})") }
        }
        if (p.runnable) item { RunTests(vm, run, ui.saving, q, code, consent) { consent = it } }
        item {
            TextButton({ solution = !solution }) { Text(if (solution) "Hide reference solution" else "Show reference solution") }
            if (solution) {
                CodeBlock(p.solution)
                Text(q.explanation)
                if (q.mistake.isNotBlank()) Text("Common mistake: ${q.mistake}", style = MaterialTheme.typography.bodySmall)
            }
        }
        item { Text("Check what your attempt covers", style = MaterialTheme.typography.titleMedium) }
        items(q.rubric.indices.toList()) { i ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(i in checked, { on -> checked = if (on) checked + i else checked - i })
                Text(q.rubric[i], Modifier.weight(1f))
            }
        }
        item {
            Text("Interviewer follow-ups", style = MaterialTheme.typography.titleMedium)
            q.followups.forEachIndexed { i, text -> Text("${i + 1}. $text") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ vm.assess(q, false); saved = false }, enabled = !ui.saving) { Text("Needs more work") }
                Button({ vm.assess(q, true); saved = true }, enabled = !ui.saving) { Text("Solved it") }
            }
            saved?.let { Text(if (it) "Saved. It returns for review on a growing schedule." else "Saved to your revision deck. It is due again now.", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun RunTests(vm: PracticalViewModel, run: CodeRunState, saving: Boolean, q: Q, code: String, consent: Boolean, setConsent: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Run the tests", style = MaterialTheme.typography.titleMedium)
        when {
            !run.signedIn -> Text("Connect to the internet to run tests on the code service. Without it, trace each test case by hand, then compare with the reference solution.")
            !run.available -> Text("The code service is unavailable. Trace each test case by hand, then compare with the reference solution.")
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(consent, setConsent)
                    Text("Send this code to the code execution service.", Modifier.weight(1f))
                }
                Button({ vm.runTests(q, code) }, enabled = consent && code.isNotBlank() && !saving) { Text("Run ${q.practical!!.tests.size} tests") }
                run.result?.takeIf { run.resultFor == q.id }?.let { result ->
                    val report = testReport(result.stdout)
                    Text(report?.let { "Passed ${it.passed} of ${it.total} tests" } ?: result.description, style = MaterialTheme.typography.titleMedium)
                    if (result.status in 1..2) Button(vm::pollCode, enabled = !saving) { Text("Check result") }
                    report?.failed?.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (result.compileOutput.isNotBlank()) Text("Compiler:\n${result.compileOutput}")
                    if (result.stderr.isNotBlank()) Text("Error:\n${result.stderr}")
                }
            }
        }
    }
}

@Composable
private fun Workbench(vm: PracticalViewModel, run: CodeRunState, saving: Boolean) {
    var language by rememberSaveable { mutableStateOf(0) }
    var code by rememberSaveable { mutableStateOf("") }
    var stdin by rememberSaveable { mutableStateOf("") }
    var consent by rememberSaveable { mutableStateOf(false) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Coding practice", style = MaterialTheme.typography.headlineSmall)
            Text("Run a small program with your own input and inspect compiler errors or output. This is a practice workbench; output is not an interview score.")
            if (!run.signedIn) Text("Connect to the internet to use code execution.")
            else if (!run.available) Text("Code execution is unavailable. Check available tools in Account.")
            else {
                OutlinedButton(vm::loadLanguages, enabled = !saving) { Text("Load available languages") }
                val selected = run.languages.firstOrNull { it.id == language }
                AreaSelector(run.languages.map { it.name }, selected?.name ?: "Choose language") { name -> language = run.languages.first { it.name == name }.id }
                OutlinedTextField(code, { if (it.length <= 32_000) code = it }, label = { Text("Source code") }, minLines = 8, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(stdin, { if (it.length <= 8_000) stdin = it }, label = { Text("Standard input") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Row { Checkbox(consent, { consent = it }); Text("Send this program and input to the code execution service.", Modifier.weight(1f)) }
                Button({ vm.runCode(language, code, stdin) }, enabled = consent && selected != null && code.isNotBlank() && !saving) { Text("Run program") }
                run.result?.takeIf { run.resultFor == null }?.let { result ->
                    Text(result.description, style = MaterialTheme.typography.titleMedium)
                    if (result.status in 1..2) Button(vm::pollCode, enabled = !saving) { Text("Check result") }
                    if (result.stdout.isNotBlank()) Text("Output:\n${result.stdout}")
                    if (result.compileOutput.isNotBlank()) Text("Compiler:\n${result.compileOutput}")
                    if (result.stderr.isNotBlank()) Text("Error:\n${result.stderr}")
                }
            }
        }
    }
}
