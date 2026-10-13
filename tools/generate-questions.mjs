// Reviewed JSON packs are the authoring format; Room is the runtime source of truth.
import fs from 'node:fs';
import assert from 'node:assert/strict';
const sources = JSON.parse(fs.readFileSync('content/sources.json', 'utf8'));
const resources = JSON.parse(fs.readFileSync('content/resources.json', 'utf8'));
const packs = ['questions.json', ...fs.readdirSync('content').filter(name => name.endsWith('-questions.json')).sort()];
const contentPacks = packs.map(name => JSON.parse(fs.readFileSync(`content/${name}`, 'utf8')));
const all = contentPacks.flat();
const sourceIds = new Set();
for (const s of sources) {
    for (const key of ['id', 'name', 'author', 'url', 'license', 'kind']) assert.equal(typeof s[key], 'string', `Source ${key}`);
    assert(!sourceIds.has(s.id), `Duplicate source ${s.id}`);
    assert(new URL(s.url).protocol === 'https:', `Unsafe URL ${s.url}`);
    sourceIds.add(s.id);
}
const ids = new Set();
for (const q of all) {
    for (const key of ['id', 'area', 'q', 'a']) assert(typeof q[key] === 'string' && q[key].trim(), `Missing ${key}`);
    assert(!ids.has(q.id), `Duplicate question ID ${q.id}`); ids.add(q.id);
    assert(Number.isInteger(q.level) && q.level >= 1 && q.level <= 4, `Invalid level ${q.id}`);
    assert(Number.isInteger(q.version) && q.version > 0, `Invalid version ${q.id}`);
    for (const id of q.sourceIds ?? []) assert(sourceIds.has(id), `Unknown source ${id}`);
    for (const key of ['rubric', 'followups', 'sourceIds']) assert(Array.isArray(q[key] ?? []) && (q[key] ?? []).every(x => typeof x === 'string' && x.trim()), `Invalid ${key}`);
    if (!q.id.startsWith('general-')) {
        assert(q.rubric.length >= 3 && q.followups.length >= 2 && q.explanation && q.mistake && q.sourceIds.length >= 1 && q.subtopic && q.round, `Incomplete interview question ${q.id}`);
        assert(/^\d{4}-\d{2}-\d{2}$/.test(q.reviewedAt), `Missing review date ${q.id}`);
    }
    if (q.practical) validatePractical(q);
}
// Keep in sync with validPractical in Content.kt. Run tools/verify-practicals.mjs to execute solutions.
function validatePractical(q) {
    const p = q.practical;
    assert.equal(q.round, 'Practical', `Practical ${q.id} must use the Practical round`);
    assert(['Write code', 'Debug', 'Predict output', 'Hands-on task', 'Case exercise'].includes(p.kind), `Unknown practical kind ${q.id}`);
    for (const key of ['language', 'task', 'solution']) assert(typeof p[key] === 'string' && p[key].trim(), `Practical ${q.id} needs ${key}`);
    for (const key of ['starter', 'harness']) assert(p[key] === undefined || typeof p[key] === 'string', `Invalid ${key} ${q.id}`);
    assert(Array.isArray(p.hints ?? []) && (p.hints ?? []).every(h => typeof h === 'string' && h.trim()), `Invalid hints ${q.id}`);
    assert(Array.isArray(p.tests ?? []) && (p.tests ?? []).every(t => typeof t.input === 'string' && t.input.trim() && typeof t.expected === 'string' && t.expected.trim()), `Invalid tests ${q.id}`);
    if (p.harness?.trim()) assert((p.tests ?? []).length >= 2, `Runnable practical ${q.id} needs at least two tests`);
    if (p.kind === 'Write code' || p.kind === 'Debug') assert(p.starter?.trim(), `Practical ${q.id} needs starter code`);
}
const str = x => JSON.stringify(x).replace(/\$/g, '\\$');
const list = xs => `listOf(${xs.map(str).join(', ')})`;
const areas = new Set(all.map(q => q.area));
for (const area of areas) {
    const bank = all.filter(q => q.area === area);
    assert(bank.length >= 40, `${area} needs at least 40 questions`);
    assert.equal(new Set(bank.map(q => q.q.trim().toLowerCase())).size, bank.length, `Repeated prompt in ${area}`);
    for (const level of [1, 2, 3, 4]) assert(bank.filter(q => q.level === level && q.explanation).length >= 10, `${area} level ${level} needs 10 explained exercises`);
}
// Bound generated method size even when an authoring pack grows substantially.
const codeChunks = contentPacks.flatMap(pack => Array.from({ length: Math.ceil(pack.length / 20) }, (_, i) => pack.slice(i * 20, (i + 1) * 20)));
const resourceIds = new Set();
for (const r of resources) {
    assert(sourceIds.has(r.sourceId), `Unknown reading source ${r.sourceId}`);
    assert(!resourceIds.has(r.sourceId), `Duplicate reading source ${r.sourceId}`); resourceIds.add(r.sourceId);
    assert(Array.isArray(r.areas) && r.areas.length && r.areas.every(a => areas.has(a)), `Unknown reading track ${r.sourceId}`);
    assert(Array.isArray(r.levels) && r.levels.length && r.levels.every(l => Number.isInteger(l) && l >= 1 && l <= 4), `Invalid reading levels ${r.sourceId}`);
    assert(typeof r.note === 'string' && r.note.trim(), `Missing reading note ${r.sourceId}`);
}
const practicalCode = p => `Practical(kind = ${str(p.kind)}, language = ${str(p.language)}, task = ${str(p.task)}, starter = ${str(p.starter ?? '')}, solution = ${str(p.solution)}, hints = ${list(p.hints ?? [])}, tests = listOf(${(p.tests ?? []).map(t => `PracticalTest(${str(t.input)}, ${str(t.expected)})`).join(', ')}), harness = ${str(p.harness ?? '')})`;
const questionCode = q => `    Q(id = ${str(q.id)}, area = ${str(q.area)}, level = ${q.level}, q = ${str(q.q)}, a = ${str(q.a)}, subtopic = ${str(q.subtopic ?? 'General')}, round = ${str(q.round ?? 'Fundamentals')}, explanation = ${str(q.explanation ?? '')}, code = ${str(q.code ?? '')}, mistake = ${str(q.mistake ?? '')}, rubric = ${list(q.rubric ?? [])}, followups = ${list(q.followups ?? [])}, sourceIds = ${list(q.sourceIds ?? [])}, version = ${q.version}, reviewedAt = ${str(q.reviewedAt ?? '')}, origin = ${str(q.origin ?? 'PrepDesk original')}${q.practical ? `, practical = ${practicalCode(q.practical)}` : ''})`;
const output = `package com.prepdesk.data

import com.prepdesk.domain.*

// GENERATED by tools/generate-questions.mjs. Edit reviewed JSON packs instead.
val SOURCES = listOf(
${sources.map(s => `    Source(${['id', 'name', 'author', 'url', 'license', 'kind'].map(k => str(s[k])).join(', ')})`).join(',\n')}
)
val STUDY_RESOURCES = listOf(
${resources.map(r => `    StudyResource(${str(r.sourceId)}, ${list(r.areas)}, listOf(${r.levels.join(', ')}), ${str(r.note)})`).join(',\n')}
)
// Separate pack functions keep the JVM initializer below its method-size limit.
val QUESTIONS = listOf(${codeChunks.map((_, i) => `questionPack${i}()`).join(', ')}).flatten()
${codeChunks.map((questions, i) => `private fun questionPack${i}(): List<Q> = listOf(\n${questions.map(questionCode).join(',\n')}\n)`).join('\n')}
`;
const path = 'data/src/commonMain/kotlin/com/prepdesk/data/Questions.kt';
const coverage = JSON.stringify({
    questionCount: all.length, sourceCount: sources.length, readingResourceCount: resources.length, packs,
    tracks: [...new Set(all.map(q => q.area))].sort().map(area => {
        const questions = all.filter(q => q.area === area);
        const enriched = questions.filter(q => q.explanation && q.rubric?.length >= 3 && q.followups?.length >= 2 && q.sourceIds?.length);
        return { area, total: questions.length, enriched: enriched.length, practical: questions.filter(q => q.practical).length,
            levels: [1, 2, 3, 4].map(level => ({ level, total: questions.filter(q => q.level === level).length, enriched: enriched.filter(q => q.level === level).length })),
            sourceIds: [...new Set(enriched.flatMap(q => q.sourceIds))].sort() };
    }),
}, null, 2) + '\n';
const coveragePath = 'content/coverage.json';
if (process.argv.includes('--check')) {
    assert.equal(fs.readFileSync(path, 'utf8'), output, 'Generated questions are stale. Run node tools/generate-questions.mjs');
    assert.equal(fs.readFileSync(coveragePath, 'utf8'), coverage, 'Coverage report is stale. Run node tools/generate-questions.mjs');
    console.log(`Validated ${all.length} questions and ${sources.length} sources; generated file matches.`);
} else { fs.writeFileSync(path, output); fs.writeFileSync(coveragePath, coverage); console.log(`Generated ${all.length} questions and ${sources.length} sources.`); }
