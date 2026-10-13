package com.prepdesk.domain

import kotlinx.serialization.Serializable

@Serializable
data class Source(val id: String, val name: String, val author: String, val url: String, val license: String, val kind: String)

data class StudyResource(val sourceId: String, val areas: List<String>, val levels: List<Int>, val note: String)

data class ReadingLink(val source: Source, val note: String)

fun readingLinks(questions: List<Q>, sources: List<Source>, area: String, level: Int, resources: List<StudyResource>): List<ReadingLink> {
    val curated = resources.filter { area in it.areas && (level == 0 || level in it.levels) }
    val references = questions.filter { it.area == area && (level == 0 || it.level == level) }.flatMap { it.sourceIds }.distinct()
    val byId = sources.associateBy { it.id }
    return (curated.mapNotNull { resource -> byId[resource.sourceId]?.let { ReadingLink(it, resource.note) } } +
        references.mapNotNull { id -> byId[id]?.let { ReadingLink(it, "Reference for the topics practised in this track.") } })
        .distinctBy { it.source.id }
}

@Serializable
data class Q(
    val id: String, val area: String, val level: Int, val q: String, val a: String,
    val subtopic: String = "General", val round: String = "Fundamentals",
    val explanation: String = "", val code: String = "", val mistake: String = "",
    val rubric: List<String> = emptyList(), val followups: List<String> = emptyList(),
    val sourceIds: List<String> = emptyList(), val version: Int = 1,
    val reviewedAt: String = "", val origin: String = "PrepDesk original",
    val practical: Practical? = null,
)

/** One displayed test case. Runnable exercises check the same cases in [Practical.harness]. */
@Serializable
data class PracticalTest(val input: String, val expected: String)

/**
 * A hands-on exercise: write, debug, or predict code. A non-blank harness is appended to the
 * learner's code and prints one `PASS`/`FAIL` line per test, so a single submission checks every case.
 */
@Serializable
data class Practical(
    val kind: String, val language: String, val task: String, val starter: String = "", val solution: String,
    val hints: List<String> = emptyList(), val tests: List<PracticalTest> = emptyList(), val harness: String = "",
) {
    val runnable: Boolean get() = harness.isNotBlank()
    fun program(code: String) = "$code\n\n$harness"
}

data class TestReport(val passed: Int, val failed: List<String>) { val total get() = passed + failed.size }

fun testReport(stdout: String): TestReport? {
    val lines = stdout.lines().map { it.trim() }
    val failed = lines.filter { it.startsWith("FAIL") }
    val passed = lines.count { it.startsWith("PASS") }
    return if (passed + failed.size == 0) null else TestReport(passed, failed)
}

data class QuestionFilter(
    val area: String = "All areas", val level: Int = 0, val round: String = "All rounds",
    val search: String = "", val bookmarkedOnly: Boolean = false, val subtopic: String = "All topics",
)

fun questionAreas(questions: List<Q>): List<String> = questions.map { it.area }.distinct().sorted()

fun selectMockCards(questions: List<Q>, cards: List<Card>, area: String, level: Int): List<Card> {
    if (area == "My deck") return cards.shuffled().take(5)
    // Mock rounds are spoken and timed; hands-on exercises are practised in the Practical tab.
    val matched = questions.filter { it.practical == null && (area == "All areas" || it.area == area) && (level == 0 || it.level == level) }
    val (enriched, basic) = matched.partition { it.rubric.isNotEmpty() && it.explanation.isNotBlank() }
    val pool = enriched.shuffled() + if (enriched.size >= 5) emptyList() else basic.shuffled()
    val balanced = pool.distinctBy { it.round }.take(5)
    return (balanced + pool.filter { q -> balanced.none { it.id == q.id } }).take(5)
        .mapIndexed { i, q -> Card(nowMs() + i, q.area, q.q, q.a, questionId = q.id) }
}

fun filterQuestions(questions: List<Q>, filter: QuestionFilter, bookmarks: Set<String>): List<Q> {
    val terms = filter.search.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    return questions.filter { q ->
        (filter.area == "All areas" || q.area == filter.area) &&
            (filter.level == 0 || q.level == filter.level) &&
            (filter.subtopic == "All topics" || q.subtopic == filter.subtopic) &&
            (filter.round == "All rounds" || q.round == filter.round) &&
            (!filter.bookmarkedOnly || q.id in bookmarks) &&
            terms.all { it in "${q.q} ${q.area} ${q.subtopic} ${q.a} ${q.explanation} ${q.mistake} ${q.rubric.joinToString(" ")} ${q.followups.joinToString(" ")}".lowercase() }
    }
}

val PRACTICAL_KINDS = listOf("Write code", "Debug", "Predict output", "Hands-on task", "Case exercise")

// Mirrors the authoring checks in tools/generate-questions.mjs for remotely published packs.
fun validPractical(p: Practical): Boolean =
    p.kind in PRACTICAL_KINDS && p.language.isNotBlank() && p.task.isNotBlank() && p.solution.isNotBlank() &&
        (p.task.length + p.starter.length + p.solution.length + p.harness.length) <= 40_000 &&
        p.tests.all { it.input.isNotBlank() && it.expected.isNotBlank() } && (!p.runnable || p.tests.size >= 2)
