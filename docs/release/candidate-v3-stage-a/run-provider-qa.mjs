// Adds QA to a generated, task-owned copy of the existing full real product gate.
// Does not change the application, its original gate or the historical v2 report.
import assert from 'node:assert/strict';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { execFileSync, spawn } from 'node:child_process';
import { once } from 'node:events';
import { fileURLToPath, pathToFileURL } from 'node:url';
import path from 'node:path';

const root = fileURLToPath(new URL('../../../', import.meta.url));
const git = (...args) => execFileSync('git', args, { cwd: root, encoding: 'utf8' }).trim();
const testedSHA = git('rev-parse', 'HEAD');
git('diff', '--exit-code', 'HEAD', '--', 'frontend/src', 'backend', 'scripts', 'docker', 'database');
for (const source of ['0f3729f27a015457b3a3aee98deea6ea1a9244e4', '380acac342cc162f11e39c2a33651d2b916802ab']) {
  git('merge-base', '--is-ancestor', source, 'HEAD');
}
const temporary = path.join(root, 'tmp'); mkdirSync(temporary, { recursive: true });
const stamp = Date.now().toString();
let source = readFileSync(path.join(root, 'frontend/tests/product-completion-browser.integration.mjs'), 'utf8');
const replace = (before, after) => { assert.equal(source.split(before).length, 2, 'Expected exactly one runner hook: ' + before); source = source.replace(before, after); };
replace("const frontendDir = path.resolve(fileURLToPath(new URL('..', import.meta.url)));", `const frontendDir = ${JSON.stringify(path.join(root, 'frontend'))};`);
replace('createRequire(import.meta.url)', "createRequire(new URL('../frontend/package.json', import.meta.url))");
replace("await import('./candidate-v2-journeys.mjs')", `await import(${JSON.stringify(pathToFileURL(path.join(root, 'frontend/tests/candidate-v2-journeys.mjs')).href)})`);
replace("const outDir = path.join(frontendDir, 'test-artifacts', smoke ? 'release-smoke' : 'product-completion-browser');", `const outDir = path.join(frontendDir, 'test-artifacts', 'v3-provider-qa-${stamp}');`);
const hook = `
  writeFileSync(path.join(outDir, 'baseline-results.json'), JSON.stringify({ testedSHA: '${testedSHA}', passed: checks.length, checks }, null, 2));
  const { providerJourneys } = await import(${JSON.stringify(new URL('./provider-journeys.mjs', import.meta.url).href)});
  await providerJourneys({ origin, browser, cookies, outDir, testedSHA: '${testedSHA}' });
`;
replace("  writeFileSync(path.join(outDir, 'results.json')", hook + "  writeFileSync(path.join(outDir, 'results.json')");
const script = path.join(temporary, `v3-provider-runner-${stamp}.mjs`); writeFileSync(script, source);
console.log(`Candidate-v3 ${testedSHA}; frontend/test-artifacts/v3-provider-qa-${stamp}`);
const child = spawn(process.execPath, [script, '--candidate-v2'], {
  cwd: root, stdio: 'inherit', windowsHide: true,
  env: { ...process.env, FINSIGHT_BROWSER_TMP_DIR: path.join(temporary, `v3-provider-runtime-${stamp}`) },
});
const [code] = await once(child, 'exit'); process.exitCode = code ?? 1;
