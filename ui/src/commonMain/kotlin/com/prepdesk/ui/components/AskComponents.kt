package com.prepdesk.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.prepdesk.domain.TutorAction

/** One-tap requests about a question; each opens Ask with the answer. */
@Composable
fun AskActions(modifier: Modifier = Modifier, onAsk: (TutorAction) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Ask MockMentor", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TutorAction.entries.forEach { action ->
                AssistChip(onClick = { onAsk(action) }, label = { Text(action.label) },
                    leadingIcon = { Icon(PrepIcons.Ask, null, Modifier.size(16.dp)) })
            }
        }
    }
}

/**
 * Renders the small Markdown subset answers use: paragraphs, "- " bullets, numbered lines, **bold**,
 * `inline code`, and fenced code blocks. Anything else shows as plain text.
 */
@Composable
fun RichText(text: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        text.split("```").forEachIndexed { i, part ->
            if (i % 2 == 1) {
                // Drop a language tag such as "kotlin" on the fence's first line.
                val lines = part.trimEnd().lines()
                val code = if (lines.firstOrNull()?.trim()?.matches(Regex("[A-Za-z+#-]{1,20}")) == true) lines.drop(1) else lines
                CodeBlock(code.joinToString("\n").trim('\n'))
            } else part.trim().split(Regex("\n\\s*\n")).filter { it.isNotBlank() }.forEach { block ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.lines().filter { it.isNotBlank() }.forEach { line -> MarkdownLine(line.trim()) }
                }
            }
        }
    }
}

@Composable
private fun MarkdownLine(line: String) {
    val bullet = line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ")
    val heading = line.startsWith("#")
    val body = when { bullet -> line.drop(2); heading -> line.trimStart('#').trim(); else -> line }
    val styled = inline(body)
    if (bullet) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("•"); Text(styled) }
    else Text(styled, style = if (heading) MaterialTheme.typography.titleSmall else LocalTextStyle.current)
}

private fun inline(text: String) = buildAnnotatedString {
    Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`").let { pattern ->
        var last = 0
        pattern.findAll(text).forEach { m ->
            append(text.substring(last, m.range.first))
            m.groups[1]?.let { withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(it.value) } }
            m.groups[2]?.let { withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(it.value) } }
            last = m.range.last + 1
        }
        append(text.substring(last))
    }
}
