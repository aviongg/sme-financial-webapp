// Full real product gate, unchanged original two DEF-08 assertions, then extra
// read-only capability evidence. A generated task-owned runner edits no source.
import assert from 'node:assert/strict';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { execFileSync, spawn } from 'node:child_process';
import { once } from 'node:events';
import { fileURLToPath, pathToFileURL } from 'node:url';
import path from 'node:path';

const root = fileURLToPath(new URL('../../../', import.meta.url));
const git = (...args) => execFileSync('git', args, { cwd: root, encoding: 'utf8' }).trim();
const testedSHA = git('rev-parse', 'HEAD');
git('diff', '--exit-code', 'HEAD', '--', 'frontend', 'backend', 'scripts', 'docker', 'database');
for (const source of ['ce3d1b06436c478b71f792b700b8e42467ad5e84', '7ecf6097779cbb64963baf3f50f6bb724f0dd140']) git('merge-base', '--is-ancestor', source, 'HEAD');
git('diff', '--exit-code', 'ce3d1b06436c478b71f792b700b8e42467ad5e84', '--',
  'docs/release/candidate-v3-stage-a/provider-journeys.mjs', 'docs/release/candidate-v3-stage-a/run-provider-qa.mjs');
const temporary = path.join(root, 'tmp'); mkdirSync(temporary, { recursive: true });
const stamp = Date.now().toString();
let source = readFileSync(path.join(root, 'frontend/tests/product-completion-browser.integration.mjs'), 'utf8');
const replace = (before, after) => { assert.equal(source.split(before).length, 2, 'Expected exactly one runner hook: ' + before); source = source.replace(before, after); };
replace("const frontendDir = path.resolve(fileURLToPath(new URL('..', import.meta.url)));", `const frontendDir = ${JSON.stringify(path.join(root, 'frontend'))};`);
replace('createRequire(import.meta.url)', "createRequire(new URL('../frontend/package.json', import.meta.url))");
replace("await import('./candidate-v2-journeys.mjs')", `await import(${JSON.stringify(pathToFileURL(path.join(root, 'frontend/tests/candidate-v2-journeys.mjs')).href)})`);
replace("const outDir = path.join(frontendDir, 'test-artifacts', smoke ? 'release-smoke' : 'product-completion-browser');", `const outDir = path.join(frontendDir, 'test-artifacts', 'stage-c-capability-${stamp}');`);
const hook = `
  writeFileSync(path.join(outDir, 'baseline-results.json'), JSON.stringify({ testedSHA: '${testedSHA}', passed: checks.length, checks }, null, 2));
  const { providerJourneys } = await import(${JSON.stringify(new URL('../candidate-v3-stage-a/provider-journeys.mjs', import.meta.url).href)});
  await providerJourneys({ origin, browser, cookies, outDir, testedSHA: '${testedSHA}' });
  const { capabilityReadOnly } = await import(${JSON.stringify(new URL('./capability-readonly.mjs', import.meta.url).href)});
  await capabilityReadOnly({ origin, call, csrf, outDir, testedSHA: '${testedSHA}' });
`;
replace("  writeFileSync(path.join(outDir, 'results.json')", hook + "  writeFileSync(path.join(outDir, 'results.json')");
const script = path.join(temporary, `stage-c-runner-${stamp}.mjs`); writeFileSync(script, source);
console.log(`Stage-C combined candidate ${testedSHA}; frontend/test-artifacts/stage-c-capability-${stamp}`);
const child = spawn(process.execPath, [script, '--candidate-v2'], {
  cwd: root, stdio: 'inherit', windowsHide: true,
  env: { ...process.env, FINSIGHT_BROWSER_TMP_DIR: path.join(temporary, `stage-c-runtime-${stamp}`) },
});
const [code] = await once(child, 'exit'); process.exitCode = code ?? 1;
