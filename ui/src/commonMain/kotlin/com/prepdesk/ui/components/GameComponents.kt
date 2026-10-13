package com.prepdesk.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prepdesk.domain.*
import kotlin.random.Random

@Composable
fun PlayerCard(player: PlayerSummary, open: (() -> Unit)? = null) {
    val level = player.level
    val progress by animateFloatAsState(level.progress, tween(900, easing = FastOutSlowInEasing))
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(HeroStart, HeroEnd)))
        .then(if (open != null) Modifier.clickable(onClick = open) else Modifier)) {
        Sparkles(Modifier.matchParentSize())
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LevelOrb(level.level)
                Column(Modifier.weight(1f)) {
                    Text("LEVEL ${level.level}", color = Gold, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(level.title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${level.needed - level.into} XP to level ${level.level + 1}", color = Color(0xFFE5DAFF), style = MaterialTheme.typography.bodySmall)
                }
            }
            XpBar(progress)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip("🔥", "${player.streak}", "streak", Modifier.weight(1f))
                StatChip("🏅", "${player.badgesEarned}/${player.badgesTotal}", "badges", Modifier.weight(1f))
                StatChip("⚡", "${player.bestRush}", "best rush", Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun LevelOrb(level: Int) {
    val pulse = rememberInfiniteTransition()
    val scale by pulse.animateFloat(1f, 1.07f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse))
    Box(Modifier.size(68.dp).scale(scale).clip(CircleShape).background(Brush.radialGradient(listOf(Gold, Flame)))
        .border(3.dp, Color.White.copy(alpha = 0.6f), CircleShape), contentAlignment = Alignment.Center) {
        Text("$level", color = Night, fontSize = 28.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun XpBar(progress: Float) {
    Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(Color.White.copy(alpha = 0.18f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0.02f, 1f)).clip(RoundedCornerShape(7.dp))
            .background(Brush.horizontalGradient(listOf(Gold, Flame))))
    }
}

@Composable
private fun StatChip(icon: String, value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.12f)).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$icon $value", color = Color.White, fontWeight = FontWeight.Bold)
        Text(label, color = Color(0xFFE5DAFF), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Sparkles(modifier: Modifier) {
    val twinkle = rememberInfiniteTransition()
    val alpha by twinkle.animateFloat(0.25f, 0.9f, infiniteRepeatable(tween(1600), RepeatMode.Reverse))
    Canvas(modifier) {
        drawCircle(Color.White.copy(alpha = 0.06f), size.width * 0.32f, Offset(size.width * 0.98f, 0f))
        listOf(Offset(size.width * 0.82f, size.height * 0.18f), Offset(size.width * 0.93f, size.height * 0.5f), Offset(size.width * 0.7f, size.height * 0.08f)).forEachIndexed { i, p ->
            val a = if (i % 2 == 0) alpha else 1.15f - alpha
            val r = (4 + i).dp.toPx()
            drawLine(Gold.copy(alpha = a), p - Offset(r, 0f), p + Offset(r, 0f), 2.dp.toPx())
            drawLine(Gold.copy(alpha = a), p - Offset(0f, r), p + Offset(0f, r), 2.dp.toPx())
        }
    }
}

@Composable
fun ChallengesCard(challenges: List<Challenge>, go: (Challenge) -> Unit) {
    val done = challenges.count { it.done }
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Mint), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Daily challenges", color = Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("$done/${challenges.size}", color = Ink, fontWeight = FontWeight.Bold)
            }
            challenges.forEach { c -> ChallengeRow(c) { go(c) } }
            AnimatedVisibility(done == challenges.size, enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn()) {
                Text("🎉 Daily sweep! +$SWEEP_XP XP. Come back tomorrow for new challenges.", color = Ink, fontWeight = FontWeight.Bold)
            }
            if (done < challenges.size) Text("Complete all three for a +$SWEEP_XP XP bonus.", color = Ink, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ChallengeRow(c: Challenge, go: () -> Unit) {
    val progress by animateFloatAsState(c.progress.toFloat() / c.goal, tween(700))
    val check by animateFloatAsState(if (c.done) 1f else 0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.7f)).clickable(enabled = !c.done, onClick = go).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(if (c.done) Good else Lilac), contentAlignment = Alignment.Center) {
            if (c.done) Text("✓", color = Color.White, fontWeight = FontWeight.Black, modifier = Modifier.scale(check)) else Text(c.icon)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(c.title, color = Ink, fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = if (c.done) Good else Purple, trackColor = Lilac)
        }
        Text(if (c.done) "Done" else if (c.goal > 1) "${c.progress}/${c.goal}" else "Go →", color = Ink, style = MaterialTheme.typography.labelLarge)
    }
}

private class Particle(val x: Float, val delay: Float, val speed: Float, val drift: Float, val spin: Float, val color: Color, val w: Float)

@Composable
fun Confetti(modifier: Modifier, pieces: Int = 90) {
    val particles = remember { List(pieces) { Particle(Random.nextFloat(), Random.nextFloat() * 0.3f, 0.6f + Random.nextFloat() * 0.6f,
        Random.nextFloat() * 2 - 1, Random.nextFloat() * 720 - 360, ConfettiColors.random(), 6f + Random.nextFloat() * 6f) } }
    val time = remember { Animatable(0f) }
    LaunchedEffect(Unit) { time.animateTo(1f, tween(2600, easing = LinearEasing)) }
    Canvas(modifier) {
        val t = time.value
        if (t >= 1f) return@Canvas
        particles.forEach { p ->
            val local = ((t - p.delay) / (1 - p.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val y = -20f + local * local * size.height * 1.2f * p.speed + local * size.height * 0.2f
            val x = p.x * size.width + p.drift * 60.dp.toPx() * local
            rotate(p.spin * local, Offset(x, y)) {
                drawRect(p.color.copy(alpha = 1f - local * 0.6f), Offset(x, y), Size(p.w.dp.toPx() * 0.6f, p.w.dp.toPx()))
            }
        }
    }
}

/** What a celebration announces; produced by AppViewModel. */
data class Celebration(val welcome: Boolean, val levelUp: Boolean, val level: PlayerLevel, val badges: List<Badge>)

@Composable
fun CelebrationDialog(c: Celebration, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, confirmButton = { Button(dismiss) { Text("Let's go!") } },
        title = { Text(when { c.welcome -> "Welcome, ${c.level.title}!"; c.levelUp -> "Level up! 🎉"; else -> "Badge unlocked! 🏅" }, fontWeight = FontWeight.Black) },
        text = { Box {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (c.levelUp) {
                    LevelOrb(c.level.level)
                    Text(if (c.welcome) "Earn XP by studying, finishing mocks, focus sessions, and games. Daily challenges give a bonus."
                        else "You reached level ${c.level.level} · ${c.level.title}", textAlign = TextAlign.Center)
                }
                c.badges.forEach { b ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(b.icon, fontSize = 28.sp)
                        Column { Text(b.title, fontWeight = FontWeight.Bold); Text(b.description, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            // Drawn inside the dialog so the scrim does not hide it.
            Confetti(Modifier.matchParentSize(), pieces = 50)
        } })
}
