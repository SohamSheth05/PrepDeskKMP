// Executes every runnable practical exercise: the reference solution must pass all tests,
// and the starter code must compile with the harness without already passing them.
// Requires kotlinc on PATH (or KOTLINC=/path/to/kotlinc) and java. Usage: node tools/verify-practicals.mjs [question-id ...]
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

const kotlinc = process.env.KOTLINC ?? 'kotlinc';
const only = new Set(process.argv.slice(2));
const packs = fs.readdirSync('content').filter(name => name.endsWith('-questions.json'));
const exercises = packs.flatMap(name => JSON.parse(fs.readFileSync(`content/${name}`, 'utf8')))
    .filter(q => q.practical?.harness?.trim() && (only.size === 0 || only.has(q.id)));

function run(source) {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'prepdesk-practical-'));
    try {
        fs.writeFileSync(path.join(dir, 'Main.kt'), source);
        const jar = path.join(dir, 'main.jar');
        execFileSync(kotlinc, [path.join(dir, 'Main.kt'), '-include-runtime', '-d', jar], { stdio: 'pipe' });
        return execFileSync('java', ['-jar', jar], { encoding: 'utf8', timeout: 10_000 });
    } finally { fs.rmSync(dir, { recursive: true, force: true }); }
}

const lines = output => output.split('\n').map(l => l.trim()).filter(l => /^(PASS|FAIL)/.test(l));
let failures = 0;
for (const q of exercises) {
    const p = q.practical;
    if (p.language !== 'Kotlin') { console.log(`SKIP ${q.id}: no local runner for ${p.language}`); continue; }
    try {
        const solved = lines(run(`${p.solution}\n\n${p.harness}`));
        if (solved.length !== p.tests.length || solved.some(l => !l.startsWith('PASS'))) throw new Error(`solution: ${solved.join(' | ')}`);
        const started = lines(run(`${p.starter}\n\n${p.harness}`));
        if (started.length !== p.tests.length) throw new Error(`starter reported ${started.length} of ${p.tests.length} tests`);
        if (started.every(l => l.startsWith('PASS'))) throw new Error('starter already passes every test');
        console.log(`OK   ${q.id} (${p.tests.length} tests)`);
    } catch (e) { failures++; console.error(`FAIL ${q.id}: ${e.stderr?.toString() || e.message}`); }
}
console.log(`${exercises.length - failures}/${exercises.length} runnable exercises verified.`);
if (failures) process.exit(1);
