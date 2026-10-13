package com.prepdesk.ui.play

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prepdesk.domain.*
import com.prepdesk.ui.components.*
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

@Composable
fun PlayScreen(vm: PlayViewModel = koinViewModel()) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val game by vm.game.collectAsStateWithLifecycle()
    game?.let { g ->
        when {
            g.questions.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Not enough questions in ${g.area} for a game yet.")
                Button(vm::quit) { Text("Back") }
            }
            g.finished -> RushResults(g, again = vm::playAgain, addMissed = vm::addMissed, exit = vm::quit)
            else -> RushBoard(g, vm::answer, vm::quit)
        }
        return
    }
    var choice by rememberSaveable(ui.defaultArea) { mutableStateOf(ui.defaultArea) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PlayerCard(ui.player) }
        item { ChallengesCard(ui.challenges, vm::go) }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Peach), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("⚡ ANSWER RUSH", color = Flame, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                    Text("$RUSH_SECONDS seconds. Four choices. Chain correct answers for up to ×4 points; a miss costs ${RUSH_PENALTY_MS / 1000} seconds.", color = Ink)
                    ChoiceRow(listOf(ALL_TRACKS) + ui.areas, choice) { choice = it }
                    Text("Personal best: ${ui.bests[choice] ?: 0}", color = Ink, fontWeight = FontWeight.Bold)
                    Button({ vm.start(choice) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Flame)) {
                        Text("Play", fontSize = 20.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        val bests = ui.bests.entries.sortedByDescending { it.value }.take(5)
        if (bests.isNotEmpty()) item {
            Text("Your top scores", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            bests.forEachIndexed { i, (track, score) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(listOf("🥇", "🥈", "🥉", "4.", "5.")[i], Modifier.width(36.dp))
                    Text(track, Modifier.weight(1f))
                    Text("$score", fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Text("Badges", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${ui.badges.count { it.second }} of ${ui.badges.size} unlocked", style = MaterialTheme.typography.bodySmall)
        }
        items(ui.badges.chunked(3)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                row.forEach { (badge, earned) -> BadgeTile(badge, earned, Modifier.weight(1f).fillMaxHeight()) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BadgeTile(badge: Badge, earned: Boolean, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(if (earned) Lilac else MaterialTheme.colorScheme.surfaceVariant).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (earned) badge.icon else "🔒", fontSize = 28.sp)
        Text(badge.title, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge,
            color = if (earned) Ink else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(badge.description, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall,
            color = if (earned) Ink else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RushBoard(g: RushGame, answer: (Int) -> Boolean, exit: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val q = g.current
    val left = g.secondsLeft
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(exit) { Text("✕ Quit") }
            Spacer(Modifier.weight(1f))
            AnimatedContent(g.score.score, transitionSpec = { slideInVertically { -it } + fadeIn() togetherWith slideOutVertically { it } + fadeOut() }) { value ->
                Text("$value", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Purple)
            }
            Text(" pts", color = Purple)
        }
        val timeColor by animateColorAsState(if (left < 10) Bad else Purple)
        LinearProgressIndicator(progress = { (left / RUSH_SECONDS).toFloat() }, color = timeColor, trackColor = Lilac,
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⏱ ${left.roundToInt()}s", color = timeColor, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            ComboBadge(g.score)
        }
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Lilac), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${q.q.area} · ${q.q.subtopic}", color = Ink, style = MaterialTheme.typography.labelLarge)
                Text(q.q.q, color = Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        q.options.forEachIndexed { i, option ->
            val state = when {
                g.picked == null -> 0
                i == q.correct -> 1
                i == g.picked -> 2
                else -> 3
            }
            val bg by animateColorAsState(when (state) { 1 -> Good; 2 -> Bad; else -> MaterialTheme.colorScheme.surfaceVariant })
            val fg = if (state == 1 || state == 2) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            val bump by animateFloatAsState(if (state == 1) 1.03f else 1f, spring(Spring.DampingRatioMediumBouncy))
            Row(Modifier.fillMaxWidth().scale(bump).clip(RoundedCornerShape(16.dp)).background(bg)
                .clickable(enabled = g.picked == null) {
                    haptics.performHapticFeedback(if (answer(i)) HapticFeedbackType.TextHandleMove else HapticFeedbackType.LongPress)
                }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${'A' + i}", fontWeight = FontWeight.Black, color = fg, modifier = Modifier.width(28.dp))
                Text(option, color = fg, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ComboBadge(score: RushScore) {
    AnimatedVisibility(score.multiplier > 1, enter = scaleIn(spring(Spring.DampingRatioHighBouncy)) + fadeIn(), exit = fadeOut()) {
        val pulse = rememberInfiniteTransition()
        val scale by pulse.animateFloat(1f, 1.12f, infiniteRepeatable(tween(400), RepeatMode.Reverse))
        Text("🔥 ${score.combo} streak · ×${score.multiplier}", color = Color.White, fontWeight = FontWeight.Black,
            modifier = Modifier.scale(scale).clip(RoundedCornerShape(12.dp)).background(Flame).padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
private fun RushResults(g: RushGame, again: () -> Unit, addMissed: () -> Unit, exit: () -> Unit) {
    val score = g.score
    val accuracy = g.accuracy
    val shown = remember(g.questions) { Animatable(0f) }
    LaunchedEffect(g.questions) { shown.animateTo(score.score.toFloat(), tween(1200, easing = FastOutSlowInEasing)) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text(when { accuracy >= 90 -> "🏆"; accuracy >= 70 -> "🌟"; accuracy >= 40 -> "💪"; else -> "🌱" }, fontSize = 64.sp)
                Text(when { accuracy >= 90 -> "Outstanding!"; accuracy >= 70 -> "Great run!"; accuracy >= 40 -> "Nice effort!"; else -> "Every run teaches you something." },
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
            item {
                Text("${shown.value.roundToInt()}", fontSize = 64.sp, fontWeight = FontWeight.Black, color = Purple)
                Text("points · ${g.area}", textAlign = TextAlign.Center)
            }
            if (g.newBest) item {
                Text("NEW PERSONAL BEST!", color = Night, fontWeight = FontWeight.Black,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Gold).padding(horizontal = 14.dp, vertical = 6.dp))
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ResultTile("${score.correct}/${score.answered}", "correct", Mint, Modifier.weight(1f))
                    ResultTile("$accuracy%", "accuracy", Sky, Modifier.weight(1f))
                    ResultTile("${score.bestCombo}", "best streak", Peach, Modifier.weight(1f))
                }
            }
            item { Text("+${score.score / 10 + 10} XP", color = Flame, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge) }
            item {
                Button(again, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Flame)) { Text("Play again", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(8.dp))
                val missed = score.missed.distinct().size
                if (missed > 0) OutlinedButton(addMissed, enabled = !g.missedAdded, modifier = Modifier.fillMaxWidth()) {
                    Text(if (g.missedAdded) "Added to your revision deck ✓" else "Revise the $missed I missed")
                }
                TextButton(exit, modifier = Modifier.fillMaxWidth()) { Text("Back to Play") }
            }
        }
        if (g.newBest) Confetti(Modifier.matchParentSize())
    }
}

@Composable
private fun ResultTile(value: String, label: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(color).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Ink, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
        Text(label, color = Ink, style = MaterialTheme.typography.labelSmall)
    }
}
